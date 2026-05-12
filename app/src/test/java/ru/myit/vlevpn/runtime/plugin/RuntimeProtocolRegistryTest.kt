package ru.myit.vlevpn.runtime.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.myit.vlevpn.runtime.contract.ProtocolCapability
import ru.myit.vlevpn.runtime.contract.RuntimeKeys
import ru.myit.vlevpn.runtime.contract.RuntimeModuleStatus
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolPlugin

class RuntimeProtocolRegistryTest {
    @Test
    fun xrayPluginHandlesBuiltInProtocols() {
        val xray = XrayProtocolPlugin()
        val registry = RuntimeProtocolRegistry(setOf(xray))

        assertSame(xray, registry.requireActiveByServerProtocol("VLESS"))
        assertSame(xray, registry.requireActiveByServerProtocol("CUSTOM_JSON"))
        assertNull(registry.findByServerProtocol(RuntimeKeys.OLCRTC))
    }

    @Test
    fun scaffoldPluginIsVisibleButNotActive() {
        val registry = RuntimeProtocolRegistry(setOf(ScaffoldPlugin))

        assertEquals("SCAFFOLD", registry.descriptors().single().status.name)
        assertThrows(IllegalStateException::class.java) {
            registry.requireActiveByServerProtocol(RuntimeKeys.OLCRTC)
        }
    }

    private object ScaffoldPlugin : RuntimeProtocolPlugin {
        override val runtimeKey: String = RuntimeKeys.OLCRTC
        override val displayName: String = "olcRTC"
        override val moduleName: String = ":runtime-olcrtc"
        override val serverProtocolKeys: Set<String> = setOf(RuntimeKeys.OLCRTC)
        override val capabilities: Set<ProtocolCapability> = setOf(ProtocolCapability.URI_IMPORT)
        override val status: RuntimeModuleStatus = RuntimeModuleStatus.SCAFFOLD
        override val removable: Boolean = true
    }
}
