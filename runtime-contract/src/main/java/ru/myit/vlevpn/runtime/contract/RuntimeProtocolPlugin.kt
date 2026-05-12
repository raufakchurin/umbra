package ru.myit.vlevpn.runtime.contract

interface RuntimeProtocolPlugin {
    val runtimeKey: String
    val displayName: String
    val moduleName: String
    val serverProtocolKeys: Set<String>
    val capabilities: Set<ProtocolCapability>
    val status: RuntimeModuleStatus
    val removable: Boolean

    fun supports(serverProtocolKey: String): Boolean =
        serverProtocolKeys.any { it.equals(serverProtocolKey, ignoreCase = true) }

    fun descriptor(): RuntimeProtocolDescriptor =
        RuntimeProtocolDescriptor(
            runtimeKey = runtimeKey,
            displayName = displayName,
            moduleName = moduleName,
            serverProtocolKeys = serverProtocolKeys,
            capabilities = capabilities,
            status = status,
            removable = removable,
        )
}
