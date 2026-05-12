package ru.myit.vlevpn.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.AppTextSize
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.BackendMode
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.model.VpnProfile

private val runtimeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun StartProxyRequest.encodeForService(): String =
    runtimeJson.encodeToString(toDto())

fun decodeStartProxyRequest(payload: String): StartProxyRequest =
    runtimeJson.decodeFromString<StartProxyRequestDto>(payload).toDomain()

@Serializable
private data class StartProxyRequestDto(
    val server: ServerProfileDto,
    val settings: AppSettingsDto,
    val vpnProfile: VpnProfileDto,
)

@Serializable
private data class ServerProfileDto(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val credential: String,
    val password: String,
    val method: String,
    val transport: String,
    val security: String,
    val sni: String,
    val path: String,
    val flow: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val networkMode: String,
    val headerHost: String,
    val subscriptionId: String,
    val subscriptionName: String,
    val subscriptionImportedAtMillis: Long,
    val subscriptionActivatedAtMillis: Long,
    val subscriptionPosition: Int,
    val subscriptionUpdateIntervalHours: Int,
    val subscriptionUploadBytes: Long,
    val subscriptionDownloadBytes: Long,
    val subscriptionTotalBytes: Long,
    val subscriptionExpireAtMillis: Long,
    val subscriptionSupportUrl: String,
    val subscriptionWebPageUrl: String,
    val subscriptionSourceUrl: String,
    val subscriptionAnnounce: String = "",
    val subscriptionAutoUpdateOnLaunchEnabled: Boolean = false,
    val protocolPayloadJson: String = "",
    val customJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
private data class AppSettingsDto(
    val selectedServerId: String?,
    val socksPort: Int,
    val httpPort: Int,
    val localDnsPort: Int,
    val tunMtu: Int,
    val dnsServers: List<String>,
    val logsEnabled: Boolean,
    val debugConfigPreviewEnabled: Boolean,
    val xrayTunModeEnabled: Boolean,
    val routeAllTraffic: Boolean,
    val allowIpv6: Boolean,
    val pingProtocol: String,
    val autoConnectMode: String = AutoConnectMode.DISABLED.name,
    val excludedAppPackages: Set<String> = emptySet(),
    val backendMode: String,
    val subscriptionProviderId: String = "",
    val subscriptionProviderDomainHash: String = "",
    val providerTelemetryLastSentAtMillis: Long = 0L,
    val appLanguage: String = AppLanguage.RU.name,
    val appTextSize: String = AppTextSize.NORMAL.name,
    val appAccentColor: String = AppAccentColor.TEAL.name,
    val appBackgroundStyle: String = AppBackgroundStyle.LIGHT.name,
)

@Serializable
private data class VpnProfileDto(
    val sessionName: String,
    val mtu: Int,
    val dnsServers: List<String>,
    val routeAllTraffic: Boolean,
    val allowIpv6: Boolean,
    val excludedPackageNames: Set<String> = emptySet(),
)

private fun StartProxyRequest.toDto(): StartProxyRequestDto =
    StartProxyRequestDto(
        server = server.toDto(),
        settings = settings.toDto(),
        vpnProfile = vpnProfile.toDto(),
    )

private fun ServerProfile.toDto(): ServerProfileDto =
    ServerProfileDto(
        id = id.value,
        name = name,
        protocol = protocol.name,
        host = host,
        port = port,
        credential = credential,
        password = password,
        method = method,
        transport = transport,
        security = security,
        sni = sni,
        path = path,
        flow = flow,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        spiderX = spiderX,
        networkMode = networkMode,
        headerHost = headerHost,
        subscriptionId = subscriptionId,
        subscriptionName = subscriptionName,
        subscriptionImportedAtMillis = subscriptionImportedAtMillis,
        subscriptionActivatedAtMillis = subscriptionActivatedAtMillis,
        subscriptionPosition = subscriptionPosition,
        subscriptionUpdateIntervalHours = subscriptionUpdateIntervalHours,
        subscriptionUploadBytes = subscriptionUploadBytes,
        subscriptionDownloadBytes = subscriptionDownloadBytes,
        subscriptionTotalBytes = subscriptionTotalBytes,
        subscriptionExpireAtMillis = subscriptionExpireAtMillis,
        subscriptionSupportUrl = subscriptionSupportUrl,
        subscriptionWebPageUrl = subscriptionWebPageUrl,
        subscriptionSourceUrl = subscriptionSourceUrl,
        subscriptionAnnounce = subscriptionAnnounce,
        subscriptionAutoUpdateOnLaunchEnabled = subscriptionAutoUpdateOnLaunchEnabled,
        protocolPayloadJson = protocolPayloadJson,
        customJson = customJson,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

private fun AppSettings.toDto(): AppSettingsDto =
    AppSettingsDto(
        selectedServerId = selectedServerId?.value,
        socksPort = socksPort,
        httpPort = httpPort,
        localDnsPort = localDnsPort,
        tunMtu = tunMtu,
        dnsServers = dnsServers,
        logsEnabled = logsEnabled,
        debugConfigPreviewEnabled = debugConfigPreviewEnabled,
        xrayTunModeEnabled = xrayTunModeEnabled,
        routeAllTraffic = routeAllTraffic,
        allowIpv6 = allowIpv6,
        pingProtocol = pingProtocol.name,
        autoConnectMode = autoConnectMode.name,
        excludedAppPackages = excludedAppPackages,
        backendMode = backendMode.name,
        subscriptionProviderId = subscriptionProviderId,
        subscriptionProviderDomainHash = subscriptionProviderDomainHash,
        providerTelemetryLastSentAtMillis = providerTelemetryLastSentAtMillis,
        appLanguage = appLanguage.name,
        appTextSize = appTextSize.name,
        appAccentColor = appAccentColor.name,
        appBackgroundStyle = appBackgroundStyle.name,
    )

private fun VpnProfile.toDto(): VpnProfileDto =
    VpnProfileDto(
        sessionName = sessionName,
        mtu = mtu,
        dnsServers = dnsServers,
        routeAllTraffic = routeAllTraffic,
        allowIpv6 = allowIpv6,
        excludedPackageNames = excludedPackageNames,
    )

private fun StartProxyRequestDto.toDomain(): StartProxyRequest =
    StartProxyRequest(
        server = server.toDomain(),
        settings = settings.toDomain(),
        vpnProfile = vpnProfile.toDomain(),
    )

private fun ServerProfileDto.toDomain(): ServerProfile =
    ServerProfile(
        id = ServerId(id),
        name = name,
        protocol = ProxyProtocol.valueOf(protocol),
        host = host,
        port = port,
        credential = credential,
        password = password,
        method = method,
        transport = transport,
        security = security,
        sni = sni,
        path = path,
        flow = flow,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        spiderX = spiderX,
        networkMode = networkMode,
        headerHost = headerHost,
        subscriptionId = subscriptionId,
        subscriptionName = subscriptionName,
        subscriptionImportedAtMillis = subscriptionImportedAtMillis,
        subscriptionActivatedAtMillis = subscriptionActivatedAtMillis,
        subscriptionPosition = subscriptionPosition,
        subscriptionUpdateIntervalHours = subscriptionUpdateIntervalHours,
        subscriptionUploadBytes = subscriptionUploadBytes,
        subscriptionDownloadBytes = subscriptionDownloadBytes,
        subscriptionTotalBytes = subscriptionTotalBytes,
        subscriptionExpireAtMillis = subscriptionExpireAtMillis,
        subscriptionSupportUrl = subscriptionSupportUrl,
        subscriptionWebPageUrl = subscriptionWebPageUrl,
        subscriptionSourceUrl = subscriptionSourceUrl,
        subscriptionAnnounce = subscriptionAnnounce,
        subscriptionAutoUpdateOnLaunchEnabled = subscriptionAutoUpdateOnLaunchEnabled,
        protocolPayloadJson = protocolPayloadJson,
        customJson = customJson,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

private fun AppSettingsDto.toDomain(): AppSettings =
    AppSettings(
        selectedServerId = selectedServerId?.let(::ServerId),
        socksPort = socksPort,
        httpPort = httpPort,
        localDnsPort = localDnsPort,
        tunMtu = tunMtu,
        dnsServers = dnsServers,
        logsEnabled = logsEnabled,
        debugConfigPreviewEnabled = debugConfigPreviewEnabled,
        xrayTunModeEnabled = xrayTunModeEnabled,
        routeAllTraffic = routeAllTraffic,
        allowIpv6 = allowIpv6,
        pingProtocol = PingProtocol.valueOf(pingProtocol),
        autoConnectMode = runCatching { AutoConnectMode.valueOf(autoConnectMode) }
            .getOrDefault(AutoConnectMode.DISABLED),
        excludedAppPackages = excludedAppPackages,
        backendMode = BackendMode.valueOf(backendMode),
        subscriptionProviderId = subscriptionProviderId,
        subscriptionProviderDomainHash = subscriptionProviderDomainHash,
        providerTelemetryLastSentAtMillis = providerTelemetryLastSentAtMillis,
        appLanguage = runCatching { AppLanguage.valueOf(appLanguage) }.getOrDefault(AppLanguage.RU),
        appTextSize = runCatching { AppTextSize.valueOf(appTextSize) }.getOrDefault(AppTextSize.NORMAL),
        appAccentColor = runCatching { AppAccentColor.valueOf(appAccentColor) }.getOrDefault(AppAccentColor.TEAL),
        appBackgroundStyle = runCatching { AppBackgroundStyle.valueOf(appBackgroundStyle) }.getOrDefault(AppBackgroundStyle.LIGHT),
    )

private fun VpnProfileDto.toDomain(): VpnProfile =
    VpnProfile(
        sessionName = sessionName,
        mtu = mtu,
        dnsServers = dnsServers,
        routeAllTraffic = routeAllTraffic,
        allowIpv6 = allowIpv6,
        excludedPackageNames = excludedPackageNames,
    )
