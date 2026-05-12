package ru.myit.vlevpn.runtime

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import java.net.Socket
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeCallbacks
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeEngine
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeRequest
import ru.myit.vlevpn.runtime.contract.RuntimeKeys
import ru.myit.vlevpn.runtime.plugin.RuntimeProtocolRegistry
import ru.myit.vlevpn.runtime.vpn.AndroidVpnController
import ru.myit.vlevpn.runtime.vpn.VleVpnService

@Singleton
class VpnServiceRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xrayRuntime: XrayRuntime,
    private val configFactory: XrayConfigFactory,
    private val healthChecker: RuntimeHealthChecker,
    private val logs: LogRepository,
    private val protectBridge: VpnProtectBridge,
    private val runtimeProtocolRegistry: RuntimeProtocolRegistry,
    private val olcRtcRuntimeEngines: Set<@JvmSuppressWildcards OlcRtcRuntimeEngine>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)

    private var activeController: AndroidVpnController? = null
    private var statsJob: Job? = null
    private var baselineUidTxBytes: Long = 0L
    private var baselineUidRxBytes: Long = 0L
    private var activeSessionId: String? = null
    private var activeSessionStartedAtMillis: Long = 0L
    private var activeRuntimeKey: String? = null

    suspend fun start(service: VleVpnService, request: StartProxyRequest) {
        mutex.withLock {
            if (state.value !is RuntimeState.Idle && state.value !is RuntimeState.Error) {
                log(LogLevel.WARN, "Service runtime start ignored in state ${stateName(state.value)}")
                return
            }

            val sessionId = newSessionId()
            activeSessionId = sessionId
            activeSessionStartedAtMillis = System.currentTimeMillis()

            runCatching {
                val runtimePlugin = runtimeProtocolRegistry.requireActiveByServerProtocol(request.server.protocol.name)
                activeRuntimeKey = runtimePlugin.runtimeKey
                log(
                    LogLevel.INFO,
                    "Runtime session $sessionId starting with ${runtimePlugin.displayName} for ${request.server.protocol.name} profile '${request.server.name}'",
                )

                when (runtimePlugin.runtimeKey) {
                    RuntimeKeys.XRAY -> startXrayRuntime(service, request, sessionId)
                    RuntimeKeys.OLCRTC -> startOlcRtcRuntime(service, request, sessionId)
                    else -> error("Runtime ${runtimePlugin.displayName} is not wired into the VPN service yet")
                }

                updateState(RuntimeState.Running(System.currentTimeMillis()))
                log(LogLevel.INFO, "Runtime session $sessionId is running in ${elapsedSince(activeSessionStartedAtMillis)} ms")
                startStatsPolling()
            }.onFailure { error ->
                log(LogLevel.ERROR, "Runtime session $sessionId start failed: ${error.message.orEmpty()}")
                stopLocked(setIdle = false, reason = "start failure")
                updateState(RuntimeState.Error(error.message ?: "Runtime start failed"))
                service.finishRuntimeStop(recycleRuntimeProcess = true)
            }
        }
    }

    suspend fun stop(service: VleVpnService) {
        mutex.withLock {
            stopLocked(setIdle = true, reason = "stop request")
            service.finishRuntimeStop(recycleRuntimeProcess = true)
        }
    }

    private suspend fun startXrayRuntime(
        service: VleVpnService,
        request: StartProxyRequest,
        sessionId: String,
    ) {
        val configJson = runStep("Build config", sessionId, CONFIG_BUILD_TIMEOUT_MS) {
            updateState(RuntimeState.BuildingConfig)
            configFactory.build(request.server, request.settings, includeMetrics = true)
        }
        if (request.settings.debugConfigPreviewEnabled) {
            logs.setGeneratedConfigPreview(configJson)
        }
        log(LogLevel.INFO, "Generated Xray-compatible config with metrics enabled")

        val session = runStep("Establish VPN", sessionId, ESTABLISH_VPN_TIMEOUT_MS) {
            updateState(RuntimeState.EstablishingVpn)
            val controller = AndroidVpnController(service, context.packageName, logs)
            activeController = controller
            protectBridge.setProtector(controller::protect)
            protectBridge.setSocketProtector(controller::protect)
            controller.establish(request.vpnProfile)
        }
        log(LogLevel.INFO, "VPN TUN interface established")

        runStep("Start Xray core", sessionId, XRAY_START_TIMEOUT_MS) {
            updateState(RuntimeState.StartingNativeCore)
            xrayRuntime.init(
                XrayEnvironment(
                    filesDirPath = context.filesDir.absolutePath,
                    cacheDirPath = context.cacheDir.absolutePath,
                    nativeLibraryDir = context.applicationInfo.nativeLibraryDir.orEmpty(),
                ),
            )
            xrayRuntime.start(configJson, session.tunFd)
        }
        if (request.server.isCustomJson) {
            log(LogLevel.WARN, "Runtime health check skipped for custom JSON profile")
        } else {
            val health = runStep(
                name = "Verify proxy health",
                sessionId = sessionId,
                timeoutMillis = HEALTH_CHECK_STEP_TIMEOUT_MS,
                timeoutMessage = "Сервер недоступен",
            ) {
                updateState(RuntimeState.VerifyingConnection)
                healthChecker.check(request.settings)
            }
            if (!health.healthy) {
                log(
                    LogLevel.WARN,
                    "Runtime health check failed: ${health.error.orEmpty().ifBlank { "connection check failed" }}",
                )
                error("Сервер недоступен")
            }
            log(LogLevel.INFO, "Runtime health check passed in ${health.delayMs ?: 0L} ms")
        }
    }

    private suspend fun startOlcRtcRuntime(
        service: VleVpnService,
        request: StartProxyRequest,
        sessionId: String,
    ) {
        val engine = requireOlcRtcRuntimeEngine()
        val olcRtcRequest = runStep("Build olcRTC request", sessionId, CONFIG_BUILD_TIMEOUT_MS) {
            updateState(RuntimeState.BuildingConfig)
            request.server.toOlcRtcRuntimeRequest(
                ownPackageName = context.packageName,
                request = request,
            )
        }

        runStep("Start olcRTC tunnel", sessionId, OLCRTC_START_TIMEOUT_MS) {
            updateState(RuntimeState.StartingNativeCore)
            protectBridge.setProtector { fd -> service.protect(fd) }
            protectBridge.setSocketProtector { socket: Socket -> service.protect(socket) }
            engine.start(
                service = service,
                request = olcRtcRequest,
                callbacks = object : OlcRtcRuntimeCallbacks {
                    override fun protect(fd: Int): Boolean = service.protect(fd)
                    override fun log(message: String) {
                        this@VpnServiceRuntime.log(LogLevel.DEBUG, message)
                    }
                },
            )
        }
        log(LogLevel.INFO, "olcRTC tunnel established")
    }

    private suspend fun stopLocked(setIdle: Boolean, reason: String) {
        if (isAlreadyStopped()) {
            protectBridge.setProtector(null)
            protectBridge.setSocketProtector(null)
            sendStats(RuntimeStats())
            if (setIdle) {
                updateState(RuntimeState.Idle)
            }
            log(LogLevel.DEBUG, "Service runtime stop ignored because it is already idle")
            clearSession()
            return
        }

        val sessionId = activeSessionId ?: "unknown"
        log(LogLevel.INFO, "Stopping runtime session $sessionId: $reason")
        if (state.value !is RuntimeState.Idle) {
            updateState(RuntimeState.Stopping)
        }
        stopStatsPolling()
        when (activeRuntimeKey) {
            RuntimeKeys.XRAY -> runStopAction("Xray runtime stop", XRAY_STOP_TIMEOUT_MS) {
                xrayRuntime.stop()
            }
            RuntimeKeys.OLCRTC -> runStopAction("olcRTC runtime stop", OLCRTC_STOP_TIMEOUT_MS) {
                requireOlcRtcRuntimeEngine().stop()
            }
            else -> {
                runStopAction("Xray runtime stop", XRAY_STOP_TIMEOUT_MS) {
                    xrayRuntime.stop()
                }
                olcRtcRuntimeEngines.firstOrNull()?.let { engine ->
                    runStopAction("olcRTC runtime stop", OLCRTC_STOP_TIMEOUT_MS) {
                        engine.stop()
                    }
                }
            }
        }
        runStopAction("VPN close", VPN_CLOSE_TIMEOUT_MS) {
            activeController?.close()
        }
        activeController = null
        protectBridge.setProtector(null)
        protectBridge.setSocketProtector(null)
        sendStats(RuntimeStats())
        log(LogLevel.INFO, "Runtime session $sessionId stopped in ${elapsedSince(activeSessionStartedAtMillis)} ms")
        if (setIdle) {
            updateState(RuntimeState.Idle)
        }
        clearSession()
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        baselineUidTxBytes = currentUidTxBytes()
        baselineUidRxBytes = currentUidRxBytes()
        statsJob = scope.launch {
            var hasLoggedStatsFailure = false
            while (isActive) {
                runCatching {
                    val runtimeStats = queryActiveRuntimeStats()
                    val fallbackUplink = (currentUidTxBytes() - baselineUidTxBytes).coerceAtLeast(0L)
                    val fallbackDownlink = (currentUidRxBytes() - baselineUidRxBytes).coerceAtLeast(0L)
                    sendStats(
                        RuntimeStats(
                            uplinkBytes = runtimeStats.uplinkBytes.takeIf { it > 0L } ?: fallbackUplink,
                            downlinkBytes = runtimeStats.downlinkBytes.takeIf { it > 0L } ?: fallbackDownlink,
                        ),
                    )
                    hasLoggedStatsFailure = false
                }.onFailure { error ->
                    if (!hasLoggedStatsFailure) {
                        log(LogLevel.WARN, "Runtime stats polling failed: ${error.message.orEmpty()}")
                        hasLoggedStatsFailure = true
                    }
                }
                delay(2_000)
            }
        }
    }

    private suspend fun queryActiveRuntimeStats(): RuntimeStats =
        when (activeRuntimeKey) {
            RuntimeKeys.XRAY -> {
                val stats = xrayRuntime.queryStats()
                RuntimeStats(uplinkBytes = stats.uplinkBytes, downlinkBytes = stats.downlinkBytes)
            }
            RuntimeKeys.OLCRTC -> {
                val stats = requireOlcRtcRuntimeEngine().queryStats()
                RuntimeStats(uplinkBytes = stats.uplinkBytes, downlinkBytes = stats.downlinkBytes)
            }
            else -> RuntimeStats()
        }

    private suspend fun stopStatsPolling() {
        val job = statsJob ?: return
        statsJob = null
        job.cancel()
        val stopped = withTimeoutOrNull(STATS_STOP_TIMEOUT_MS) {
            job.join()
            true
        } ?: false
        if (!stopped) {
            log(LogLevel.WARN, "Runtime stats polling did not stop within ${STATS_STOP_TIMEOUT_MS} ms")
        }
    }

    private suspend fun <T> runStep(
        name: String,
        sessionId: String,
        timeoutMillis: Long,
        timeoutMessage: String? = null,
        block: suspend () -> T,
    ): T {
        val startedAt = System.currentTimeMillis()
        return runCatching {
            try {
                withTimeout(timeoutMillis) {
                    block()
                }
            } catch (error: TimeoutCancellationException) {
                throw IllegalStateException(timeoutMessage ?: "$name timed out after ${timeoutMillis} ms", error)
            }
        }
            .onSuccess { log(LogLevel.DEBUG, "$name completed for session $sessionId in ${elapsedSince(startedAt)} ms") }
            .onFailure { log(LogLevel.WARN, "$name failed for session $sessionId after ${elapsedSince(startedAt)} ms") }
            .getOrThrow()
    }

    private suspend fun runStopAction(name: String, timeoutMillis: Long, block: suspend () -> Unit) {
        val completed = withTimeoutOrNull(timeoutMillis) {
            runCatching { block() }
                .onFailure { log(LogLevel.WARN, "$name failed: ${it.message.orEmpty()}") }
            true
        } ?: false
        if (!completed) {
            log(LogLevel.WARN, "$name timed out after ${timeoutMillis} ms")
        }
    }

    private fun isAlreadyStopped(): Boolean =
        state.value is RuntimeState.Idle && activeController == null && statsJob == null

    private fun clearSession() {
        activeSessionId = null
        activeSessionStartedAtMillis = 0L
        activeRuntimeKey = null
    }

    private fun requireOlcRtcRuntimeEngine(): OlcRtcRuntimeEngine =
        olcRtcRuntimeEngines.firstOrNull()
            ?: error("olcRTC runtime module is not included in this build")

    private fun newSessionId(): String = System.currentTimeMillis().toString(36)

    private fun elapsedSince(startedAtMillis: Long): Long =
        if (startedAtMillis == 0L) 0L else (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)

    private fun stateName(value: RuntimeState): String = when (value) {
        RuntimeState.Idle -> "Idle"
        RuntimeState.PreparingVpnPermission -> "PreparingVpnPermission"
        RuntimeState.StartingForeground -> "StartingForeground"
        RuntimeState.BuildingConfig -> "BuildingConfig"
        RuntimeState.EstablishingVpn -> "EstablishingVpn"
        RuntimeState.StartingNativeCore -> "StartingNativeCore"
        RuntimeState.VerifyingConnection -> "VerifyingConnection"
        is RuntimeState.Running -> "Running"
        RuntimeState.Stopping -> "Stopping"
        is RuntimeState.Error -> "Error"
    }

    private fun updateState(newState: RuntimeState) {
        state.value = newState
        context.sendBroadcast(RuntimeServiceContract.stateIntent(context, newState))
    }

    private fun sendStats(stats: RuntimeStats) {
        context.sendBroadcast(RuntimeServiceContract.statsIntent(context, stats))
    }

    private fun log(level: LogLevel, message: String) {
        logs.add(level, message)
        context.sendBroadcast(RuntimeServiceContract.logIntent(context, level.name, message))
    }

    private fun currentUidTxBytes(): Long =
        TrafficStats.getUidTxBytes(Process.myUid()).takeUnless { it == TrafficStats.UNSUPPORTED.toLong() } ?: 0L

    private fun currentUidRxBytes(): Long =
        TrafficStats.getUidRxBytes(Process.myUid()).takeUnless { it == TrafficStats.UNSUPPORTED.toLong() } ?: 0L

    private companion object {
        const val STATS_STOP_TIMEOUT_MS = 1_000L
        const val CONFIG_BUILD_TIMEOUT_MS = 5_000L
        const val ESTABLISH_VPN_TIMEOUT_MS = 10_000L
        const val XRAY_START_TIMEOUT_MS = 20_000L
        const val OLCRTC_START_TIMEOUT_MS = 35_000L
        const val HEALTH_CHECK_STEP_TIMEOUT_MS = 15_000L
        const val XRAY_STOP_TIMEOUT_MS = 4_000L
        const val OLCRTC_STOP_TIMEOUT_MS = 5_000L
        const val VPN_CLOSE_TIMEOUT_MS = 4_000L
    }
}

