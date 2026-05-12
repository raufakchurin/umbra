package ru.myit.vlevpn.data.apps

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.myit.vlevpn.domain.model.InstalledApp

class AppRoutingPriorityTest {
    @Test
    fun `places known russian services before regular apps`() {
        val sorted = AppRoutingPriority.sort(
            listOf(
                app("com.android.chrome", "Chrome"),
                app("com.spotify.music", "Spotify"),
                app("ru.ozon.app.android", "Ozon"),
                app("com.vkontakte.android", "VK"),
                app("ru.yandex.yandexmaps", "Яндекс Карты"),
                app("com.google.android.youtube", "YouTube"),
                app("ru.oneme.app", "MAX"),
            ),
        )

        assertEquals(
            listOf("MAX", "VK", "Ozon", "Яндекс Карты", "Chrome", "Spotify", "YouTube"),
            sorted.map { it.label },
        )
    }

    @Test
    fun `keeps regular apps in label order after priority group`() {
        val sorted = AppRoutingPriority.sort(
            listOf(
                app("com.spotify.music", "Spotify"),
                app("com.android.chrome", "Chrome"),
                app("com.google.android.youtube", "YouTube"),
            ),
        )

        assertEquals(
            listOf("Chrome", "Spotify", "YouTube"),
            sorted.map { it.label },
        )
    }

    @Test
    fun `prioritizes yandex maps before other yandex apps`() {
        val sorted = AppRoutingPriority.sort(
            listOf(
                app("ru.yandex.searchplugin", "Яндекс"),
                app("ru.yandex.yandexmaps", "Яндекс Карты"),
                app("com.android.chrome", "Chrome"),
            ),
        )

        assertEquals(
            listOf("Яндекс Карты", "Яндекс", "Chrome"),
            sorted.map { it.label },
        )
    }

    private fun app(packageName: String, label: String): InstalledApp =
        InstalledApp(
            packageName = packageName,
            label = label,
            isSystemApp = false,
        )
}
