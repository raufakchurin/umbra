package ru.myit.vlevpn.data.branding

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.network.ProtectedTraffic
import ru.myit.vlevpn.data.subscription.SubscriptionFetchClient

@Singleton
class PartnerBrandingClient @Inject constructor(
    @ProtectedTraffic
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun fetch(appKey: String, providerIds: List<String>): PartnerBrandingResponse = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BRANDING_URL.toHttpUrlOrNull()
            ?: error("Invalid branding URL")
        require(baseUrl.isHttps || baseUrl.host in LOCAL_HOSTS) {
            "Branding URL must use HTTPS"
        }
        val url = baseUrl.newBuilder()
            .setQueryParameter("app_key", appKey)
            .also { builder ->
                providerIds.forEach { providerId ->
                    builder.addQueryParameter("partner_id", providerId)
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", SubscriptionFetchClient.REMNAWAVE_COMPATIBLE_USER_AGENT)
            .header("X-VLE-Network-Path", NETWORK_PATH_DIRECT_PROTECTED)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Branding request failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) PartnerBrandingResponse() else json.decodeFromString(body)
        }
    }

    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: error("Invalid branding image URL")
        require(httpUrl.isHttps || httpUrl.host in LOCAL_HOSTS) {
            "Branding image URL must use HTTPS"
        }
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", SubscriptionFetchClient.REMNAWAVE_COMPATIBLE_USER_AGENT)
            .header("X-VLE-Network-Path", NETWORK_PATH_DIRECT_PROTECTED)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Branding image failed: HTTP ${response.code}")
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    private companion object {
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
        const val NETWORK_PATH_DIRECT_PROTECTED = "direct-protected"
    }
}
