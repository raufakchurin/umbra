package ru.myit.vlevpn.runtime.plugin

import javax.inject.Inject
import javax.inject.Singleton
import ru.myit.vlevpn.runtime.contract.RuntimeModuleStatus
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolDescriptor
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolPlugin

@Singleton
class RuntimeProtocolRegistry @Inject constructor(
    private val plugins: Set<@JvmSuppressWildcards RuntimeProtocolPlugin>,
) {
    fun descriptors(): List<RuntimeProtocolDescriptor> =
        plugins
            .map { it.descriptor() }
            .sortedWith(compareBy<RuntimeProtocolDescriptor> { it.removable }.thenBy { it.runtimeKey })

    fun findByServerProtocol(serverProtocolKey: String): RuntimeProtocolPlugin? =
        plugins.firstOrNull { it.supports(serverProtocolKey) }

    fun requireActiveByServerProtocol(serverProtocolKey: String): RuntimeProtocolPlugin {
        val plugin = findByServerProtocol(serverProtocolKey)
            ?: error("No runtime plugin registered for protocol $serverProtocolKey")

        if (plugin.status != RuntimeModuleStatus.ACTIVE) {
            error("Runtime plugin ${plugin.displayName} is registered but not active")
        }

        return plugin
    }
}
