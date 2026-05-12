package ru.myit.vlevpn.runtime

interface XrayRuntime {
    suspend fun init(environment: XrayEnvironment)
    suspend fun start(configJson: String, tunFd: Int?)
    suspend fun stop()
    suspend fun queryStats(): XrayStats
}
