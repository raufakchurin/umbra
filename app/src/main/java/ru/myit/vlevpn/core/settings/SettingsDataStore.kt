package ru.myit.vlevpn.core.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.AppTextSize
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.BackendMode
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ServerId

private val Context.settingsDataStore by preferencesDataStore(name = "vle_vpn_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        val defaults = AppSettings.Default
        AppSettings(
            selectedServerId = preferences[Keys.SelectedServerId]?.takeIf { it.isNotBlank() }?.let(::ServerId),
            socksPort = preferences[Keys.SocksPort] ?: defaults.socksPort,
            httpPort = preferences[Keys.HttpPort] ?: defaults.httpPort,
            localDnsPort = preferences[Keys.LocalDnsPort] ?: defaults.localDnsPort,
            tunMtu = preferences[Keys.TunMtu] ?: defaults.tunMtu,
            dnsServers = preferences[Keys.DnsServers]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: defaults.dnsServers,
            logsEnabled = preferences[Keys.LogsEnabled] ?: defaults.logsEnabled,
            debugConfigPreviewEnabled = preferences[Keys.DebugConfigPreviewEnabled]
                ?: defaults.debugConfigPreviewEnabled,
            xrayTunModeEnabled = preferences[Keys.XrayTunModeEnabled] ?: defaults.xrayTunModeEnabled,
            routeAllTraffic = preferences[Keys.RouteAllTraffic] ?: defaults.routeAllTraffic,
            allowIpv6 = preferences[Keys.AllowIpv6] ?: defaults.allowIpv6,
            pingProtocol = preferences[Keys.PingProtocol]
                ?.let { value -> runCatching { PingProtocol.valueOf(value) }.getOrNull() }
                ?: defaults.pingProtocol,
            autoConnectMode = preferences[Keys.AutoConnectMode]
                ?.let { value -> runCatching { AutoConnectMode.valueOf(value) }.getOrNull() }
                ?: defaults.autoConnectMode,
            excludedAppPackages = preferences[Keys.ExcludedAppPackages]
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: defaults.excludedAppPackages,
            backendMode = BackendMode.FAKE,
            subscriptionProviderId = preferences[Keys.SubscriptionProviderId].orEmpty(),
            subscriptionProviderDomainHash = preferences[Keys.SubscriptionProviderDomainHash].orEmpty(),
            providerTelemetryLastSentAtMillis = preferences[Keys.ProviderTelemetryLastSentAtMillis]
                ?: defaults.providerTelemetryLastSentAtMillis,
            appLanguage = preferences[Keys.AppLanguage]
                ?.let { value -> runCatching { AppLanguage.valueOf(value) }.getOrNull() }
                ?: defaults.appLanguage,
            appTextSize = preferences[Keys.AppTextSize]
                ?.let { value -> runCatching { AppTextSize.valueOf(value) }.getOrNull() }
                ?: defaults.appTextSize,
            appAccentColor = preferences[Keys.AppAccentColor]
                ?.let { value -> runCatching { AppAccentColor.valueOf(value) }.getOrNull() }
                ?: defaults.appAccentColor,
            appBackgroundStyle = preferences[Keys.AppBackgroundStyle]
                ?.let { value -> runCatching { AppBackgroundStyle.valueOf(value) }.getOrNull() }
                ?: defaults.appBackgroundStyle,
            localBrandingOverrideEnabled = preferences[Keys.LocalBrandingOverrideEnabled]
                ?: defaults.localBrandingOverrideEnabled,
            remoteBrandingProviderId = preferences[Keys.RemoteBrandingProviderId].orEmpty(),
            remoteBrandingPriority = preferences[Keys.RemoteBrandingPriority]
                ?: defaults.remoteBrandingPriority,
            remoteBrandingImagePath = preferences[Keys.RemoteBrandingImagePath].orEmpty(),
            remoteBrandingImageSha256 = preferences[Keys.RemoteBrandingImageSha256].orEmpty(),
            remoteBrandingBlurPercent = preferences[Keys.RemoteBrandingBlurPercent]
                ?: defaults.remoteBrandingBlurPercent,
            remoteBrandingAccentColor = preferences[Keys.RemoteBrandingAccentColor].orEmpty(),
            remoteBrandingBackgroundColor = preferences[Keys.RemoteBrandingBackgroundColor].orEmpty(),
            remoteBrandingUpdatedAt = preferences[Keys.RemoteBrandingUpdatedAt].orEmpty(),
        )
    }

    suspend fun selectServer(serverId: ServerId?) {
        context.settingsDataStore.edit { preferences ->
            if (serverId == null) {
                preferences.remove(Keys.SelectedServerId)
            } else {
                preferences[Keys.SelectedServerId] = serverId.value
            }
        }
    }

    suspend fun updatePorts(socksPort: Int, httpPort: Int, localDnsPort: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SocksPort] = socksPort
            preferences[Keys.HttpPort] = httpPort
            preferences[Keys.LocalDnsPort] = localDnsPort
        }
    }

    suspend fun updateTun(mtu: Int, xrayTunModeEnabled: Boolean, routeAllTraffic: Boolean, allowIpv6: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.TunMtu] = mtu
            preferences[Keys.XrayTunModeEnabled] = xrayTunModeEnabled
            preferences[Keys.RouteAllTraffic] = routeAllTraffic
            preferences[Keys.AllowIpv6] = allowIpv6
        }
    }

    suspend fun updateDns(servers: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DnsServers] = servers.joinToString(",")
        }
    }

    suspend fun updateLogSettings(logsEnabled: Boolean, debugConfigPreviewEnabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.LogsEnabled] = logsEnabled
            preferences[Keys.DebugConfigPreviewEnabled] = debugConfigPreviewEnabled
        }
    }

    suspend fun updatePingProtocol(protocol: PingProtocol) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PingProtocol] = protocol.name
        }
    }

    suspend fun updateAutoConnectMode(mode: AutoConnectMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutoConnectMode] = mode.name
        }
    }

    suspend fun updateExcludedAppPackages(packages: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ExcludedAppPackages] = packages
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    suspend fun updateSubscriptionProvider(providerId: String, domainHash: String) {
        context.settingsDataStore.edit { preferences ->
            val normalizedProviderId = providerId.trim()
            val normalizedDomainHash = domainHash.trim()
            val providerChanged = preferences[Keys.SubscriptionProviderId] != normalizedProviderId ||
                preferences[Keys.SubscriptionProviderDomainHash] != normalizedDomainHash
            if (providerId.isBlank()) {
                preferences.remove(Keys.SubscriptionProviderId)
                preferences.remove(Keys.SubscriptionProviderDomainHash)
                preferences.remove(Keys.ProviderTelemetryLastSentAtMillis)
            } else {
                preferences[Keys.SubscriptionProviderId] = normalizedProviderId
                preferences[Keys.SubscriptionProviderDomainHash] = normalizedDomainHash
                if (providerChanged) {
                    preferences.remove(Keys.ProviderTelemetryLastSentAtMillis)
                }
            }
        }
    }

    suspend fun updateInterface(language: AppLanguage, textSize: AppTextSize) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AppLanguage] = language.name
            preferences[Keys.AppTextSize] = textSize.name
        }
    }

    suspend fun updateAppearance(accentColor: AppAccentColor, backgroundStyle: AppBackgroundStyle) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AppAccentColor] = accentColor.name
            preferences[Keys.AppBackgroundStyle] = backgroundStyle.name
        }
    }

    suspend fun updateLocalBrandingOverride(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.LocalBrandingOverrideEnabled] = enabled
        }
    }

    suspend fun updateRemoteBranding(
        providerId: String,
        priority: Int,
        imagePath: String,
        imageSha256: String,
        blurPercent: Int,
        accentColor: String,
        backgroundColor: String,
        updatedAt: String,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.RemoteBrandingProviderId] = providerId
            preferences[Keys.RemoteBrandingPriority] = priority
            preferences[Keys.RemoteBrandingImagePath] = imagePath
            preferences[Keys.RemoteBrandingImageSha256] = imageSha256
            preferences[Keys.RemoteBrandingBlurPercent] = blurPercent.coerceIn(5, 90)
            preferences[Keys.RemoteBrandingAccentColor] = accentColor
            preferences[Keys.RemoteBrandingBackgroundColor] = backgroundColor
            preferences[Keys.RemoteBrandingUpdatedAt] = updatedAt
        }
    }

    suspend fun clearRemoteBranding() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.RemoteBrandingProviderId)
            preferences.remove(Keys.RemoteBrandingPriority)
            preferences.remove(Keys.RemoteBrandingImagePath)
            preferences.remove(Keys.RemoteBrandingImageSha256)
            preferences.remove(Keys.RemoteBrandingBlurPercent)
            preferences.remove(Keys.RemoteBrandingAccentColor)
            preferences.remove(Keys.RemoteBrandingBackgroundColor)
            preferences.remove(Keys.RemoteBrandingUpdatedAt)
        }
    }

    suspend fun markProviderTelemetrySent(sentAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ProviderTelemetryLastSentAtMillis] = sentAtMillis
        }
    }

    suspend fun resetToDefaults() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.SocksPort)
            preferences.remove(Keys.HttpPort)
            preferences.remove(Keys.LocalDnsPort)
            preferences.remove(Keys.TunMtu)
            preferences.remove(Keys.DnsServers)
            preferences.remove(Keys.LogsEnabled)
            preferences.remove(Keys.DebugConfigPreviewEnabled)
            preferences.remove(Keys.XrayTunModeEnabled)
            preferences.remove(Keys.RouteAllTraffic)
            preferences.remove(Keys.AllowIpv6)
            preferences.remove(Keys.PingProtocol)
            preferences.remove(Keys.AutoConnectMode)
            preferences.remove(Keys.ExcludedAppPackages)
            preferences.remove(Keys.AppLanguage)
            preferences.remove(Keys.AppTextSize)
            preferences.remove(Keys.AppAccentColor)
            preferences.remove(Keys.AppBackgroundStyle)
            preferences.remove(Keys.LocalBrandingOverrideEnabled)
            preferences.remove(Keys.RemoteBrandingProviderId)
            preferences.remove(Keys.RemoteBrandingPriority)
            preferences.remove(Keys.RemoteBrandingImagePath)
            preferences.remove(Keys.RemoteBrandingImageSha256)
            preferences.remove(Keys.RemoteBrandingBlurPercent)
            preferences.remove(Keys.RemoteBrandingAccentColor)
            preferences.remove(Keys.RemoteBrandingBackgroundColor)
            preferences.remove(Keys.RemoteBrandingUpdatedAt)
        }
        context.filesDir.resolve("branding").deleteRecursively()
    }

    suspend fun getOrCreateSubscriptionInstallId(): String {
        context.settingsDataStore.data.first()[Keys.SubscriptionInstallId]
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val generated = java.util.UUID.randomUUID().toString()
        context.settingsDataStore.edit { preferences ->
            if (preferences[Keys.SubscriptionInstallId].isNullOrBlank()) {
                preferences[Keys.SubscriptionInstallId] = generated
            }
        }
        return context.settingsDataStore.data.first()[Keys.SubscriptionInstallId] ?: generated
    }

    suspend fun getOrCreatePushDeviceSecret(): String {
        context.settingsDataStore.data.first()[Keys.PushDeviceSecret]
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val randomBytes = ByteArray(PUSH_DEVICE_SECRET_BYTES)
        SecureRandom().nextBytes(randomBytes)
        val generated = Base64.encodeToString(
            randomBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        context.settingsDataStore.edit { preferences ->
            if (preferences[Keys.PushDeviceSecret].isNullOrBlank()) {
                preferences[Keys.PushDeviceSecret] = generated
            }
        }
        return context.settingsDataStore.data.first()[Keys.PushDeviceSecret] ?: generated
    }

    suspend fun getMobileTelemetryLastQueuedAtMillis(): Long {
        val preferences = context.settingsDataStore.data.first()
        return preferences[Keys.MobileTelemetryLastQueuedAtMillis]
            ?: preferences[Keys.MobileTelemetryLastSentAtMillis]
            ?: 0L
    }

    suspend fun markMobileTelemetryQueued(queuedAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MobileTelemetryLastQueuedAtMillis] = queuedAtMillis
        }
    }

    suspend fun getMobileTelemetryLastUploadedAtMillis(): Long =
        context.settingsDataStore.data.first()[Keys.MobileTelemetryLastUploadedAtMillis] ?: 0L

    suspend fun markMobileTelemetryUploaded(uploadedAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MobileTelemetryLastUploadedAtMillis] = uploadedAtMillis
        }
    }

    suspend fun getMobileTelemetryInstallQueuedAtMillis(): Long =
        context.settingsDataStore.data.first()[Keys.MobileTelemetryInstallQueuedAtMillis] ?: 0L

    suspend fun markMobileTelemetryInstallQueued(queuedAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MobileTelemetryInstallQueuedAtMillis] = queuedAtMillis
        }
    }

    suspend fun getMobileTelemetryLastAppLaunchQueuedAtMillis(): Long =
        context.settingsDataStore.data.first()[Keys.MobileTelemetryLastAppLaunchQueuedAtMillis] ?: 0L

    suspend fun markMobileTelemetryAppLaunchQueued(queuedAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MobileTelemetryLastAppLaunchQueuedAtMillis] = queuedAtMillis
        }
    }

    suspend fun getPushRegistrationFingerprint(): String =
        context.settingsDataStore.data.first()[Keys.PushRegistrationFingerprint].orEmpty()

    suspend fun getPushRegistrationLastSentAtMillis(): Long =
        context.settingsDataStore.data.first()[Keys.PushRegistrationLastSentAtMillis] ?: 0L

    suspend fun markPushRegistrationSent(fingerprint: String, sentAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PushRegistrationFingerprint] = fingerprint
            preferences[Keys.PushRegistrationLastSentAtMillis] = sentAtMillis
        }
    }

    suspend fun getInAppPushRegistrationFingerprint(): String =
        context.settingsDataStore.data.first()[Keys.InAppPushRegistrationFingerprint].orEmpty()

    suspend fun getInAppPushRegistrationLastSentAtMillis(): Long =
        context.settingsDataStore.data.first()[Keys.InAppPushRegistrationLastSentAtMillis] ?: 0L

    suspend fun markInAppPushRegistrationSent(fingerprint: String, sentAtMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.InAppPushRegistrationFingerprint] = fingerprint
            preferences[Keys.InAppPushRegistrationLastSentAtMillis] = sentAtMillis
        }
    }

    private object Keys {
        val SelectedServerId = stringPreferencesKey("selected_server_id")
        val SocksPort = intPreferencesKey("socks_port")
        val HttpPort = intPreferencesKey("http_port")
        val LocalDnsPort = intPreferencesKey("local_dns_port")
        val TunMtu = intPreferencesKey("tun_mtu")
        val DnsServers = stringPreferencesKey("dns_servers")
        val LogsEnabled = booleanPreferencesKey("logs_enabled")
        val DebugConfigPreviewEnabled = booleanPreferencesKey("debug_config_preview_enabled")
        val XrayTunModeEnabled = booleanPreferencesKey("xray_tun_mode_enabled")
        val RouteAllTraffic = booleanPreferencesKey("route_all_traffic")
        val AllowIpv6 = booleanPreferencesKey("allow_ipv6")
        val PingProtocol = stringPreferencesKey("ping_protocol")
        val AutoConnectMode = stringPreferencesKey("auto_connect_mode")
        val ExcludedAppPackages = stringSetPreferencesKey("excluded_app_packages")
        val SubscriptionInstallId = stringPreferencesKey("subscription_install_id")
        val MobileTelemetryLastSentAtMillis = longPreferencesKey("mobile_telemetry_last_sent_at_millis")
        val MobileTelemetryLastQueuedAtMillis = longPreferencesKey("mobile_telemetry_last_queued_at_millis")
        val MobileTelemetryLastUploadedAtMillis = longPreferencesKey("mobile_telemetry_last_uploaded_at_millis")
        val MobileTelemetryInstallQueuedAtMillis = longPreferencesKey("mobile_telemetry_install_queued_at_millis")
        val MobileTelemetryLastAppLaunchQueuedAtMillis = longPreferencesKey("mobile_telemetry_last_app_launch_queued_at_millis")
        val SubscriptionProviderId = stringPreferencesKey("subscription_provider_id")
        val SubscriptionProviderDomainHash = stringPreferencesKey("subscription_provider_domain_hash")
        val ProviderTelemetryLastSentAtMillis = longPreferencesKey("provider_telemetry_last_sent_at_millis")
        val PushDeviceSecret = stringPreferencesKey("push_device_secret")
        val PushRegistrationFingerprint = stringPreferencesKey("push_registration_fingerprint")
        val PushRegistrationLastSentAtMillis = longPreferencesKey("push_registration_last_sent_at_millis")
        val InAppPushRegistrationFingerprint = stringPreferencesKey("in_app_push_registration_fingerprint")
        val InAppPushRegistrationLastSentAtMillis = longPreferencesKey("in_app_push_registration_last_sent_at_millis")
        val AppLanguage = stringPreferencesKey("app_language")
        val AppTextSize = stringPreferencesKey("app_text_size")
        val AppAccentColor = stringPreferencesKey("app_accent_color")
        val AppBackgroundStyle = stringPreferencesKey("app_background_style")
        val LocalBrandingOverrideEnabled = booleanPreferencesKey("local_branding_override_enabled")
        val RemoteBrandingProviderId = stringPreferencesKey("remote_branding_provider_id")
        val RemoteBrandingPriority = intPreferencesKey("remote_branding_priority")
        val RemoteBrandingImagePath = stringPreferencesKey("remote_branding_image_path")
        val RemoteBrandingImageSha256 = stringPreferencesKey("remote_branding_image_sha256")
        val RemoteBrandingBlurPercent = intPreferencesKey("remote_branding_blur_percent")
        val RemoteBrandingAccentColor = stringPreferencesKey("remote_branding_accent_color")
        val RemoteBrandingBackgroundColor = stringPreferencesKey("remote_branding_background_color")
        val RemoteBrandingUpdatedAt = stringPreferencesKey("remote_branding_updated_at")
    }

    private companion object {
        const val PUSH_DEVICE_SECRET_BYTES = 32
    }
}
