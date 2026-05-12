package ru.myit.vlevpn

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.myit.vlevpn.data.provider.ProviderTelemetryManager
import ru.myit.vlevpn.data.push.InAppNotificationManager
import ru.myit.vlevpn.data.push.PushRegistrationManager
import ru.myit.vlevpn.data.telemetry.MobileTelemetryManager
import ru.myit.vlevpn.subscription.SubscriptionAutoUpdateManager

@HiltAndroidApp
class VleVpnApp : Application() {
    @Inject lateinit var subscriptionAutoUpdateManager: SubscriptionAutoUpdateManager
    @Inject lateinit var providerTelemetryManager: ProviderTelemetryManager
    @Inject lateinit var mobileTelemetryManager: MobileTelemetryManager
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager
    @Inject lateinit var inAppNotificationManager: InAppNotificationManager
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            subscriptionAutoUpdateManager.schedulePeriodicUpdates()
            providerTelemetryManager.schedulePeriodicChecks()
            mobileTelemetryManager.schedulePeriodicReports()
            inAppNotificationManager.schedulePeriodicChecks()
            mobileTelemetryManager.startRuntimeAnalyticsObserver(appScope)
            appScope.launch {
                runCatching { mobileTelemetryManager.sendColdLaunchReport() }
                runCatching { inAppNotificationManager.registerAndPollNow() }
            }
            pushRegistrationManager.registerCurrentToken()
        }
    }

    private fun isMainProcess(): Boolean =
        packageName == currentProcessName()

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = android.os.Process.myPid()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager
            ?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
    }
}
