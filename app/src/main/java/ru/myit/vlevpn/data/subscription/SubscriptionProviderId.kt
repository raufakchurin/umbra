package ru.myit.vlevpn.data.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SubscriptionProviderId {
    private val providerCommentRegex = Regex(
        pattern = """(?im)^\s*#\s*(partnerid|providerid)\s+([A-Za-z0-9_-]{5,64})\s*$""",
    )

    fun fromSubscriptionUrl(urlText: String): String {
        val uri = runCatching { URI(urlText) }.getOrNull() ?: return ""
        return findProviderIdInRawQuery(uri.rawFragment?.removePrefix("?"))
            .ifBlank { findProviderIdInRawQuery(uri.rawQuery) }
    }

    fun fromSubscriptionBody(body: String): String =
        providerCommentRegex.find(body)?.groupValues?.getOrNull(2).orEmpty()

    fun sourceDomainHash(sourceHint: String, fallback: String): String {
        val uri = runCatching { URI(sourceHint) }.getOrNull()
        val domain = uri?.host?.trim()?.lowercase().orEmpty()
        val source = domain.ifBlank { "manual:${fallback.trim().take(512)}" }
        return sha256(source).take(32)
    }

    fun fetchUrlWithoutFragment(urlText: String): String {
        val uri = URI(urlText)
        return URI(uri.scheme, uri.authority, uri.path, uri.query, null).toString()
    }

    private fun findProviderIdInRawQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) return ""
        return rawQuery.split("&").firstNotNullOfOrNull { part ->
            val key = part.substringBefore("=", "").decode()
            if (!key.isPartnerIdKey()) return@firstNotNullOfOrNull null
            part.substringAfter("=", "").decode().takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun String.isPartnerIdKey(): Boolean =
        equals("partnerid", ignoreCase = true) ||
            equals("partner_id", ignoreCase = true) ||
            equals("providerid", ignoreCase = true)

    private fun String.decode(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
