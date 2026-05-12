package ru.myit.vlevpn.data.subscription

import android.util.Base64
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.network.ProtectedTraffic

@Singleton
class SubscriptionFetchClient @Inject constructor(
    @ProtectedTraffic
    private val okHttpClient: OkHttpClient,
    private val deviceHeaders: SubscriptionDeviceHeaders,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(urlText: String): SubscriptionFetchResult = withContext(Dispatchers.IO) {
        val partnerIdFromUrl = SubscriptionProviderId.fromSubscriptionUrl(urlText)
        val providerDomainHash = SubscriptionProviderId.sourceDomainHash(urlText, urlText)
        val url = URL(SubscriptionProviderId.fetchUrlWithoutFragment(urlText))
        require(url.protocol == "https") {
            "Only HTTPS subscription URLs are allowed"
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json,text/plain,*/*")
            .header("User-Agent", REMNAWAVE_COMPATIBLE_USER_AGENT)

        deviceHeaders.values().forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Subscription request failed: HTTP ${response.code}")
            }
            SubscriptionFetchResult(
                body = response.body?.string().orEmpty(),
                metadata = SubscriptionMetadata(
                    title = response.header("profile-title").orEmpty().decodeBase64PrefixedHeader(),
                    updateIntervalHours = response.header("profile-update-interval").orEmpty().toIntOrNull() ?: 0,
                    uploadBytes = response.header("subscription-userinfo").orEmpty().userinfoLong("upload"),
                    downloadBytes = response.header("subscription-userinfo").orEmpty().userinfoLong("download"),
                    totalBytes = response.header("subscription-userinfo").orEmpty().userinfoLong("total"),
                    expireAtMillis = response.header("subscription-userinfo").orEmpty().userinfoLong("expire")
                        .takeIf { it > 0L }
                        ?.let { it * 1000L }
                        ?: 0L,
                    supportUrl = response.header("support-url").orEmpty(),
                    webPageUrl = response.header("profile-web-page-url").orEmpty(),
                    announce = response.header("announce").orEmpty().decodeBase64PrefixedHeader(),
                    updateAlways = response.header("update-always").isTruthyHeader() ||
                        response.header("profile-update-always").isTruthyHeader(),
                    providerId = response.partnerIdHeader().ifBlank { partnerIdFromUrl },
                    providerDomainHash = providerDomainHash,
                    extraKeysAddUrl = response.header("extra-keys-add").orEmpty().trim(),
                ),
            )
        }
    }

    suspend fun fetchExtraKeys(urlText: String): List<String> = withContext(Dispatchers.IO) {
        val url = urlText.trim().toHttpUrlOrNull()?.takeIf { it.isHttps }
            ?: return@withContext emptyList()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", REMNAWAVE_COMPATIBLE_USER_AGENT)

        deviceHeaders.values().forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            require(response.request.url.isHttps) {
                "Extra keys request must stay on HTTPS"
            }
            if (!response.isSuccessful) {
                error("Extra keys request failed: HTTP ${response.code}")
            }
            val element = json.parseToJsonElement(response.body?.string().orEmpty())
            val keys = element.jsonObject["keys"] as? JsonArray
                ?: error("Extra keys response must contain keys array")

            keys.mapNotNull { item ->
                item.jsonPrimitive.contentOrNull?.trim()
            }.filter { key ->
                key.startsWith("olcrtc://", ignoreCase = true)
            }
        }
    }

    companion object {
        val REMNAWAVE_COMPATIBLE_USER_AGENT = "VLEVPN/${BuildConfig.VERSION_NAME} Android Xray"
    }
}

private fun String.userinfoLong(key: String): Long =
    split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
        ?.substringAfter("=")
        ?.toLongOrNull()
        ?: 0L

private fun String.decodeBase64PrefixedHeader(): String {
    val value = trim()
    if (!value.startsWith("base64:", ignoreCase = true)) return value
    return runCatching {
        String(Base64.decode(value.substringAfter(":"), Base64.DEFAULT), Charsets.UTF_8)
    }.getOrDefault(value)
}

private fun String?.isTruthyHeader(): Boolean =
    this?.trim()?.lowercase() in setOf("1", "true", "yes", "on")

private fun okhttp3.Response.partnerIdHeader(): String =
    header("partnerid").orEmpty()
        .ifBlank { header("x-partner-id").orEmpty() }
        .ifBlank { header("vle-partner-id").orEmpty() }
        .ifBlank { header("providerid").orEmpty() }
