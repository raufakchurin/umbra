package ru.myit.vlevpn.data.push

import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.settings.SettingsDataStore

@Singleton
class PushRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val client: PushRegistrationClient,
    private val logs: LogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentToken() {
        if (!isPushRegistrationEnabled()) return

        runCatching { FirebaseMessaging.getInstance() }
            .onSuccess { messaging ->
                messaging.token
                    .addOnSuccessListener(::registerToken)
                    .addOnFailureListener { error ->
                        logs.add(LogLevel.WARN, "Push token unavailable: ${error.message.orEmpty()}")
                    }
            }
            .onFailure { error ->
                logs.add(LogLevel.WARN, "Firebase Messaging is not configured: ${error.message.orEmpty()}")
            }
    }

    fun registerToken(token: String) {
        if (!isPushRegistrationEnabled() || token.isBlank()) return

        scope.launch {
            runCatching {
                val payload = buildPayload(token)
                val fingerprint = payload.fingerprint()
                val now = System.currentTimeMillis()
                val alreadySent = settingsDataStore.getPushRegistrationFingerprint() == fingerprint &&
                    now - settingsDataStore.getPushRegistrationLastSentAtMillis() < REGISTER_REFRESH_INTERVAL_MILLIS
                if (alreadySent) {
                    logs.add(LogLevel.DEBUG, "Push token registration skipped: unchanged")
                    return@runCatching false
                }
                client.register(payload)
                settingsDataStore.markPushRegistrationSent(fingerprint, now)
                true
            }.onSuccess { registered ->
                if (registered) {
                    logs.add(LogLevel.INFO, "Push token registered")
                }
            }.onFailure { error ->
                logs.add(LogLevel.WARN, "Push registration failed: ${error.message.orEmpty()}")
            }
        }
    }

    private suspend fun buildPayload(token: String): PushRegisterPayload {
        val settings = settingsDataStore.settings.first()
        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }

        return PushRegisterPayload(
            appKey = BuildConfig.TELEMETRY_APP_KEY,
            installId = settingsDataStore.getOrCreateSubscriptionInstallId(),
            token = token,
            deviceSecret = settingsDataStore.getOrCreatePushDeviceSecret(),
            packageName = context.packageName,
            providerId = settings.subscriptionProviderId.takeIf { it.isNotBlank() },
            providerDomainHash = settings.subscriptionProviderDomainHash.takeIf { it.isNotBlank() },
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toString(),
            sdkInt = Build.VERSION.SDK_INT,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
        ).also {
            logs.add(LogLevel.INFO, "Push registration prepared for $deviceName")
        }
    }

    private fun PushRegisterPayload.fingerprint(): String =
        sha256(
            listOf(
                token,
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

    private fun isPushRegistrationEnabled(): Boolean =
        BuildConfig.PUSH_ENABLED && BuildConfig.TELEMETRY_ENABLED && BuildConfig.TELEMETRY_APP_KEY.isNotBlank()

    private companion object {
        val REGISTER_REFRESH_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}
