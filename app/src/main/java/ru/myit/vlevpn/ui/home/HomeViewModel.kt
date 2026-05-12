package ru.myit.vlevpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository
import ru.myit.vlevpn.runtime.RuntimeConnectionEvent
import ru.myit.vlevpn.runtime.RuntimeConnectionManager
import ru.myit.vlevpn.runtime.RuntimeState
import ru.myit.vlevpn.runtime.RuntimeStats

data class HomeUiState(
    val runtimeState: RuntimeState = RuntimeState.Idle,
    val selectedServer: ServerProfile? = null,
    val settings: AppSettings = AppSettings.Default,
    val stats: RuntimeStats = RuntimeStats(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val connectionManager: RuntimeConnectionManager,
) : ViewModel() {
    val events: SharedFlow<RuntimeConnectionEvent> = connectionManager.events

    val uiState: StateFlow<HomeUiState> = combine(
        connectionManager.state,
        serverRepository.selectedServer,
        settingsRepository.settings,
        connectionManager.stats,
    ) { runtimeState, selectedServer, settings, stats ->
        HomeUiState(
            runtimeState = runtimeState,
            selectedServer = selectedServer,
            settings = settings,
            stats = stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun markPreparingPermission() {
        connectionManager.markPreparingPermission()
    }

    fun onPermissionDenied() {
        connectionManager.onPermissionDenied()
    }

    fun connect() {
        viewModelScope.launch {
            connectionManager.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect()
        }
    }
}
