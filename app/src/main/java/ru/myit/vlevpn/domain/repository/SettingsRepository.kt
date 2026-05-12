package ru.myit.vlevpn.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.AppTextSize
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ServerId

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun selectServer(serverId: ServerId?)
    suspend fun updatePorts(socksPort: Int, httpPort: Int, localDnsPort: Int)
    suspend fun updateTun(mtu: Int, xrayTunModeEnabled: Boolean, routeAllTraffic: Boolean, allowIpv6: Boolean)
    suspend fun updateDns(servers: List<String>)
    suspend fun updateLogSettings(logsEnabled: Boolean, debugConfigPreviewEnabled: Boolean)
    suspend fun updatePingProtocol(protocol: PingProtocol)
    suspend fun updateAutoConnectMode(mode: AutoConnectMode)
    suspend fun updateExcludedAppPackages(packages: Set<String>)
    suspend fun updateSubscriptionProvider(providerId: String, domainHash: String)
    suspend fun updateInterface(language: AppLanguage, textSize: AppTextSize)
    suspend fun updateAppearance(accentColor: AppAccentColor, backgroundStyle: AppBackgroundStyle)
    suspend fun updateLocalBrandingOverride(enabled: Boolean)
    suspend fun markProviderTelemetrySent(sentAtMillis: Long)
    suspend fun resetToDefaults()
}
