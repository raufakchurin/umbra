package ru.myit.vlevpn.runtime

import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.VpnProfile

data class RuntimeStats(
    val uplinkBytes: Long = 0,
    val downlinkBytes: Long = 0,
    val delayMs: Long? = null,
)

data class XrayStats(
    val uplinkBytes: Long = 0,
    val downlinkBytes: Long = 0,
)

data class DelayResult(
    val serverId: ServerId,
    val delayMs: Long?,
    val error: String? = null,
)

data class XrayEnvironment(
    val filesDirPath: String,
    val cacheDirPath: String,
    val nativeLibraryDir: String,
)

data class StartProxyRequest(
    val server: ServerProfile,
    val settings: AppSettings,
    val vpnProfile: VpnProfile,
)

data class VpnSession(
    val tunFd: Int,
    val close: suspend () -> Unit,
)
