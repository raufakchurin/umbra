package ru.myit.vlevpn.subscription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ServerImportRepository
import ru.myit.vlevpn.domain.repository.ServerRepository
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

@Singleton
class SubscriptionAutoUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val importRepository: ServerImportRepository,
    private val logs: LogRepository,
) {
    private val mutex = Mutex()

    @Volatile
    private var launchUpdateStarted = false

    fun schedulePeriodicUpdates() {
        val request = PeriodicWorkRequestBuilder<SubscriptionAutoUpdateWorker>(
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

    suspend fun refreshOnColdAppLaunch() {
        if (launchUpdateStarted) return
        launchUpdateStarted = true
        refreshSubscriptions(
            reason = "app launch",
            selector = { it.autoUpdateOnLaunchEnabled },
        )
    }

    suspend fun refreshDueSubscriptions() {
        val now = System.currentTimeMillis()
        refreshSubscriptions(
            reason = "schedule",
            selector = { target ->
                target.lastImportedAtMillis <= 0L ||
                    now - target.lastImportedAtMillis >= TimeUnit.HOURS.toMillis(target.effectiveUpdateIntervalHours.toLong())
            },
        )
    }

    private suspend fun refreshSubscriptions(
        reason: String,
        selector: (SubscriptionUpdateTarget) -> Boolean,
    ) {
        mutex.withLock {
            val targets = serverRepository.servers.first()
                .subscriptionUpdateTargets()
                .filter(selector)

            if (targets.isEmpty()) {
                logs.add(LogLevel.DEBUG, "Subscription auto-update skipped for $reason: no matching subscriptions")
                return
            }

            logs.add(LogLevel.INFO, "Subscription auto-update started for $reason: ${targets.size} subscription(s)")
            targets.forEach { target ->
                runCatching {
                    importRepository.importFromInput(target.sourceUrl)
                }.onSuccess { summary ->
                    logs.add(LogLevel.INFO, "Auto-updated '${target.title}': ${summary.message}")
                }.onFailure { error ->
                    logs.add(LogLevel.WARN, "Auto-update failed for '${target.title}': ${error.message.orEmpty()}")
                }
            }
        }
    }

    private fun List<ServerProfile>.subscriptionUpdateTargets(): List<SubscriptionUpdateTarget> =
        filter { it.subscriptionId.isNotBlank() && it.subscriptionSourceUrl.isNotBlank() }
            .groupBy { it.subscriptionId }
            .mapNotNull { (subscriptionId, profiles) ->
                val sourceUrl = profiles.firstNotNullOfOrNull { profile ->
                    profile.subscriptionSourceUrl.takeIf { it.isNotBlank() }
                }
                    ?: return@mapNotNull null
                SubscriptionUpdateTarget(
                    subscriptionId = subscriptionId,
                    title = profiles.firstOrNull()?.subscriptionName?.ifBlank { null } ?: subscriptionId,
                    sourceUrl = sourceUrl,
                    effectiveUpdateIntervalHours = profiles.firstNotNullOfOrNull {
                        it.subscriptionUpdateIntervalHours.takeIf { value -> value > 0 }
                    } ?: DEFAULT_UPDATE_INTERVAL_HOURS,
                    lastImportedAtMillis = profiles.maxOfOrNull { it.subscriptionImportedAtMillis } ?: 0L,
                    autoUpdateOnLaunchEnabled = profiles.any { it.subscriptionAutoUpdateOnLaunchEnabled },
                )
            }

    private data class SubscriptionUpdateTarget(
        val subscriptionId: String,
        val title: String,
        val sourceUrl: String,
        val effectiveUpdateIntervalHours: Int,
        val lastImportedAtMillis: Long,
        val autoUpdateOnLaunchEnabled: Boolean,
    )

    companion object {
        const val WORK_NAME = "subscription_auto_update"
        const val DEFAULT_UPDATE_INTERVAL_HOURS = 12
        const val PERIODIC_WORK_INTERVAL_HOURS = 1L
    }
}
