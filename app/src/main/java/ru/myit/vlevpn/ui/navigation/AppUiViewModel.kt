package ru.myit.vlevpn.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.myit.vlevpn.data.push.InAppForegroundMessageBus
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.InAppForegroundMessage
import ru.myit.vlevpn.domain.repository.SettingsRepository

@HiltViewModel
class AppUiViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val foregroundMessageBus: InAppForegroundMessageBus,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default)

    val inAppMessage: StateFlow<InAppForegroundMessage?> = foregroundMessageBus.message

    fun clearInAppMessage(deliveryId: String) {
        foregroundMessageBus.clear(deliveryId)
    }
}
