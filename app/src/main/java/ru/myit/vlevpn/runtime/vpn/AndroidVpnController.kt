package ru.myit.vlevpn.runtime.vpn

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.domain.model.VpnProfile
import ru.myit.vlevpn.runtime.VpnController
import ru.myit.vlevpn.runtime.VpnSession

class AndroidVpnController(
    private val service: VpnService,
    private val ownPackageName: String,
    private val logs: LogRepository,
) : VpnController {
    private var tunFd: Int? = null

    override suspend fun establish(profile: VpnProfile): VpnSession = withContext(Dispatchers.IO) {
        val builder = service.Builder()
            .setSession(profile.sessionName)
            .setMtu(profile.mtu)
            .addAddress("10.0.0.1", 30)

        if (profile.routeAllTraffic) {
            builder.addRoute("0.0.0.0", 0)
        }

        if (profile.allowIpv6) {
            builder.addAddress("fd26:2626::1", 126)
            if (profile.routeAllTraffic) {
                builder.addRoute("::", 0)
            }
        }

        profile.dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { logs.add(LogLevel.WARN, "Skipped invalid DNS server: $dns") }
        }

        runCatching { builder.addDisallowedApplication(ownPackageName) }
            .onFailure { logs.add(LogLevel.WARN, "Could not exclude own package from VPN: ${it.message.orEmpty()}") }
        profile.excludedPackageNames
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != ownPackageName }
            .distinct()
            .forEach { packageName ->
                runCatching { builder.addDisallowedApplication(packageName) }
                    .onFailure { logs.add(LogLevel.WARN, "Could not exclude $packageName from VPN: ${it.message.orEmpty()}") }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val descriptor = builder.establish() ?: error("Android did not return a VPN file descriptor")
        val fd = descriptor.detachFd()
        tunFd = fd
        VpnSession(
            tunFd = fd,
            close = { close() },
        )
    }

    override fun protect(fd: Int): Boolean = service.protect(fd)

    fun protect(socket: Socket): Boolean = service.protect(socket)

    override suspend fun close() = withContext(Dispatchers.IO) {
        val fd = tunFd ?: return@withContext
        tunFd = null
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            .onFailure { error ->
                if (error !is IOException) {
                    logs.add(LogLevel.WARN, "Unexpected TUN close error: ${error.message.orEmpty()}")
                }
            }
    }
}
