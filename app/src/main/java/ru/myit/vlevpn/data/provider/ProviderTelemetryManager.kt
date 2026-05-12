package ru.myit.vlevpn.data.provider

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.data.subscription.SubscriptionDeviceHeaders
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

@Singleton
class ProviderTelemetryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val deviceHeaders: SubscriptionDeviceHeaders,
    private val client: ProviderTelemetryClient,
    private val logs: LogRepository,
) {
    private val mutex = Mutex()

    fun schedulePeriodicChecks() {
        val request = PeriodicWorkRequestBuilder<ProviderTelemetryWorker>(
            PERIODIC_WORK_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun sendDueProviderCheck() {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        mutex.withLock {
            val settings = settingsRepository.settings.first()
            val providerKeys = buildProviderKeys(settings)
            if (providerKeys.isEmpty()) return

            val now = System.currentTimeMillis()
            if (now - settings.providerTelemetryLastSentAtMillis < MIN_SEND_INTERVAL_MILLIS) {
                return
            }

            val baseCheck = ProviderTelemetryCheckBase(
                hwid = deviceHeaders.stableHwid(),
                osName = "Android",
                osVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() },
                appVersion = BuildConfig.VERSION_NAME,
                deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android device" },
            )
            providerKeys.forEach { providerKey ->
                client.send(
                    ProviderTelemetryCheck(
                        providerId = providerKey.providerId,
                        domainHash = providerKey.domainHash,
                        hwid = baseCheck.hwid,
                        osName = baseCheck.osName,
                        osVersion = baseCheck.osVersion,
                        appVersion = baseCheck.appVersion,
                        deviceModel = baseCheck.deviceModel,
                    ),
                )
            }
            settingsRepository.markProviderTelemetrySent(now)
            logs.add(LogLevel.INFO, "PartnerID telemetry sent: ${providerKeys.size} provider(s)")
        }
    }

    private suspend fun buildProviderKeys(settings: AppSettings): List<ProviderKey> =
        buildList {
            addProvider(settings.subscriptionProviderId, settings.subscriptionProviderDomainHash)
            serverRepository.servers.first().forEach { server ->
                addProvider(server.subscriptionProviderId, server.subscriptionProviderDomainHash)
            }
        }
            .distinct()
            .take(MAX_PROVIDER_CHECKS_PER_RUN)

    private fun MutableList<ProviderKey>.addProvider(providerId: String, domainHash: String) {
        val normalizedProviderId = providerId.trim()
        val normalizedDomainHash = domainHash.trim()
        if (normalizedProviderId.isBlank() || normalizedDomainHash.isBlank()) return
        add(ProviderKey(normalizedProviderId, normalizedDomainHash))
    }

    companion object {
        const val WORK_NAME = "provider_id_telemetry"
        const val PERIODIC_WORK_INTERVAL_HOURS = 12L
        private val MIN_SEND_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
        private const val MAX_PROVIDER_CHECKS_PER_RUN = 16
    }
}

private data class ProviderKey(
    val providerId: String,
    val domainHash: String,
)

private data class ProviderTelemetryCheckBase(
    val hwid: String,
    val osName: String,
    val osVersion: String,
    val appVersion: String,
    val deviceModel: String,
)
