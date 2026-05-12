package ru.myit.vlevpn.runtime.olcrtc

import javax.inject.Inject
import javax.inject.Singleton
import ru.myit.vlevpn.runtime.contract.ProtocolCapability
import ru.myit.vlevpn.runtime.contract.RuntimeKeys
import ru.myit.vlevpn.runtime.contract.RuntimeModuleStatus
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolPlugin

@Singleton
class OlcRtcProtocolPlugin @Inject constructor() : RuntimeProtocolPlugin {
    override val runtimeKey: String = RuntimeKeys.OLCRTC
    override val displayName: String = "olcRTC"
    override val moduleName: String = ":runtime-olcrtc"
    override val serverProtocolKeys: Set<String> = setOf(RuntimeKeys.OLCRTC)
    override val capabilities: Set<ProtocolCapability> = setOf(
        ProtocolCapability.URI_IMPORT,
        ProtocolCapability.SUBSCRIPTION_IMPORT,
        ProtocolCapability.VPN_RUNTIME,
        ProtocolCapability.PING,
        ProtocolCapability.STATS,
    )
    override val status: RuntimeModuleStatus = RuntimeModuleStatus.ACTIVE
    override val removable: Boolean = true
}
