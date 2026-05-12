package ru.myit.vlevpn.data.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBackendRepositoryTest {
    private val repository = FakeBackendRepository(FakeBackendApi())

    @Test
    fun subscriptionFixtureIsDeterministic() = runBlocking {
        val first = repository.updateSubscription()
        val second = repository.updateSubscription()

        assertEquals(first, second)
        assertEquals(2, first.servers.size)
        assertTrue(first.servers.all { it.host.endsWith(".example.invalid") })
    }

    @Test
    fun updateCheckDoesNotRequireNetwork() = runBlocking {
        val metadata = repository.checkAppUpdate()

        assertEquals("0.1.0", metadata.latestVersionName)
        assertFalse(metadata.required)
    }
}
