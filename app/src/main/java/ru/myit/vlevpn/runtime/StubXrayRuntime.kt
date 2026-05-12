package ru.myit.vlevpn.runtime

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class StubXrayRuntime @Inject constructor() : XrayRuntime {
    private val mutex = Mutex()
    private var running = false
    private var startedAtMillis = 0L

    override suspend fun init(environment: XrayEnvironment) = Unit

    override suspend fun start(configJson: String, tunFd: Int?) {
        mutex.withLock {
            delay(200)
            running = true
            startedAtMillis = System.currentTimeMillis()
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            running = false
            startedAtMillis = 0L
        }
    }

    override suspend fun queryStats(): XrayStats = mutex.withLock {
        if (!running) return@withLock XrayStats()
        val seconds = ((System.currentTimeMillis() - startedAtMillis) / 1000).coerceAtLeast(0)
        XrayStats(
            uplinkBytes = seconds * 512,
            downlinkBytes = seconds * 2048,
        )
    }
}
