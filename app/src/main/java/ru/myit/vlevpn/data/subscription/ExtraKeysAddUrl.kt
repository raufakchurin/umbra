package ru.myit.vlevpn.data.subscription

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ExtraKeysAddUrl {
    fun resolve(headerUrl: String, subscriptionUrl: String): String {
        val normalizedHeaderUrl = headerUrl.trim()
        if (normalizedHeaderUrl.isBlank()) return ""

        val userId = subscriptionUrl.lastPathSegment()
        if (userId.isBlank()) return ""

        val baseUrl = normalizedHeaderUrl.toHttpUrlOrNull() ?: return ""
        if (!baseUrl.isHttps) return ""
        return baseUrl
            .newBuilder()
            .addPathSegment(userId)
            .build()
            .toString()
    }

    private fun String.lastPathSegment(): String {
        val urlWithoutFragment = runCatching {
            SubscriptionProviderId.fetchUrlWithoutFragment(trim())
        }.getOrNull() ?: return ""

        val path = runCatching { URI(urlWithoutFragment).path }.getOrNull().orEmpty()
        return path
            .trim('/')
            .split('/')
            .lastOrNull()
            .orEmpty()
            .trim()
    }
}
