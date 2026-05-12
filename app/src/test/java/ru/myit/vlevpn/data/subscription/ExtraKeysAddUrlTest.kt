package ru.myit.vlevpn.data.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraKeysAddUrlTest {
    @Test
    fun appendsSubscriptionUserIdToHeaderUrl() {
        val resolved = ExtraKeysAddUrl.resolve(
            headerUrl = "https://key.vlesvpn.ru/api/keys-3764frbvsadf",
            subscriptionUrl = "https://key.vlesvpn.ru/rem/sub/DSGT80",
        )

        assertEquals("https://key.vlesvpn.ru/api/keys-3764frbvsadf/DSGT80", resolved)
    }

    @Test
    fun keepsHeaderQueryWhenAppendingSubscriptionUserId() {
        val resolved = ExtraKeysAddUrl.resolve(
            headerUrl = "https://key.vlesvpn.ru/api/keys-3764frbvsadf?source=android",
            subscriptionUrl = "https://key.vlesvpn.ru/rem/sub/DSGT80?client=app",
        )

        assertEquals("https://key.vlesvpn.ru/api/keys-3764frbvsadf/DSGT80?source=android", resolved)
    }

    @Test
    fun rejectsInvalidHeaderUrl() {
        val resolved = ExtraKeysAddUrl.resolve(
            headerUrl = "not a url",
            subscriptionUrl = "https://key.vlesvpn.ru/rem/sub/DSGT80",
        )

        assertEquals("", resolved)
    }

    @Test
    fun rejectsNonHttpsHeaderUrl() {
        val resolved = ExtraKeysAddUrl.resolve(
            headerUrl = "http://key.vlesvpn.ru/api/keys-3764frbvsadf",
            subscriptionUrl = "https://key.vlesvpn.ru/rem/sub/DSGT80",
        )

        assertEquals("", resolved)
    }

    @Test
    fun rejectsHeaderUrlWhenSubscriptionUserIdIsMissing() {
        val resolved = ExtraKeysAddUrl.resolve(
            headerUrl = "https://key.vlesvpn.ru/api/keys-3764frbvsadf",
            subscriptionUrl = "https://key.vlesvpn.ru/",
        )

        assertEquals("", resolved)
    }
}
