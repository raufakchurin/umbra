package ru.myit.vlevpn.core.network

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory
import ru.myit.vlevpn.runtime.VpnProtectBridge

class ProtectedSocketFactory(
    private val delegate: SocketFactory,
    private val protectBridge: VpnProtectBridge,
) : SocketFactory() {
    override fun createSocket(): Socket =
        delegate.createSocket().also(::protect)

    override fun createSocket(host: String, port: Int): Socket =
        createSocket().apply {
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createSocket().apply {
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }

    private fun protect(socket: Socket) {
        protectBridge.protect(socket)
    }
}
