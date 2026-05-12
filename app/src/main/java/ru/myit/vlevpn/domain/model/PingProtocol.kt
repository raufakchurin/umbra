package ru.myit.vlevpn.domain.model

enum class PingProtocol(val displayName: String) {
    PROXY_GET("Через прокси(GET)"),
    PROXY_HEAD("Через прокси(HEAD)"),
    TCP("TCP"),
    ICMP("ICMP"),
}
