package ru.myit.vlevpn.data.branding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.settings.SettingsDataStore
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository

@Singleton
class PartnerBrandingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val serverRepository: ServerRepository,
    private val client: PartnerBrandingClient,
    private val logs: LogRepository,
) {
    private val mutex = Mutex()
    private val cacheDir: File
        get() = context.filesDir.resolve(CACHE_DIR_NAME)

    fun refreshAsync(scope: CoroutineScope) {
        scope.launch {
            runCatching { refreshNow() }
                .onFailure { logs.add(LogLevel.WARN, "Partner branding refresh failed: ${it.message.orEmpty()}") }
        }
    }

    suspend fun refreshNow() {
        if (!BuildConfig.TELEMETRY_ENABLED || BuildConfig.TELEMETRY_APP_KEY.isBlank()) return
        mutex.withLock {
            val settings = settingsRepository.settings.first()
            if (settings.localBrandingOverrideEnabled) return

            val providerIds = providerIdsByLastUse(
                currentProviderId = settings.subscriptionProviderId,
                servers = serverRepository.servers.first(),
            )
            if (providerIds.isEmpty()) {
                settingsDataStore.clearRemoteBranding()
                return
            }

            val response = client.fetch(BuildConfig.TELEMETRY_APP_KEY, providerIds)
            val selected = response.items.firstOrNull()
            if (selected == null) {
                settingsDataStore.clearRemoteBranding()
                cleanupUnusedCache(activePath = "")
                return
            }

            val imagePath = if (selected.useImage && !selected.imageUrl.isNullOrBlank()) {
                cacheImage(selected)
            } else {
                ""
            }
            settingsDataStore.updateRemoteBranding(
                providerId = selected.providerId,
                priority = selected.priority,
                imagePath = imagePath,
                imageSha256 = selected.imageSha256.orEmpty(),
                blurPercent = selected.blurPercent,
                accentColor = selected.accentColor,
                backgroundColor = selected.backgroundColor,
                updatedAt = selected.updatedAt,
            )
            cleanupUnusedCache(activePath = imagePath)
        }
    }

    private fun providerIdsByLastUse(currentProviderId: String, servers: List<ServerProfile>): List<String> {
        val seen = linkedSetOf<String>()
        currentProviderId.trim().takeIf { it.isNotBlank() }?.let(seen::add)
        servers
            .filter { it.subscriptionProviderId.isNotBlank() }
            .sortedWith(
                compareByDescending<ServerProfile> { it.subscriptionActivatedAtMillis }
                    .thenByDescending { it.subscriptionImportedAtMillis }
                    .thenByDescending { it.updatedAtMillis },
            )
            .forEach { seen.add(it.subscriptionProviderId.trim()) }
        return seen.filter { it.isNotBlank() }.take(MAX_PROVIDER_IDS)
    }

    private suspend fun cacheImage(item: PartnerBrandingItem): String {
        val sha = item.imageSha256.orEmpty().ifBlank { item.updatedAt.hashCode().toString() }
        cacheDir.mkdirs()
        val target = cacheDir.resolve("${item.providerId}_$sha.webp")
        if (target.exists() && (item.imageSha256.isNullOrBlank() || sha256(target.readBytes()) == item.imageSha256)) {
            target.setLastModified(System.currentTimeMillis())
            return target.absolutePath
        }

        val bytes = client.download(item.imageUrl.orEmpty())
        if (bytes.isEmpty()) return ""
        if (!item.imageSha256.isNullOrBlank() && sha256(bytes) != item.imageSha256) {
            logs.add(LogLevel.WARN, "Partner branding image checksum mismatch for ${item.providerId}")
            return ""
        }
        target.writeBytes(bytes)
        target.setLastModified(System.currentTimeMillis())
        return target.absolutePath
    }

    private fun cleanupUnusedCache(activePath: String) {
        val now = System.currentTimeMillis()
        cacheDir.listFiles()
            ?.filter { file ->
                file.absolutePath != activePath &&
                    now - file.lastModified() > TimeUnit.DAYS.toMillis(30)
            }
            ?.forEach { file -> file.delete() }
    }

    private fun sha256(fileBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val CACHE_DIR_NAME = "branding"
        const val MAX_PROVIDER_IDS = 20
    }
}
