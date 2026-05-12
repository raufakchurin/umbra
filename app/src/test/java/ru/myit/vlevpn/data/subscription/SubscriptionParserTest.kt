package ru.myit.vlevpn.data.subscription

import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.myit.vlevpn.domain.model.ProxyProtocol

class SubscriptionParserTest {
    private val parser = SubscriptionParser()

    @Test
    fun parsesSingleVlessUri() {
        val result = parser.parse(
            "vless://00000000-0000-4000-8000-000000000001@example.com:443" +
                "?type=ws&security=tls&sni=sni.example.com&path=%2Fws#Main",
        )

        val profile = result.profiles.single()
        assertEquals(ProxyProtocol.VLESS, profile.protocol)
        assertEquals("Main", profile.name)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("ws", profile.transport)
        assertEquals("/ws", profile.path)
        assertEquals("sni.example.com", profile.sni)
        assertEquals("", profile.subscriptionId)
    }

    @Test
    fun parsesVlessRealityUri() {
        val result = parser.parse(
            "vless://00000000-0000-4000-8000-000000000001@example.com:443" +
                "?type=tcp&security=reality&sni=sni.example.com&fp=chrome" +
                "&pbk=public-key&sid=abcd&spx=%2F&flow=xtls-rprx-vision#Reality",
        )

        val profile = result.profiles.single()
        assertEquals(ProxyProtocol.VLESS, profile.protocol)
        assertEquals("reality", profile.security)
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("chrome", profile.fingerprint)
        assertEquals("public-key", profile.publicKey)
        assertEquals("abcd", profile.shortId)
        assertEquals("/", profile.spiderX)
    }

    @Test
    fun parsesBase64SubscriptionList() {
        val lines = listOf(
            "trojan://secret@example.com:443?security=tls&sni=example.com#Trojan",
            "ss://YWVzLTEyOC1nY206cGFzczFAc3MuZXhhbXBsZS5jb206ODM4OA==#SS",
        ).joinToString("\n")

        val result = parser.parse(lines.encodeUtf8().base64())

        assertEquals(2, result.profiles.size)
        assertEquals(ProxyProtocol.TROJAN, result.profiles[0].protocol)
        assertEquals(ProxyProtocol.SHADOWSOCKS, result.profiles[1].protocol)
        assertEquals(result.profiles[0].subscriptionId, result.profiles[1].subscriptionId)
        assertEquals(0, result.profiles[0].subscriptionPosition)
        assertEquals(1, result.profiles[1].subscriptionPosition)
    }

    @Test
    fun parsesUnsupportedAppPlaceholders() {
        val lines = listOf(
            "vless://00000000-0000-0000-0000-000000000000@0.0.0.0:1" +
                "?encryption=none&type=tcp&security=none#" +
                "%D0%9F%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5%20%D0%BD%D0%B5%20" +
                "%D0%BF%D0%BE%D0%B4%D0%B4%D0%B5%D1%80%D0%B6%D0%B8%D0%B2%D0%B0%D0%B5%D1%82%D1%81%D1%8F.",
            "vless://00000000-0000-0000-0000-000000000000@0.0.0.0:1" +
                "?encryption=none&type=tcp&security=none#" +
                "%D0%A3%D1%81%D1%82%D0%B0%D0%BD%D0%BE%D0%B2%D0%B8%D1%82%D0%B5%20" +
                "%D0%BF%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5%20HAPP",
        ).joinToString("\n")

        val result = parser.parse(
            lines.encodeUtf8().base64(),
            sourceHint = "https://example.com/sub/token",
            metadata = SubscriptionMetadata(title = "Subscription"),
        )

        assertEquals(2, result.profiles.size)
        assertEquals("Приложение не поддерживается.", result.profiles[0].name)
        assertEquals("Установите приложение HAPP", result.profiles[1].name)
        assertEquals("0.0.0.0", result.profiles[0].host)
        assertEquals(1, result.profiles[0].port)
        assertEquals(result.profiles[0].subscriptionId, result.profiles[1].subscriptionId)
    }

    @Test
    fun storesSourceUrlForUrlSubscriptions() {
        val result = parser.parse(
            "vless://00000000-0000-4000-8000-000000000001@example.com:443#Main",
            sourceHint = "https://example.com/sub/token",
            metadata = SubscriptionMetadata(announce = "Обновите подписку"),
        )

        val profile = result.profiles.single()
        assertEquals("https://example.com/sub/token", profile.subscriptionSourceUrl)
        assertEquals("Обновите подписку", profile.subscriptionAnnounce)
    }

