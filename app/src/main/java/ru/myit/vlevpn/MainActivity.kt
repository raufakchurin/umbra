package ru.myit.vlevpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.myit.vlevpn.data.branding.PartnerBrandingManager
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.data.provider.ProviderTelemetryManager
import ru.myit.vlevpn.data.push.InAppNotificationManager
import ru.myit.vlevpn.data.push.PushRegistrationManager
import ru.myit.vlevpn.data.telemetry.MobileTelemetryManager
import ru.myit.vlevpn.domain.repository.ServerImportRepository
import ru.myit.vlevpn.quick.QuickCommand
import ru.myit.vlevpn.quick.QuickCommandHandler
import ru.myit.vlevpn.quick.QuickCommandParser
import ru.myit.vlevpn.subscription.SubscriptionAutoUpdateManager
import ru.myit.vlevpn.ui.navigation.VleVpnApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var importRepository: ServerImportRepository
    @Inject lateinit var quickCommandHandler: QuickCommandHandler
    @Inject lateinit var subscriptionAutoUpdateManager: SubscriptionAutoUpdateManager
    @Inject lateinit var providerTelemetryManager: ProviderTelemetryManager
    @Inject lateinit var mobileTelemetryManager: MobileTelemetryManager
    @Inject lateinit var partnerBrandingManager: PartnerBrandingManager
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager
    @Inject lateinit var inAppNotificationManager: InAppNotificationManager
    @Inject lateinit var logs: LogRepository
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var pendingVpnCommand: QuickCommand? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val command = pendingVpnCommand
            pendingVpnCommand = null
            if (result.resultCode == RESULT_OK && command != null) {
                lifecycleScope.launch { quickCommandHandler.execute(command) }
            } else {
                quickCommandHandler.onPermissionDenied()
            }
        }
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                pushRegistrationManager.registerCurrentToken()
                inAppNotificationManager.registerAndPollAsync()
            } else {
                logs.add(LogLevel.WARN, "Push notification permission denied")
            }
        }
        handleImportIntent(intent)
        requestNotificationPermissionIfNeeded()
        lifecycleScope.launch {
            subscriptionAutoUpdateManager.refreshOnColdAppLaunch()
            runCatching { partnerBrandingManager.refreshNow() }
            runCatching { providerTelemetryManager.sendDueProviderCheck() }
            runCatching { mobileTelemetryManager.sendDueReport() }
        }
        setContent {
            VleVpnApp()
        }
    }

    override fun onStart() {
        super.onStart()
        inAppNotificationManager.startForegroundPolling()
    }

    override fun onStop() {
        inAppNotificationManager.stopForegroundPolling()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        if (intent == null) return
        val input = when (intent.action) {
            Intent.ACTION_SEND -> intent.readSharedText()?.trim()
            Intent.ACTION_VIEW -> {
                val raw = intent.dataString?.trim()
                val command = QuickCommandParser.parse(raw)
                if (command != null) {
                    clearHandledInput(intent)
                    executeQuickCommand(command)
                    return
                }
                raw
            }
            else -> null
        }

        if (input.isNullOrBlank()) return
        clearHandledInput(intent)

        lifecycleScope.launch {
            runCatching {
                importRepository.importFromInput(input)
            }.onFailure { error ->
                logs.add(LogLevel.ERROR, "Import failed: ${error.message.orEmpty()}")
            }
        }
    }

    private fun executeQuickCommand(command: QuickCommand) {
        if (command.requiresVpnPermissionCheck()) {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                pendingVpnCommand = command
                quickCommandHandler.markPreparingPermission()
                vpnPermissionLauncher.launch(prepareIntent)
                return
            }
        }

        lifecycleScope.launch { quickCommandHandler.execute(command) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        val notificationFeaturesEnabled = BuildConfig.PUSH_ENABLED || BuildConfig.IN_APP_PUSH_ENABLED
        if (!notificationFeaturesEnabled || !BuildConfig.TELEMETRY_ENABLED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permissionState = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun QuickCommand.requiresVpnPermissionCheck(): Boolean =
        this is QuickCommand.Connect || (this is QuickCommand.Toggle && !quickCommandHandler.isRunning)

    private fun clearHandledInput(intent: Intent) {
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.data = null
    }

    private fun Intent.readSharedText(): String? =
        getStringExtra(Intent.EXTRA_TEXT)
            ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
            ?: extras?.keySet()?.firstNotNullOfOrNull { key -> extras?.getString(key) }
}
