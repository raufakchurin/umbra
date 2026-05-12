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
class InAppNotificationClient @Inject constructor(
    @ProtectedTraffic
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun register(payload: InAppPushRegisterPayload): InAppPushRegisterResponse = postJson(
        url = BuildConfig.IN_APP_PUSH_REGISTER_URL,
        body = json.encodeToString(payload),
    ) { responseBody ->
        if (responseBody.isBlank()) InAppPushRegisterResponse() else json.decodeFromString(responseBody)
    }

    suspend fun inbox(payload: InAppPushAuthPayload): InAppPushInboxResponse = postJson(
        url = BuildConfig.IN_APP_PUSH_INBOX_URL,
        body = json.encodeToString(payload),
    ) { responseBody ->
        if (responseBody.isBlank()) InAppPushInboxResponse() else json.decodeFromString(responseBody)
    }

    suspend fun ack(payload: InAppPushAckPayload): InAppPushAckResponse = postJson(
        url = BuildConfig.IN_APP_PUSH_ACK_URL,
        body = json.encodeToString(payload),
    ) { responseBody ->
        if (responseBody.isBlank()) InAppPushAckResponse() else json.decodeFromString(responseBody)
    }

    private suspend fun <T> postJson(
        url: String,
        body: String,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: error("Invalid in-app notification URL")
        require(httpUrl.isHttps || httpUrl.host in LOCAL_HOSTS) {
            "In-app notification URL must use HTTPS"
        }
        val request = Request.Builder()
            .url(httpUrl)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", SubscriptionFetchClient.REMNAWAVE_COMPATIBLE_USER_AGENT)
            .header("X-VLE-Network-Path", NETWORK_PATH_DIRECT_PROTECTED)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("In-app notification request failed: HTTP ${response.code}")
            }
            parse(response.body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
        const val NETWORK_PATH_DIRECT_PROTECTED = "direct-protected"
    }
}
