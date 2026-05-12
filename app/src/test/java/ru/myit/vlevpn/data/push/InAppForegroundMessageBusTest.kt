package ru.myit.vlevpn.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.myit.vlevpn.domain.model.InAppForegroundMessage

class InAppForegroundMessageBusTest {
    @Test
    fun `messages are shown one by one in publish order`() {
        val bus = InAppForegroundMessageBus()
        val first = message("delivery-1")
        val second = message("delivery-2")

        assertTrue(bus.publish(first))
        assertTrue(bus.publish(second))
        assertEquals("delivery-1", bus.message.value?.deliveryId)

        bus.clear("delivery-1")
        assertEquals("delivery-2", bus.message.value?.deliveryId)

        bus.clear("delivery-2")
        assertNull(bus.message.value)
    }

    @Test
    fun `duplicate delivery id is not queued twice`() {
        val bus = InAppForegroundMessageBus()
        val first = message("delivery-1")
        val duplicate = message("delivery-1")
        val second = message("delivery-2")

        bus.publish(first)
        bus.publish(duplicate)
        bus.publish(second)

        bus.clear("delivery-1")
        assertEquals("delivery-2", bus.message.value?.deliveryId)

        bus.clear("delivery-2")
        assertNull(bus.message.value)
    }

    private fun message(deliveryId: String): InAppForegroundMessage =
        InAppForegroundMessage(
            deliveryId = deliveryId,
            title = "Title",
            message = "Message",
            severity = "info",
            pushType = "announcement",
        )
}
