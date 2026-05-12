package ru.myit.vlevpn.data.subscription

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.data.branding.PartnerBrandingManager
import ru.myit.vlevpn.data.push.InAppNotificationManager
import ru.myit.vlevpn.data.push.PushRegistrationManager
import ru.myit.vlevpn.data.telemetry.MobileTelemetryManager
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ImportSummary
import ru.myit.vlevpn.domain.repository.ServerImportRepository
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

@Singleton
class ServerImportRepositoryImpl @Inject constructor(
    private val fetchClient: SubscriptionFetchClient,
    private val parser: SubscriptionParser,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val logs: LogRepository,
    private val mobileTelemetryManager: MobileTelemetryManager,
    private val pushRegistrationManager: PushRegistrationManager,
    private val inAppNotificationManager: InAppNotificationManager,
    private val partnerBrandingManager: PartnerBrandingManager,
) : ServerImportRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun importFromInput(input: String): ImportSummary {
        val trimmed = input.normalizedImportInput()
        if (trimmed.isBlank()) {
            return ImportSummary(0, 0, "Clipboard is empty")
        }

        val fetched = if (trimmed.isSubscriptionUrl()) {
            logs.add(LogLevel.INFO, "Fetching subscription URL")
            fetchClient.fetch(trimmed)
        } else {
            SubscriptionFetchResult(body = trimmed)
        }

        val parsed = parser.parse(
            input = fetched.body,
            sourceHint = trimmed,
            metadata = fetched.metadata,
        )
        if (parsed.profiles.isNotEmpty()) {
            val hadServers = serverRepository.hasServers()
            val subscriptionId = parsed.profiles.firstOrNull()?.subscriptionId.orEmpty()
            if (subscriptionId.isNotBlank()) {
                serverRepository.replaceSubscription(subscriptionId, parsed.profiles)
            } else {
                serverRepository.upsertAll(parsed.profiles)
            }
            if (!hadServers) {
                serverRepository.select(parsed.profiles.first().id)
            }
            saveProviderMetadataBestEffort(parsed)
            runCatching {
                mobileTelemetryManager.trackSubscriptionAdded(
                    importedCount = parsed.importedCount,
                    skippedCount = parsed.skippedCount,
                    providerId = parsed.providerId,
                    domainHash = parsed.providerDomainHash,
                    sourceType = trimmed.subscriptionSourceType(),
                )
            }.onFailure { error ->
                logs.add(LogLevel.WARN, "Subscription analytics queue failed: ${error.message.orEmpty()}")
            }
            scheduleExtraKeysImport(
                sourceHint = trimmed,
                metadata = fetched.metadata,
                mainProfiles = parsed.profiles,
            )
        }

        val message = when {
            parsed.importedCount == 0 && parsed.skippedCount == 0 -> "Nothing to import"
            parsed.skippedCount == 0 -> "Imported ${parsed.importedCount} profile(s)"
            else -> "Imported ${parsed.importedCount}, skipped ${parsed.skippedCount}"
        }
        logs.add(LogLevel.INFO, message)
        return ImportSummary(parsed.importedCount, parsed.skippedCount, message)
    }

    private fun String.isSubscriptionUrl(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

    private fun String.subscriptionSourceType(): String =
        if (isSubscriptionUrl()) "url" else "manual_text"

    private suspend fun saveProviderMetadataBestEffort(parsed: ParsedSubscription) {
        if (parsed.providerId.isBlank()) return
        runCatching {
            settingsRepository.updateSubscriptionProvider(
                providerId = parsed.providerId,
                domainHash = parsed.providerDomainHash,
            )
            logs.add(LogLevel.INFO, "Subscription provider metadata saved")
            pushRegistrationManager.registerCurrentToken()
            inAppNotificationManager.registerAndPollAsync()
            partnerBrandingManager.refreshAsync(scope)
        }.onFailure { error ->
            logs.add(LogLevel.WARN, "Subscription provider metadata failed: ${error.message.orEmpty()}")
        }
    }

    private fun scheduleExtraKeysImport(
        sourceHint: String,
        metadata: SubscriptionMetadata,
        mainProfiles: List<ServerProfile>,
    ) {
        if (metadata.extraKeysAddUrl.isBlank()) return
        val sourceProfile = mainProfiles.firstOrNull { it.subscriptionId.isNotBlank() } ?: return
        val importedAtMillis = sourceProfile.subscriptionImportedAtMillis
        if (importedAtMillis <= 0L) return
        val startPosition = mainProfiles.maxOfOrNull { it.subscriptionPosition }?.plus(1)
            ?: mainProfiles.size
        val extraKeysUrl = runCatching {
            ExtraKeysAddUrl.resolve(metadata.extraKeysAddUrl, sourceHint)
        }.onFailure { error ->
            logs.add(LogLevel.WARN, "Extra subscription keys URL ignored: ${error.message.orEmpty()}")
        }.getOrDefault("")
        if (extraKeysUrl.isBlank()) return

        scope.launch {
            val extraKeys = fetchExtraKeys(extraKeysUrl)
            if (extraKeys.isEmpty()) return@launch

            val parsedExtra = parser.parse(
                input = "",
                sourceHint = sourceHint,
                metadata = metadata,
                extraItems = extraKeys,
            )
            val extraProfiles = parsedExtra.profiles
                .filter { it.isOlcRtc }
                .mapIndexed { index, profile ->
                    profile
                        .copy(name = extraKeyDisplayName(index))
                        .withSubscriptionSource(sourceProfile, startPosition + index)
                }
            if (extraProfiles.isEmpty()) return@launch

            val appended = serverRepository.appendSubscriptionProfiles(
                subscriptionId = sourceProfile.subscriptionId,
                importedAtMillis = importedAtMillis,
                servers = extraProfiles,
            )
            if (appended) {
                logs.add(LogLevel.INFO, "Added ${extraProfiles.size} extra olcRTC key(s) to subscription")
            } else {
                logs.add(LogLevel.INFO, "Extra subscription keys ignored because subscription changed")
            }
        }
    }

    private fun ServerProfile.withSubscriptionSource(source: ServerProfile, position: Int): ServerProfile =
        copy(
            subscriptionId = source.subscriptionId,
            subscriptionName = source.subscriptionName,
            subscriptionImportedAtMillis = source.subscriptionImportedAtMillis,
            subscriptionActivatedAtMillis = source.subscriptionActivatedAtMillis,
            subscriptionPosition = position,
            subscriptionUpdateIntervalHours = source.subscriptionUpdateIntervalHours,
            subscriptionUploadBytes = source.subscriptionUploadBytes,
            subscriptionDownloadBytes = source.subscriptionDownloadBytes,
            subscriptionTotalBytes = source.subscriptionTotalBytes,
            subscriptionExpireAtMillis = source.subscriptionExpireAtMillis,
            subscriptionSupportUrl = source.subscriptionSupportUrl,
            subscriptionWebPageUrl = source.subscriptionWebPageUrl,
            subscriptionSourceUrl = source.subscriptionSourceUrl,
            subscriptionAnnounce = source.subscriptionAnnounce,
            subscriptionAutoUpdateOnLaunchEnabled = source.subscriptionAutoUpdateOnLaunchEnabled,
            subscriptionProviderId = source.subscriptionProviderId,
            subscriptionProviderDomainHash = source.subscriptionProviderDomainHash,
        )

    private fun extraKeyDisplayName(index: Int): String =
        "LTE | Обход БС 🏳️ #${index + 1}"

    private suspend fun fetchExtraKeys(url: String): List<String> {
        if (url.isBlank()) return emptyList()
        logs.add(LogLevel.INFO, "Fetching extra subscription keys")
        return runCatching {
            fetchClient.fetchExtraKeys(url)
        }.onSuccess { keys ->
            if (keys.isNotEmpty()) {
                logs.add(LogLevel.INFO, "Fetched ${keys.size} extra olcRTC key(s)")
            } else {
                logs.add(LogLevel.INFO, "Extra subscription keys response contained no olcRTC keys")
            }
        }.onFailure { error ->
            logs.add(LogLevel.WARN, "Extra subscription keys failed: ${error.message.orEmpty()}")
        }.getOrDefault(emptyList())
    }
}

internal fun String.normalizedImportInput(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWithKnownImportPayload()) return trimmed
    return IMPORT_PAYLOAD_REGEX
        .find(trimmed)
        ?.value
        ?.trimImportTokenSuffix()
        ?: trimmed
}

private fun String.startsWithKnownImportPayload(): Boolean =
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("vless://", ignoreCase = true) ||
        startsWith("vmess://", ignoreCase = true) ||
        startsWith("trojan://", ignoreCase = true) ||
        startsWith("ss://", ignoreCase = true) ||
        startsWith("olcrtc://", ignoreCase = true) ||
        startsWith("{") ||
        startsWith("[")

private fun String.trimImportTokenSuffix(): String =
    trimEnd { char ->
        char.isWhitespace() || char in setOf(',', '.', ';', ':', ')', ']', '}', '>', '"', '\'')
    }

private val IMPORT_PAYLOAD_REGEX = Regex(
    pattern = """(?i)\b(?:https?://|vless://|vmess://|trojan://|ss://|olcrtc://)[^\s<>"']+""",
)
