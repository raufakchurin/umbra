package ru.myit.vlevpn

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.myit.vlevpn.data.branding.PartnerBrandingManager
import ru.myit.vlevpn.data.provider.ProviderTelemetryManager
import ru.myit.vlevpn.data.push.InAppNotificationManager
import ru.myit.vlevpn.data.push.PushRegistrationManager
import ru.myit.vlevpn.data.telemetry.MobileTelemetryManager
import ru.myit.vlevpn.subscription.SubscriptionAutoUpdateManager

@HiltAndroidApp
class VleVpnApp : Application() {
    @Inject lateinit var subscriptionAutoUpdateManager: Provider<SubscriptionAutoUpdateManager>
    @Inject lateinit var providerTelemetryManager: Provider<ProviderTelemetryManager>
    @Inject lateinit var mobileTelemetryManager: Provider<MobileTelemetryManager>
    @Inject lateinit var partnerBrandingManager: Provider<PartnerBrandingManager>
    @Inject lateinit var pushRegistrationManager: Provider<PushRegistrationManager>
    @Inject lateinit var inAppNotificationManager: Provider<InAppNotificationManager>
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            val subscriptionAutoUpdateManager = subscriptionAutoUpdateManager.get()
            val providerTelemetryManager = providerTelemetryManager.get()
            val mobileTelemetryManager = mobileTelemetryManager.get()
            val partnerBrandingManager = partnerBrandingManager.get()
            val pushRegistrationManager = pushRegistrationManager.get()
            val inAppNotificationManager = inAppNotificationManager.get()

            subscriptionAutoUpdateManager.schedulePeriodicUpdates()
            providerTelemetryManager.schedulePeriodicChecks()
            mobileTelemetryManager.schedulePeriodicReports()
            inAppNotificationManager.schedulePeriodicChecks()
            mobileTelemetryManager.startRuntimeAnalyticsObserver(appScope)
            appScope.launch {
                runCatching { mobileTelemetryManager.sendColdLaunchReport() }
                runCatching { partnerBrandingManager.refreshNow() }
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
