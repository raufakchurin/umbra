package ru.myit.vlevpn.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile

class XrayConfigFactoryTest {
    private val factory = XrayConfigFactory()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun buildsVlessOutboundAndTunInbound() {
        val config = json.parseToJsonElement(factory.build(vlessProfile(), AppSettings.Default)).jsonObject

        val inbounds = config["inbounds"]!!.jsonArray
        val outbounds = config["outbounds"]!!.jsonArray

        assertTrue(inbounds.any { it.jsonObject["tag"]!!.jsonPrimitive.content == "tun" })
        assertEquals("vless", outbounds.first().jsonObject["protocol"]!!.jsonPrimitive.content)
        assertEquals("proxy", outbounds.first().jsonObject["tag"]!!.jsonPrimitive.content)
    }

    @Test
    fun returnsCustomJsonAsValidJson() {
        val custom = vlessProfile().copy(
            protocol = ProxyProtocol.CUSTOM_JSON,
            customJson = """{"inbounds":[],"outbounds":[{"tag":"proxy","protocol":"freedom"}]}""",
        )

        val config = json.parseToJsonElement(factory.build(custom, AppSettings.Default)).jsonObject

        assertEquals("freedom", config["outbounds"]!!.jsonArray.first().jsonObject["protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun omitsRuntimeMetricsByDefault() {
        val config = json.parseToJsonElement(factory.build(vlessProfile(), AppSettings.Default)).jsonObject

        assertFalse("stats" in config)
        assertFalse("metrics" in config)
        assertFalse("system" in config["policy"]!!.jsonObject)
    }

    @Test
    fun includesRuntimeMetricsWhenRequested() {
        val config = json.parseToJsonElement(
            factory.build(vlessProfile(), AppSettings.Default, includeMetrics = true),
        ).jsonObject

        assertTrue("stats" in config)
        assertTrue("metrics" in config)
        assertTrue("system" in config["policy"]!!.jsonObject)
    }

    @Test
    fun removesMetricsFromCustomJsonByDefault() {
        val custom = vlessProfile().copy(
            protocol = ProxyProtocol.CUSTOM_JSON,
            customJson = """
                {
                  "stats": {},
                  "metrics": {"tag": "metrics"},
                  "policy": {
                    "system": {
                      "statsOutboundUplink": true,
                      "statsOutboundDownlink": true
                    }
                  },
                  "inbounds": [],
                  "outbounds": [{"tag":"proxy","protocol":"freedom"}]
                }
            """.trimIndent(),
        )

        val config = json.parseToJsonElement(factory.build(custom, AppSettings.Default)).jsonObject

        assertFalse("stats" in config)
        assertFalse("metrics" in config)
        assertEquals(0, config["policy"]!!.jsonObject["system"]!!.jsonObject.size)
    }

    @Test
    fun buildsVlessRealitySettings() {
        val profile = vlessProfile().copy(
            security = "reality",
            flow = "xtls-rprx-vision",
            fingerprint = "chrome",
            publicKey = "public-key",
            shortId = "abcd",
            spiderX = "/",
        )

        val config = json.parseToJsonElement(factory.build(profile, AppSettings.Default)).jsonObject
        val outbound = config["outbounds"]!!.jsonArray.first().jsonObject
        val user = outbound["settings"]!!.jsonObject["vnext"]!!.jsonArray
            .first().jsonObject["users"]!!.jsonArray.first().jsonObject
        val stream = outbound["streamSettings"]!!.jsonObject
        val reality = stream["realitySettings"]!!.jsonObject

        assertEquals("reality", stream["security"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", user["flow"]!!.jsonPrimitive.content)
        assertEquals("chrome", reality["fingerprint"]!!.jsonPrimitive.content)
        assertEquals("public-key", reality["password"]!!.jsonPrimitive.content)
        assertEquals("public-key", reality["publicKey"]!!.jsonPrimitive.content)
        assertEquals("abcd", reality["shortId"]!!.jsonPrimitive.content)
        assertEquals("/", reality["spiderX"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsBatchProxyPingConfigWithOneInboundPerServer() {
        val config = json.parseToJsonElement(
            factory.buildProxyPingConfig(
                targets = listOf(
                    vlessProfile() to 21001,
                    vlessProfile().copy(id = ServerId("test-2"), host = "vpn2.example.invalid") to 21002,
                ),
                settings = AppSettings.Default,
            ),
        ).jsonObject

        val inbounds = config["inbounds"]!!.jsonArray.map { it.jsonObject }
        val outbounds = config["outbounds"]!!.jsonArray.map { it.jsonObject }
        val rules = config["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }

        assertEquals(listOf("ping-in-0", "ping-in-1"), inbounds.map { it["tag"]!!.jsonPrimitive.content })
        assertEquals(listOf("ping-out-0", "ping-out-1"), outbounds.take(2).map { it["tag"]!!.jsonPrimitive.content })
        assertEquals("ping-out-0", rules.first()["outboundTag"]!!.jsonPrimitive.content)
        assertEquals("ping-out-1", rules[1]["outboundTag"]!!.jsonPrimitive.content)
    }

    private fun vlessProfile(): ServerProfile = ServerProfile(
        id = ServerId("test"),
        name = "Test",
        protocol = ProxyProtocol.VLESS,
        host = "vpn.example.invalid",
        port = 443,
        credential = "00000000-0000-4000-8000-000000000001",
        password = "",
        method = "",
        transport = "tcp",
        security = "tls",
        sni = "vpn.example.invalid",
        path = "",
        customJson = "",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
}
