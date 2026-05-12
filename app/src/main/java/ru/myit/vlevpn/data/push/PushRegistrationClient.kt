package ru.myit.vlevpn.data.push

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
class PushRegistrationClient @Inject constructor(
    @ProtectedTraffic
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun register(payload: PushRegisterPayload): PushRegisterResponse = withContext(Dispatchers.IO) {
        val url = BuildConfig.PUSH_REGISTER_URL.toHttpUrlOrNull()
            ?: error("Invalid push registration URL")
        require(url.isHttps || url.host in LOCAL_HOSTS) {
            "Push registration URL must use HTTPS"
        }

        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", SubscriptionFetchClient.REMNAWAVE_COMPATIBLE_USER_AGENT)
            .header("X-VLE-Network-Path", NETWORK_PATH_DIRECT_PROTECTED)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Push registration failed: HTTP ${response.code}")
            }
            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isBlank()) {
                PushRegisterResponse(status = "ok", tokenId = "")
            } else {
                json.decodeFromString<PushRegisterResponse>(responseBody)
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
        const val NETWORK_PATH_DIRECT_PROTECTED = "direct-protected"
    }
}
