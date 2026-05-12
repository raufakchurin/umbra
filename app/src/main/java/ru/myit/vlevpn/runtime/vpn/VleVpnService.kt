package ru.myit.vlevpn.runtime.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.myit.vlevpn.MainActivity
import ru.myit.vlevpn.R
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.runtime.RuntimeRequestStore
import ru.myit.vlevpn.runtime.RuntimeServiceContract
import ru.myit.vlevpn.runtime.RuntimeState
import ru.myit.vlevpn.runtime.VpnServiceRuntime

@AndroidEntryPoint
class VleVpnService : VpnService() {
    @Inject lateinit var runtimeProvider: Lazy<VpnServiceRuntime>
    @Inject lateinit var requestStoreProvider: Provider<RuntimeRequestStore>
    @Inject lateinit var logsProvider: Provider<LogRepository>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var exitAfterDestroy = false
    @Volatile
    private var exitScheduled = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    runCatching { runtimeProvider.get().stop(this@VleVpnService) }
                        .onFailure(::handleRuntimeBootstrapFailure)
                }
            }
            else -> {
                runCatching { startAsForeground() }
                    .onFailure {
                        handleRuntimeBootstrapFailure(it)
                        return Service.START_NOT_STICKY
                    }
                val requestPath = intent?.getStringExtra(RuntimeServiceContract.EXTRA_REQUEST_PATH)
                serviceScope.launch {
                    val request = try {
                        val path = requireNotNull(requestPath) { "Missing runtime request" }
                        val requestStore = requestStoreProvider.get()
                        requestStore.read(path).also { requestStore.clear(path) }
                    } catch (error: Throwable) {
                        if (requestPath != null) {
                            runCatching { requestStoreProvider.get().clear(requestPath) }
                        }
                        handleRuntimeBootstrapFailure(error)
                        return@launch
                    }
                    runCatching { runtimeProvider.get().start(this@VleVpnService, request) }
                        .onFailure(::handleRuntimeBootstrapFailure)
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        if (exitAfterDestroy) {
            scheduleRuntimeProcessExit()
        }
    }

    fun finishRuntimeStop(recycleRuntimeProcess: Boolean = true) {
        exitAfterDestroy = recycleRuntimeProcess
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (recycleRuntimeProcess) {
            scheduleRuntimeProcessExit()
        }
        stopSelf()
    }

    private fun handleRuntimeBootstrapFailure(error: Throwable) {
        val message = error.userSafeMessage()
        runCatching {
            logsProvider.get().add(LogLevel.ERROR, "VPN service runtime failed: $message")
        }
        runCatching {
            sendBroadcast(RuntimeServiceContract.logIntent(this, LogLevel.ERROR.name, "VPN service runtime failed: $message"))
            sendBroadcast(RuntimeServiceContract.stateIntent(this, RuntimeState.Error(message)))
        }
        finishRuntimeStop(recycleRuntimeProcess = true)
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VleVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key_24)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("VPN runtime is active")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN runtime",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun scheduleRuntimeProcessExit() {
        if (exitScheduled) return
        exitScheduled = true
        mainHandler.postDelayed({ exitProcess(0) }, RUNTIME_PROCESS_EXIT_DELAY_MS)
    }

    companion object {
        const val ACTION_START = "ru.myit.vlevpn.action.START"
        const val ACTION_STOP = "ru.myit.vlevpn.action.STOP"
        private const val CHANNEL_ID = "vpn_runtime"
        private const val NOTIFICATION_ID = 2601
        private const val RUNTIME_PROCESS_EXIT_DELAY_MS = 1_000L
    }
}

private fun Throwable.userSafeMessage(): String =
    message
        ?.takeIf { it.isNotBlank() }
        ?: this::class.java.simpleName.ifBlank { "VPN service startup failed" }
