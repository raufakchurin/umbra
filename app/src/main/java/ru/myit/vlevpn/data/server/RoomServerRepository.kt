package ru.myit.vlevpn.data.server

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.myit.vlevpn.data.local.ServerDao
import ru.myit.vlevpn.data.local.toDomain
import ru.myit.vlevpn.data.local.toEntity
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

@Singleton
class RoomServerRepository @Inject constructor(
    private val dao: ServerDao,
    private val settingsRepository: SettingsRepository,
) : ServerRepository {
    override val servers: Flow<List<ServerProfile>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override val selectedServerId: Flow<ServerId?> =
        settingsRepository.settings.map { it.selectedServerId }

    override val selectedServer: Flow<ServerProfile?> =
        combine(servers, selectedServerId) { servers, selectedId ->
            servers.firstOrNull { it.id == selectedId }
        }

    override fun observeServer(id: ServerId): Flow<ServerProfile?> =
        dao.observeById(id.value).map { it?.toDomain() }

    override suspend fun getServer(id: ServerId): ServerProfile? =
        dao.getById(id.value)?.toDomain()

    override suspend fun getSelectedServer(): ServerProfile? {
        val selectedId = selectedServerId.first() ?: return null
        return getServer(selectedId)
    }

    override suspend fun hasServers(): Boolean = dao.count() > 0

    override suspend fun upsert(server: ServerProfile) {
        val hadServers = dao.count() > 0
        dao.upsert(server.toEntity())
        if (!hadServers) {
            settingsRepository.selectServer(server.id)
        }
    }

    override suspend fun upsertAll(servers: List<ServerProfile>) {
        if (servers.isEmpty()) return
        val hadServers = dao.count() > 0
        dao.upsertAll(servers.map { it.toEntity() })
        if (!hadServers) {
            settingsRepository.selectServer(servers.first().id)
        }
    }

    override suspend fun replaceSubscription(subscriptionId: String, servers: List<ServerProfile>) {
        if (subscriptionId.isBlank() || servers.isEmpty()) return
        val selectedBefore = selectedServerId.first()
            ?.let { selectedId -> dao.getById(selectedId.value)?.toDomain() }
        val existingProfiles = dao.getBySubscriptionId(subscriptionId).map { it.toDomain() }
        val existingAutoUpdateOnLaunch = existingProfiles.firstOrNull()?.subscriptionAutoUpdateOnLaunchEnabled
        val hadServers = dao.count() > 0
        val selectedWasInSubscription = selectedBefore?.subscriptionId == subscriptionId
        val headerRequestsAutoUpdateOnLaunch = servers.any { it.subscriptionAutoUpdateOnLaunchEnabled }
        val resolvedAutoUpdateOnLaunch = when {
            headerRequestsAutoUpdateOnLaunch -> true
            existingAutoUpdateOnLaunch != null -> existingAutoUpdateOnLaunch
            else -> false
        }
        val normalizedServers = servers.map {
            it.copy(subscriptionAutoUpdateOnLaunchEnabled = resolvedAutoUpdateOnLaunch)
        }

        dao.deleteBySubscriptionId(subscriptionId)
        dao.upsertAll(normalizedServers.map { it.toEntity() })

        when {
            !hadServers || selectedWasInSubscription -> select(normalizedServers.first().id)
        }
    }

    override suspend fun appendSubscriptionProfiles(
        subscriptionId: String,
        importedAtMillis: Long,
        servers: List<ServerProfile>,
    ): Boolean {
        if (subscriptionId.isBlank() || importedAtMillis <= 0L || servers.isEmpty()) return false

        val existingProfiles = dao.getBySubscriptionId(subscriptionId).map { it.toDomain() }
        if (existingProfiles.isEmpty()) return false

        val currentImportedAt = existingProfiles
            .map { it.subscriptionImportedAtMillis }
            .filter { it > 0L }
            .minOrNull()
            ?: return false
        if (currentImportedAt != importedAtMillis) return false

        val autoUpdateOnLaunch = existingProfiles.first().subscriptionAutoUpdateOnLaunchEnabled
        val activatedAtMillis = existingProfiles.maxOfOrNull { it.subscriptionActivatedAtMillis } ?: 0L
        val normalizedServers = servers.map { server ->
            server.copy(
                subscriptionAutoUpdateOnLaunchEnabled = autoUpdateOnLaunch,
                subscriptionActivatedAtMillis = activatedAtMillis,
            )
        }
        dao.upsertAll(normalizedServers.map { it.toEntity() })
        return true
    }

    override suspend fun updateSubscriptionAutoUpdateOnLaunch(subscriptionId: String, enabled: Boolean) {
        if (subscriptionId.isBlank()) return
        dao.updateSubscriptionAutoUpdateOnLaunch(subscriptionId, enabled)
    }

    override suspend fun deleteSubscription(subscriptionId: String) {
        if (subscriptionId.isBlank()) return
        val selectedBefore = selectedServerId.first()
            ?.let { selectedId -> dao.getById(selectedId.value)?.toDomain() }
        val selectedWasInSubscription = selectedBefore?.subscriptionId == subscriptionId

        dao.deleteBySubscriptionId(subscriptionId)

        if (selectedWasInSubscription) {
            val next = servers.first().firstOrNull()
            settingsRepository.selectServer(next?.id)
        }
        refreshCurrentSubscriptionProvider()
    }

    override suspend fun delete(id: ServerId) {
        val server = dao.getById(id.value) ?: return
        dao.delete(server)
        val selected = selectedServerId.first()
        if (selected == id) {
            val next = servers.first().firstOrNull()
            settingsRepository.selectServer(next?.id)
        }
        refreshCurrentSubscriptionProvider()
    }

    override suspend fun select(id: ServerId?) {
        if (id != null) {
            dao.getById(id.value)?.let { server ->
                val activatedAt = System.currentTimeMillis()
                if (server.subscriptionId.isNotBlank()) {
                    dao.activateSubscription(server.subscriptionId, activatedAt)
                } else {
                    dao.activateSingleServer(server.id, activatedAt)
                }
                server.toDomain().syncProviderMetadata()
            }
        }
        settingsRepository.selectServer(id)
    }

    private suspend fun refreshCurrentSubscriptionProvider() {
        val nextProvider = dao.getAll()
            .map { it.toDomain() }
            .filter { it.subscriptionProviderId.isNotBlank() && it.subscriptionProviderDomainHash.isNotBlank() }
            .maxWithOrNull(
                compareBy<ServerProfile> { it.subscriptionActivatedAtMillis }
                    .thenBy { it.subscriptionImportedAtMillis }
                    .thenBy { it.updatedAtMillis },
            )
        if (nextProvider == null) {
            settingsRepository.updateSubscriptionProvider(providerId = "", domainHash = "")
        } else {
            nextProvider.syncProviderMetadata()
        }
    }

    private suspend fun ServerProfile.syncProviderMetadata() {
        if (subscriptionProviderId.isBlank() || subscriptionProviderDomainHash.isBlank()) return
        settingsRepository.updateSubscriptionProvider(
            providerId = subscriptionProviderId,
            domainHash = subscriptionProviderDomainHash,
        )
    }
}
