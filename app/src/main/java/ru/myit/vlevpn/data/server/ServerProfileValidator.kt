package ru.myit.vlevpn.data.server

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import ru.myit.vlevpn.domain.model.ProxyProtocol

data class ServerDraft(
    val id: String?,
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: String,
    val credential: String,
    val password: String,
    val method: String,
    val transport: String,
    val security: String,
    val sni: String,
    val path: String,
    val flow: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val networkMode: String,
    val headerHost: String,
    val protocolPayloadJson: String,
    val customJson: String,
)

data class ValidationResult(
    val errors: Map<String, String>,
) {
    val isValid: Boolean = errors.isEmpty()
}

@Singleton
class ServerProfileValidator @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(draft: ServerDraft): ValidationResult {
        val errors = mutableMapOf<String, String>()
        if (draft.name.isBlank()) errors["name"] = "Name is required"

        if (draft.protocol == ProxyProtocol.CUSTOM_JSON) {
            if (draft.customJson.isBlank()) {
                errors["customJson"] = "Xray JSON is required"
            } else {
                runCatching { json.parseToJsonElement(draft.customJson) }
                    .onFailure { errors["customJson"] = "Invalid JSON: ${it.message.orEmpty()}" }
            }
            return ValidationResult(errors)
        }

        if (draft.protocol == ProxyProtocol.OLCRTC) {
            if (draft.host.isBlank()) errors["host"] = "Carrier is required"
            if (draft.transport.isBlank()) errors["transport"] = "Transport is required"
            if (draft.path.isBlank()) errors["path"] = "Room ID is required"
            if (draft.credential.isBlank()) errors["credential"] = "Encryption key is required"
            if (draft.password.isBlank()) errors["password"] = "Client ID is required"
            return ValidationResult(errors)
        }

        if (draft.host.isBlank()) errors["host"] = "Host is required"
        val port = draft.port.toIntOrNull()
        if (port == null || port !in 1..65535) errors["port"] = "Port must be 1..65535"
        if (draft.security.equals("reality", ignoreCase = true)) {
            if (draft.publicKey.isBlank()) errors["publicKey"] = "REALITY public key is required"
            if (draft.sni.isBlank()) errors["sni"] = "REALITY SNI is required"
        }
        when (draft.protocol) {
            ProxyProtocol.VLESS,
            ProxyProtocol.VMESS,
            -> if (draft.credential.isBlank()) errors["credential"] = "UUID is required"
            ProxyProtocol.TROJAN -> if ((draft.password.ifBlank { draft.credential }).isBlank()) {
                errors["password"] = "Password is required"
            }
            ProxyProtocol.SHADOWSOCKS -> {
                if ((draft.password.ifBlank { draft.credential }).isBlank()) {
                    errors["password"] = "Password is required"
                }
                if (draft.method.isBlank()) errors["method"] = "Method is required"
            }
            ProxyProtocol.CUSTOM_JSON -> Unit
            ProxyProtocol.OLCRTC -> Unit
        }
        return ValidationResult(errors)
    }
}
