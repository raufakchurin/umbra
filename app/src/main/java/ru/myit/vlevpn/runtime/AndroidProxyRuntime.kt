package ru.myit.vlevpn.runtime

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.runtime.vpn.VleVpnService

@Singleton
class AndroidProxyRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateStore: RuntimeStateStore,
    private val logs: LogRepository,
    private val requestStore: RuntimeRequestStore,
) : ProxyRuntime {
    private val mutex = Mutex()
    private val _stats = MutableStateFlow(RuntimeStats())

    @Volatile
    private var restartAvailableAtMillis: Long = 0L

    override val state: StateFlow<RuntimeState> = stateStore.state
    override val stats: Flow<RuntimeStats> = _stats

    init {
        val filter = IntentFilter().apply {
            addAction(RuntimeServiceContract.ACTION_STATE)
            addAction(RuntimeServiceContract.ACTION_STATS)
            addAction(RuntimeServiceContract.ACTION_LOG)
        }
        ContextCompat.registerReceiver(
            context,
            RuntimeBroadcastReceiver(),
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        restoreStateFromRunningVpnServiceIfNeeded()
    }

    override suspend fun start(request: StartProxyRequest) {
        val accepted = mutex.withLock {
            restoreStateFromRunningVpnServiceIfNeeded()
            if (!state.value.canAcceptStartRequest()) {
                logs.add(LogLevel.WARN, "Runtime start ignored because another transition is active")
                return@withLock false
            }
            delayUntilRuntimeProcessCanRestart()
            stateStore.update(RuntimeState.StartingForeground)
            logs.add(LogLevel.INFO, "Starting foreground VPN service for ${request.server.name}")
            val requestPath = requestStore.write(request)
            val intent = Intent(context, VleVpnService::class.java)
                .setAction(VleVpnService.ACTION_START)
                .putExtra(RuntimeServiceContract.EXTRA_REQUEST_PATH, requestPath)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                requestStore.clear(requestPath)
                val message = error.message ?: "Could not start VPN service"
                logs.add(LogLevel.ERROR, "VPN service start failed: $message")
                stateStore.update(RuntimeState.Error(message))
            }
            true
        }
        if (accepted) {
            awaitServiceAcceptedStart()
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            restoreStateFromRunningVpnServiceIfNeeded()
            when (state.value) {
                RuntimeState.Idle -> {
                    _stats.value = RuntimeStats()
                    logs.add(LogLevel.DEBUG, "Runtime stop ignored because runtime is idle")
                    return
                }
                RuntimeState.PreparingVpnPermission,
                is RuntimeState.Error,
                -> {
                    _stats.value = RuntimeStats()
                    stateStore.update(RuntimeState.Idle)
                    logs.add(LogLevel.DEBUG, "Runtime stop cleared non-running state")
                    return
                }
                RuntimeState.Stopping -> {
                    logs.add(LogLevel.DEBUG, "Runtime stop ignored because runtime is already stopping")
                    return
                }
                RuntimeState.StartingForeground,
                RuntimeState.BuildingConfig,
                RuntimeState.EstablishingVpn,
                RuntimeState.StartingNativeCore,
                RuntimeState.VerifyingConnection,
                is RuntimeState.Running,
                -> Unit
            }
            stateStore.update(RuntimeState.Stopping)
            logs.add(LogLevel.INFO, "Stopping foreground VPN service")
            runCatching {
                context.startService(Intent(context, VleVpnService::class.java).setAction(VleVpnService.ACTION_STOP))
            }.onFailure { error ->
                val message = error.message ?: "Could not stop VPN service"
                logs.add(LogLevel.ERROR, "VPN service stop failed: $message")
                stateStore.update(RuntimeState.Error(message))
            }
        }
    }

    override suspend fun measureDelay(serverId: ServerId): DelayResult {
        val state = state.value
        return if (state is RuntimeState.Running) {
            DelayResult(serverId = serverId, delayMs = 42L)
        } else {
            DelayResult(serverId = serverId, delayMs = null, error = "Runtime is not running")
        }
    }

    fun markPreparingVpnPermission() {
        stateStore.update(RuntimeState.PreparingVpnPermission)
    }

    private fun restoreStateFromRunningVpnServiceIfNeeded() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val restoredState = restoreRuntimeStateAfterProcessRestart(
            currentState = state.value,
            vpnServiceRunning = isVpnServiceRunning(activityManager, VleVpnService::class.java.name),
            connectedAtMillis = System.currentTimeMillis(),
        )
        if (restoredState == state.value) return
        stateStore.update(restoredState)
        logs.add(LogLevel.INFO, "Restored runtime state from active VPN service process")
    }

    private suspend fun delayUntilRuntimeProcessCanRestart() {
        val waitMillis = restartAvailableAtMillis - System.currentTimeMillis()
        if (waitMillis > 0) {
            delay(waitMillis)
        }
    }

    private suspend fun awaitServiceAcceptedStart() {
        val transitioned = withTimeoutOrNull(FOREGROUND_START_TIMEOUT_MS) {
            state.first { runtimeState -> runtimeState !is RuntimeState.StartingForeground }
        } != null
        if (!transitioned && state.value is RuntimeState.StartingForeground) {
            val message = "VPN service did not enter foreground in time"
            logs.add(LogLevel.ERROR, message)
            stateStore.update(RuntimeState.Error(message))
        }
    }

    private inner class RuntimeBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                RuntimeServiceContract.ACTION_STATE -> handleState(intent)
                RuntimeServiceContract.ACTION_STATS -> {
                    _stats.value = RuntimeStats(
                        uplinkBytes = intent.getLongExtra(RuntimeServiceContract.EXTRA_UPLINK, 0L),
                        downlinkBytes = intent.getLongExtra(RuntimeServiceContract.EXTRA_DOWNLINK, 0L),
                    )
                }
                RuntimeServiceContract.ACTION_LOG -> {
                    val level = runCatching {
                        LogLevel.valueOf(intent.getStringExtra(RuntimeServiceContract.EXTRA_LOG_LEVEL).orEmpty())
                    }.getOrDefault(LogLevel.INFO)
                    logs.add(
                        level,
                        intent.getStringExtra(RuntimeServiceContract.EXTRA_LOG_MESSAGE).orEmpty(),
                    )
                }
            }
        }

        private fun handleState(intent: Intent) {
            val runtimeState = intent.toRuntimeState() ?: return
            when (runtimeState) {
                RuntimeState.Idle -> {
                    restartAvailableAtMillis = System.currentTimeMillis() + RUNTIME_PROCESS_RESTART_GUARD_MS
                    _stats.value = RuntimeStats()
                    stateStore.update(RuntimeState.Idle)
                }
                is RuntimeState.Error -> {
                    restartAvailableAtMillis = System.currentTimeMillis() + RUNTIME_PROCESS_RESTART_GUARD_MS
                    _stats.value = RuntimeStats()
                    stateStore.update(runtimeState)
                }
                else -> stateStore.update(runtimeState)
            }
        }
    }

    private companion object {
        const val RUNTIME_PROCESS_RESTART_GUARD_MS = 3_000L
        const val FOREGROUND_START_TIMEOUT_MS = 5_000L
    }
}

