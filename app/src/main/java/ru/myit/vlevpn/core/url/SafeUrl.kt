package ru.myit.vlevpn.core.url

import android.net.Uri

fun String?.toSafeActionUri(): Uri? {
    val value = this?.trim().orEmpty()
    if (value.isBlank() || value.length > MAX_ACTION_URL_LENGTH) return null
    if (value.any { it == '\r' || it == '\n' || it == '\t' }) return null

    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    if (uri.scheme.isNullOrBlank()) return null
    return uri
}

private const val MAX_ACTION_URL_LENGTH = 2048
