package ru.myit.vlevpn.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.MainActivity
import ru.myit.vlevpn.R
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.settings.SettingsDataStore
import ru.myit.vlevpn.core.url.toSafeActionUri

@AndroidEntryPoint
class VleFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var logs: LogRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun onNewToken(token: String) {
        pushRegistrationManager.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.PUSH_ENABLED || !BuildConfig.TELEMETRY_ENABLED) return
        val data = message.data
        if (data["kind"] != KIND || data["version"] != VERSION) return

        serviceScope.launch {
            runCatching { handlePartnerPush(data) }
                .onFailure { error ->
                    logs.add(LogLevel.WARN, "Rejected push message: ${error.message.orEmpty()}")
                }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handlePartnerPush(data: Map<String, String>) {
        val deliveryId = data.requireValue("delivery_id")
        val campaignId = data.requireValue("campaign_id")
        val encryptedPayload = data.requireValue("payload")
        val signature = data.requireValue("signature")
        val deviceSecret = settingsDataStore.getOrCreatePushDeviceSecret()

        check(
            PushMessageCrypto.verifySignature(
                deviceSecretBase64 = deviceSecret,
                deliveryId = deliveryId,
                campaignId = campaignId,
                encryptedPayload = encryptedPayload,
                signatureHex = signature,
            ),
        ) {
            "Invalid push signature"
        }

        val payloadJson = PushMessageCrypto.decryptPayload(deviceSecret, encryptedPayload)
        val payload = json.decodeFromString<PartnerPushPayload>(payloadJson)
        showNotification(deliveryId, payload)
    }

    private fun showNotification(deliveryId: String, payload: PartnerPushPayload) {
        if (!canPostNotifications()) {
            logs.add(LogLevel.WARN, "Push received but notification permission is not granted")
            return
        }

        ensureNotificationChannel()
        val intent = payload.actionUrl.toSafeActionUri()?.let { uri ->
            Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } ?: Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            deliveryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key_24)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(payload.notificationPriority())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(this).notify(deliveryId.hashCode(), notification)
        logs.add(LogLevel.INFO, "Push notification shown")
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
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
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun PartnerPushPayload.notificationPriority(): Int = when (severity) {
        "critical", "warning" -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun Map<String, String>.requireValue(key: String): String =
        requireNotNull(this[key]?.takeIf { it.isNotBlank() }) { "Missing push field: $key" }

    private companion object {
        const val KIND = "vle_partner_push"
        const val VERSION = "1"
        const val CHANNEL_ID = "partner_push"
    }
}
