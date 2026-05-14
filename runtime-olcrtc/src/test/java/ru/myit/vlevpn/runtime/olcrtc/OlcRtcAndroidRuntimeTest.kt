package ru.myit.vlevpn.runtime.olcrtc

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeRequest

class OlcRtcAndroidRuntimeTest {
    @Test
    fun buildTun2SocksConfigUsesUdpRelayAndSelectedDns() {
        val request = OlcRtcRuntimeRequest(
            carrierName = "wbstream",
            transportName = "datachannel",
            roomId = "room",
            clientId = "default",
            keyHex = "00112233",
            sessionName = "umbra",
            ownPackageName = "com.proxy.umbra",
            dnsServers = listOf("8.8.4.4:53"),
            excludedPackageNames = emptySet(),
            tunMtu = 1400,
            routeAllTraffic = true,
            allowIpv6 = false,
            debugLogging = false,
        )

        val config = buildTun2SocksConfig(request, localSocksPort = 10818)

        assertTrue(config.contains("port: 10818"))
        assertTrue(config.contains("udp: 'udp'"))
        assertTrue(config.contains("address: 8.8.4.4"))
    }
}
