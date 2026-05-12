package ru.myit.vlevpn.quick

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.domain.repository.ServerImportRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository
import ru.myit.vlevpn.runtime.RuntimeConnectionManager

@Singleton
class QuickCommandHandler @Inject constructor(
    private val importRepository: ServerImportRepository,
    private val settingsRepository: SettingsRepository,
    private val connectionManager: RuntimeConnectionManager,
    private val logs: LogRepository,
) {
    val isRunning: Boolean
        get() = connectionManager.isRunning

    suspend fun execute(command: QuickCommand) {
        when (command) {
            QuickCommand.Connect -> connectionManager.connect()
            QuickCommand.Disconnect -> connectionManager.disconnect()
            QuickCommand.Toggle -> connectionManager.toggle()
            is QuickCommand.Import -> import(command.input, "import")
            is QuickCommand.Add -> import(command.input, "add")
            is QuickCommand.RoutingAdd -> addRouting(command.packages, command.apply)
        }
    }

    fun markPreparingPermission() {
        connectionManager.markPreparingPermission()
    }

    fun onPermissionDenied() {
        connectionManager.onPermissionDenied()
    }

    private suspend fun import(input: String, source: String) {
        runCatching {
            importRepository.importFromInput(input)
        }.onSuccess { summary ->
            logs.add(LogLevel.INFO, "URL command $source completed: ${summary.message}")
        }.onFailure { error ->
            logs.add(LogLevel.ERROR, "URL command $source failed: ${error.message.orEmpty()}")
        }
    }

    private suspend fun addRouting(packages: Set<String>, apply: Boolean) {
        if (packages.isEmpty()) {
            logs.add(LogLevel.WARN, "URL routing command ignored because package list is empty")
            return
        }

        val current = settingsRepository.settings.first().excludedAppPackages
        val next = current + packages
        settingsRepository.updateExcludedAppPackages(next)
        logs.add(LogLevel.INFO, "URL routing command added ${packages.size} excluded app(s)")

        if (apply && connectionManager.isRunning) {
            logs.add(LogLevel.INFO, "Applying routing URL command by reconnecting VPN")
            if (connectionManager.disconnectAndAwait(APPLY_RECONNECT_TIMEOUT_MS)) {
                connectionManager.connect()
            } else {
                logs.add(LogLevel.WARN, "Routing URL command reconnect skipped because VPN did not stop in time")
            }
        }
    }

    private companion object {
        const val APPLY_RECONNECT_TIMEOUT_MS = 15_000L
    }
}
