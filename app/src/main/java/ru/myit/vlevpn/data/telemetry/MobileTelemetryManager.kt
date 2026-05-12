package ru.myit.vlevpn.data.telemetry

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.logging.RuntimeLogEntry
import ru.myit.vlevpn.core.settings.SettingsDataStore
import ru.myit.vlevpn.data.subscription.SubscriptionDeviceHeaders
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository
import ru.myit.vlevpn.runtime.RuntimeConnectionManager
import ru.myit.vlevpn.runtime.RuntimeState
import ru.myit.vlevpn.runtime.RuntimeStats

@Singleton
class MobileTelemetryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val serverRepository: ServerRepository,
    private val deviceHeaders: SubscriptionDeviceHeaders,
    private val connectionManager: RuntimeConnectionManager,
    private val logs: LogRepository,
    private val client: MobileTelemetryClient,
    private val outboxDao: MobileTelemetryOutboxDao,
) {
    private val mutex = Mutex()
    private val runtimeObserverStarted = AtomicBoolean(false)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun schedulePeriodicReports() {
        val request = PeriodicWorkRequestBuilder<MobileTelemetryWorker>(
            PERIODIC_WORK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                networkConstraints(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun startRuntimeAnalyticsObserver(scope: CoroutineScope) {
        if (!runtimeObserverStarted.compareAndSet(false, true)) return

        scope.launch {
            var previous = connectionManager.state.value
            connectionManager.state.collect { current ->
                val previousRunning = previous as? RuntimeState.Running
                val currentRunning = current as? RuntimeState.Running
                when {
                    previousRunning == null && currentRunning != null -> {
                        runCatching {
                            queueAnalyticsEvent(
                                eventType = EVENT_TYPE_VPN_CONNECTED,
                                capturedAtMillis = currentRunning.connectedAtMillis,
                                properties = mapOf("transition" to "connected"),
                            )
                        }
                    }
                    previousRunning != null && currentRunning == null -> {
                        val now = System.currentTimeMillis()
                        runCatching {
                            queueAnalyticsEvent(
                                eventType = EVENT_TYPE_VPN_DISCONNECTED,
                                capturedAtMillis = now,
                                properties = mapOf(
                                    "transition" to "disconnected",
                                    "connected_at" to isoUtc(previousRunning.connectedAtMillis),
                                    "duration_seconds" to ((now - previousRunning.connectedAtMillis) / 1_000L).toString(),
                                ),
                            )
                        }
                    }
                }
                previous = current
            }
        }
    }

    suspend fun sendDueReport(force: Boolean = false) {
        if (!isTelemetryEnabled()) return

        mutex.withLock {
            val now = System.currentTimeMillis()
            queueInstallEventIfNeededLocked(now)
            queueDueReportLocked(force = force, now = now)
            flushOutboxBestEffortLocked(now)
        }
        enqueueFlushWork()
    }

    suspend fun sendColdLaunchReport() {
        if (!isTelemetryEnabled()) return

        mutex.withLock {
            val now = System.currentTimeMillis()
            queueInstallEventIfNeededLocked(now)
            queueAppLaunchEventIfDueLocked(now)
            queueDueReportLocked(force = false, now = now)
            flushOutboxBestEffortLocked(now)
        }
        enqueueFlushWork()
    }

    suspend fun runDueWork() {
        if (!isTelemetryEnabled()) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            queueInstallEventIfNeededLocked(now)
            queueDueReportLocked(force = false, now = now)
            flushOutboxLocked(now)
        }
    }

    suspend fun trackSubscriptionAdded(
        importedCount: Int,
        skippedCount: Int,
        providerId: String,
        domainHash: String,
        sourceType: String,
    ) {
        queueAnalyticsEvent(
            eventType = EVENT_TYPE_SUBSCRIPTION_ADDED,
            capturedAtMillis = System.currentTimeMillis(),
            properties = mapOf(
                "imported_count" to importedCount.toString(),
                "skipped_count" to skippedCount.toString(),
                "partner_id" to providerId.takeIf { it.isNotBlank() },
                "provider_domain_hash" to domainHash.takeIf { it.isNotBlank() },
                "source_type" to sourceType,
            ),
        )
    }

    suspend fun flushOutbox() {
        if (!isTelemetryEnabled()) return
        mutex.withLock {
            flushOutboxLocked(System.currentTimeMillis())
        }
    }

    private fun enqueueFlushWork() {
        val request = OneTimeWorkRequestBuilder<MobileTelemetryWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BASE_RETRY_DELAY_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            FLUSH_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private suspend fun queueAnalyticsEvent(
        eventType: String,
        capturedAtMillis: Long,
        properties: Map<String, String?> = emptyMap(),
    ) {
        if (!isTelemetryEnabled()) return
        mutex.withLock {
            queueEventLocked(
                eventType = eventType,
                capturedAtMillis = capturedAtMillis,
                properties = properties,
            )
            trimOutbox(System.currentTimeMillis())
        }
        enqueueFlushWork()
    }

    private suspend fun queueInstallEventIfNeededLocked(now: Long) {
        if (settingsDataStore.getMobileTelemetryInstallQueuedAtMillis() > 0L) return
        queueEventLocked(
            eventType = EVENT_TYPE_APP_INSTALL,
            capturedAtMillis = now,
            properties = mapOf("source" to "first_launch"),
        )
        settingsDataStore.markMobileTelemetryInstallQueued(now)
    }

    private suspend fun queueAppLaunchEventIfDueLocked(now: Long) {
        val lastQueuedAt = settingsDataStore.getMobileTelemetryLastAppLaunchQueuedAtMillis()
        if (now - lastQueuedAt < APP_LAUNCH_MIN_INTERVAL_MILLIS) return
        queueEventLocked(
            eventType = EVENT_TYPE_APP_LAUNCH,
            capturedAtMillis = now,
            properties = mapOf("source" to "cold_launch"),
        )
        settingsDataStore.markMobileTelemetryAppLaunchQueued(now)
    }

    private suspend fun queueEventLocked(
        eventType: String,
        capturedAtMillis: Long,
        properties: Map<String, String?> = emptyMap(),
    ) {
        val payload = buildPayload(
            lastSentAt = capturedAtMillis,
            cutoffMillis = capturedAtMillis,
            eventType = eventType,
            eventProperties = properties,
            includeErrors = false,
        )
        outboxDao.insert(
            MobileTelemetryOutboxEntity(
                eventId = payload.eventId,
                payloadJson = json.encodeToString(payload),
                createdAtMillis = capturedAtMillis,
                updatedAtMillis = capturedAtMillis,
            ),
        )
        logs.add(LogLevel.INFO, "Mobile analytics event queued: $eventType")
    }

    private suspend fun queueDueReportLocked(force: Boolean, now: Long) {
        val lastQueuedAt = settingsDataStore.getMobileTelemetryLastQueuedAtMillis()
        val lastUploadedAt = settingsDataStore.getMobileTelemetryLastUploadedAtMillis()
        val interval = heartbeatIntervalMillis(lastUploadedAt, now)
        if (!force && now - lastQueuedAt < interval) return

        val payload = buildPayload(
            lastSentAt = lastUploadedAt,
            cutoffMillis = now,
            eventType = EVENT_TYPE_HEARTBEAT,
            includeErrors = true,
        )
        outboxDao.insert(
            MobileTelemetryOutboxEntity(
                eventId = payload.eventId,
                payloadJson = json.encodeToString(payload),
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        settingsDataStore.markMobileTelemetryQueued(now)
        trimOutbox(now)
        logs.add(LogLevel.INFO, "Mobile telemetry queued")
    }

    private fun heartbeatIntervalMillis(lastUploadedAt: Long, now: Long): Long {
        val runtimeState = connectionManager.state.value
        val hasRecentError = logs.entries.value.any { entry ->
            entry.level == LogLevel.ERROR &&
                entry.timestampMillis > lastUploadedAt &&
                entry.timestampMillis <= now
        }
        return if (runtimeState is RuntimeState.Running || runtimeState is RuntimeState.Error || hasRecentError) {
            ACTIVE_HEARTBEAT_INTERVAL_MILLIS
        } else {
            CALM_HEARTBEAT_INTERVAL_MILLIS
        }
    }

    private suspend fun buildPayload(
        lastSentAt: Long,
        cutoffMillis: Long,
        eventType: String,
        eventProperties: Map<String, String?> = emptyMap(),
        includeErrors: Boolean,
    ): MobileTelemetryPayload {
        val settings = settingsRepository.settings.first()
        val selectedServer = serverRepository.selectedServer.first()
        val servers = serverRepository.servers.first()
        val runtimeState = connectionManager.state.value
        val runtimeStats = withTimeoutOrNull(STATS_READ_TIMEOUT_MS) {
            connectionManager.stats.first()
        } ?: RuntimeStats()
        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }
        val providerId = if (eventType == EVENT_TYPE_SUBSCRIPTION_ADDED) {
            eventProperties["partner_id"]?.takeIf { it.isNotBlank() }
                ?: eventProperties["provider_id"]?.takeIf { it.isNotBlank() }
        } else {
            settings.subscriptionProviderId.takeIf { it.isNotBlank() }
        }
        val providerDomainHash = if (eventType == EVENT_TYPE_SUBSCRIPTION_ADDED) {
            eventProperties["provider_domain_hash"]?.takeIf { it.isNotBlank() }
        } else {
            settings.subscriptionProviderDomainHash.takeIf { it.isNotBlank() }
        }

        return MobileTelemetryPayload(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            capturedAt = isoUtc(cutoffMillis),
            capturedAtLocal = isoLocal(cutoffMillis),
            appKey = BuildConfig.TELEMETRY_APP_KEY,
            providerId = providerId,
            providerDomainHash = providerDomainHash,
            device = MobileTelemetryDevice(
                hwid = deviceHeaders.stableHwid(),
                installId = settingsDataStore.getOrCreateSubscriptionInstallId(),
                packageName = context.packageName,
                appVersion = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toString(),
                osVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() },
                sdkInt = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                deviceName = deviceName,
                locale = Locale.getDefault().toLanguageTag(),
                timezone = TimeZone.getDefault().id,
            ),
            activity = MobileTelemetryActivity(
                runtimeState = runtimeState.nameForTelemetry(),
                vpnConnected = runtimeState is RuntimeState.Running,
                connectedAt = (runtimeState as? RuntimeState.Running)?.connectedAtMillis?.let(::isoUtc),
                selectedServerId = selectedServer?.id?.value,
                selectedServerName = null,
                selectedServerProtocol = selectedServer?.protocol?.name,
                serversCount = servers.size,
                uplinkBytes = runtimeStats.uplinkBytes,
                downlinkBytes = runtimeStats.downlinkBytes,
                delayMs = runtimeStats.delayMs,
            ),
            errors = if (includeErrors) {
                logs.entries.value
                    .filter { entry -> entry.level == LogLevel.ERROR }
                    .filter { entry -> entry.timestampMillis > lastSentAt && entry.timestampMillis <= cutoffMillis }
                    .takeLast(MAX_ERROR_EVENTS)
                .map(::toTelemetryError)
            } else {
                emptyList()
            },
            subscriptions = buildSubscriptionsSummary(servers, eventType),
            eventProperties = eventProperties,
        )
    }

    private fun buildSubscriptionsSummary(
        servers: List<ServerProfile>,
        eventType: String,
    ): List<MobileTelemetrySubscription> {
        if (eventType != EVENT_TYPE_HEARTBEAT && eventType != EVENT_TYPE_SUBSCRIPTION_ADDED) {
            return emptyList()
        }

        return servers
            .asSequence()
            .filter { it.subscriptionId.isNotBlank() }
            .groupBy { it.subscriptionId }
            .values
            .mapNotNull { profiles ->
                val primary = profiles.maxWithOrNull(
                    compareBy<ServerProfile> { it.subscriptionActivatedAtMillis }
                        .thenBy { it.subscriptionImportedAtMillis }
                        .thenBy { it.updatedAtMillis },
                ) ?: return@mapNotNull null
                val providerId = profiles.firstNotNullOfOrNull { profile ->
                    profile.subscriptionProviderId.takeIf { it.isNotBlank() }
                }
                val providerDomainHash = profiles.firstNotNullOfOrNull { profile ->
                    profile.subscriptionProviderDomainHash.takeIf { it.isNotBlank() }
                }
                val uploaded = profiles.maxOfOrNull { it.subscriptionUploadBytes }.orZero()
                val downloaded = profiles.maxOfOrNull { it.subscriptionDownloadBytes }.orZero()
                val total = profiles.maxOfOrNull { it.subscriptionTotalBytes }.orZero()
                MobileTelemetrySubscription(
                    subscriptionId = primary.subscriptionId,
                    providerId = providerId,
                    providerDomainHash = providerDomainHash,
                    profilesCount = profiles.size,
                    autoUpdateOnLaunch = profiles.any { it.subscriptionAutoUpdateOnLaunchEnabled },
                    updateIntervalHours = profiles.firstNotNullOfOrNull { profile ->
                        profile.subscriptionUpdateIntervalHours.takeIf { it > 0 }
                    } ?: 0,
                    expiresAt = profiles.maxOfOrNull { it.subscriptionExpireAtMillis }
                        ?.takeIf { it > 0L }
                        ?.let(::isoUtc),
                    trafficTotalBytes = total.takeIf { it > 0L },
                    trafficUsedBytes = (uploaded + downloaded).takeIf { it > 0L },
                )
            }
            .sortedWith(
                compareByDescending<MobileTelemetrySubscription> { subscription ->
                    servers
                        .filter { it.subscriptionId == subscription.subscriptionId }
                        .maxOfOrNull { it.subscriptionActivatedAtMillis }
                        .orZero()
                }.thenBy { it.subscriptionId },
            )
            .take(MAX_SUBSCRIPTIONS_PER_REPORT)
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private suspend fun flushOutboxLocked(now: Long) {
        trimOutbox(now)
        val rows = outboxDao.getPending(nowMillis = now, limit = OUTBOX_BATCH_SIZE)
        if (rows.isEmpty()) return

        val decoded = rows.mapNotNull { row ->
            runCatching { json.decodeFromString<MobileTelemetryPayload>(row.payloadJson) }
                .getOrElse {
                    logs.add(LogLevel.WARN, "Dropping invalid mobile telemetry event ${row.eventId}")
                    null
                }
        }
        val decodedEventIds = decoded.map { it.eventId }.toSet()
        val invalidEventIds = rows.map { it.eventId }.filterNot { it in decodedEventIds }
        if (invalidEventIds.isNotEmpty()) {
            outboxDao.deleteByEventIds(invalidEventIds)
        }
        if (decoded.isEmpty()) return

        val eventIds = decoded.map { it.eventId }
        try {
            val response = client.sendBatch(decoded)
            val processedEventIds = (response.acceptedEventIds + response.duplicateEventIds)
                .toSet()
                .ifEmpty { eventIds.toSet() }
            outboxDao.deleteByEventIds(processedEventIds.toList())
            if (decoded.any { it.eventId in processedEventIds && it.eventType == EVENT_TYPE_HEARTBEAT }) {
                settingsDataStore.markMobileTelemetryUploaded(now)
            }
            logs.add(LogLevel.INFO, "Mobile telemetry uploaded: ${processedEventIds.size} events")
        } catch (error: MobileTelemetryPermanentException) {
            outboxDao.deleteByEventIds(eventIds)
            logs.add(LogLevel.WARN, "Dropping mobile telemetry batch: ${error.message.orEmpty()}")
        } catch (error: Throwable) {
            val maxRetryCount = rows.maxOfOrNull { it.retryCount } ?: 0
            outboxDao.markFailed(
                eventIds = eventIds,
                nextRetryAtMillis = now + retryDelayMillis(maxRetryCount),
                updatedAtMillis = now,
                lastError = error.message?.take(MAX_LAST_ERROR_LENGTH),
            )
            throw error
        }
    }

    private suspend fun flushOutboxBestEffortLocked(now: Long) {
        runCatching {
            flushOutboxLocked(now)
        }.onFailure { error ->
            logs.add(LogLevel.WARN, "Mobile telemetry upload deferred: ${error.message.orEmpty()}")
        }
    }

    private suspend fun trimOutbox(now: Long) {
        outboxDao.deleteOlderThan(now - OUTBOX_RETENTION_MILLIS)
        outboxDao.trimToMaxRows(OUTBOX_MAX_ROWS)
    }

    private fun retryDelayMillis(retryCount: Int): Long {
        val multiplier = 1L shl retryCount.coerceIn(0, MAX_BACKOFF_POWER)
        return (BASE_RETRY_DELAY_MILLIS * multiplier).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }

    private fun toTelemetryError(entry: RuntimeLogEntry): MobileTelemetryError =
        MobileTelemetryError(
            title = "Android runtime error",
            message = null,
            severity = "error",
            fingerprint = sha256(entry.message).take(40),
            occurredAt = isoUtc(entry.timestampMillis),
            payload = mapOf("level" to entry.level.name),
        )

    private fun RuntimeState.nameForTelemetry(): String = when (this) {
        RuntimeState.Idle -> "idle"
        RuntimeState.PreparingVpnPermission -> "preparing_vpn_permission"
        RuntimeState.StartingForeground -> "starting_foreground"
        RuntimeState.BuildingConfig -> "building_config"
        RuntimeState.EstablishingVpn -> "establishing_vpn"
        RuntimeState.StartingNativeCore -> "starting_native_core"
        RuntimeState.VerifyingConnection -> "verifying_connection"
        is RuntimeState.Running -> "running"
        RuntimeState.Stopping -> "stopping"
        is RuntimeState.Error -> "error"
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun isoUtc(timestampMillis: Long): String =
        checkNotNull(ISO_FORMAT.get()).format(Date(timestampMillis))

    private fun isoLocal(timestampMillis: Long): String =
        checkNotNull(LOCAL_ISO_FORMAT.get()).format(Date(timestampMillis))

    companion object {
        const val WORK_NAME = "mobile_telemetry"
        const val FLUSH_WORK_NAME = "mobile_telemetry_flush"
        const val PERIODIC_WORK_INTERVAL_MINUTES = 30L
        private const val EVENT_TYPE_HEARTBEAT = "heartbeat"
        private const val EVENT_TYPE_APP_INSTALL = "app_install"
        private const val EVENT_TYPE_APP_LAUNCH = "app_launch"
        private const val EVENT_TYPE_SUBSCRIPTION_ADDED = "subscription_added"
        private const val EVENT_TYPE_VPN_CONNECTED = "vpn_connected"
        private const val EVENT_TYPE_VPN_DISCONNECTED = "vpn_disconnected"
        private val ACTIVE_HEARTBEAT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30)
        private val CALM_HEARTBEAT_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
        private val APP_LAUNCH_MIN_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30)
        private const val OUTBOX_BATCH_SIZE = 50
        private const val OUTBOX_MAX_ROWS = 2_000
        private val OUTBOX_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(14)
        private val BASE_RETRY_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(15)
        private val MAX_RETRY_DELAY_MILLIS = TimeUnit.HOURS.toMillis(12)
        private const val MAX_BACKOFF_POWER = 5
        private const val MAX_LAST_ERROR_LENGTH = 220
        private const val MAX_ERROR_EVENTS = 30
        private const val MAX_SUBSCRIPTIONS_PER_REPORT = 32
        private const val STATS_READ_TIMEOUT_MS = 500L
        private fun isTelemetryEnabled(): Boolean =
            BuildConfig.TELEMETRY_ENABLED && BuildConfig.TELEMETRY_APP_KEY.isNotBlank()
        private val ISO_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }
        private val LOCAL_ISO_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        }

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}
