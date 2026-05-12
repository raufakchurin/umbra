package ru.myit.vlevpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.AppTextSize
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.InstalledApp
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.repository.InstalledAppsRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

data class SettingsUiState(
    val settings: AppSettings = AppSettings.Default,
    val installedApps: List<InstalledApp> = emptyList(),
    val installedAppsLoading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val installedAppsLoading = MutableStateFlow(true)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        installedApps,
        installedAppsLoading,
        message,
    ) { settings, apps, appsLoading, message ->
        SettingsUiState(
            settings = settings,
            installedApps = apps,
            installedAppsLoading = appsLoading,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshInstalledApps()
    }

    fun savePorts(socks: String, http: String, dns: String) {
        val socksPort = socks.toIntOrNull()
        val httpPort = http.toIntOrNull()
        val dnsPort = dns.toIntOrNull()
        if (
            socksPort == null || socksPort !in 1..65535 ||
            httpPort == null || httpPort !in 1..65535 ||
            dnsPort == null || dnsPort !in 1..65535
        ) {
            message.value = "Ports must be 1..65535"
            return
        }
        viewModelScope.launch {
            settingsRepository.updatePorts(socksPort, httpPort, dnsPort)
            message.value = "Ports saved"
        }
    }

    fun saveDns(value: String) {
        val servers = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (servers.isEmpty()) {
            message.value = "At least one DNS server is required"
            return
        }
        viewModelScope.launch {
            settingsRepository.updateDns(servers)
            message.value = "DNS saved"
        }
    }

    fun updateTun(mtu: String, xrayTun: Boolean, routeAll: Boolean, allowIpv6: Boolean) {
        val parsedMtu = mtu.toIntOrNull()
        if (parsedMtu == null || parsedMtu !in 1200..9000) {
            message.value = "MTU must be 1200..9000"
            return
        }
        viewModelScope.launch {
            settingsRepository.updateTun(parsedMtu, xrayTun, routeAll, allowIpv6)
            message.value = "VPN settings saved"
        }
    }

    fun updateLogs(logsEnabled: Boolean, debugPreview: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateLogSettings(logsEnabled, debugPreview)
            message.value = "Log settings saved"
        }
    }

    fun updatePingProtocol(protocol: PingProtocol) {
        viewModelScope.launch {
            settingsRepository.updatePingProtocol(protocol)
            message.value = "Ping protocol saved"
        }
    }

    fun updateAutoConnectMode(mode: AutoConnectMode) {
        viewModelScope.launch {
            settingsRepository.updateAutoConnectMode(mode)
            message.value = "Сценарий автоподключения сохранен"
        }
    }

    fun updateInterface(language: AppLanguage, textSize: AppTextSize) {
        viewModelScope.launch {
            settingsRepository.updateInterface(language, textSize)
            message.value = when (language) {
                AppLanguage.RU -> "Настройки интерфейса сохранены"
                AppLanguage.EN -> "Interface settings saved"
            }
        }
    }

    fun updateAppearance(accentColor: AppAccentColor, backgroundStyle: AppBackgroundStyle) {
        viewModelScope.launch {
            settingsRepository.updateAppearance(accentColor, backgroundStyle)
            message.value = when (uiState.value.settings.appLanguage) {
                AppLanguage.RU -> "Оформление сохранено"
                AppLanguage.EN -> "Appearance saved"
            }
        }
    }

    fun updateLocalBrandingOverride(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateLocalBrandingOverride(enabled)
            message.value = when (uiState.value.settings.appLanguage) {
                AppLanguage.RU -> if (enabled) "Ваше оформление имеет приоритет" else "Партнерское оформление снова может применяться"
                AppLanguage.EN -> if (enabled) "Your appearance has priority" else "Partner appearance can apply again"
            }
        }
    }

    fun toggleExcludedApp(packageName: String, excluded: Boolean) {
        val current = uiState.value.settings.excludedAppPackages
        val next = if (excluded) {
            current + packageName
        } else {
            current - packageName
        }
        viewModelScope.launch {
            settingsRepository.updateExcludedAppPackages(next)
            message.value = if (excluded) {
                "Приложение исключено из VPN. Переподключите VPN."
            } else {
                "Приложение снова идет через VPN. Переподключите VPN."
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            installedAppsLoading.value = true
            runCatching { installedAppsRepository.getLaunchableApps() }
                .onSuccess { apps ->
                    installedApps.value = apps
                    message.value = null
                }
                .onFailure { error ->
                    message.value = "Не удалось загрузить список приложений: ${error.message.orEmpty()}"
                }
            installedAppsLoading.value = false
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            message.value = "Настройки сброшены по умолчанию"
        }
    }
}
