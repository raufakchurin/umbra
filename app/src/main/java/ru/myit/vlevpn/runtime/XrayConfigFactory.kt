package ru.myit.vlevpn.runtime

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerProfile

@Singleton
class XrayConfigFactory @Inject constructor() {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Enable metrics only inside the isolated VPN process; libXray registers Go expvar globally.
    fun build(server: ServerProfile, settings: AppSettings, includeMetrics: Boolean = false): String {
        if (server.protocol == ProxyProtocol.CUSTOM_JSON) {
            val parsed = json.parseToJsonElement(server.customJson).jsonObject
            val config = if (includeMetrics) parsed else parsed.withoutRuntimeMetrics()
            return json.encodeToString(JsonObject.serializer(), config)
        }

        val config = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", if (settings.logsEnabled) "warning" else "none")
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    settings.dnsServers.forEach { add(JsonPrimitive(it)) }
                }
            }
            putJsonArray("inbounds") {
                add(socksInbound(settings.socksPort))
                add(httpInbound(settings.httpPort))
                if (settings.xrayTunModeEnabled) {
                    add(tunInbound(settings.tunMtu))
                }
            }
            putJsonArray("outbounds") {
                add(proxyOutbound(server))
                add(taggedOutbound("direct", "freedom"))
                add(taggedOutbound("block", "blackhole"))
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {}
            }
            putJsonObject("policy") {
                putJsonObject("levels") {
                    putJsonObject("8") {
                        put("connIdle", 300)
                        put("uplinkOnly", 1)
                        put("downlinkOnly", 1)
                    }
                }
                if (includeMetrics) {
                    putJsonObject("system") {
                        put("statsInboundUplink", true)
                        put("statsInboundDownlink", true)
                        put("statsOutboundUplink", true)
                        put("statsOutboundDownlink", true)
                    }
                }
            }
            if (includeMetrics) {
                putJsonObject("metrics") {
                    put("tag", "metrics")
                    put("listen", XrayMetrics.LISTEN)
                }
                putJsonObject("stats") {}
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    fun buildProxyPingConfig(targets: List<Pair<ServerProfile, Int>>, settings: AppSettings): String {
        require(targets.isNotEmpty()) { "Ping targets are empty" }
        val config = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", if (settings.logsEnabled) "warning" else "none")
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    settings.dnsServers.forEach { add(JsonPrimitive(it)) }
                }
            }
            putJsonArray("inbounds") {
                targets.forEachIndexed { index, (_, port) ->
                    add(httpInbound(port, tag = pingInboundTag(index)))
                }
            }
            putJsonArray("outbounds") {
                targets.forEachIndexed { index, (server, _) ->
                    add(proxyOutbound(server, tag = pingOutboundTag(index)))
                }
                add(taggedOutbound("direct", "freedom"))
                add(taggedOutbound("block", "blackhole"))
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    targets.indices.forEach { index ->
                        add(
                            buildJsonObject {
                                put("type", "field")
                                putJsonArray("inboundTag") {
                                    add(JsonPrimitive(pingInboundTag(index)))
                                }
                                put("outboundTag", pingOutboundTag(index))
                            },
                        )
                    }
                }
            }
            putJsonObject("policy") {
                putJsonObject("levels") {
                    putJsonObject("8") {
                        put("connIdle", 300)
                        put("uplinkOnly", 1)
                        put("downlinkOnly", 1)
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun JsonObject.withoutRuntimeMetrics(): JsonObject = buildJsonObject {
        this@withoutRuntimeMetrics.forEach { (key, value) ->
            when (key) {
                "metrics", "stats" -> Unit
                "policy" -> put(key, value.withoutPolicyStats())
                else -> put(key, value)
            }
        }
    }

    private fun JsonElement.withoutPolicyStats(): JsonElement {
        val policy = this as? JsonObject ?: return this
        return buildJsonObject {
            policy.forEach { (key, value) ->
                if (key == "system") {
                    val system = value as? JsonObject ?: return@forEach
                    put(
                        key,
                        JsonObject(
                            system.filterKeys { systemKey ->
                                !systemKey.startsWith("stats", ignoreCase = true)
                            },
                        ),
                    )
                } else {
                    put(key, value)
                }
            }
        }
    }

    private fun socksInbound(port: Int, tag: String = "socks"): JsonObject = buildJsonObject {
        put("tag", tag)
        put("listen", "127.0.0.1")
        put("port", port)
        put("protocol", "socks")
        putJsonObject("settings") {
            put("auth", "noauth")
            put("udp", true)
            put("userLevel", 8)
        }
        putSniffing()
    }

    private fun httpInbound(port: Int, tag: String = "http"): JsonObject = buildJsonObject {
        put("tag", tag)
        put("listen", "127.0.0.1")
        put("port", port)
        put("protocol", "http")
        putJsonObject("settings") {
            put("userLevel", 8)
        }
        putSniffing()
    }

    private fun tunInbound(mtu: Int): JsonObject = buildJsonObject {
        put("tag", "tun")
        put("protocol", "tun")
        putJsonObject("settings") {
            put("name", "vle0")
            put("mtu", mtu)
            put("stack", "system")
        }
    }

    private fun proxyOutbound(server: ServerProfile, tag: String = "proxy"): JsonObject =
        when (server.protocol) {
            ProxyProtocol.VLESS -> vlessOutbound(server, tag)
            ProxyProtocol.VMESS -> vmessOutbound(server, tag)
            ProxyProtocol.TROJAN -> trojanOutbound(server, tag)
            ProxyProtocol.SHADOWSOCKS -> shadowsocksOutbound(server, tag)
            ProxyProtocol.CUSTOM_JSON -> customJsonOutbound(server, tag)
            ProxyProtocol.OLCRTC -> error("olcRTC profiles require the olcRTC runtime")
        }

    private fun customJsonOutbound(server: ServerProfile, tag: String): JsonObject {
        val config = json.parseToJsonElement(server.customJson).jsonObject.withoutRuntimeMetrics()
        val outbounds = config["outbounds"]?.jsonArray ?: error("Custom JSON has no outbounds")
        val source = outbounds
            .mapNotNull { it as? JsonObject }
            .firstOrNull { outbound ->
                val protocol = (outbound["protocol"] as? JsonPrimitive)?.content.orEmpty()
                protocol != "freedom" && protocol != "blackhole"
            }
            ?: outbounds.firstOrNull() as? JsonObject
            ?: error("Custom JSON has no usable outbound")
        return source.withTag(tag)
    }

    private fun JsonObject.withTag(tag: String): JsonObject = buildJsonObject {
        this@withTag.forEach { (key, value) ->
            put(key, if (key == "tag") JsonPrimitive(tag) else value)
        }
        if ("tag" !in this@withTag) {
            put("tag", tag)
        }
    }

    private fun vlessOutbound(server: ServerProfile, tag: String): JsonObject = buildJsonObject {
        put("tag", tag)
        put("protocol", "vless")
        putJsonObject("settings") {
            putJsonArray("vnext") {
                add(
                    buildJsonObject {
                        put("address", server.host)
                        put("port", server.port)
                        putJsonArray("users") {
                            add(
                                buildJsonObject {
                                    put("id", server.credential)
                                    put("encryption", "none")
                                    put("level", 8)
                                    if (server.flow.isNotBlank()) {
                                        put("flow", server.flow)
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
        putStreamSettings(server)
    }

    private fun vmessOutbound(server: ServerProfile, tag: String): JsonObject = buildJsonObject {
        put("tag", tag)
        put("protocol", "vmess")
        putJsonObject("settings") {
            putJsonArray("vnext") {
                add(
                    buildJsonObject {
                        put("address", server.host)
                        put("port", server.port)
                        putJsonArray("users") {
                            add(
                                buildJsonObject {
                                    put("id", server.credential)
                                    put("alterId", 0)
                                    put("security", "auto")
                                    put("level", 8)
                                },
                            )
                        }
                    },
                )
            }
        }
        putStreamSettings(server)
    }

    private fun trojanOutbound(server: ServerProfile, tag: String): JsonObject = buildJsonObject {
        put("tag", tag)
        put("protocol", "trojan")
        putJsonObject("settings") {
            putJsonArray("servers") {
                add(
                    buildJsonObject {
                        put("address", server.host)
                        put("port", server.port)
                        put("password", server.password.ifBlank { server.credential })
                        put("level", 8)
                    },
                )
            }
        }
        putStreamSettings(server)
    }

    private fun shadowsocksOutbound(server: ServerProfile, tag: String): JsonObject = buildJsonObject {
        put("tag", tag)
        put("protocol", "shadowsocks")
        putJsonObject("settings") {
            putJsonArray("servers") {
                add(
                    buildJsonObject {
                        put("address", server.host)
                        put("port", server.port)
                        put("method", server.method.ifBlank { "aes-128-gcm" })
                        put("password", server.password.ifBlank { server.credential })
                        put("uot", true)
                        put("level", 8)
                    },
                )
            }
        }
    }

    private fun taggedOutbound(tag: String, protocol: String): JsonObject = buildJsonObject {
        put("tag", tag)
        put("protocol", protocol)
    }

    private fun pingInboundTag(index: Int): String = "ping-in-$index"

    private fun pingOutboundTag(index: Int): String = "ping-out-$index"

    private fun JsonObjectBuilderScope.putSniffing() {
        putJsonObject("sniffing") {
            put("enabled", true)
            putJsonArray("destOverride") {
                add(JsonPrimitive("http"))
                add(JsonPrimitive("tls"))
                add(JsonPrimitive("quic"))
            }
        }
    }

    private fun JsonObjectBuilderScope.putStreamSettings(server: ServerProfile) {
        val network = server.transport.ifBlank { "tcp" }
        val security = server.security.ifBlank { "none" }
        putJsonObject("streamSettings") {
            put("network", network)
            put("security", security)
            if (security == "tls") {
                putJsonObject("tlsSettings") {
                    if (server.sni.isNotBlank()) put("serverName", server.sni)
                    if (server.fingerprint.isNotBlank()) put("fingerprint", server.fingerprint)
                }
            }
            if (security == "reality") {
                putJsonObject("realitySettings") {
                    if (server.sni.isNotBlank()) put("serverName", server.sni)
                    if (server.fingerprint.isNotBlank()) put("fingerprint", server.fingerprint)
                    if (server.publicKey.isNotBlank()) {
                        put("password", server.publicKey)
                        put("publicKey", server.publicKey)
                    }
                    put("shortId", server.shortId)
                    put("spiderX", server.spiderX)
                }
            }
            if (network == "ws") {
                putJsonObject("wsSettings") {
                    if (server.path.isNotBlank()) put("path", server.path)
                    if (server.headerHost.isNotBlank() || server.host.isNotBlank()) {
                        putJsonObject("headers") {
                            put("Host", server.headerHost.ifBlank { server.sni.ifBlank { server.host } })
                        }
                    }
                }
            }
            if (network == "grpc") {
                putJsonObject("grpcSettings") {
                    put("serviceName", server.path.trim('/'))
                    if (server.networkMode == "multi") put("multiMode", true)
                }
            }
        }
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder

object XrayMetrics {
    const val HOST = "127.0.0.1"
    const val PORT = 49227
    const val LISTEN = "$HOST:$PORT"
    const val URL = "http://$HOST:$PORT/debug/vars"
}