@Suppress("DEPRECATION")
internal fun isVpnServiceRunning(
    activityManager: ActivityManager?,
    serviceClassName: String,
): Boolean =
    activityManager
        ?.getRunningServices(Int.MAX_VALUE)
        ?.any { serviceInfo ->
            serviceInfo.service.className == serviceClassName && serviceInfo.foreground
        } == true

internal fun restoreRuntimeStateAfterProcessRestart(
    currentState: RuntimeState,
    vpnServiceRunning: Boolean,
    connectedAtMillis: Long,
): RuntimeState {
    if (!vpnServiceRunning) return currentState
    return when (currentState) {
        RuntimeState.Idle,
        is RuntimeState.Error,
        -> RuntimeState.Running(connectedAtMillis)
        RuntimeState.PreparingVpnPermission,
        RuntimeState.StartingForeground,
        RuntimeState.BuildingConfig,
        RuntimeState.EstablishingVpn,
        RuntimeState.StartingNativeCore,
        RuntimeState.VerifyingConnection,
        is RuntimeState.Running,
        RuntimeState.Stopping,
        -> currentState
    }
}

private fun RuntimeState.canAcceptStartRequest(): Boolean = when (this) {
    RuntimeState.Idle,
    RuntimeState.PreparingVpnPermission,
    is RuntimeState.Error,
    -> true
    RuntimeState.StartingForeground,
    RuntimeState.BuildingConfig,
    RuntimeState.EstablishingVpn,
    RuntimeState.StartingNativeCore,
    RuntimeState.VerifyingConnection,
    is RuntimeState.Running,
    RuntimeState.Stopping
    -> false
}
