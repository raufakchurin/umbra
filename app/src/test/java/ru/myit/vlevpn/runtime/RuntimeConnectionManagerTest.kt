package ru.myit.vlevpn.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.logging.RuntimeLogEntry
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.PingRepository
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

class RuntimeConnectionManagerTest {
    @Test
    fun autoConnectKeepsSelectedServerWhenItRespondsToPing() = runBlocking {
        val first = server("server-1")
        val selected = server("server-3")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.FIRST_AVAILABLE)
        val serverRepository = FakeServerRepository(listOf(first, selected), selected.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    first.id to 20L,
                    selected.id to 1L,
                ),
            ),
            runtime = runtime,
        )

        manager.connect()

        assertEquals(selected.id, serverRepository.selectedServerId.value)
        assertEquals(selected.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun autoConnectLowestPingFallsBackWhenSelectedServerHasNoPing() = runBlocking {
        val selected = server("server-1")
        val fastest = server("server-2")
        val slow = server("server-3")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.LOWEST_PING)
        val serverRepository = FakeServerRepository(listOf(selected, fastest, slow), selected.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    selected.id to null,
                    fastest.id to 24L,
                    slow.id to 120L,
                ),
            ),
            runtime = runtime,
        )

        manager.connect()

        assertEquals(fastest.id, serverRepository.selectedServerId.value)
        assertEquals(fastest.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun autoConnectEmitsFallbackMessageWhenSelectedServerIsUnavailable() = runBlocking {
        val selected = server("server-1")
        val fallback = server("server-2")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.FIRST_AVAILABLE)
        val manager = manager(
            serverRepository = FakeServerRepository(listOf(selected, fallback), selected.id),
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    selected.id to null,
                    fallback.id to 32L,
                ),
            ),
            runtime = FakeProxyRuntime(),
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { it.message.contains("недоступна") }
        }

        manager.connect()

        assertEquals("«server-1» недоступна. Подключаемся к «server-2»", event.await().message)
    }

    @Test
    fun autoConnectPublishesPingResultsForUi() = runBlocking {
        val slow = server("server-1")
        val fastest = server("server-2")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.LOWEST_PING)
        val manager = manager(
            serverRepository = FakeServerRepository(listOf(slow, fastest), null),
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    slow.id to 90L,
                    fastest.id to 12L,
                ),
            ),
            runtime = FakeProxyRuntime(),
        )
        val fastestPing = async(start = CoroutineStart.UNDISPATCHED) {
            manager.pingUpdates.first { update ->
                update.serverId == fastest.id && update.delayMs == 12L
            }
        }

        manager.connect()

        assertEquals(12L, withTimeout(1_000) { fastestPing.await().delayMs })
    }

    @Test
    fun autoConnectUsesFirstServerFromTopFiveByPingInListOrder() = runBlocking {
        val servers = (1..6).map { server("server-$it") }
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.FIRST_FROM_TOP_FIVE)
        val serverRepository = FakeServerRepository(servers, null)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    servers[0].id to 300L,
                    servers[1].id to 20L,
                    servers[2].id to 10L,
                    servers[3].id to 40L,
                    servers[4].id to 50L,
                    servers[5].id to 60L,
                ),
            ),
            runtime = runtime,
        )

        manager.connect()

        assertEquals(servers[1].id, serverRepository.selectedServerId.value)
        assertEquals(servers[1].id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun autoConnectClearsCheckingStateBeforeStartingRuntime() = runBlocking {
        val selected = server("server-1")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.FIRST_AVAILABLE)
        val stateStore = RuntimeStateStore()
        val runtime = GuardedFakeProxyRuntime(stateStore)
        val manager = manager(
            serverRepository = FakeServerRepository(listOf(selected), selected.id),
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(delays = mapOf(selected.id to 13L)),
            runtime = runtime,
            stateStore = stateStore,
        )

        manager.connect()

        assertEquals(RuntimeState.Idle, runtime.statesAtStart.single())
        assertEquals(selected.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun connectSelectedProfileIgnoresAutoConnectScenario() = runBlocking {
        val selected = server("server-1")
        val fastest = server("server-2")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.LOWEST_PING)
        val serverRepository = FakeServerRepository(listOf(selected, fastest), selected.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    selected.id to null,
                    fastest.id to 1L,
                ),
            ),
            runtime = runtime,
        )

        manager.connectSelectedProfile()

        assertEquals(selected.id, serverRepository.selectedServerId.value)
        assertEquals(selected.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun selectAndConnectStartsTappedServerWhenIdleAndAutoConnectIsDisabled() = runBlocking {
        val selected = server("server-1")
        val tapped = server("server-2")
        val fallback = server("server-3")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.DISABLED)
        val serverRepository = FakeServerRepository(listOf(selected, tapped, fallback), selected.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    selected.id to null,
                    tapped.id to null,
                    fallback.id to 1L,
                ),
            ),
            runtime = runtime,
        )

        manager.selectAndConnect(tapped.id)

        assertEquals(tapped.id, serverRepository.selectedServerId.value)
        assertEquals(tapped.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun selectAndConnectChecksTappedServerFirstThenUsesAutoconnectScenario() = runBlocking {
        val selected = server("server-1")
        val tapped = server("server-2")
        val fallback = server("server-3")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.LOWEST_PING)
        val serverRepository = FakeServerRepository(listOf(selected, tapped, fallback), selected.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    selected.id to 100L,
                    tapped.id to null,
                    fallback.id to 1L,
                ),
            ),
            runtime = runtime,
        )

        manager.selectAndConnect(tapped.id)

        assertEquals(fallback.id, serverRepository.selectedServerId.value)
        assertEquals(fallback.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun selectAndConnectUsesTappedServerWhenAutoconnectPingSucceeds() = runBlocking {
        val selected = server("server-1")
        val tapped = server("server-2")
        val fasterButNotTapped = server("server-3")
        val settings = AppSettings.Default.copy(autoConnectMode = AutoConnectMode.LOWEST_PING)
        val serverRepository = FakeServerRepository(listOf(selected, tapped, fasterButNotTapped), selected.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(settings),
            pingRepository = FakePingRepository(
                delays = mapOf(
                    selected.id to null,
                    tapped.id to 30L,
                    fasterButNotTapped.id to 1L,
                ),
            ),
            runtime = runtime,
        )

        manager.selectAndConnect(tapped.id)

        assertEquals(tapped.id, serverRepository.selectedServerId.value)
        assertEquals(tapped.id, runtime.startedRequests.single().server.id)
    }

    @Test
    fun selectAndConnectStopsRunningRuntimeBeforeStartingTappedOlcRtcServer() = runBlocking {
        val xray = server("xray-server")
        val olcRtc = server("olcrtc-server", ProxyProtocol.OLCRTC)
        val serverRepository = FakeServerRepository(listOf(xray, olcRtc), xray.id)
        val runtime = FakeProxyRuntime()
        val manager = manager(
            serverRepository = serverRepository,
            settingsRepository = FakeSettingsRepository(AppSettings.Default),
            pingRepository = FakePingRepository(delays = emptyMap()),
            runtime = runtime,
        )
        manager.connectSelectedProfile()

        manager.selectAndConnect(olcRtc.id)

        assertEquals(1, runtime.stopCount)
        assertEquals(listOf(xray.id, olcRtc.id), runtime.startedRequests.map { it.server.id })
        assertEquals(ProxyProtocol.OLCRTC, runtime.startedRequests.last().server.protocol)
        assertEquals(olcRtc.id, serverRepository.selectedServerId.value)
    }

    @Test
    fun disconnectCancelsPendingAutoconnectBeforeRuntimeStart() = runBlocking {
        val selected = server("server-1")
        val stateStore = RuntimeStateStore()
        val runtime = GuardedFakeProxyRuntime(stateStore)
        val manager = manager(
            serverRepository = FakeServerRepository(listOf(selected), selected.id),
            settingsRepository = FakeSettingsRepository(
                AppSettings.Default.copy(autoConnectMode = AutoConnectMode.FIRST_AVAILABLE),
            ),
            pingRepository = FakePingRepository(
                delays = mapOf(selected.id to 12L),
                delayMillis = 200L,
            ),
            runtime = runtime,
            stateStore = stateStore,
        )

        val connectJob = async { manager.connect() }
        withTimeout(1_000) {
            stateStore.state.first { it is RuntimeState.BuildingConfig }
        }

        manager.disconnect()
        connectJob.await()

        assertEquals(emptyList<StartProxyRequest>(), runtime.startedRequests)
        assertEquals(RuntimeState.Idle, stateStore.state.value)
    }

    @Test
    fun concurrentConnectRequestsStartRuntimeOnlyOnce() = runBlocking {
        val selected = server("server-1")
        val runtime = FakeProxyRuntime(startDelayMillis = 150L)
        val manager = manager(
            serverRepository = FakeServerRepository(listOf(selected), selected.id),
            settingsRepository = FakeSettingsRepository(AppSettings.Default),
            pingRepository = FakePingRepository(delays = mapOf(selected.id to 12L)),
            runtime = runtime,
        )

        val first = async { manager.connect() }
        delay(20L)
        val second = async { manager.connect() }
        first.await()
        second.await()

        assertEquals(1, runtime.startedRequests.size)
    }

    @Test
    fun disconnectAndAwaitWaitsUntilRuntimeIsIdle() = runBlocking {
        val selected = server("server-1")
        val runtime = FakeProxyRuntime(stopDelayMillis = 120L)
        val manager = manager(
            serverRepository = FakeServerRepository(listOf(selected), selected.id),
            settingsRepository = FakeSettingsRepository(AppSettings.Default),
            pingRepository = FakePingRepository(delays = mapOf(selected.id to 12L)),
            runtime = runtime,
        )

        manager.connect()
        val stopped = manager.disconnectAndAwait(timeoutMillis = 1_000L)

        assertEquals(true, stopped)
        assertEquals(RuntimeState.Idle, runtime.state.value)
    }

    private fun manager(
        serverRepository: ServerRepository,
        settingsRepository: SettingsRepository,
        pingRepository: PingRepository,
        runtime: ProxyRuntime,
        stateStore: RuntimeStateStore = RuntimeStateStore(),
    ): RuntimeConnectionManager =
        RuntimeConnectionManager(
            serverRepository = serverRepository,
            settingsRepository = settingsRepository,
            pingRepository = pingRepository,
            runtime = runtime,
            stateStore = stateStore,
            logs = FakeLogRepository(),
        )

    private fun server(id: String, protocol: ProxyProtocol = ProxyProtocol.VLESS): ServerProfile =
        ServerProfile(
            id = ServerId(id),
            name = id,
            protocol = protocol,
            host = "$id.example.com",
            port = 443,
            credential = "00000000-0000-4000-8000-000000000001",
            password = "",
            method = "",
            transport = "tcp",
            security = "none",
            sni = "",
            path = "",
            customJson = "",
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
}

private class FakeServerRepository(
    initialServers: List<ServerProfile>,
    selectedId: ServerId?,
) : ServerRepository {
    override val servers = MutableStateFlow(initialServers)
    override val selectedServerId = MutableStateFlow(selectedId)
    override val selectedServer: Flow<ServerProfile?> =
        combine(servers, selectedServerId) { servers, id -> servers.firstOrNull { it.id == id } }

    override fun observeServer(id: ServerId): Flow<ServerProfile?> =
        servers.map { servers -> servers.firstOrNull { it.id == id } }

    override suspend fun getServer(id: ServerId): ServerProfile? =
        servers.value.firstOrNull { it.id == id }

    override suspend fun getSelectedServer(): ServerProfile? =
        selectedServerId.value?.let { getServer(it) }

    override suspend fun hasServers(): Boolean = servers.value.isNotEmpty()

    override suspend fun upsert(server: ServerProfile) = Unit
    override suspend fun upsertAll(servers: List<ServerProfile>) = Unit
    override suspend fun replaceSubscription(subscriptionId: String, servers: List<ServerProfile>) = Unit
    override suspend fun appendSubscriptionProfiles(
        subscriptionId: String,
        importedAtMillis: Long,
        servers: List<ServerProfile>,
    ): Boolean = true
    override suspend fun updateSubscriptionAutoUpdateOnLaunch(subscriptionId: String, enabled: Boolean) = Unit
    override suspend fun deleteSubscription(subscriptionId: String) = Unit
    override suspend fun delete(id: ServerId) = Unit
    override suspend fun select(id: ServerId?) {
        selectedServerId.value = id
    }
}

private class FakeSettingsRepository(
    initialSettings: AppSettings,
) : SettingsRepository {
    override val settings = MutableStateFlow(initialSettings)

    override suspend fun selectServer(serverId: ServerId?) {
        settings.value = settings.value.copy(selectedServerId = serverId)
    }

    override suspend fun updatePorts(socksPort: Int, httpPort: Int, localDnsPort: Int) = Unit
    override suspend fun updateTun(mtu: Int, xrayTunModeEnabled: Boolean, routeAllTraffic: Boolean, allowIpv6: Boolean) = Unit
    override suspend fun updateDns(servers: List<String>) = Unit
    override suspend fun updateLogSettings(logsEnabled: Boolean, debugConfigPreviewEnabled: Boolean) = Unit
    override suspend fun updatePingProtocol(protocol: PingProtocol) = Unit
    override suspend fun updateAutoConnectMode(mode: AutoConnectMode) {
        settings.value = settings.value.copy(autoConnectMode = mode)
    }
    override suspend fun updateExcludedAppPackages(packages: Set<String>) = Unit
    override suspend fun updateSubscriptionProvider(providerId: String, domainHash: String) = Unit
    override suspend fun updateInterface(
        language: ru.myit.vlevpn.domain.model.AppLanguage,
        textSize: ru.myit.vlevpn.domain.model.AppTextSize,
    ) = Unit
    override suspend fun updateAppearance(
        accentColor: ru.myit.vlevpn.domain.model.AppAccentColor,
        backgroundStyle: ru.myit.vlevpn.domain.model.AppBackgroundStyle,
    ) = Unit
    override suspend fun updateLocalBrandingOverride(enabled: Boolean) = Unit
    override suspend fun markProviderTelemetrySent(sentAtMillis: Long) = Unit
    override suspend fun resetToDefaults() {
        settings.value = AppSettings.Default.copy(selectedServerId = settings.value.selectedServerId)
    }
}

private class FakePingRepository(
    private val delays: Map<ServerId, Long?>,
    private val delayMillis: Long = 0L,
) : PingRepository {
    override suspend fun measure(
        server: ServerProfile,
        protocol: PingProtocol,
        settings: AppSettings,
    ): DelayResult {
        if (delayMillis > 0L) {
            delay(delayMillis)
        }
        return DelayResult(server.id, delays[server.id], error = delays[server.id]?.let { null } ?: "n/a")
    }

    override fun measureAll(
        servers: List<ServerProfile>,
        protocol: PingProtocol,
        settings: AppSettings,
    ): Flow<DelayResult> = flow {
        servers.forEach { emit(measure(it, protocol, settings)) }
    }
}

private class FakeProxyRuntime(
    private val startDelayMillis: Long = 0L,
    private val stopDelayMillis: Long = 0L,
) : ProxyRuntime {
    val startedRequests = mutableListOf<StartProxyRequest>()
    var stopCount = 0
    override val state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    override val stats = MutableStateFlow(RuntimeStats())

    override suspend fun start(request: StartProxyRequest) {
        if (startDelayMillis > 0L) {
            delay(startDelayMillis)
        }
        startedRequests += request
        state.value = RuntimeState.Running(System.currentTimeMillis())
    }

    override suspend fun stop() {
        stopCount += 1
        if (stopDelayMillis > 0L) {
            state.value = RuntimeState.Stopping
            delay(stopDelayMillis)
        }
        state.value = RuntimeState.Idle
    }

    override suspend fun measureDelay(serverId: ServerId): DelayResult =
        DelayResult(serverId, null)
}

private class GuardedFakeProxyRuntime(
    private val stateStore: RuntimeStateStore,
) : ProxyRuntime {
    val statesAtStart = mutableListOf<RuntimeState>()
    val startedRequests = mutableListOf<StartProxyRequest>()
    override val state = stateStore.state
    override val stats = MutableStateFlow(RuntimeStats())

    override suspend fun start(request: StartProxyRequest) {
        statesAtStart += state.value
        startedRequests += request
        stateStore.update(RuntimeState.Running(System.currentTimeMillis()))
    }

    override suspend fun stop() {
        stateStore.update(RuntimeState.Idle)
    }

    override suspend fun measureDelay(serverId: ServerId): DelayResult =
        DelayResult(serverId, null)
}

private class FakeLogRepository : LogRepository {
    override val entries: StateFlow<List<RuntimeLogEntry>> = MutableStateFlow(emptyList())
    override val generatedConfigPreview: StateFlow<String?> = MutableStateFlow(null)

    override fun add(level: LogLevel, message: String) = Unit
    override fun setGeneratedConfigPreview(configJson: String?) = Unit
    override fun clear() = Unit
}
