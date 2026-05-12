package ru.myit.vlevpn.runtime.contract

import android.net.VpnService

data class OlcRtcRuntimeRequest(
    val carrierName: String,
    val transportName: String,
    val roomId: String,
    val clientId: String,
    val keyHex: String,
    val sessionName: String,
    val ownPackageName: String,
    val dnsServers: List<String>,
    val excludedPackageNames: Set<String>,
    val tunMtu: Int,
    val routeAllTraffic: Boolean,
    val allowIpv6: Boolean,
    val debugLogging: Boolean,
)

data class OlcRtcPingRequest(
    val carrierName: String,
    val transportName: String,
    val roomId: String,
    val clientId: String,
    val keyHex: String,
    val timeoutMillis: Int = 10_000,
    val pingUrl: String = "",
)

data class OlcRtcRuntimeStats(
    val uplinkBytes: Long = 0,
    val downlinkBytes: Long = 0,
)

interface OlcRtcRuntimeCallbacks {
    fun protect(fd: Int): Boolean
    fun log(message: String)
}

interface OlcRtcRuntimeEngine {
    suspend fun start(
        service: VpnService,
        request: OlcRtcRuntimeRequest,
        callbacks: OlcRtcRuntimeCallbacks,
    )

    suspend fun stop()
    suspend fun queryStats(): OlcRtcRuntimeStats
    suspend fun measureDelay(request: OlcRtcPingRequest): Long
}
