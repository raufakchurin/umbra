package ru.myit.vlevpn.runtime.olcrtc

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.myit.vlevpn.olcrtcbind.mobile.LogWriter
import ru.myit.vlevpn.olcrtcbind.mobile.Mobile
import ru.myit.vlevpn.olcrtcbind.mobile.SocketProtector
import ru.myit.vlevpn.runtime.contract.OlcRtcPingRequest
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeCallbacks
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeEngine
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeRequest
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeStats

@Singleton
class OlcRtcAndroidRuntime @Inject constructor() : OlcRtcRuntimeEngine {
    private val mutex = Mutex()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2socksThread: Thread? = null
    private var localSocksPort: Int = LOCAL_SOCKS_PORT_BASE
    private var nextSocksPort: Int = LOCAL_SOCKS_PORT_BASE
    private var mobileLibraryLoaded: Boolean = false
    @Volatile
    private var tun2socksStarted: Boolean = false
    @Volatile
    private var tun2socksStopRequested: Boolean = false

    override suspend fun start(
        service: VpnService,
        request: OlcRtcRuntimeRequest,
        callbacks: OlcRtcRuntimeCallbacks,
    ) {
        mutex.withLock {
            stopLocked(waitForSocksPort = false)
            try {
                localSocksPort = chooseAvailableSocksPort()
                    ?: error("No free local SOCKS port for olcRTC")
                configureMobile(request, callbacks)
                callbacks.log(
                    "Starting olcRTC carrier=${request.carrierName}, transport=${request.transportName}, room=${request.roomId}",
                )
                Mobile.startWithTransport(
                    request.carrierName,
                    request.transportName,
                    request.roomId,
                    request.clientId,
                    request.keyHex,
                    localSocksPort.toLong(),
                    "",
                    "",
                )
                Mobile.waitReady(MOBILE_READY_TIMEOUT_MS)
                callbacks.log("olcRTC SOCKS ready on 127.0.0.1:$localSocksPort")

                val descriptor = establishVpn(service, request, callbacks)
                vpnInterface = descriptor
                startTun2Socks(service, descriptor, request, callbacks)
                delay(TUN2SOCKS_HANDOFF_DELAY_MS)
            } catch (error: Throwable) {
                stopLocked(waitForSocksPort = true)
                throw error
            }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            stopLocked(waitForSocksPort = true)
        }
    }

    override suspend fun queryStats(): OlcRtcRuntimeStats = withContext(Dispatchers.IO) {
        if (!OlcRtcTun2Socks.ensureLoaded()) return@withContext OlcRtcRuntimeStats()
        val stats = runCatching { OlcRtcTun2Socks.statsNative() }.getOrNull()
        OlcRtcRuntimeStats(
            uplinkBytes = stats?.getOrNull(TX_BYTES_INDEX) ?: 0L,
            downlinkBytes = stats?.getOrNull(RX_BYTES_INDEX) ?: 0L,
        )
    }

    override suspend fun measureDelay(request: OlcRtcPingRequest): Long = withContext(Dispatchers.IO) {
        loadMobileLibrary()
        Mobile.setProviders()
        Mobile.setLink("direct")
        val socksPort = mutex.withLock {
            chooseAvailableSocksPort()
                ?: error("No free local SOCKS port for olcRTC ping")
        }
        Mobile.ping(
            request.carrierName,
            request.transportName,
            request.roomId,
            request.clientId,
            request.keyHex,
            socksPort.toLong(),
            request.timeoutMillis.toLong(),
            request.pingUrl,
            DEFAULT_VP8_FPS.toLong(),
            DEFAULT_VP8_BATCH.toLong(),
        )
    }

    private fun configureMobile(
        request: OlcRtcRuntimeRequest,
        callbacks: OlcRtcRuntimeCallbacks,
    ) {
        loadMobileLibrary()
        Mobile.setProviders()
        Mobile.setProtector(
            object : SocketProtector {
                override fun protect(fd: Long): Boolean = callbacks.protect(fd.toInt())
            },
        )
        Mobile.setLogWriter(
            object : LogWriter {
                override fun writeLog(msg: String) {
                    callbacks.log(msg)
                }
            },
        )
        Mobile.setDebug(request.debugLogging)
        Mobile.setLink("direct")
        Mobile.setTransport(request.transportName)
        Mobile.setDNS(request.dnsServers.firstOrNull().asDnsEndpoint())
        Mobile.setVP8Options(DEFAULT_VP8_FPS.toLong(), DEFAULT_VP8_BATCH.toLong())
        callbacks.log("olcRTC protect/log bridges are installed for Android VPN mode")
    }

    private fun establishVpn(
        service: VpnService,
        request: OlcRtcRuntimeRequest,
        callbacks: OlcRtcRuntimeCallbacks,
    ): ParcelFileDescriptor = with(request) {
        val builder = service.Builder()
            .setSession(sessionName)
            .setMtu(tunMtu)
            .addAddress(TUN_IPV4_ADDRESS, IPV4_PREFIX_LENGTH)
            .addDnsServer(request.dnsServers.firstOrNull().asDnsAddress())
            .setBlocking(true)

        if (routeAllTraffic) {
            builder.addRoute("0.0.0.0", 0)
        }
        if (allowIpv6) {
            callbacks.log("olcRTC IPv6 routing is not enabled yet; using IPv4 TUN")
        }
        runCatching { builder.addDisallowedApplication(ownPackageName) }
            .onFailure { callbacks.log("Could not exclude own package from olcRTC VPN: ${it.message.orEmpty()}") }
        excludedPackageNames
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != ownPackageName }
            .distinct()
            .forEach { packageName ->
                runCatching { builder.addDisallowedApplication(packageName) }
                    .onFailure { callbacks.log("Could not exclude $packageName from olcRTC VPN: ${it.message.orEmpty()}") }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.establish() ?: error("Android did not return an olcRTC VPN descriptor")
    }

