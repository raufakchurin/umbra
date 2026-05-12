package ru.myit.vlevpn.domain.model

enum class ProxyProtocol(val displayName: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    CUSTOM_JSON("Custom Xray JSON"),
    OLCRTC("olcRTC"),
}
