package ru.myit.vlevpn.runtime.plugin

import javax.inject.Inject
import javax.inject.Singleton
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.runtime.contract.ProtocolCapability
import ru.myit.vlevpn.runtime.contract.RuntimeKeys
import ru.myit.vlevpn.runtime.contract.RuntimeModuleStatus
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolPlugin

@Singleton
class XrayProtocolPlugin @Inject constructor() : RuntimeProtocolPlugin {
    override val runtimeKey: String = RuntimeKeys.XRAY
    override val displayName: String = "Xray"
    override val moduleName: String = ":app"
    override val serverProtocolKeys: Set<String> = ProxyProtocol.entries
        .filterNot { it == ProxyProtocol.OLCRTC }
        .mapTo(mutableSetOf()) { it.name }
    override val capabilities: Set<ProtocolCapability> = setOf(
        ProtocolCapability.URI_IMPORT,
        ProtocolCapability.SUBSCRIPTION_IMPORT,
        ProtocolCapability.VPN_RUNTIME,
        ProtocolCapability.PING,
        ProtocolCapability.STATS,
    )
    override val status: RuntimeModuleStatus = RuntimeModuleStatus.ACTIVE
    override val removable: Boolean = false
}
