package ru.myit.vlevpn.quick

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object QuickCommandParser {
    const val CANONICAL_SCHEME = "vlevpn"
    private val supportedSchemes = listOf(CANONICAL_SCHEME, "vle")
    private val packageRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    private val splitRegex = Regex("[,\\n\\r\\t ]+")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String?): QuickCommand? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        supportedSchemes.forEach { scheme ->
            commandWithoutPayload(value, scheme, "connect")?.let { return QuickCommand.Connect }
            commandWithoutPayload(value, scheme, "open")?.let { return QuickCommand.Connect }
            commandWithoutPayload(value, scheme, "disconnect")?.let { return QuickCommand.Disconnect }
            commandWithoutPayload(value, scheme, "close")?.let { return QuickCommand.Disconnect }
            commandWithoutPayload(value, scheme, "toggle")?.let { return QuickCommand.Toggle }

            payloadAfter(value, scheme, "import")?.let { encoded ->
                return decodeBase64Utf8(encoded)?.let { QuickCommand.Import(it) }
            }
            payloadAfter(value, scheme, "add")?.let { payload ->
                return QuickCommand.Add(percentDecode(payload))
            }
            routingPayloadAfter(value, scheme, "onadd")?.let { encoded ->
                return decodeBase64Utf8(encoded)
                    ?.let(::parseRoutingPackages)
                    ?.let { QuickCommand.RoutingAdd(packages = it, apply = true) }
            }
            routingPayloadAfter(value, scheme, "add")?.let { encoded ->
                return decodeBase64Utf8(encoded)
                    ?.let(::parseRoutingPackages)
                    ?.let { QuickCommand.RoutingAdd(packages = it, apply = false) }
            }
        }

        return null
    }

    fun encodeBase64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun commandWithoutPayload(raw: String, scheme: String, command: String): Unit? {
        val lower = raw.lowercase()
        val prefix = "$scheme://$command"
        return Unit.takeIf {
            lower == prefix || lower == "$prefix/"
        }
    }

    private fun payloadAfter(raw: String, scheme: String, command: String): String? {
        val prefix = "$scheme://$command/"
        return raw.substringAfterPrefix(prefix)
            ?.takeIf { it.isNotBlank() }
    }

    private fun routingPayloadAfter(raw: String, scheme: String, command: String): String? {
        val prefix = "$scheme://routing/$command/"
        return raw.substringAfterPrefix(prefix)
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.substringAfterPrefix(prefix: String): String? =
        takeIf { startsWith(prefix, ignoreCase = true) }?.substring(prefix.length)

    private fun decodeBase64Utf8(value: String): String? {
        val decoded = percentDecode(value.trim())
        val padded = decoded.padEnd(decoded.length + ((4 - decoded.length % 4) % 4), '=')
        val decoders = listOf(Base64.getUrlDecoder(), Base64.getDecoder())
        return decoders.firstNotNullOfOrNull { decoder ->
            runCatching {
                String(decoder.decode(padded), StandardCharsets.UTF_8)
            }.getOrNull()
        }
    }

    private fun parseRoutingPackages(payload: String): Set<String> {
        val fromJson = runCatching {
            val packages = mutableSetOf<String>()
            collectPackageNames(json.parseToJsonElement(payload), packages)
            packages
        }.getOrNull()

        return (fromJson?.takeIf { it.isNotEmpty() } ?: payload
            .split(splitRegex)
            .map { it.trim().trim('"', '\'') }
            .filterTo(mutableSetOf()) { it.matches(packageRegex) })
    }

    private fun collectPackageNames(element: JsonElement, out: MutableSet<String>) {
        when (element) {
            is JsonArray -> element.forEach { collectPackageNames(it, out) }
            is JsonObject -> element.forEach { (key, value) ->
                val primitive = value as? JsonPrimitive
                if (key.equals("package", ignoreCase = true) || key.equals("packageName", ignoreCase = true)) {
                    primitive?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.matches(packageRegex) }
                        ?.let(out::add)
                }
                collectPackageNames(value, out)
            }
            is JsonPrimitive -> element.contentOrNull
                ?.takeIf { it.matches(packageRegex) }
                ?.let(out::add)
        }
    }

    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val out = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    index += 3
                    continue
                }
            }
            val bytes = char.toString().toByteArray(StandardCharsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            index += 1
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }
}
