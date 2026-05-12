package ru.myit.vlevpn.data.ping

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import libXray.DialerController
import libXray.LibXray
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.PingRepository
import ru.myit.vlevpn.runtime.DelayResult
import ru.myit.vlevpn.runtime.XrayConfigFactory
import ru.myit.vlevpn.runtime.contract.OlcRtcPingRequest
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeEngine

@Singleton
class DefaultPingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configFactory: XrayConfigFactory,
    private val olcRtcRuntimeEngines: Set<@JvmSuppressWildcards OlcRtcRuntimeEngine>,
) : PingRepository {
    private val proxyPingMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun measure(
        server: ServerProfile,
        protocol: PingProtocol,
        settings: AppSettings,
    ): DelayResult {
        if (server.protocol == ProxyProtocol.OLCRTC) {
            return olcRtcPing(server)
        }
        return when (protocol) {
            PingProtocol.PROXY_GET -> measureAll(listOf(server), protocol, settings).first()
            PingProtocol.PROXY_HEAD -> measureAll(listOf(server), protocol, settings).first()
            PingProtocol.TCP -> tcpPing(server)
            PingProtocol.ICMP -> icmpPing(server)
        }
    }

    override fun measureAll(
        servers: List<ServerProfile>,
        protocol: PingProtocol,
        settings: AppSettings,
    ): Flow<DelayResult> = when (protocol) {
        PingProtocol.PROXY_GET -> proxyHttpPings(servers, settings, "GET")
        PingProtocol.PROXY_HEAD -> proxyHttpPings(servers, settings, "HEAD")
        PingProtocol.TCP,
        PingProtocol.ICMP,
        -> parallelSocketPings(servers, protocol, settings)
    }

    private fun parallelSocketPings(
        servers: List<ServerProfile>,
        protocol: PingProtocol,
        settings: AppSettings,
    ): Flow<DelayResult> = channelFlow {
        val semaphore = Semaphore(PARALLEL_SOCKET_PING_LIMIT)
        servers.forEach { server ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    send(measure(server, protocol, settings))
                }
            }
        }
    }

    private fun proxyHttpPings(
        servers: List<ServerProfile>,
        settings: AppSettings,
        method: String,
    ): Flow<DelayResult> = channelFlow {
        if (servers.isEmpty()) return@channelFlow
        val olcRtcServers = servers.filter { it.protocol == ProxyProtocol.OLCRTC }
        olcRtcServers.forEach { server ->
            launch(Dispatchers.IO) {
                send(olcRtcPing(server))
            }
        }
        val xrayServers = servers.filterNot { it.protocol == ProxyProtocol.OLCRTC }
        if (xrayServers.isEmpty()) return@channelFlow
        withContext(Dispatchers.IO) {
            proxyPingMutex.withLock {
                if (LibXray.getXrayState()) {
                    xrayServers.forEach { server ->
                        send(DelayResult(server.id, null, "Another Xray ping is already running"))
                    }
                    return@withLock
                }

                var started = false
                try {
                    initLibXrayDns()
                    val pingSettings = settings.copy(
                        xrayTunModeEnabled = false,
                        routeAllTraffic = false,
                        allowIpv6 = false,
                        debugConfigPreviewEnabled = false,
                    )
                    val ports = allocateLocalPorts(xrayServers.size)
                    val configJson = configFactory.buildProxyPingConfig(xrayServers.zip(ports), pingSettings)
                    val request = LibXray.newXrayRunFromJSONRequest(
                        context.filesDir.absolutePath,
                        "",
                        configJson,
                    )
                    decodeUnitResponse(LibXray.runXrayFromJSON(request))
                    started = true
                    delay(PROXY_STARTUP_DELAY_MILLIS)
                    coroutineScope {
                        val semaphore = Semaphore(PARALLEL_PROXY_HTTP_LIMIT)
                        xrayServers.zip(ports).forEach { (server, port) ->
                            launch(Dispatchers.IO) {
                                semaphore.withPermit {
                                    send(proxyHttpRequestPing(server, port, method))
                                }
                            }
                        }
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    xrayServers.forEach { server ->
                        send(DelayResult(server.id, null, error.safeMessage()))
                    }
                } finally {
                    if (started || LibXray.getXrayState()) {
                        runCatching { decodeUnitResponse(LibXray.stopXray()) }
                    }
                }
            }
        }
    }

    private suspend fun olcRtcPing(server: ServerProfile): DelayResult = withContext(Dispatchers.IO) {
        runCatching {
            val engine = olcRtcRuntimeEngines.firstOrNull()
                ?: error("olcRTC runtime module is not included in this build")
            engine.measureDelay(server.toOlcRtcPingRequest())
        }.fold(
            onSuccess = { delay -> DelayResult(server.id, delay) },
            onFailure = { error -> DelayResult(server.id, null, error.safeMessage()) },
        )
    }

    private suspend fun tcpPing(server: ServerProfile): DelayResult = withContext(Dispatchers.IO) {
        runCatching {
            val startedAt = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(server.host, server.port), TCP_TIMEOUT_MILLIS)
            }
            elapsedMs(startedAt)
        }.fold(
            onSuccess = { delay -> DelayResult(server.id, delay) },
            onFailure = { error -> DelayResult(server.id, null, error.safeMessage()) },
        )
    }

    private suspend fun icmpPing(server: ServerProfile): DelayResult = withContext(Dispatchers.IO) {
        runCatching {
            val startedAt = System.nanoTime()
            val reachable = InetAddress.getByName(server.host).isReachable(ICMP_TIMEOUT_MILLIS)
            if (!reachable) error("timeout")
            elapsedMs(startedAt)
        }.fold(
            onSuccess = { delay -> DelayResult(server.id, delay) },
            onFailure = { error -> DelayResult(server.id, null, error.safeMessage()) },
        )
    }

    private fun proxyHttpRequestPing(server: ServerProfile, port: Int, method: String): DelayResult {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
            .connectTimeout(PROXY_HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(PROXY_HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .writeTimeout(PROXY_HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .callTimeout(PROXY_HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

        var lastError: Throwable? = null
        PROXY_PING_URLS.forEach { url ->
            val startedAt = System.nanoTime()
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .method(method, null)
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code !in 200..399) {
                        error("HTTP ${response.code}")
                    }
                    elapsedMs(startedAt)
                }
            }
            result.onSuccess { delay -> return DelayResult(server.id, delay) }
            lastError = result.exceptionOrNull()
        }

        return DelayResult(server.id, null, lastError.safeMessageOrDefault())
    }

    private fun elapsedMs(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).coerceAtLeast(1L)

    private fun decodeUnitResponse(base64: String) {
        val obj = json.parseToJsonElement(
            String(Base64.decode(base64, Base64.DEFAULT)),
        ).jsonObject
        if (obj["success"]?.jsonPrimitive?.content != "true") {
            val error = obj["error"]?.jsonPrimitive?.content.orEmpty()
            throw IllegalStateException(error.ifBlank { "libXray call failed" })
        }
    }

    private fun allocateLocalPorts(count: Int): List<Int> {
        val sockets = mutableListOf<ServerSocket>()
        return try {
            buildList {
                repeat(count) {
                    val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
                    sockets += socket
                    add(socket.localPort)
                }
            }
        } finally {
            sockets.forEach { socket -> runCatching { socket.close() } }
        }
    }

    private fun Throwable.safeMessage(): String =
        message?.take(96)?.ifBlank { null } ?: "Ping failed"

    private fun Throwable?.safeMessageOrDefault(): String =
        this?.safeMessage() ?: "Ping failed"

    private fun initLibXrayDns() {
        val controller = DialerController { true }
        LibXray.registerDialerController(controller)
        LibXray.initDns(controller, "1.1.1.1:53")
    }

    private companion object {
        val PROXY_PING_URLS = listOf(
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204",
            "https://www.google.com/generate_204",
        )
        const val PROXY_STARTUP_DELAY_MILLIS = 250L
        const val PROXY_HTTP_TIMEOUT_MILLIS = 3_000L
        const val PARALLEL_PROXY_HTTP_LIMIT = 8
        const val PARALLEL_SOCKET_PING_LIMIT = 16
        const val TCP_TIMEOUT_MILLIS = 5_000
        const val ICMP_TIMEOUT_MILLIS = 5_000
    }
}

private fun ServerProfile.toOlcRtcPingRequest(): OlcRtcPingRequest {
    val carrierName = host.trim()
    val transportName = transport.trim()
    val roomId = path.trim()
    val clientId = password.trim()
    val keyHex = credential.trim()
    require(carrierName.isNotBlank()) { "olcRTC carrier is required" }
    require(transportName.isNotBlank()) { "olcRTC transport is required" }
    require(roomId.isNotBlank()) { "olcRTC room ID is required" }
    require(clientId.isNotBlank()) { "olcRTC client ID is required" }
    require(keyHex.isNotBlank()) { "olcRTC key is required" }
    return OlcRtcPingRequest(
        carrierName = carrierName,
        transportName = transportName,
        roomId = roomId,
        clientId = clientId,
        keyHex = keyHex,
    )
}
