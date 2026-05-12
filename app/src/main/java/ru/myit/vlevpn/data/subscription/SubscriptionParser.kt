package ru.myit.vlevpn.data.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okio.ByteString.Companion.decodeBase64
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile

@Singleton
class SubscriptionParser @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        input: String,
        sourceHint: String = "",
        metadata: SubscriptionMetadata = SubscriptionMetadata(),
        extraItems: List<String> = emptyList(),
    ): ParsedSubscription {
        val normalized = input.trim()
        val normalizedExtraItems = extraItems
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (normalized.isBlank() && normalizedExtraItems.isEmpty()) return ParsedSubscription(emptyList())

        val expanded = buildList {
            if (normalized.isNotBlank()) addAll(expandInput(normalized))
            addAll(normalizedExtraItems)
        }
        val providerId = metadata.providerId.ifBlank {
            SubscriptionProviderId.fromSubscriptionBody(normalized)
        }
        val providerDomainHash = metadata.providerDomainHash.ifBlank {
            SubscriptionProviderId.sourceDomainHash(sourceHint, normalized)
        }
        val providerMetadata = metadata.copy(
            providerId = providerId,
            providerDomainHash = providerDomainHash,
        )
        val sourceInput = normalized.ifBlank { normalizedExtraItems.joinToString("\n") }
        val subscriptionSource = subscriptionSource(sourceInput, sourceHint, expanded.size, providerMetadata)
        val profiles = mutableListOf<ServerProfile>()
        val skipped = mutableListOf<String>()

        expanded.forEach { item ->
            runCatching { parseSingle(item, sourceHint) }
                .onSuccess { parsed ->
                    if (parsed == null) {
                        skipped += item.take(80)
                    } else {
                        profiles += parsed.withSubscriptionSource(subscriptionSource, profiles.size)
                    }
                }
                .onFailure { skipped += item.take(80) }
        }

        return ParsedSubscription(
            profiles = profiles.distinctBy { it.id.value },
            skippedItems = skipped,
            providerId = providerId,
            providerDomainHash = providerDomainHash,
        )
    }

    private fun expandInput(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.startsWith("{")) return listOf(trimmed)
        if (trimmed.startsWith("[")) {
            val element = json.parseToJsonElement(trimmed)
            if (element is JsonArray && element.all { it is JsonPrimitive && it.isString }) {
                return element.map { it.jsonPrimitive.content.trim() }.filter { it.isNotBlank() }
            }
            return listOf(trimmed)
        }

        val directLines = trimmed.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
        if (directLines.any { it.contains("://") }) return directLines

        val decoded = decodeBase64Text(trimmed)
        if (decoded != null && decoded.contains("://")) {
            return decoded.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
        }
        return directLines
    }

    private fun parseSingle(input: String, sourceHint: String): ServerProfile? {
        val item = input.trim()
        return when {
            item.startsWith("{") -> parseXrayJson(item, sourceHint)
            item.startsWith("vless://", ignoreCase = true) -> parseVless(item)
            item.startsWith("vmess://", ignoreCase = true) -> parseVmess(item)
            item.startsWith("trojan://", ignoreCase = true) -> parseTrojan(item)
            item.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(item)
            item.startsWith("olcrtc://", ignoreCase = true) -> parseOlcRtc(item)
            else -> null
        }
    }

    private fun parseXrayJson(input: String, sourceHint: String): ServerProfile {
        val element = json.parseToJsonElement(input)
        require(element is JsonObject) { "Xray JSON must be an object" }
        val looksLikeXray = "inbounds" in element || "outbounds" in element || "routing" in element
        require(looksLikeXray) { "Unsupported JSON subscription format" }
        return baseProfile(
            raw = input,
            name = sourceHint.takeIf { it.startsWith("https://") }?.substringAfter("://")?.take(48)
                ?: "Custom Xray JSON",
            protocol = ProxyProtocol.CUSTOM_JSON,
            host = "",
            port = 0,
        ).copy(customJson = input)
    }

    private fun parseVless(input: String): ServerProfile {
        val uri = URI(input)
        val query = parseQuery(uri.rawQuery)
        return baseProfile(
            raw = input,
            name = decode(uri.rawFragment).ifBlank { uri.host ?: "VLESS" },
            protocol = ProxyProtocol.VLESS,
            host = uri.host.orEmpty(),
            port = uri.port,
        ).copy(
            credential = decode(uri.rawUserInfo),
            transport = query["type"].orEmpty().ifBlank { "tcp" },
            security = query["security"].orEmpty().ifBlank { "none" },
            sni = query["sni"].orEmpty().ifBlank { query["host"].orEmpty() },
            path = query["path"].orEmpty().ifBlank { query["serviceName"].orEmpty() },
            flow = query["flow"].orEmpty(),
            fingerprint = query["fp"].orEmpty(),
            publicKey = query["pbk"].orEmpty(),
            shortId = query["sid"].orEmpty(),
            spiderX = query["spx"].orEmpty(),
            networkMode = query["mode"].orEmpty(),
            headerHost = query["host"].orEmpty(),
        )
    }

    private fun parseTrojan(input: String): ServerProfile {
        val uri = URI(input)
        val query = parseQuery(uri.rawQuery)
        return baseProfile(
            raw = input,
            name = decode(uri.rawFragment).ifBlank { uri.host ?: "Trojan" },
            protocol = ProxyProtocol.TROJAN,
            host = uri.host.orEmpty(),
            port = uri.port,
        ).copy(
            password = decode(uri.rawUserInfo),
            transport = query["type"].orEmpty().ifBlank { "tcp" },
            security = query["security"].orEmpty().ifBlank { "tls" },
            sni = query["sni"].orEmpty().ifBlank { query["host"].orEmpty() },
            path = query["path"].orEmpty().ifBlank { query["serviceName"].orEmpty() },
            fingerprint = query["fp"].orEmpty(),
            publicKey = query["pbk"].orEmpty(),
            shortId = query["sid"].orEmpty(),
            spiderX = query["spx"].orEmpty(),
            networkMode = query["mode"].orEmpty(),
            headerHost = query["host"].orEmpty(),
        )
    }

    private fun parseVmess(input: String): ServerProfile {
        val payload = input.substringAfter("vmess://")
        val decoded = decodeBase64Text(payload) ?: error("Invalid VMess payload")
        val obj = json.parseToJsonElement(decoded).jsonObject
        val rawSecurity = obj.string("tls").ifBlank { obj.string("security") }
        return baseProfile(
            raw = input,
            name = obj.string("ps").ifBlank { obj.string("add").ifBlank { "VMess" } },
            protocol = ProxyProtocol.VMESS,
            host = obj.string("add"),
            port = obj.string("port").toIntOrNull() ?: obj["port"]?.jsonPrimitive?.intOrNull ?: 0,
        ).copy(
            credential = obj.string("id"),
            transport = obj.string("net").ifBlank { "tcp" },
            security = when {
                rawSecurity.equals("tls", ignoreCase = true) -> "tls"
                obj["tls"]?.jsonPrimitive?.booleanOrNull == true -> "tls"
                else -> "none"
            },
            sni = obj.string("sni").ifBlank { obj.string("host") },
            path = obj.string("path"),
            headerHost = obj.string("host"),
        )
    }

    private fun parseShadowsocks(input: String): ServerProfile {
        val withoutScheme = input.substringAfter("ss://")
        val parts = withoutScheme.split("#", limit = 2)
        val main = parts[0]
        val name = parts.getOrNull(1)?.let(::decode).orEmpty()

        val decodedMain = if ("@" in main) {
            val userInfo = main.substringBefore("@")
            val hostPort = main.substringAfter("@")
            val methodPassword = decodeBase64Text(userInfo) ?: decode(userInfo)
            "$methodPassword@$hostPort"
        } else {
            decodeBase64Text(main) ?: decode(main)
        }

        val methodPassword = decodedMain.substringBefore("@")
        val hostPort = decodedMain.substringAfter("@")
        val method = methodPassword.substringBefore(":")
        val password = methodPassword.substringAfter(":", "")
        val host = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 0

        return baseProfile(
            raw = input,
            name = name.ifBlank { host.ifBlank { "Shadowsocks" } },
            protocol = ProxyProtocol.SHADOWSOCKS,
            host = host,
            port = port,
        ).copy(
            method = method,
            password = password,
            security = "none",
            transport = "tcp",
        )
    }

    private fun parseOlcRtc(input: String): ServerProfile {
        val payload = input.substringAfter("olcrtc://", "")
        val transportMarker = payload.indexOf('?')
        val roomMarker = payload.indexOf('@', startIndex = transportMarker + 1)
        val keyMarker = payload.indexOf('#', startIndex = roomMarker + 1)
        require(transportMarker > 0 && roomMarker > transportMarker && keyMarker > roomMarker) {
            "Invalid olcRTC URI"
        }

        val clientMarker = payload.indexOf('%', startIndex = keyMarker + 1).takeIf { it >= 0 }
        val mimoMarker = payload.indexOf('$', startIndex = keyMarker + 1).takeIf { it >= 0 }
        val keyEnd = listOfNotNull(clientMarker, mimoMarker).minOrNull() ?: payload.length
        val clientEnd = mimoMarker ?: payload.length

        val carrier = decode(payload.substring(0, transportMarker).trim())
        val transportSpec = payload.substring(transportMarker + 1, roomMarker).trim()
        val transportName = decode(transportSpec.substringBefore("<").trim())
        val transportPayload = transportSpec
            .substringAfter("<", "")
            .substringBeforeLast(">", "")
            .takeIf { "<" in transportSpec && transportSpec.endsWith(">") }
            .orEmpty()
        val transportOptions = parseQuery(transportPayload)
        val roomId = decode(payload.substring(roomMarker + 1, keyMarker).trim())
        val encryptionKey = decode(payload.substring(keyMarker + 1, keyEnd).trim())
        val clientId = clientMarker
            ?.let { decode(payload.substring(it + 1, clientEnd).trim()) }
            .orEmpty()
        val mimo = mimoMarker
            ?.let { decode(payload.substring(it + 1).trim()) }
            .orEmpty()

        require(carrier.isNotBlank()) { "olcRTC carrier is required" }
        require(transportName.isNotBlank()) { "olcRTC transport is required" }
        require(roomId.isNotBlank()) { "olcRTC room ID is required" }
        require(encryptionKey.isNotBlank()) { "olcRTC encryption key is required" }
        require(clientId.isNotBlank()) { "olcRTC client ID is required" }

        val payloadJson = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("rawUri", input)
                put("carrier", carrier)
                put("transport", transportName)
                put("roomId", roomId)
                put("encryptionKey", encryptionKey)
                put("clientId", clientId)
                put("mimo", mimo)
                putJsonObject("transportOptions") {
                    transportOptions.forEach { (key, value) -> put(key, value) }
                }
            },
        )

        return baseProfile(
            raw = input,
            name = mimo.ifBlank { "$carrier / $transportName" },
            protocol = ProxyProtocol.OLCRTC,
            host = carrier,
            port = 0,
        ).copy(
            transport = transportName,
            path = roomId,
            credential = encryptionKey,
            password = clientId,
            security = "olcrtc",
            protocolPayloadJson = payloadJson,
        )
    }

    private fun baseProfile(
        raw: String,
        name: String,
        protocol: ProxyProtocol,
        host: String,
        port: Int,
    ): ServerProfile {
        val now = System.currentTimeMillis()
        return ServerProfile(
            id = ServerId("import-${sha256(raw).take(16)}"),
            name = name.ifBlank { protocol.displayName },
            protocol = protocol,
            host = host,
            port = port.coerceAtLeast(0),
            credential = "",
            password = "",
            method = "",
            transport = "tcp",
            security = "none",
            sni = "",
            path = "",
            customJson = "",
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    private fun ServerProfile.withSubscriptionSource(
        source: SubscriptionSource?,
        position: Int,
    ): ServerProfile =
        if (source == null) {
            copy(subscriptionPosition = position)
        } else {
            copy(
                subscriptionId = source.id,
                subscriptionName = source.name,
                subscriptionImportedAtMillis = source.importedAtMillis,
                subscriptionPosition = position,
                subscriptionUpdateIntervalHours = source.metadata.updateIntervalHours,
                subscriptionUploadBytes = source.metadata.uploadBytes,
                subscriptionDownloadBytes = source.metadata.downloadBytes,
                subscriptionTotalBytes = source.metadata.totalBytes,
                subscriptionExpireAtMillis = source.metadata.expireAtMillis,
                subscriptionSupportUrl = source.metadata.supportUrl,
                subscriptionWebPageUrl = source.metadata.webPageUrl,
                subscriptionSourceUrl = source.sourceUrl,
                subscriptionAnnounce = source.metadata.announce,
                subscriptionAutoUpdateOnLaunchEnabled = source.metadata.updateAlways,
                subscriptionProviderId = source.metadata.providerId,
                subscriptionProviderDomainHash = source.metadata.providerDomainHash,
            )
        }

    private fun subscriptionSource(
        input: String,
        sourceHint: String,
        itemCount: Int,
        metadata: SubscriptionMetadata,
    ): SubscriptionSource? {
        val sourceUrl = sourceHint.takeIf { it.startsWith("https://", ignoreCase = true) }
        if (sourceUrl == null && itemCount <= 1) return null

        val stableSource = sourceUrl ?: input
        val fallbackName = sourceUrl?.let { url ->
            runCatching { URI(url).host }.getOrNull()?.ifBlank { null }
        } ?: "Imported subscription"

        return SubscriptionSource(
            id = "sub-${sha256(stableSource).take(16)}",
            name = metadata.title.ifBlank { fallbackName },
            sourceUrl = sourceUrl.orEmpty(),
            importedAtMillis = System.currentTimeMillis(),
            metadata = metadata,
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { part ->
            val key = part.substringBefore("=", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = part.substringAfter("=", "")
            decode(key) to decode(value)
        }.toMap()
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private fun decodeBase64Text(value: String): String? {
        val compact = value.trim().replace("\\s".toRegex(), "")
        val candidates = listOf(
            compact,
            compact.padEnd(compact.length + ((4 - compact.length % 4) % 4), '='),
            compact.replace('-', '+').replace('_', '/'),
        ).distinct()
        return candidates.firstNotNullOfOrNull { candidate ->
            candidate.decodeBase64()?.utf8()
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class SubscriptionSource(
        val id: String,
        val name: String,
        val sourceUrl: String,
        val importedAtMillis: Long,
        val metadata: SubscriptionMetadata,
    )
}
