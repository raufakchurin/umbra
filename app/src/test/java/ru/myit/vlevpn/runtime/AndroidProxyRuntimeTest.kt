package ru.myit.vlevpn.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProxyRuntimeTest {
    @Test
    fun restoreRuntimeStateAfterProcessRestartReturnsRunningWhenVpnServiceIsStillAlive() {
        val restored = restoreRuntimeStateAfterProcessRestart(
            currentState = RuntimeState.Idle,
            vpnServiceRunning = true,
            connectedAtMillis = 123L,
        )

        assertEquals(RuntimeState.Running(123L), restored)
    }

    @Test
    fun restoreRuntimeStateAfterProcessRestartKeepsExistingRunningState() {
        val running = RuntimeState.Running(456L)

        val restored = restoreRuntimeStateAfterProcessRestart(
            currentState = running,
            vpnServiceRunning = true,
            connectedAtMillis = 999L,
        )

        assertEquals(running, restored)
    }

    @Test
    fun restoreRuntimeStateAfterProcessRestartDoesNotInventRunningWithoutVpnService() {
        val restored = restoreRuntimeStateAfterProcessRestart(
            currentState = RuntimeState.Idle,
            vpnServiceRunning = false,
            connectedAtMillis = 123L,
        )

        assertTrue(restored is RuntimeState.Idle)
    }
}