    private fun startTun2Socks(
        service: VpnService,
        descriptor: ParcelFileDescriptor,
        request: OlcRtcRuntimeRequest,
        callbacks: OlcRtcRuntimeCallbacks,
    ) {
        if (!OlcRtcTun2Socks.ensureLoaded()) {
            error("olcRTC tun2socks native libraries are unavailable")
        }
        val nativeFd = ParcelFileDescriptor.dup(descriptor.fileDescriptor).detachFd()
        val configFile = writeTun2SocksConfig(service, request)
        tun2socksStarted = true
        tun2socksStopRequested = false
        tun2socksThread = thread(name = "VleOlcRtcTun2Socks", isDaemon = true) {
            try {
                val result = OlcRtcTun2Socks.startNative(configFile.absolutePath, nativeFd)
                if (!tun2socksStopRequested && result != 0) {
                    callbacks.log("olcRTC tun2socks exited with code $result")
                }
            } finally {
                tun2socksStarted = false
                tun2socksStopRequested = false
            }
        }
        callbacks.log("olcRTC tun2socks started")
    }

    private fun writeTun2SocksConfig(service: VpnService, request: OlcRtcRuntimeRequest): File {
        val file = File(service.filesDir, TUN2SOCKS_CONFIG_FILE_NAME)
        file.writeText(buildTun2SocksConfig(request, localSocksPort))
        return file
    }

    private suspend fun stopLocked(waitForSocksPort: Boolean) = withContext(Dispatchers.IO) {
        if (OlcRtcTun2Socks.ensureLoaded() && tun2socksStarted && !tun2socksStopRequested) {
            tun2socksStopRequested = true
            runCatching { OlcRtcTun2Socks.stopNative() }
        }
        if (mobileLibraryLoaded) {
            runCatching { Mobile.stop() }
        }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        tun2socksThread?.interrupt()
        tun2socksThread = null
        if (waitForSocksPort) waitForSocksPortReleased(localSocksPort)
    }

    private fun loadMobileLibrary() {
        if (mobileLibraryLoaded) return
        synchronized(this) {
            if (!mobileLibraryLoaded) {
                System.loadLibrary("olcrtcjni")
                mobileLibraryLoaded = true
            }
        }
    }

    private suspend fun waitForSocksPortReleased(port: Int) {
        val deadline = System.currentTimeMillis() + SOCKS_RELEASE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!isLocalSocksPortOpen(port)) return
            delay(SOCKS_RELEASE_POLL_MS)
        }
    }

    private fun chooseAvailableSocksPort(): Int? {
        repeat(LOCAL_SOCKS_PORT_MAX - LOCAL_SOCKS_PORT_BASE + 1) {
            val candidate = nextSocksPort
            nextSocksPort = if (candidate >= LOCAL_SOCKS_PORT_MAX) {
                LOCAL_SOCKS_PORT_BASE
            } else {
                candidate + 1
            }
            if (!isLocalSocksPortOpen(candidate)) return candidate
        }
        return null
    }

    private fun isLocalSocksPortOpen(port: Int): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), SOCKET_CONNECT_TIMEOUT_MS)
            }
        }.isSuccess

}

private const val TUN2SOCKS_CONFIG_FILE_NAME = "olcrtc-tun2socks.yaml"
private const val LOCAL_SOCKS_PORT_BASE = 10818
private const val LOCAL_SOCKS_PORT_MAX = 10858
private const val MOBILE_READY_TIMEOUT_MS = 25_000L
private const val TUN2SOCKS_HANDOFF_DELAY_MS = 300L
private const val SOCKS_RELEASE_TIMEOUT_MS = 2_500L
private const val SOCKS_RELEASE_POLL_MS = 100L
private const val SOCKET_CONNECT_TIMEOUT_MS = 150
private const val TUN_IPV4_ADDRESS = "10.0.88.88"
private const val IPV4_PREFIX_LENGTH = 24
private const val MAPDNS_NETWORK = "100.64.0.0"
private const val MAPDNS_NETMASK = "255.192.0.0"
private const val DEFAULT_VP8_FPS = 120
private const val DEFAULT_VP8_BATCH = 64
private const val TX_BYTES_INDEX = 1
private const val RX_BYTES_INDEX = 3

internal fun buildTun2SocksConfig(
    request: OlcRtcRuntimeRequest,
    localSocksPort: Int,
): String = """
    tunnel:
      name: tun0
      mtu: ${request.tunMtu}
      multi-queue: false
      ipv4: $TUN_IPV4_ADDRESS

    socks5:
      address: 127.0.0.1
      port: $localSocksPort
      udp: 'udp'
      pipeline: false

    mapdns:
      address: ${request.dnsServers.firstOrNull().asDnsAddress()}
      port: 53
      network: $MAPDNS_NETWORK
      netmask: $MAPDNS_NETMASK
      cache-size: 10000

    misc:
      task-stack-size: 24576
      tcp-buffer-size: 4096
      max-session-count: 1200
      connect-timeout: 10000
      tcp-read-write-timeout: 300000
      udp-read-write-timeout: 60000
      log-file: stderr
      log-level: warn
""".trimIndent()

private fun String?.asDnsAddress(): String =
    this?.substringBefore(":")?.takeIf { value ->
        runCatching { InetAddress.getByName(value) }.isSuccess
    } ?: "1.1.1.1"

private fun String?.asDnsEndpoint(): String =
    this?.takeIf { ":" in it } ?: "${asDnsAddress()}:53"
