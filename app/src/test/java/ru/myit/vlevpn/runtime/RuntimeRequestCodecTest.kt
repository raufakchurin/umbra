package ru.myit.vlevpn.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.model.VpnProfile

class RuntimeRequestCodecTest {
    @Test
    fun roundTripsStartProxyRequest() {
        val request = StartProxyRequest(
            server = ServerProfile(
                id = ServerId("server-1"),
                name = "Germany",
                protocol = ProxyProtocol.VLESS,
                host = "example.com",
                port = 443,
                credential = "uuid",
                password = "",
                method = "",
                transport = "tcp",
                security = "reality",
                sni = "example.com",
                path = "",
                flow = "xtls-rprx-vision",
                fingerprint = "chrome",
                publicKey = "public-key",
                shortId = "short-id",
                spiderX = "/",
                subscriptionId = "subscription-1",
                subscriptionName = "Subscription",
                subscriptionSourceUrl = "https://example.com/sub",
                customJson = "",
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
            ),
            settings = AppSettings.Default,
            vpnProfile = VpnProfile(
                sessionName = "Germany",
                mtu = 1500,
                dnsServers = listOf("1.1.1.1"),
                routeAllTraffic = true,
                allowIpv6 = false,
                excludedPackageNames = setOf("com.example.browser"),
            ),
        )

        assertEquals(request, decodeStartProxyRequest(request.encodeForService()))
    }
}
