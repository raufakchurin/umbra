package ru.myit.vlevpn.domain.model

data class AppSettings(
    val selectedServerId: ServerId?,
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
    val pingProtocol: PingProtocol,
    val autoConnectMode: AutoConnectMode,
    val excludedAppPackages: Set<String>,
    val backendMode: BackendMode,
    val subscriptionProviderId: String,
    val subscriptionProviderDomainHash: String,
    val providerTelemetryLastSentAtMillis: Long,
    val appLanguage: AppLanguage,
    val appTextSize: AppTextSize,
    val appAccentColor: AppAccentColor,
    val appBackgroundStyle: AppBackgroundStyle,
    val localBrandingOverrideEnabled: Boolean = false,
    val remoteBrandingProviderId: String = "",
    val remoteBrandingPriority: Int = 100,
    val remoteBrandingImagePath: String = "",
    val remoteBrandingImageSha256: String = "",
    val remoteBrandingBlurPercent: Int = 35,
    val remoteBrandingAccentColor: String = "",
    val remoteBrandingBackgroundColor: String = "",
    val remoteBrandingUpdatedAt: String = "",
) {
    companion object {
        val Default = AppSettings(
            selectedServerId = null,
            socksPort = 10808,
            httpPort = 10809,
            localDnsPort = 10853,
            tunMtu = 1500,
            dnsServers = listOf("1.1.1.1", "8.8.8.8"),
            logsEnabled = true,
            debugConfigPreviewEnabled = true,
            xrayTunModeEnabled = true,
            routeAllTraffic = true,
            allowIpv6 = false,
            pingProtocol = PingProtocol.PROXY_GET,
            autoConnectMode = AutoConnectMode.DISABLED,
            excludedAppPackages = emptySet(),
            backendMode = BackendMode.FAKE,
            subscriptionProviderId = "",
            subscriptionProviderDomainHash = "",
            providerTelemetryLastSentAtMillis = 0L,
            appLanguage = AppLanguage.RU,
            appTextSize = AppTextSize.NORMAL,
            appAccentColor = AppAccentColor.TEAL,
            appBackgroundStyle = AppBackgroundStyle.LIGHT,
            localBrandingOverrideEnabled = false,
            remoteBrandingProviderId = "",
            remoteBrandingPriority = 100,
            remoteBrandingImagePath = "",
            remoteBrandingImageSha256 = "",
            remoteBrandingBlurPercent = 35,
            remoteBrandingAccentColor = "",
            remoteBrandingBackgroundColor = "",
            remoteBrandingUpdatedAt = "",
        )
    }
}

enum class BackendMode {
    FAKE,
}

enum class AppLanguage(
    val displayName: String,
    val localeTag: String,
) {
    RU("Русский", "ru"),
    EN("English", "en"),
}

enum class AppTextSize(
    val displayName: String,
    val scale: Float,
) {
    SMALL("Small", 0.92f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.12f),
    EXTRA_LARGE("Extra large", 1.24f),
}

enum class AppAccentColor {
    TEAL,
    BLUE,
    VIOLET,
    RUBY,
    GRAPHITE,
}

enum class AppBackgroundStyle {
    LIGHT,
    MIST,
    WARM,
    DARK,
}

enum class AutoConnectMode(
    val displayName: String,
    val description: String,
) {
    DISABLED(
        displayName = "Выключено",
        description = "Start подключает выбранный вручную конфиг без автоподбора.",
    ),
    FIRST_AVAILABLE(
        displayName = "Первый доступный",
        description = "Проверяет пинг и подключается к первому серверу, который ответил.",
    ),
    LOWEST_PING(
        displayName = "Наименьший пинг",
        description = "Проверяет все серверы и выбирает минимальное время отклика.",
    ),
    FIRST_FROM_TOP_FIVE(
        displayName = "Первый из топ 5",
        description = "Берет 5 серверов с лучшим пингом и выбирает первый из них по порядку списка.",
    ),
}
