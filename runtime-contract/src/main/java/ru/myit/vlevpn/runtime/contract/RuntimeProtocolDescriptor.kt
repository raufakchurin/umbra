package ru.myit.vlevpn.runtime.contract

data class RuntimeProtocolDescriptor(
    val runtimeKey: String,
    val displayName: String,
    val moduleName: String,
    val serverProtocolKeys: Set<String>,
    val capabilities: Set<ProtocolCapability>,
    val status: RuntimeModuleStatus,
    val removable: Boolean,
)
