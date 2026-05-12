package ru.myit.vlevpn.data.provider

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.core.network.ProtectedTraffic
import ru.myit.vlevpn.data.subscription.SubscriptionFetchClient

@Singleton
class ProviderTelemetryClient @Inject constructor(
    @ProtectedTraffic
    private val okHttpClient: OkHttpClient,
) {
    suspend fun send(check: ProviderTelemetryCheck): Unit = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.PROVIDER_CHECK_URL.toHttpUrlOrNull()
            ?: error("Invalid PartnerID check URL")
        require(baseUrl.isHttps || baseUrl.host in LOCAL_HOSTS) {
            "PartnerID check URL must use HTTPS"
        }

        val url = baseUrl.newBuilder()
            .setQueryParameter("id", check.providerId)
            .setQueryParameter("domain_hash", check.domainHash)
            .setQueryParameter("hwid", check.hwid)
            .setQueryParameter("os_name", check.osName)
            .setQueryParameter("os_version", check.osVersion)
            .setQueryParameter("app_version", check.appVersion)
            .setQueryParameter("device_model", check.deviceModel)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("partnerid", check.providerId)
            .header("User-Agent", SubscriptionFetchClient.REMNAWAVE_COMPATIBLE_USER_AGENT)
            .header("X-VLE-Network-Path", NETWORK_PATH_DIRECT_PROTECTED)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("PartnerID check failed: HTTP ${response.code}")
            }
        }
    }
}

private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
private const val NETWORK_PATH_DIRECT_PROTECTED = "direct-protected"

data class ProviderTelemetryCheck(
    val providerId: String,
    val domainHash: String,
    val hwid: String,
    val osName: String,
    val osVersion: String,
    val appVersion: String,
    val deviceModel: String,
)
