package ru.myit.vlevpn.runtime

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.myit.vlevpn.domain.model.AppSettings

data class RuntimeHealthCheckResult(
    val healthy: Boolean,
    val delayMs: Long? = null,
    val error: String? = null,
)

@Singleton
class RuntimeHealthChecker @Inject constructor(
    private val baseClient: OkHttpClient,
) {
    suspend fun check(settings: AppSettings): RuntimeHealthCheckResult =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(HEALTH_CHECK_TOTAL_TIMEOUT_MS) {
                runChecks(settings)
            } ?: RuntimeHealthCheckResult(
                healthy = false,
                error = "Проверка соединения истекла по таймауту",
            )
        }

    private suspend fun runChecks(settings: AppSettings): RuntimeHealthCheckResult {
        val client = baseClient.newBuilder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LOOPBACK_HOST, settings.httpPort)))
            .connectTimeout(HEALTH_CHECK_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HEALTH_CHECK_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(HEALTH_CHECK_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(HEALTH_CHECK_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

        var lastError: String? = null
        repeat(HEALTH_CHECK_ATTEMPTS) { attempt ->
            HEALTH_CHECK_URLS.forEach { url ->
                val startedAt = System.nanoTime()
                val result = runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .header("Cache-Control", "no-cache")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.code !in 200..399) {
                            error("HTTP ${response.code}")
                        }
                        RuntimeHealthCheckResult(
                            healthy = true,
                            delayMs = elapsedMs(startedAt),
                        )
                    }
                }
                result.onSuccess { return it }
                lastError = result.exceptionOrNull().safeMessageOrDefault()
            }
            if (attempt != HEALTH_CHECK_ATTEMPTS - 1) {
                delay(HEALTH_CHECK_RETRY_DELAY_MS)
            }
        }
        return RuntimeHealthCheckResult(
            healthy = false,
            error = lastError ?: "Проверка соединения не прошла",
        )
    }

    private fun elapsedMs(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).coerceAtLeast(1L)

    private fun Throwable?.safeMessageOrDefault(): String =
        this?.message?.take(96)?.ifBlank { null } ?: "Проверка соединения не прошла"

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val HEALTH_CHECK_TOTAL_TIMEOUT_MS = 8_000L
        const val HEALTH_CHECK_CALL_TIMEOUT_MS = 2_500L
        const val HEALTH_CHECK_RETRY_DELAY_MS = 350L
        const val HEALTH_CHECK_ATTEMPTS = 2
        val HEALTH_CHECK_URLS = listOf(
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204",
        )
    }
}