private fun ServerProfile.toOlcRtcRuntimeRequest(
    ownPackageName: String,
    request: StartProxyRequest,
): OlcRtcRuntimeRequest {
    require(protocol == ProxyProtocol.OLCRTC) { "Profile is not olcRTC" }
    val carrierName = host.trim()
    val transportName = transport.trim()
    val roomId = path.trim()
    val clientId = password.trim()
    val keyHex = credential.trim()
    require(carrierName.isNotBlank()) { "olcRTC carrier is required" }
    require(transportName.isNotBlank()) { "olcRTC transport is required" }
    require(roomId.isNotBlank()) { "olcRTC room ID is required" }
    require(clientId.isNotBlank()) { "olcRTC client ID is required" }
    require(keyHex.isNotBlank()) { "olcRTC key is required" }
    return OlcRtcRuntimeRequest(
        carrierName = carrierName,
        transportName = transportName,
        roomId = roomId,
        clientId = clientId,
        keyHex = keyHex,
        sessionName = request.vpnProfile.sessionName,
        ownPackageName = ownPackageName,
        dnsServers = request.vpnProfile.dnsServers,
        excludedPackageNames = request.vpnProfile.excludedPackageNames,
        tunMtu = request.vpnProfile.mtu,
        routeAllTraffic = request.vpnProfile.routeAllTraffic,
        allowIpv6 = request.vpnProfile.allowIpv6,
        debugLogging = request.settings.logsEnabled,
    )
}
