package ru.myit.vlevpn.data.telemetry

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.network.ProtectedTraffic
import ru.myit.vlevpn.data.subscription.SubscriptionFetchClient

@Singleton
class MobileTelemetryClient @Inject constructor(
    @ProtectedTraffic
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun sendBatch(payloads: List<MobileTelemetryPayload>): MobileTelemetryBatchResponse = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.TELEMETRY_URL.toHttpUrlOrNull()
            ?: error("Invalid mobile telemetry URL")
        require(baseUrl.isHttps || baseUrl.host in LOCAL_HOSTS) {
            "Mobile telemetry URL must use HTTPS"
        }

        val body = json
            .encodeToString(MobileTelemetryBatchPayload(payloads))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("batch").build())
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", SubscriptionFetchClient.REMNAWAVE_COMPATIBLE_USER_AGENT)
            .header("X-VLE-Network-Path", NETWORK_PATH_DIRECT_PROTECTED)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = "Mobile telemetry failed: HTTP ${response.code}"
                if (response.code in 400..499 && response.code != 408 && response.code != 429) {
                    throw MobileTelemetryPermanentException(message)
                }
                error(message)
            }
            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isBlank()) {
                MobileTelemetryBatchResponse(
                    status = "ok",
                    accepted = payloads.size,
                    duplicates = 0,
                    acceptedEventIds = payloads.map { it.eventId },
                    duplicateEventIds = emptyList(),
                )
            } else {
                json.decodeFromString<MobileTelemetryBatchResponse>(responseBody)
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
        const val NETWORK_PATH_DIRECT_PROTECTED = "direct-protected"
    }
}

class MobileTelemetryPermanentException(message: String) : RuntimeException(message)
