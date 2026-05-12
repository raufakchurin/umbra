package ru.myit.vlevpn.runtime

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.model.VpnProfile
import ru.myit.vlevpn.domain.repository.PingRepository
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

data class RuntimeConnectionEvent(
    val message: String,
)

data class RuntimePingUpdate(
    val serverId: ServerId,
    val checking: Boolean = false,
    val delayMs: Long? = null,
    val error: String? = null,
    val generation: Long,
)

@Singleton
class RuntimeConnectionManager @Inject constructor(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val pingRepository: PingRepository,
    private val runtime: ProxyRuntime,
    private val stateStore: RuntimeStateStore,
    private val logs: LogRepository,
) {
    val state: StateFlow<RuntimeState> = runtime.state
    val stats: Flow<RuntimeStats> = runtime.stats
    private val _events = MutableSharedFlow<RuntimeConnectionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RuntimeConnectionEvent> = _events.asSharedFlow()
    private val _pingUpdates = MutableSharedFlow<RuntimePingUpdate>(extraBufferCapacity = 64)
    val pingUpdates: SharedFlow<RuntimePingUpdate> = _pingUpdates.asSharedFlow()
    private val pingGeneration = AtomicLong()
    private val connectionGeneration = AtomicLong()
    private val connectMutex = Mutex()

    val isRunning: Boolean
        get() = runtime.state.value is RuntimeState.Running

    fun markPreparingPermission() {
        stateStore.update(RuntimeState.PreparingVpnPermission)
    }

    fun onPermissionDenied() {
        stateStore.update(RuntimeState.Error("VPN permission was denied"))
        logs.add(LogLevel.WARN, "VPN permission was denied")
    }

    suspend fun connect() {
        if (!connectMutex.tryLock()) {
            logs.add(LogLevel.WARN, "Connect request ignored because another connect operation is active")
            _events.emit(RuntimeConnectionEvent("Подключение уже выполняется"))
            return
        }
        val generation = connectionGeneration.incrementAndGet()
        try {
            if (!state.value.canStartConnection()) {
                logs.add(LogLevel.WARN, "Connect request ignored in state ${state.value.logName()}")
                return
            }
            val settings = settingsRepository.settings.first()
            if (settings.autoConnectMode != AutoConnectMode.DISABLED) {
                autoConnect(settings, generation)
            } else {
                connectSelected(settings, generation)
            }
        } finally {
            connectMutex.unlock()
        }
    }

    suspend fun connectSelectedProfile() {
        if (!connectMutex.tryLock()) {
            logs.add(LogLevel.WARN, "Selected-profile connect ignored because another connect operation is active")
            _events.emit(RuntimeConnectionEvent("Подключение уже выполняется"))
            return
        }
        val generation = connectionGeneration.incrementAndGet()
        try {
            if (!state.value.canStartConnection()) {
                logs.add(LogLevel.WARN, "Selected-profile connect ignored in state ${state.value.logName()}")
                return
            }
            connectSelected(settingsRepository.settings.first(), generation)
        } finally {
            connectMutex.unlock()
        }
    }

    private suspend fun connectSelected(settings: AppSettings, generation: Long) {
        val server = serverRepository.getSelectedServer()
        if (server == null) {
            if (isCurrentConnection(generation)) {
                stateStore.update(RuntimeState.Error("Select or create a server profile before connecting"))
                logs.add(LogLevel.WARN, "Select or create a server profile before connecting")
            }
            return
        }
        if (cancelIfConnectionStale(generation)) return
        _events.emit(RuntimeConnectionEvent("Подключаемся к ${server.quotedName()}"))
        connectServer(server, settings, generation)
    }

    private suspend fun autoConnect(settings: AppSettings, generation: Long) {
        stateStore.update(RuntimeState.BuildingConfig)
        val servers = serverRepository.servers.first()
        val pingGeneration = nextPingGeneration()
        if (servers.isEmpty()) {
            if (isCurrentConnection(generation)) {
                stateStore.update(RuntimeState.Error("No server profiles available for autoconnect"))
                logs.add(LogLevel.WARN, "No server profiles available for autoconnect")
            }
            return
        }
        if (cancelIfConnectionStale(generation)) return

        val selectedServer = serverRepository.getSelectedServer()
        if (selectedServer != null) {
            logs.add(LogLevel.INFO, "Autoconnect checking last selected server")
            val selectedDelay = measureSelectedForAutoconnect(selectedServer, settings, pingGeneration)
            if (cancelIfConnectionStale(generation)) return
            if (selectedDelay.delayMs != null) {
                logs.add(LogLevel.INFO, "Autoconnect using last selected server: ${selectedDelay.delayMs} ms")
                _events.emit(RuntimeConnectionEvent("Подключаемся к ${selectedServer.quotedName()}"))
                connectAutoSelectedServer(selectedServer, settings, generation)
                return
            }
            logs.add(LogLevel.INFO, "Autoconnect last selected server ping is n/a; selecting by scenario")
        }

        val candidates = if (selectedServer != null) {
            servers.filter { server -> server.id != selectedServer.id }
        } else {
            servers
        }
        if (candidates.isEmpty()) {
            if (isCurrentConnection(generation)) {
                stateStore.update(RuntimeState.Error("Autoconnect failed: selected server ping is n/a"))
                logs.add(LogLevel.WARN, "Autoconnect failed: selected server ping is n/a")
            }
            return
        }

        val target = selectAutoConnectServer(candidates, settings, pingGeneration)
        if (cancelIfConnectionStale(generation)) return
        if (target == null) {
            stateStore.update(RuntimeState.Error("Autoconnect failed: no server answered ping"))
            logs.add(LogLevel.WARN, "Autoconnect failed: no server answered ping")
            _events.emit(RuntimeConnectionEvent("Выбранная локация недоступна, другой доступной локации нет"))
            return
        }

        serverRepository.select(target.id)
        logs.add(LogLevel.INFO, "Autoconnect selected '${target.name}'")
        if (selectedServer != null) {
            _events.emit(
                RuntimeConnectionEvent(
                    "${selectedServer.quotedName()} недоступна. Подключаемся к ${target.quotedName()}",
                ),
            )
        } else {
            _events.emit(RuntimeConnectionEvent("Подключаемся к ${target.quotedName()}"))
        }
        connectAutoSelectedServer(target, settings, generation)
    }

    private suspend fun connectAutoSelectedServer(server: ServerProfile, settings: AppSettings, generation: Long) {
        if (cancelIfConnectionStale(generation)) return
        stateStore.update(RuntimeState.Idle)
        connectServer(server, settings, generation)
    }

    private suspend fun measureSelectedForAutoconnect(
        server: ServerProfile,
        settings: AppSettings,
        generation: Long,
    ): DelayResult {
        emitPingChecking(server, generation)
        val result = withTimeoutOrNull(AUTOCONNECT_SELECTED_PING_TIMEOUT_MS) {
            runCatching {
                pingRepository.measure(server, settings.pingProtocol, settings)
            }.getOrElse { error ->
                DelayResult(server.id, null, error.message)
            }
        } ?: DelayResult(server.id, null, "Autoconnect ping timeout")
        emitPingResult(result, generation)
        return result
    }

    private suspend fun selectAutoConnectServer(
        candidates: List<ServerProfile>,
        settings: AppSettings,
        generation: Long,
    ): ServerProfile? =
        when (settings.autoConnectMode) {
            AutoConnectMode.DISABLED -> null
            AutoConnectMode.FIRST_AVAILABLE -> {
                val firstAvailable = firstSuccessfulPingWithinTimeout(candidates, settings, generation)
                candidates.firstOrNull { it.id == firstAvailable?.serverId }
            }
            AutoConnectMode.LOWEST_PING -> {
                val best = successfulPingResults(candidates, settings, generation)
                    .minByOrNull { it.delayMs ?: Long.MAX_VALUE }
                candidates.firstOrNull { it.id == best?.serverId }
            }
            AutoConnectMode.FIRST_FROM_TOP_FIVE -> {
                val topFiveIds = successfulPingResults(candidates, settings, generation)
                    .sortedBy { it.delayMs ?: Long.MAX_VALUE }
                    .take(TOP_AUTOCONNECT_CANDIDATES)
                    .map { it.serverId }
                    .toSet()
                candidates.firstOrNull { it.id in topFiveIds }
            }
        }

    private suspend fun successfulPingResults(
        candidates: List<ServerProfile>,
        settings: AppSettings,
        generation: Long,
    ): List<DelayResult> {
        val results = mutableListOf<DelayResult>()
        candidates.forEach { server -> emitPingChecking(server, generation) }
        withTimeoutOrNull(AUTOCONNECT_SELECTION_TIMEOUT_MS) {
            pingRepository
                .measureAll(candidates, settings.pingProtocol, settings)
                .collect { result ->
                    results += result
                    emitPingResult(result, generation)
                }
        }
        val measuredIds = results.map { it.serverId }.toSet()
        candidates
            .filterNot { server -> server.id in measuredIds }
            .forEach { server ->
                emitPingResult(
                    DelayResult(server.id, null, "Autoconnect ping timeout"),
                    generation,
                )
            }
        return results.filter { it.delayMs != null }
    }

    private suspend fun firstSuccessfulPingWithinTimeout(
        candidates: List<ServerProfile>,
        settings: AppSettings,
        generation: Long,
    ): DelayResult? =
        withTimeoutOrNull(AUTOCONNECT_SELECTION_TIMEOUT_MS) {
            pingRepository
                .measureAll(candidates, settings.pingProtocol, settings)
                .firstOrNull { result ->
                    emitPingResult(result, generation)
                    result.delayMs != null
                }
        }

    private fun emitPingChecking(server: ServerProfile, generation: Long) {
        _pingUpdates.tryEmit(
            RuntimePingUpdate(
                serverId = server.id,
                checking = true,
                generation = generation,
            ),
        )
    }

    private fun emitPingResult(result: DelayResult, generation: Long) {
        _pingUpdates.tryEmit(
            RuntimePingUpdate(
                serverId = result.serverId,
                delayMs = result.delayMs,
                error = result.error,
                generation = generation,
            ),
        )
    }

    private suspend fun connectServer(server: ServerProfile, settings: AppSettings, generation: Long) {
        if (cancelIfConnectionStale(generation)) return
        val vpnProfile = VpnProfile(
            sessionName = server.name,
            mtu = settings.tunMtu,
            dnsServers = settings.dnsServers,
            routeAllTraffic = settings.routeAllTraffic,
            allowIpv6 = settings.allowIpv6,
            excludedPackageNames = settings.excludedAppPackages,
        )
        runtime.start(
            StartProxyRequest(
                server = server,
                settings = settings,
                vpnProfile = vpnProfile,
            ),
        )
    }

    suspend fun disconnect() {
        connectionGeneration.incrementAndGet()
        runtime.stop()
    }

    suspend fun disconnectAndAwait(timeoutMillis: Long = RUNTIME_STOP_TIMEOUT_MS): Boolean {
        disconnect()
        return awaitStopped(timeoutMillis)
    }

    suspend fun awaitStopped(timeoutMillis: Long = RUNTIME_STOP_TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            state.first { runtimeState -> runtimeState.isStoppedForOperation() }
        } != null

    suspend fun toggle() {
        if (state.value.isActiveOrStarting()) {
            disconnect()
        } else {
            connect()
        }
    }

    private fun isCurrentConnection(generation: Long): Boolean =
        connectionGeneration.get() == generation

    private fun cancelIfConnectionStale(generation: Long): Boolean {
        if (isCurrentConnection(generation)) return false
        logs.add(LogLevel.INFO, "Pending connect operation was cancelled")
        if (state.value is RuntimeState.BuildingConfig || state.value is RuntimeState.Stopping) {
            stateStore.update(RuntimeState.Idle)
        }
        return true
    }

    private companion object {
        const val TOP_AUTOCONNECT_CANDIDATES = 5
        const val AUTOCONNECT_SELECTED_PING_TIMEOUT_MS = 12_000L
        const val AUTOCONNECT_SELECTION_TIMEOUT_MS = 25_000L
        const val RUNTIME_STOP_TIMEOUT_MS = 15_000L
    }

    private fun nextPingGeneration(): Long =
        pingGeneration.incrementAndGet()
}

private fun ServerProfile.quotedName(): String = "«$name»"

private fun RuntimeState.canStartConnection(): Boolean = when (this) {
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
    RuntimeState.Stopping,
    -> false
}

private fun RuntimeState.isActiveOrStarting(): Boolean = when (this) {
    RuntimeState.StartingForeground,
    RuntimeState.BuildingConfig,
    RuntimeState.EstablishingVpn,
    RuntimeState.StartingNativeCore,
    RuntimeState.VerifyingConnection,
    is RuntimeState.Running,
    -> true
    RuntimeState.Idle,
    RuntimeState.PreparingVpnPermission,
    RuntimeState.Stopping,
    is RuntimeState.Error,
    -> false
}

private fun RuntimeState.isStoppedForOperation(): Boolean =
    this is RuntimeState.Idle || this is RuntimeState.Error

private fun RuntimeState.logName(): String = when (this) {
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
