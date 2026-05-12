package ru.myit.vlevpn.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile

interface ServerRepository {
    val servers: Flow<List<ServerProfile>>
    val selectedServer: Flow<ServerProfile?>
    val selectedServerId: Flow<ServerId?>

    fun observeServer(id: ServerId): Flow<ServerProfile?>
    suspend fun getServer(id: ServerId): ServerProfile?
    suspend fun getSelectedServer(): ServerProfile?
    suspend fun hasServers(): Boolean
    suspend fun upsert(server: ServerProfile)
    suspend fun upsertAll(servers: List<ServerProfile>)
    suspend fun replaceSubscription(subscriptionId: String, servers: List<ServerProfile>)
    suspend fun appendSubscriptionProfiles(
        subscriptionId: String,
        importedAtMillis: Long,
        servers: List<ServerProfile>,
    ): Boolean
    suspend fun updateSubscriptionAutoUpdateOnLaunch(subscriptionId: String, enabled: Boolean)
    suspend fun deleteSubscription(subscriptionId: String)
    suspend fun delete(id: ServerId)
    suspend fun select(id: ServerId?)
}
