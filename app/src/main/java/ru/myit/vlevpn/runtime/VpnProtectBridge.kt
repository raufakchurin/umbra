package ru.myit.vlevpn.runtime

import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnProtectBridge @Inject constructor() {
    @Volatile
    private var protector: ((Int) -> Boolean)? = null

    @Volatile
    private var socketProtector: ((Socket) -> Boolean)? = null

    fun setProtector(protector: ((Int) -> Boolean)?) {
        this.protector = protector
    }

    fun setSocketProtector(protector: ((Socket) -> Boolean)?) {
        socketProtector = protector
    }

    fun protect(fd: Int): Boolean = protector?.invoke(fd) ?: false

    fun protect(socket: Socket): Boolean = socketProtector?.invoke(socket) ?: false
}
