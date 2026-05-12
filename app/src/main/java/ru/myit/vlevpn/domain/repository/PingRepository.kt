package ru.myit.vlevpn.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.runtime.DelayResult

interface PingRepository {
    suspend fun measure(
        server: ServerProfile,
        protocol: PingProtocol,
        settings: AppSettings,
    ): DelayResult

    fun measureAll(
        servers: List<ServerProfile>,
        protocol: PingProtocol,
        settings: AppSettings,
    ): Flow<DelayResult> = flow {
        servers.forEach { server ->
            emit(measure(server, protocol, settings))
        }
    }
}
