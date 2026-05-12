package ru.myit.vlevpn.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ru.myit.vlevpn.domain.model.ServerId

interface ProxyRuntime {
    val state: StateFlow<RuntimeState>
    val stats: Flow<RuntimeStats>

    suspend fun start(request: StartProxyRequest)
    suspend fun stop()
    suspend fun measureDelay(serverId: ServerId): DelayResult
}