    @Test
    fun enablesLaunchAutoUpdateFromUpdateAlwaysHeader() {
        val result = parser.parse(
            "vless://00000000-0000-4000-8000-000000000001@example.com:443#Main",
            sourceHint = "https://example.com/sub/token",
            metadata = SubscriptionMetadata(updateAlways = true),
        )

        assertTrue(result.profiles.single().subscriptionAutoUpdateOnLaunchEnabled)
    }

    @Test
    fun extractsProviderIdFromSubscriptionComment() {
        val body = """
            #partnerid pid_DEMO123456789
            vless://00000000-0000-4000-8000-000000000001@example.com:443#Main
        """.trimIndent()

        val result = parser.parse(
            body,
            sourceHint = "https://partner.example.com/sub/token",
        )

        assertEquals("pid_DEMO123456789", result.providerId)
        assertTrue(result.providerDomainHash.isNotBlank())
    }

    @Test
    fun extractsProviderIdFromSubscriptionMetadata() {
        val result = parser.parse(
            "vless://00000000-0000-4000-8000-000000000001@example.com:443#Main",
            sourceHint = "https://partner.example.com/sub/token",
            metadata = SubscriptionMetadata(
                providerId = "pid_FROM_HEADER",
                providerDomainHash = "domain-hash",
            ),
        )

        assertEquals("pid_FROM_HEADER", result.providerId)
        assertEquals("domain-hash", result.providerDomainHash)
    }

    @Test
    fun parsesVmessBase64Payload() {
        val vmessJson = """
            {
              "v": "2",
              "ps": "VMess Main",
              "add": "vmess.example.com",
              "port": "443",
              "id": "00000000-0000-4000-8000-000000000002",
              "net": "tcp",
              "tls": "tls",
              "sni": "vmess.example.com"
            }
        """.trimIndent()

        val result = parser.parse("vmess://${vmessJson.encodeUtf8().base64()}")

        val profile = result.profiles.single()
        assertEquals(ProxyProtocol.VMESS, profile.protocol)
        assertEquals("VMess Main", profile.name)
        assertEquals("vmess.example.com", profile.host)
        assertEquals("tls", profile.security)
    }

    @Test
    fun parsesXrayJsonAsCustomProfile() {
        val result = parser.parse("""{"inbounds":[],"outbounds":[{"tag":"proxy","protocol":"vless"}]}""")

        val profile = result.profiles.single()
        assertEquals(ProxyProtocol.CUSTOM_JSON, profile.protocol)
        assertTrue(profile.customJson.contains("outbounds"))
    }

    @Test
    fun parsesOlcRtcUri() {
        val result = parser.parse(
            "olcrtc://wbstream?datachannel@019e0bd3-c8c0-776b-aecc-1ba4833993fc" +
                "#b36efc171b783c788bd248bb592a7fbb3c49f7857c87e751822aaf67391e942a" +
                "%382fd315${'$'}OlcRTC",
        )

        val profile = result.profiles.single()
        assertEquals(ProxyProtocol.OLCRTC, profile.protocol)
        assertEquals("OlcRTC", profile.name)
        assertEquals("wbstream", profile.host)
        assertEquals("datachannel", profile.transport)
        assertEquals("019e0bd3-c8c0-776b-aecc-1ba4833993fc", profile.path)
        assertEquals("b36efc171b783c788bd248bb592a7fbb3c49f7857c87e751822aaf67391e942a", profile.credential)
        assertEquals("382fd315", profile.password)
        assertTrue(profile.protocolPayloadJson.contains("\"carrier\":\"wbstream\""))
        assertTrue(profile.protocolPayloadJson.contains("\"transport\":\"datachannel\""))
    }

    @Test
    fun appendsExtraOlcRtcKeysToSubscriptionProfiles() {
        val result = parser.parse(
            input = """{"inbounds":[],"outbounds":[{"tag":"proxy","protocol":"vless"}]}""",
            sourceHint = "https://key.example.com/sub/token",
            metadata = SubscriptionMetadata(title = "Main subscription"),
            extraItems = listOf(
                "olcrtc://wbstream?datachannel@019e1292-7ff7-7438-8752-8740c83f9fb1" +
                    "#6c638a2681386a2ce5b94453387b61c181b96274fba6771314d4642f55236989" +
                    "%91234567890002${'$'}OlcRTC",
            ),
        )

        assertEquals(2, result.profiles.size)
        assertEquals(ProxyProtocol.CUSTOM_JSON, result.profiles[0].protocol)
        assertEquals(ProxyProtocol.OLCRTC, result.profiles[1].protocol)
        assertEquals(result.profiles[0].subscriptionId, result.profiles[1].subscriptionId)
        assertEquals(0, result.profiles[0].subscriptionPosition)
        assertEquals(1, result.profiles[1].subscriptionPosition)
        assertEquals("Main subscription", result.profiles[1].subscriptionName)
    }
}
