package ru.myit.vlevpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.PingRepository
import ru.myit.vlevpn.domain.repository.ServerImportRepository
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository
import ru.myit.vlevpn.runtime.RuntimeConnectionManager

data class ServerGroupUiState(
    val id: String,
    val title: String,
    val servers: List<ServerProfile>,
    val checking: Boolean,
    val activatedAtMillis: Long,
    val importedAtMillis: Long,
    val updateIntervalHours: Int,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireAtMillis: Long,
    val supportUrl: String,
    val webPageUrl: String,
    val sourceUrl: String,
    val announce: String,
    val autoUpdateOnLaunchEnabled: Boolean,
    val refreshing: Boolean,
    val isSubscription: Boolean,
)

data class ServerPingUiState(
    val checking: Boolean = false,
    val delayMs: Long? = null,
    val error: String? = null,
    val generation: Long = 0L,
)

data class ServersUiEvent(
    val message: String,
)

private data class ServersBaseState(
    val servers: List<ServerProfile>,
    val selectedServerId: ServerId?,
    val pingProtocol: PingProtocol,
)

private data class ServersTransientState(
    val pingResults: Map<ServerId, ServerPingUiState>,
    val checkingGroups: Set<String>,
    val refreshingGroups: Set<String>,
    val importing: Boolean,
)

