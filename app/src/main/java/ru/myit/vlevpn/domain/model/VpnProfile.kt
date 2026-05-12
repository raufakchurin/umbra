package ru.myit.vlevpn.domain.model

data class VpnProfile(
    val sessionName: String,
    val mtu: Int,
    val dnsServers: List<String>,
    val routeAllTraffic: Boolean,
    val allowIpv6: Boolean,
    val excludedPackageNames: Set<String>,
)
