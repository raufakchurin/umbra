package ru.myit.vlevpn.ui.logs

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.logging.RuntimeLogEntry

data class LogsUiState(
    val logs: List<RuntimeLogEntry> = emptyList(),
    val generatedConfigPreview: String? = null,
) {
    val plainText: String = logs.joinToString("\n") { "${it.level}: ${it.message}" }
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logRepository: LogRepository,
) : ViewModel() {
    val uiState: StateFlow<LogsUiState> = combine(
        logRepository.entries,
        logRepository.generatedConfigPreview,
    ) { logs, preview ->
        LogsUiState(logs = logs.reversed(), generatedConfigPreview = preview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogsUiState())

    fun clear() {
        logRepository.clear()
    }
}
