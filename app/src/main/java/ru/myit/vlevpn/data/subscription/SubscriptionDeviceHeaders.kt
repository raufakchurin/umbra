package ru.myit.vlevpn.data.subscription

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import ru.myit.vlevpn.core.settings.SettingsDataStore

@Singleton
class SubscriptionDeviceHeaders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend fun values(): Map<String, String> = mapOf(
        "x-hwid" to stableHwid(),
        "x-device-os" to "Android",
        "x-ver-os" to androidVersion(),
        "x-device-model" to deviceModel(),
    )

    suspend fun stableHwid(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != LEGACY_BAD_ANDROID_ID }
        val source = androidId ?: settingsDataStore.getOrCreateSubscriptionInstallId()
        return sha256("${context.packageName}:$source").take(HWID_LENGTH)
    }

    private fun androidVersion(): String =
        Build.VERSION.RELEASE.takeIf { it.isNotBlank() } ?: Build.VERSION.SDK_INT.toString()

    private fun deviceModel(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "Android"

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val LEGACY_BAD_ANDROID_ID = "9774d56d682e549c"
        const val HWID_LENGTH = 32
    }
}
