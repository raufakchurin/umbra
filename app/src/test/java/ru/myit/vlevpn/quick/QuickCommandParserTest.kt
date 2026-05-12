package ru.myit.vlevpn.quick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCommandParserTest {
    @Test
    fun parsesConnectionCommands() {
        assertEquals(QuickCommand.Connect, QuickCommandParser.parse("vlevpn://connect"))
        assertEquals(QuickCommand.Connect, QuickCommandParser.parse("vlevpn://open"))
        assertEquals(QuickCommand.Disconnect, QuickCommandParser.parse("vlevpn://disconnect"))
        assertEquals(QuickCommand.Disconnect, QuickCommandParser.parse("vlevpn://close"))
        assertEquals(QuickCommand.Toggle, QuickCommandParser.parse("vlevpn://toggle"))
    }

    @Test
    fun parsesAddCommandWithRawOrEncodedUrl() {
        assertEquals(
            QuickCommand.Add("https://key.vlesvpn.ru/DSGT80"),
            QuickCommandParser.parse("vlevpn://add/https://key.vlesvpn.ru/DSGT80"),
        )
        assertEquals(
            QuickCommand.Add("vless://uuid@example.com:443"),
            QuickCommandParser.parse("vlevpn://add/vless%3A%2F%2Fuuid%40example.com%3A443"),
        )
    }

    @Test
    fun parsesBase64ImportCommand() {
        val encoded = QuickCommandParser.encodeBase64Url("vless://uuid@example.com:443")

        assertEquals(
            QuickCommand.Import("vless://uuid@example.com:443"),
            QuickCommandParser.parse("vlevpn://import/$encoded"),
        )
    }

    @Test
    fun parsesRoutingCommandsFromDelimitedBase64() {
        val encoded = QuickCommandParser.encodeBase64Url("com.android.chrome, ru.yandex.yandexmaps")

        assertEquals(
            QuickCommand.RoutingAdd(
                packages = setOf("com.android.chrome", "ru.yandex.yandexmaps"),
                apply = false,
            ),
            QuickCommandParser.parse("vlevpn://routing/add/$encoded"),
        )
        assertEquals(
            QuickCommand.RoutingAdd(
                packages = setOf("com.android.chrome", "ru.yandex.yandexmaps"),
                apply = true,
            ),
            QuickCommandParser.parse("vlevpn://routing/onadd/$encoded"),
        )
    }

    @Test
    fun parsesRoutingCommandsFromJsonBase64() {
        val encoded = QuickCommandParser.encodeBase64Url("""{"packages":["com.android.chrome","ru.yandex.yandexmaps"]}""")
        val command = QuickCommandParser.parse("vle://routing/add/$encoded")

        assertTrue(command is QuickCommand.RoutingAdd)
        assertEquals(
            setOf("com.android.chrome", "ru.yandex.yandexmaps"),
            (command as QuickCommand.RoutingAdd).packages,
        )
    }
}