data class ServersUiState(
    val groups: List<ServerGroupUiState> = emptyList(),
    val selectedServerId: ServerId? = null,
    val pingProtocol: PingProtocol = PingProtocol.PROXY_GET,
    val pingResults: Map<ServerId, ServerPingUiState> = emptyMap(),
    val importing: Boolean = false,
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val importRepository: ServerImportRepository,
    private val settingsRepository: SettingsRepository,
    private val pingRepository: PingRepository,
    private val runtimeConnectionManager: RuntimeConnectionManager,
) : ViewModel() {
    private val importing = MutableStateFlow(false)
    private val pingResults = MutableStateFlow<Map<ServerId, ServerPingUiState>>(emptyMap())
    private val checkingGroups = MutableStateFlow<Set<String>>(emptySet())
    private val refreshingGroups = MutableStateFlow<Set<String>>(emptySet())
    private val _events = MutableSharedFlow<ServersUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ServersUiEvent> = _events.asSharedFlow()
    private val manualPingMutex = Mutex()
    private var pingGeneration = 0L

    init {
        viewModelScope.launch {
            runtimeConnectionManager.pingUpdates.collect { update ->
                val generation = runtimePingGeneration(update.generation)
                pingResults.update { results ->
                    results + (
                        update.serverId to ServerPingUiState(
                            checking = update.checking,
                            delayMs = update.delayMs,
                            error = update.error,
                            generation = generation,
                        )
                    )
                }
                if (!update.checking) {
                    clearPingResultsLater(setOf(update.serverId), generation)
                }
            }
        }
    }

    private val baseState = combine(
        repository.servers,
        repository.selectedServerId,
        settingsRepository.settings,
    ) { servers, selectedId, settings ->
        ServersBaseState(
            servers = servers,
            selectedServerId = selectedId,
            pingProtocol = settings.pingProtocol,
        )
    }

    private val transientState = combine(
        pingResults,
        checkingGroups,
        refreshingGroups,
        importing,
    ) { pingResults, checkingGroups, refreshingGroups, importing ->
        ServersTransientState(
            pingResults = pingResults,
            checkingGroups = checkingGroups,
            refreshingGroups = refreshingGroups,
            importing = importing,
        )
    }

    val uiState: StateFlow<ServersUiState> = combine(
        baseState,
        transientState,
    ) { baseState, transientState ->
        ServersUiState(
            groups = baseState.servers.toGroups(transientState.checkingGroups, transientState.refreshingGroups),
            selectedServerId = baseState.selectedServerId,
            pingProtocol = baseState.pingProtocol,
            pingResults = transientState.pingResults,
            importing = transientState.importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServersUiState())

    fun select(id: ServerId) {
        viewModelScope.launch {
            val currentSelectedId = repository.selectedServerId.first()
            if (currentSelectedId == id) return@launch

            val target = repository.getServer(id)
            if (target == null) {
                _events.emit(ServersUiEvent("Локация не найдена"))
                return@launch
            }

            val reconnect = runtimeConnectionManager.isRunning
            repository.select(id)
            if (!reconnect) {
                _events.emit(ServersUiEvent("Выбрана локация «${target.name}»"))
                return@launch
            }

            _events.emit(ServersUiEvent("Переподключаемся к «${target.name}»"))
            runtimeConnectionManager.disconnect()
            if (!waitForRuntimeStop()) {
                _events.emit(ServersUiEvent("Не удалось остановить VPN для переподключения"))
                return@launch
            }

            runCatching {
                runtimeConnectionManager.connect()
            }.onFailure { error ->
                _events.emit(
                    ServersUiEvent("Не удалось переподключиться: ${error.message ?: "неизвестная ошибка"}"),
                )
            }
        }
    }

    fun delete(id: ServerId) {
        viewModelScope.launch {
            clearPingResults(setOf(id))
            repository.delete(id)
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val serverIds = repository.servers.first()
                .filter { it.groupId() == groupId }
                .map { it.id }
                .toSet()
            runCatching {
                repository.deleteSubscription(groupId)
            }.onSuccess {
                clearPingResults(serverIds)
                _events.emit(ServersUiEvent("Подписка удалена"))
            }.onFailure { error ->
                _events.emit(
                    ServersUiEvent("Не удалось удалить подписку: ${error.message ?: "неизвестная ошибка"}"),
                )
            }
        }
    }

    fun pingGroup(groupId: String) {
        viewModelScope.launch {
            if (!manualPingMutex.tryLock()) {
                _events.emit(ServersUiEvent("Проверка пинга уже выполняется"))
                return@launch
            }
            try {
                pingGroupLocked(groupId)
            } finally {
                manualPingMutex.unlock()
            }
        }
    }

    private suspend fun pingGroupLocked(groupId: String) {
        if (groupId in checkingGroups.value) return
        val settings = settingsRepository.settings.first()
        val servers = repository.servers.first()
            .filter { it.groupId() == groupId }
            .sortedBy { it.subscriptionPosition }
        if (servers.isEmpty()) return

        val generation = nextPingGeneration()
        val serverIds = servers.map { it.id }.toSet()
        val reconnectAfterPing = runtimeConnectionManager.isRunning
        val selectedBeforePing = repository.selectedServerId.first()
        var vpnStoppedForPing = false
        if (reconnectAfterPing) {
            _events.emit(ServersUiEvent("VPN временно остановлен для проверки пинга"))
            val stopped = runCatching {
                runtimeConnectionManager.disconnect()
                waitForRuntimeStop()
            }.getOrElse {
                false
            }
            if (!stopped) {
                _events.emit(ServersUiEvent("Не удалось остановить VPN для проверки пинга"))
                return
            }
            vpnStoppedForPing = true
        }

        checkingGroups.update { it + groupId }
        val pendingResults = servers.associate { server ->
            server.id to ServerPingUiState(checking = true, generation = generation)
        }
        pingResults.update { it + pendingResults }
        try {
            runCatching {
                pingRepository.measureAll(servers, settings.pingProtocol, settings).collect { result ->
                    pingResults.update {
                        it + (
                            result.serverId to ServerPingUiState(
                                delayMs = result.delayMs,
                                error = result.error,
                                generation = generation,
                            )
                        )
                    }
                }
            }.onFailure { error ->
                val failedResults = servers.associate { server ->
                    server.id to ServerPingUiState(
                        error = error.message ?: "Ping failed",
                        generation = generation,
                    )
                }
                pingResults.update {
                    it + failedResults
                }
            }
        } finally {
            checkingGroups.update { it - groupId }
            clearPingResultsLater(serverIds, generation)
            if (vpnStoppedForPing) {
                reconnectAfterManualPing(selectedBeforePing)
            }
        }
    }

    fun refreshGroup(groupId: String) {
        viewModelScope.launch {
            if (groupId in refreshingGroups.value) return@launch
            val servers = repository.servers.first()
                .filter { it.groupId() == groupId }
                .sortedBy { it.subscriptionPosition }
            clearPingResults(servers.map { it.id }.toSet())
            val sourceUrl = servers.firstNotNullOfOrNull { server ->
                server.subscriptionSourceUrl.takeIf { it.isNotBlank() }
            }
            if (sourceUrl.isNullOrBlank()) {
                _events.emit(ServersUiEvent("Не удалось обновить: нет сохраненной ссылки подписки"))
                return@launch
            }

            val reconnectAfterRefresh = runtimeConnectionManager.isRunning
            var vpnStoppedForRefresh = false
            refreshingGroups.update { it + groupId }
            try {
                if (reconnectAfterRefresh) {
                    _events.emit(ServersUiEvent("VPN остановлен для обновления подписки"))
                    runtimeConnectionManager.disconnect()
                    vpnStoppedForRefresh = waitForRuntimeStop()
                    if (!vpnStoppedForRefresh) {
                        _events.emit(ServersUiEvent("Не удалось остановить VPN для обновления подписки"))
                        return@launch
                    }
                }

                runCatching {
                    importRepository.importFromInput(sourceUrl)
                }.onSuccess { summary ->
                    _events.emit(
                        ServersUiEvent("Подписка обновлена: ${summary.importedCount} профилей"),
                    )
                }.onFailure { error ->
                    _events.emit(
                        ServersUiEvent("Не удалось обновить подписку: ${error.message ?: "неизвестная ошибка"}"),
                    )
                }
            } finally {
                refreshingGroups.update { it - groupId }
                if (vpnStoppedForRefresh) {
                    runCatching {
                        runtimeConnectionManager.connect()
                    }.onSuccess {
                        _events.emit(ServersUiEvent("VPN запускается снова"))
                    }.onFailure { error ->
                        _events.emit(
                            ServersUiEvent("Не удалось снова включить VPN: ${error.message ?: "неизвестная ошибка"}"),
                        )
                    }
                }
            }
        }
    }

    fun setGroupAutoUpdateOnLaunch(groupId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.updateSubscriptionAutoUpdateOnLaunch(groupId, enabled)
            }.onSuccess {
                _events.emit(
                    ServersUiEvent(
                        if (enabled) {
                            "Подписка будет обновляться при запуске приложения"
                        } else {
                            "Обновление при запуске приложения выключено"
                        },
                    ),
                )
            }.onFailure { error ->
                _events.emit(
                    ServersUiEvent("Не удалось изменить настройку: ${error.message ?: "неизвестная ошибка"}"),
                )
            }
        }
    }

    fun importFromClipboard(text: String?) {
        viewModelScope.launch {
            importing.value = true
            clearAllPingResults()
            runCatching {
                importRepository.importFromInput(text.orEmpty())
            }.onSuccess { summary ->
                _events.emit(
                    ServersUiEvent(
                        if (summary.importedCount > 0) {
                            "Подписка добавлена"
                        } else {
                            "Нечего импортировать"
                        },
                    ),
                )
            }.onFailure { error ->
                _events.emit(ServersUiEvent("Не удалось импортировать: ${error.message ?: "неизвестная ошибка"}"))
            }
            importing.value = false
        }
    }

    private suspend fun reconnectAfterManualPing(selectedBeforePing: ServerId?) {
        if (selectedBeforePing == null) {
            _events.emit(ServersUiEvent("VPN не включен снова: нет выбранной локации"))
            return
        }

        val selectedStillExists = repository.getServer(selectedBeforePing) != null
        runCatching {
            if (selectedStillExists) {
                repository.select(selectedBeforePing)
                runtimeConnectionManager.connectSelectedProfile()
            } else {
                runtimeConnectionManager.connect()
            }
        }.onSuccess {
            _events.emit(ServersUiEvent("VPN запускается снова"))
        }.onFailure { error ->
            _events.emit(
                ServersUiEvent("Не удалось снова включить VPN: ${error.message ?: "неизвестная ошибка"}"),
            )
        }
    }

    private fun List<ServerProfile>.toGroups(
        checkingGroups: Set<String>,
        refreshingGroups: Set<String>,
    ): List<ServerGroupUiState> =
        groupBy { it.groupId() }
            .map { (id, profiles) ->
                val sortedProfiles = profiles.sortedWith(
                    compareBy<ServerProfile> { it.subscriptionPosition }
                        .thenBy { it.createdAtMillis }
                        .thenBy { it.name },
                )
                val activatedAt = profiles.maxOfOrNull { it.subscriptionActivatedAtMillis } ?: 0L
                val importedAt = profiles.map { it.subscriptionImportedAtMillis.takeIf { value -> value > 0L } ?: it.createdAtMillis }
                    .minOrNull()
                    ?: 0L
                ServerGroupUiState(
                    id = id,
                    title = profiles.firstOrNull()?.subscriptionName?.ifBlank { null }
                        ?: "Локальные профили",
                    servers = sortedProfiles,
                    checking = id in checkingGroups,
                    activatedAtMillis = activatedAt,
                    importedAtMillis = importedAt,
                    updateIntervalHours = profiles.maxOfOrNull { it.subscriptionUpdateIntervalHours } ?: 0,
                    uploadBytes = profiles.maxOfOrNull { it.subscriptionUploadBytes } ?: 0L,
                    downloadBytes = profiles.maxOfOrNull { it.subscriptionDownloadBytes } ?: 0L,
                    totalBytes = profiles.maxOfOrNull { it.subscriptionTotalBytes } ?: 0L,
                    expireAtMillis = profiles.maxOfOrNull { it.subscriptionExpireAtMillis } ?: 0L,
                    supportUrl = profiles.firstOrNull { it.subscriptionSupportUrl.isNotBlank() }?.subscriptionSupportUrl.orEmpty(),
                    webPageUrl = profiles.firstOrNull { it.subscriptionWebPageUrl.isNotBlank() }?.subscriptionWebPageUrl.orEmpty(),
                    sourceUrl = profiles.firstOrNull { it.subscriptionSourceUrl.isNotBlank() }?.subscriptionSourceUrl.orEmpty(),
                    announce = profiles.firstOrNull { it.subscriptionAnnounce.isNotBlank() }?.subscriptionAnnounce.orEmpty(),
                    autoUpdateOnLaunchEnabled = profiles.any { it.subscriptionAutoUpdateOnLaunchEnabled },
                    refreshing = id in refreshingGroups,
                    isSubscription = profiles.any { it.subscriptionId.isNotBlank() },
                )
            }
            .sortedWith(
                compareBy<ServerGroupUiState> { if (it.activatedAtMillis > 0L) 0 else 1 }
                    .thenByDescending { it.activatedAtMillis }
                    .thenBy { it.importedAtMillis },
            )

    private fun ServerProfile.groupId(): String =
        subscriptionId.ifBlank { "local" }

    private fun nextPingGeneration(): Long {
        pingGeneration += 1
        return pingGeneration
    }

    private fun runtimePingGeneration(generation: Long): Long =
        -generation

    private fun clearPingResults(serverIds: Set<ServerId>) {
        if (serverIds.isEmpty()) return
        pingResults.update { results -> results - serverIds }
    }

    private fun clearAllPingResults() {
        pingResults.value = emptyMap()
    }

    private fun clearPingResultsLater(serverIds: Set<ServerId>, generation: Long) {
        if (serverIds.isEmpty()) return
        viewModelScope.launch {
            delay(PING_RESULT_TTL_MILLIS)
            pingResults.update { results ->
                results.filterNot { (serverId, state) ->
                    serverId in serverIds && state.generation == generation
                }
            }
        }
    }

    private suspend fun waitForRuntimeStop(): Boolean =
        runtimeConnectionManager.awaitStopped(RUNTIME_STOP_TIMEOUT_MILLIS)

    private companion object {
        const val RUNTIME_STOP_TIMEOUT_MILLIS = 15_000L
        const val PING_RESULT_TTL_MILLIS = 10_000L
    }
}
