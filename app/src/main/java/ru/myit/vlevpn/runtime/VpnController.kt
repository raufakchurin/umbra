package ru.myit.vlevpn.runtime

import ru.myit.vlevpn.domain.model.VpnProfile

interface VpnController {
    suspend fun establish(profile: VpnProfile): VpnSession
    fun protect(fd: Int): Boolean
    suspend fun close()
}
