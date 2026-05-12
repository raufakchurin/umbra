package ru.myit.vlevpn.ui.servers

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.myit.vlevpn.data.server.ServerDraft
import ru.myit.vlevpn.domain.model.ProxyProtocol

@Composable
fun ServerEditorRoute(
    onDone: () -> Unit,
    viewModel: ServerEditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            viewModel.importCustomJson(text)
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    BackHandler(onBack = onDone)

    ServerEditorScreen(
        state = state,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onCancel = onDone,
        onImportFile = { filePicker.launch(arrayOf("application/json", "text/*", "*/*")) },
    )
}

@Composable
private fun ServerEditorScreen(
    state: ServerEditorUiState,
    onUpdate: ((ServerDraft) -> ServerDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onImportFile: () -> Unit,
) {
    val draft = state.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false,
                        ambientColor = Color(0xFF071312).copy(alpha = 0.06f),
                        spotColor = Color(0xFF071312).copy(alpha = 0.08f),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Редактор сервера",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onSave,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false,
                        ambientColor = Color(0xFF071312).copy(alpha = 0.06f),
                        spotColor = Color(0xFF071312).copy(alpha = 0.08f),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> onUpdate { it.copy(name = value) } },
            label = { Text("Name") },
            isError = "name" in state.errors,
            supportingText = { state.errors["name"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        ProtocolPicker(
            selected = draft.protocol,
            onSelect = { protocol -> onUpdate { it.copy(protocol = protocol) } },
        )

        if (draft.protocol == ProxyProtocol.CUSTOM_JSON) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onImportFile) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import JSON")
                    Text("Import")
                }
            }
            OutlinedTextField(
                value = draft.customJson,
                onValueChange = { value -> onUpdate { it.copy(customJson = value) } },
                label = { Text("Xray JSON") },
                minLines = 10,
                isError = "customJson" in state.errors,
                supportingText = { state.errors["customJson"]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (draft.protocol == ProxyProtocol.OLCRTC) {
            OlcRtcFields(draft = draft, errors = state.errors, onUpdate = onUpdate)
        } else {
            ManualFields(draft = draft, errors = state.errors, onUpdate = onUpdate)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(27.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
                Text("Save")
            }
        }
    }
}

@Composable
private fun OlcRtcFields(
    draft: ServerDraft,
    errors: Map<String, String>,
    onUpdate: ((ServerDraft) -> ServerDraft) -> Unit,
) {
    Text(
        "olcRTC uses its own runtime path and is started separately from Xray.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = draft.host,
        onValueChange = { value -> onUpdate { it.copy(host = value.lowercase()) } },
        label = { Text("Carrier: wbstream, telemost, jazz") },
        isError = "host" in errors,
        supportingText = { errors["host"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.transport,
        onValueChange = { value -> onUpdate { it.copy(transport = value.lowercase()) } },
        label = { Text("Transport: datachannel, vp8channel, seichannel") },
        isError = "transport" in errors,
        supportingText = { errors["transport"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.path,
        onValueChange = { value -> onUpdate { it.copy(path = value) } },
        label = { Text("Room ID") },
        isError = "path" in errors,
        supportingText = { errors["path"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.password,
        onValueChange = { value -> onUpdate { it.copy(password = value) } },
        label = { Text("Client ID") },
        isError = "password" in errors,
        supportingText = { errors["password"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.credential,
        onValueChange = { value -> onUpdate { it.copy(credential = value) } },
        label = { Text("Encryption key") },
        isError = "credential" in errors,
        supportingText = { errors["credential"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ManualFields(
    draft: ServerDraft,
    errors: Map<String, String>,
    onUpdate: ((ServerDraft) -> ServerDraft) -> Unit,
) {
    OutlinedTextField(
        value = draft.host,
        onValueChange = { value -> onUpdate { it.copy(host = value) } },
        label = { Text("Host") },
        isError = "host" in errors,
        supportingText = { errors["host"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.port,
        onValueChange = { value -> onUpdate { it.copy(port = value.filter(Char::isDigit).take(5)) } },
        label = { Text("Port") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = "port" in errors,
        supportingText = { errors["port"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.protocol == ProxyProtocol.VLESS || draft.protocol == ProxyProtocol.VMESS) {
        OutlinedTextField(
            value = draft.credential,
            onValueChange = { value -> onUpdate { it.copy(credential = value) } },
            label = { Text("UUID") },
            isError = "credential" in errors,
            supportingText = { errors["credential"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = draft.password,
            onValueChange = { value -> onUpdate { it.copy(password = value) } },
            label = { Text("Password") },
            isError = "password" in errors,
            supportingText = { errors["password"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft.protocol == ProxyProtocol.SHADOWSOCKS) {
        OutlinedTextField(
            value = draft.method,
            onValueChange = { value -> onUpdate { it.copy(method = value) } },
            label = { Text("Method") },
            isError = "method" in errors,
            supportingText = { errors["method"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = draft.transport,
        onValueChange = { value -> onUpdate { it.copy(transport = value.lowercase()) } },
        label = { Text("Transport: tcp, ws, grpc") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.security,
        onValueChange = { value -> onUpdate { it.copy(security = value.lowercase()) } },
        label = { Text("Security: none, tls, reality") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.sni,
        onValueChange = { value -> onUpdate { it.copy(sni = value) } },
        label = { Text("SNI") },
        isError = "sni" in errors,
        supportingText = { errors["sni"]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.path,
        onValueChange = { value -> onUpdate { it.copy(path = value) } },
        label = { Text("Path / gRPC service") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.protocol == ProxyProtocol.VLESS) {
        OutlinedTextField(
            value = draft.flow,
            onValueChange = { value -> onUpdate { it.copy(flow = value) } },
            label = { Text("VLESS flow") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft.security == "reality") {
        OutlinedTextField(
            value = draft.fingerprint,
            onValueChange = { value -> onUpdate { it.copy(fingerprint = value) } },
            label = { Text("Fingerprint") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.publicKey,
            onValueChange = { value -> onUpdate { it.copy(publicKey = value) } },
            label = { Text("REALITY public key") },
            isError = "publicKey" in errors,
            supportingText = { errors["publicKey"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.shortId,
            onValueChange = { value -> onUpdate { it.copy(shortId = value) } },
            label = { Text("REALITY short ID") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.spiderX,
            onValueChange = { value -> onUpdate { it.copy(spiderX = value) } },
            label = { Text("REALITY spiderX") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = draft.networkMode,
        onValueChange = { value -> onUpdate { it.copy(networkMode = value.lowercase()) } },
        label = { Text("Network mode") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.headerHost,
        onValueChange = { value -> onUpdate { it.copy(headerHost = value) } },
        label = { Text("Header Host") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProtocolPicker(
    selected: ProxyProtocol,
    onSelect: (ProxyProtocol) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "ПРОТОКОЛ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProxyProtocol.entries.forEach { protocol ->
                val selectedProtocol = protocol == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selectedProtocol) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (selectedProtocol) MaterialTheme.colorScheme.primary.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onSelect(protocol) }
                        .padding(horizontal = 17.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        protocol.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedProtocol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
