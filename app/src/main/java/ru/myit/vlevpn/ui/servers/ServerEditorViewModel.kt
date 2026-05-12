package ru.myit.vlevpn.ui.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.myit.vlevpn.data.server.ServerDraft
import ru.myit.vlevpn.data.server.ServerProfileValidator
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.domain.repository.ServerRepository

data class ServerEditorUiState(
    val draft: ServerDraft = emptyDraft(),
    val errors: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
) {
    companion object {
        fun emptyDraft(): ServerDraft = ServerDraft(
            id = null,
            name = "",
            protocol = ProxyProtocol.VLESS,
            host = "",
            port = "443",
            credential = "",
            password = "",
            method = "aes-128-gcm",
            transport = "tcp",
            security = "tls",
            sni = "",
            path = "",
            flow = "",
            fingerprint = "",
            publicKey = "",
            shortId = "",
            spiderX = "",
            networkMode = "",
            headerHost = "",
            protocolPayloadJson = "",
            customJson = "",
        )
    }
}

@HiltViewModel
class ServerEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerRepository,
    private val validator: ServerProfileValidator,
) : ViewModel() {
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    private val draft = MutableStateFlow(ServerEditorUiState.emptyDraft())
    private val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val saved = MutableStateFlow(false)
    private var existingCreatedAt: Long? = null
    private var existingServer: ServerProfile? = null

    val uiState: StateFlow<ServerEditorUiState> = combine(draft, errors, saved) { draft, errors, saved ->
        ServerEditorUiState(draft = draft, errors = errors, saved = saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerEditorUiState())

    init {
        if (serverId != "new") {
            viewModelScope.launch {
                repository.getServer(ServerId(serverId))?.let { server ->
                    existingServer = server
                    existingCreatedAt = server.createdAtMillis
                    draft.value = server.toDraft()
                }
            }
        }
    }

    fun update(transform: (ServerDraft) -> ServerDraft) {
        draft.update(transform)
        errors.value = emptyMap()
    }

    fun importCustomJson(json: String) {
        update {
            it.copy(
                protocol = ProxyProtocol.CUSTOM_JSON,
                customJson = json,
                name = it.name.ifBlank { "Custom Xray profile" },
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val current = draft.value
            val validation = validator.validate(current)
            if (!validation.isValid) {
                errors.value = validation.errors
                return@launch
            }
            val now = System.currentTimeMillis()
            val profile = ServerProfile(
                id = ServerId(current.id ?: UUID.randomUUID().toString()),
                name = current.name.trim(),
                protocol = current.protocol,
                host = current.host.trim(),
                port = current.port.toIntOrNull() ?: 0,
                credential = current.credential.trim(),
                password = current.password,
                method = current.method.trim(),
                transport = current.transport.trim().ifBlank { "tcp" },
                security = current.security.trim().ifBlank { "none" },
                sni = current.sni.trim(),
                path = current.path.trim(),
                flow = current.flow.trim(),
                fingerprint = current.fingerprint.trim(),
                publicKey = current.publicKey.trim(),
                shortId = current.shortId.trim(),
                spiderX = current.spiderX.trim(),
                networkMode = current.networkMode.trim(),
                headerHost = current.headerHost.trim(),
                subscriptionId = existingServer?.subscriptionId.orEmpty(),
                subscriptionName = existingServer?.subscriptionName.orEmpty(),
                subscriptionImportedAtMillis = existingServer?.subscriptionImportedAtMillis ?: 0L,
                subscriptionActivatedAtMillis = existingServer?.subscriptionActivatedAtMillis ?: 0L,
                subscriptionPosition = existingServer?.subscriptionPosition ?: 0,
                subscriptionUpdateIntervalHours = existingServer?.subscriptionUpdateIntervalHours ?: 0,
                subscriptionUploadBytes = existingServer?.subscriptionUploadBytes ?: 0L,
                subscriptionDownloadBytes = existingServer?.subscriptionDownloadBytes ?: 0L,
                subscriptionTotalBytes = existingServer?.subscriptionTotalBytes ?: 0L,
                subscriptionExpireAtMillis = existingServer?.subscriptionExpireAtMillis ?: 0L,
                subscriptionSupportUrl = existingServer?.subscriptionSupportUrl.orEmpty(),
                subscriptionWebPageUrl = existingServer?.subscriptionWebPageUrl.orEmpty(),
                subscriptionSourceUrl = existingServer?.subscriptionSourceUrl.orEmpty(),
                subscriptionAnnounce = existingServer?.subscriptionAnnounce.orEmpty(),
                subscriptionAutoUpdateOnLaunchEnabled = existingServer?.subscriptionAutoUpdateOnLaunchEnabled ?: false,
                subscriptionProviderId = existingServer?.subscriptionProviderId.orEmpty(),
                subscriptionProviderDomainHash = existingServer?.subscriptionProviderDomainHash.orEmpty(),
                protocolPayloadJson = current.protocolPayloadJson,
                customJson = current.customJson,
                createdAtMillis = existingCreatedAt ?: now,
                updatedAtMillis = now,
            )
            repository.upsert(profile)
            saved.value = true
        }
    }

    private fun ServerProfile.toDraft(): ServerDraft = ServerDraft(
        id = id.value,
        name = name,
        protocol = protocol,
        host = host,
        port = port.toString(),
        credential = credential,
        password = password,
        method = method,
        transport = transport,
        security = security,
        sni = sni,
        path = path,
        flow = flow,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        spiderX = spiderX,
        networkMode = networkMode,
        headerHost = headerHost,
        protocolPayloadJson = protocolPayloadJson,
        customJson = customJson,
    )
}
