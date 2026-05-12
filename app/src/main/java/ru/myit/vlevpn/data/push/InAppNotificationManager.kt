package ru.myit.vlevpn.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.MainActivity
import ru.myit.vlevpn.R
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.settings.SettingsDataStore
import ru.myit.vlevpn.core.url.toSafeActionUri
import ru.myit.vlevpn.domain.model.InAppMessageDisplayConfig
import ru.myit.vlevpn.domain.model.InAppForegroundMessage

@Singleton
class InAppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val client: InAppNotificationClient,
    private val logs: LogRepository,
    private val foregroundMessageBus: InAppForegroundMessageBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pollMutex = Mutex()
    private var foregroundPollingJob: Job? = null
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun schedulePeriodicChecks() {
        if (!isEnabled()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<InAppNotificationWorker>(
            POLL_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun registerAndPollAsync() {
        if (!isEnabled()) return
        scope.launch {
            runCatching { registerAndPollNow() }
                .onFailure { error ->
                    logs.add(LogLevel.WARN, "In-app notification check failed: ${error.message.orEmpty()}")
                }
        }
    }

    fun startForegroundPolling() {
        if (!isEnabled()) return
        if (foregroundPollingJob?.isActive == true) return

        foregroundPollingJob = scope.launch {
            while (isActive) {
                runCatching { registerAndPollNow() }
                    .onFailure { error ->
                        logs.add(LogLevel.WARN, "Foreground in-app notification check failed: ${error.message.orEmpty()}")
                    }
                delay(FOREGROUND_POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun stopForegroundPolling() {
        foregroundPollingJob?.cancel()
        foregroundPollingJob = null
    }

    suspend fun runDueWork() {
        if (!isEnabled()) return
        registerAndPollNow()
    }

    suspend fun registerAndPollNow() {
        if (!isEnabled()) return
        if (!hasNetworkConnection()) return
        pollMutex.withLock {
            if (isEnabled() && hasNetworkConnection()) {
                registerClientIfNeeded()
                pollInbox()
            }
        }
    }

    private suspend fun registerClientIfNeeded() {
        val payload = buildRegisterPayload()
        val fingerprint = payload.fingerprint()
        val now = System.currentTimeMillis()
        val alreadySent = settingsDataStore.getInAppPushRegistrationFingerprint() == fingerprint &&
            now - settingsDataStore.getInAppPushRegistrationLastSentAtMillis() < REGISTER_REFRESH_INTERVAL_MILLIS
        if (alreadySent) return

        client.register(payload)
        settingsDataStore.markInAppPushRegistrationSent(fingerprint, now)
        logs.add(LogLevel.INFO, "In-app notification client registered")
    }

    private suspend fun pollInbox() {
        val installId = settingsDataStore.getOrCreateSubscriptionInstallId()
        val deviceSecret = settingsDataStore.getOrCreatePushDeviceSecret()
        val response = client.inbox(buildAuthPayload(installId, deviceSecret))
        if (response.messages.isEmpty()) return

        val acknowledged = response.messages.mapNotNull { message ->
            runCatching {
                require(message.kind == KIND && message.version == VERSION) { "Unsupported in-app message" }
                check(
                    PushMessageCrypto.verifySignature(
                        deviceSecretBase64 = deviceSecret,
                        deliveryId = message.deliveryId,
                        campaignId = message.campaignId,
                        encryptedPayload = message.payload,
                        signatureHex = message.signature,
                    ),
                ) {
                    "Invalid in-app message signature"
                }
                val payloadJson = PushMessageCrypto.decryptPayload(deviceSecret, message.payload)
                val payload = json.decodeFromString<PartnerPushPayload>(payloadJson)
                if (showMessage(message.deliveryId, payload)) message.deliveryId else null
            }.onFailure { error ->
                logs.add(LogLevel.WARN, "Rejected in-app notification: ${error.message.orEmpty()}")
            }.getOrNull()
        }
        if (acknowledged.isNotEmpty()) {
            client.ack(buildAckPayload(installId, deviceSecret, acknowledged))
            logs.add(LogLevel.INFO, "In-app notification(s) shown: ${acknowledged.size}")
        }
    }

    private suspend fun buildRegisterPayload(): InAppPushRegisterPayload {
        val settings = settingsDataStore.settings.first()
        return InAppPushRegisterPayload(
            appKey = BuildConfig.TELEMETRY_APP_KEY,
            installId = settingsDataStore.getOrCreateSubscriptionInstallId(),
            deviceSecret = settingsDataStore.getOrCreatePushDeviceSecret(),
            packageName = context.packageName,
            providerId = settings.subscriptionProviderId.takeIf { it.isNotBlank() },
            providerDomainHash = settings.subscriptionProviderDomainHash.takeIf { it.isNotBlank() },
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toString(),
            sdkInt = Build.VERSION.SDK_INT,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
        )
    }

    private fun buildAuthPayload(installId: String, deviceSecret: String): InAppPushAuthPayload {
        val timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        val nonce = UUID.randomUUID().toString()
        return InAppPushAuthPayload(
            appKey = BuildConfig.TELEMETRY_APP_KEY,
            installId = installId,
            timestamp = timestamp,
            nonce = nonce,
            signature = PushMessageCrypto.signInAppRequest(
                deviceSecretBase64 = deviceSecret,
                appKey = BuildConfig.TELEMETRY_APP_KEY,
                installId = installId,
                timestamp = timestamp,
                nonce = nonce,
            ),
        )
    }

    private fun buildAckPayload(
        installId: String,
        deviceSecret: String,
        deliveryIds: List<String>,
    ): InAppPushAckPayload {
        val auth = buildAuthPayload(installId, deviceSecret)
        return InAppPushAckPayload(
            appKey = auth.appKey,
            installId = auth.installId,
            timestamp = auth.timestamp,
            nonce = auth.nonce,
            signature = auth.signature,
            deliveryIds = deliveryIds,
        )
    }

    private fun showMessage(deliveryId: String, payload: PartnerPushPayload): Boolean {
        val foregroundShown = foregroundMessageBus.publish(
            InAppForegroundMessage(
                deliveryId = deliveryId,
                title = payload.title,
                message = payload.message,
                actionUrl = payload.actionUrl,
                severity = payload.severity,
                pushType = payload.pushType,
                displayConfig = payload.displayConfig.toDomain(),
            ),
        )
        val systemShown = showSystemNotification(deliveryId, payload)
        if (!foregroundShown && !systemShown) {
            logs.add(LogLevel.WARN, "In-app notification received but no display channel is available")
        }
        return foregroundShown || systemShown
    }

    private fun showSystemNotification(deliveryId: String, payload: PartnerPushPayload): Boolean {
        if (!canPostNotifications()) return false
        ensureNotificationChannel()
        val intent = payload.actionUrl.toSafeActionUri()?.let { uri ->
            Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            deliveryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key_24)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(payload.notificationPriority())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(context).notify(deliveryId.hashCode(), notification)
        return true
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNetworkConnection(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Partner notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Secure messages from VPN service partners"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun PartnerPushPayload.notificationPriority(): Int = when (severity) {
        "critical", "warning" -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun PartnerPushDisplayConfig.toDomain(): InAppMessageDisplayConfig =
        InAppMessageDisplayConfig(
            theme = theme,
            icon = icon,
            headerLabel = headerLabel,
            showIcon = showIcon,
            showCloseButton = showCloseButton,
            showDismissButton = showDismissButton,
            showActionButton = showActionButton,
            showActionUrl = showActionUrl,
            dismissButtonLabel = dismissButtonLabel,
            actionButtonLabel = actionButtonLabel,
            titleSize = titleSize,
            messageSize = messageSize,
            messageAlign = messageAlign,
        )

    private fun InAppPushRegisterPayload.fingerprint(): String =
        sha256(
            listOf(
                providerId.orEmpty(),
                providerDomainHash.orEmpty(),
                appVersion,
                appVersionCode,
                packageName,
            ).joinToString("|"),
        )

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isEnabled(): Boolean =
        BuildConfig.IN_APP_PUSH_ENABLED && BuildConfig.TELEMETRY_ENABLED && BuildConfig.TELEMETRY_APP_KEY.isNotBlank()

    private companion object {
        const val WORK_NAME = "in_app_notifications"
        const val KIND = "vle_in_app_push"
        const val VERSION = "1"
        const val CHANNEL_ID = "partner_push"
        const val POLL_INTERVAL_MINUTES = 15L
        val FOREGROUND_POLL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30)
        val REGISTER_REFRESH_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}
