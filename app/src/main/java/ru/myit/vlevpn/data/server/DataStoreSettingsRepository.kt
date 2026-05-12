package ru.myit.vlevpn.data.server

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import ru.myit.vlevpn.core.settings.SettingsDataStore
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.AppTextSize
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.repository.SettingsRepository

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.settings

    override suspend fun selectServer(serverId: ServerId?) = dataStore.selectServer(serverId)

    override suspend fun updatePorts(socksPort: Int, httpPort: Int, localDnsPort: Int) {
        dataStore.updatePorts(socksPort, httpPort, localDnsPort)
    }

    override suspend fun updateTun(
        mtu: Int,
        xrayTunModeEnabled: Boolean,
        routeAllTraffic: Boolean,
        allowIpv6: Boolean,
    ) {
        dataStore.updateTun(mtu, xrayTunModeEnabled, routeAllTraffic, allowIpv6)
    }

    override suspend fun updateDns(servers: List<String>) = dataStore.updateDns(servers)

    override suspend fun updateLogSettings(logsEnabled: Boolean, debugConfigPreviewEnabled: Boolean) {
        dataStore.updateLogSettings(logsEnabled, debugConfigPreviewEnabled)
    }

    override suspend fun updatePingProtocol(protocol: PingProtocol) = dataStore.updatePingProtocol(protocol)

    override suspend fun updateAutoConnectMode(mode: AutoConnectMode) = dataStore.updateAutoConnectMode(mode)

    override suspend fun updateExcludedAppPackages(packages: Set<String>) {
        dataStore.updateExcludedAppPackages(packages)
    }

    override suspend fun updateSubscriptionProvider(providerId: String, domainHash: String) {
        dataStore.updateSubscriptionProvider(providerId, domainHash)
    }

    override suspend fun updateInterface(language: AppLanguage, textSize: AppTextSize) =
        dataStore.updateInterface(language, textSize)

    override suspend fun updateAppearance(accentColor: AppAccentColor, backgroundStyle: AppBackgroundStyle) =
        dataStore.updateAppearance(accentColor, backgroundStyle)

    override suspend fun updateLocalBrandingOverride(enabled: Boolean) =
        dataStore.updateLocalBrandingOverride(enabled)

    override suspend fun markProviderTelemetrySent(sentAtMillis: Long) {
        dataStore.markProviderTelemetrySent(sentAtMillis)
    }

    override suspend fun resetToDefaults() = dataStore.resetToDefaults()
}
