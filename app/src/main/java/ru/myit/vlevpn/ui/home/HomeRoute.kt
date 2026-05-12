package ru.myit.vlevpn.ui.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.R
import ru.myit.vlevpn.runtime.RuntimeState
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.ui.i18n.LocalAppLanguage
import ru.myit.vlevpn.ui.i18n.localized
import ru.myit.vlevpn.ui.servers.ServerProfilesContent
import ru.myit.vlevpn.ui.servers.ServerPingUiState
import ru.myit.vlevpn.ui.servers.ServersViewModel
import ru.myit.vlevpn.ui.servers.readClipboardText
import ru.myit.vlevpn.ui.shared.TranslucentSnackbarHost
import ru.myit.vlevpn.ui.shared.formatBytes

@Composable
fun HomeRoute(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    serversViewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serversState by serversViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingVpnAction by remember { mutableStateOf<PendingVpnAction?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pendingAction = pendingVpnAction
        pendingVpnAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            when (pendingAction) {
                is PendingVpnAction.SelectServer -> serversViewModel.selectAndConnect(pendingAction.serverId)
                PendingVpnAction.Connect,
                null,
                -> viewModel.connect()
            }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(serversViewModel) {
        serversViewModel.events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(event.message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(event.message)
        }
    }

    Scaffold(
        snackbarHost = { TranslucentSnackbarHost(snackbarHostState) },
    ) { padding ->
        HomeScreen(
            state = state,
            serversState = serversState,
            onConnectToggle = {
                if (state.runtimeState is RuntimeState.Running) {
                    viewModel.disconnect()
                } else {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent != null) {
                        pendingVpnAction = PendingVpnAction.Connect
                        viewModel.markPreparingPermission()
                        permissionLauncher.launch(prepareIntent)
                    } else {
                        viewModel.connect()
                    }
                }
            },
            onAddServer = onAddServer,
            onEditServer = onEditServer,
            onSelectServer = { serverId ->
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    pendingVpnAction = PendingVpnAction.SelectServer(serverId)
                    viewModel.markPreparingPermission()
                    permissionLauncher.launch(prepareIntent)
                } else {
                    serversViewModel.selectAndConnect(serverId)
                }
            },
            onDeleteServer = serversViewModel::delete,
            onDeleteGroup = serversViewModel::deleteGroup,
            onRefreshGroup = serversViewModel::refreshGroup,
            onPingGroup = serversViewModel::pingGroup,
            onToggleGroupAutoUpdateOnLaunch = serversViewModel::setGroupAutoUpdateOnLaunch,
            onImportClipboard = {
                serversViewModel.importFromClipboard(context.readClipboardText())
            },
            modifier = Modifier.padding(padding),
        )
    }
}

private sealed interface PendingVpnAction {
    data object Connect : PendingVpnAction
    data class SelectServer(val serverId: ServerId) : PendingVpnAction
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    serversState: ru.myit.vlevpn.ui.servers.ServersUiState,
    onConnectToggle: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onSelectServer: (ru.myit.vlevpn.domain.model.ServerId) -> Unit,
    onDeleteServer: (ru.myit.vlevpn.domain.model.ServerId) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onRefreshGroup: (String) -> Unit,
    onPingGroup: (String) -> Unit,
    onToggleGroupAutoUpdateOnLaunch: (String, Boolean) -> Unit,
    onImportClipboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = state.runtimeState is RuntimeState.Running
    val language = state.settings.appLanguage
    val isBusy = state.runtimeState is RuntimeState.PreparingVpnPermission ||
        state.runtimeState is RuntimeState.StartingForeground ||
        state.runtimeState is RuntimeState.BuildingConfig ||
        state.runtimeState is RuntimeState.EstablishingVpn ||
        state.runtimeState is RuntimeState.StartingNativeCore ||
        state.runtimeState is RuntimeState.VerifyingConnection ||
        state.runtimeState is RuntimeState.Stopping
    val buttonText = when {
        isRunning -> localized(language, "Стоп", "Stop")
        isBusy -> localized(language, "Ждите", "Wait")
        else -> localized(language, "Старт", "Start")
    }
    val buttonDescription = when {
        isRunning -> localized(language, "Отключить", "Disconnect")
        isBusy -> localized(language, "Подключение", "Connecting")
        else -> localized(language, "Подключить", "Connect")
    }
    val selectedPing = serversState.selectedServerId?.let { serversState.pingResults[it] }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 94.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                )
            }
            AddProfileMenu(
                importing = serversState.importing,
                onAddServer = onAddServer,
                onImportClipboard = onImportClipboard,
            )
        }
        CompactConnectionCard(
            selectedServerLabel = state.selectedServer?.let { server ->
                val protocolName = if (server.subscriptionId.isNotBlank() && server.isOlcRtc) {
                    "VLESS"
                } else {
                    server.protocol.displayName
                }
                "${server.name} · $protocolName"
            } ?: localized(language, "Сервер не выбран", "No server selected"),
            statusLabel = state.runtimeState.connectionChipText(language),
            statusIsError = state.runtimeState is RuntimeState.Error,
            latencyLabel = selectedPing.connectionLatencyLabel(language),
            uplinkLabel = "↑ ${formatBytes(state.stats.uplinkBytes)}",
            downlinkLabel = "↓ ${formatBytes(state.stats.downlinkBytes)}",
            powerEnabled = isRunning || (state.selectedServer != null && !isBusy),
            running = isRunning,
            busy = isBusy,
            buttonText = buttonText,
            buttonDescription = buttonDescription,
            onConnectToggle = onConnectToggle,
            modifier = Modifier.fillMaxWidth(),
        )

        ServerProfilesContent(
            state = serversState,
            onAdd = onAddServer,
            onEdit = onEditServer,
            onSelect = onSelectServer,
            onDelete = onDeleteServer,
            onDeleteGroup = onDeleteGroup,
            onRefreshGroup = onRefreshGroup,
            onPingGroup = onPingGroup,
            onToggleGroupAutoUpdateOnLaunch = onToggleGroupAutoUpdateOnLaunch,
            onImportClipboard = onImportClipboard,
            modifier = Modifier.fillMaxWidth(),
            title = localized(language, "Профили", "Profiles"),
            titleBadge = if (serversState.groups.isNotEmpty()) {
                localized(language, "первая подписка", "first subscription")
            } else {
                null
            },
            showAddActions = false,
        )
    }
}

@Composable
private fun CompactConnectionCard(
    selectedServerLabel: String,
    statusLabel: String,
    statusIsError: Boolean,
    latencyLabel: String,
    uplinkLabel: String,
    downlinkLabel: String,
    powerEnabled: Boolean,
    running: Boolean,
    busy: Boolean,
    buttonText: String,
    buttonDescription: String,
    onConnectToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .shadow(
                elevation = 22.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF071312).copy(alpha = 0.07f),
                spotColor = Color(0xFF071312).copy(alpha = 0.10f),
            )
            .border(1.dp, Color.White.copy(alpha = 0.86f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = selectedServerLabel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionChip(
                    text = statusLabel,
                    selected = running && !statusIsError,
                    error = statusIsError,
                )
                ConnectionChip(text = latencyLabel)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionChip(text = uplinkLabel)
                ConnectionChip(text = downlinkLabel)
            }
            PremiumPowerButton(
                enabled = powerEnabled,
                running = running,
                busy = busy,
                label = buttonText,
                contentDescription = buttonDescription,
                onClick = onConnectToggle,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 10.dp),
            )
        }
    }
}

@Composable
private fun ConnectionChip(
    text: String,
    selected: Boolean = false,
    error: Boolean = false,
) {
    val background = when {
        error -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    }
    val content = when {
        error -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(13.dp),
            )
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PremiumPowerButton(
    enabled: Boolean,
    running: Boolean,
    busy: Boolean,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = when {
        running -> Brush.verticalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFB4232D)))
        busy -> Brush.verticalGradient(listOf(Color(0xFF9AA5AE), Color(0xFF5F6A73)))
        else -> Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                MaterialTheme.colorScheme.primary,
            ),
        )
    }
    val glowColor = when {
        running -> Color(0xFFFF6B6B)
        busy -> Color(0xFF7C8790)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(124.dp)
            .alpha(if (enabled) 1f else 0.48f)
            .shadow(
                elevation = 30.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = glowColor.copy(alpha = 0.20f),
                spotColor = glowColor.copy(alpha = 0.24f),
            )
            .clip(CircleShape)
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.56f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(8.dp)
                .border(2.dp, Color.White.copy(alpha = if (busy) 0.22f else 0.34f), CircleShape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(20.dp)
                .background(glowColor.copy(alpha = 0.18f), CircleShape),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = contentDescription,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

private fun RuntimeState.connectionChipText(language: AppLanguage): String = when (this) {
    RuntimeState.Idle -> localized(language, "Готово", "Ready")
    RuntimeState.PreparingVpnPermission -> localized(language, "Разрешение", "Permission")
    RuntimeState.StartingForeground -> localized(language, "Запуск", "Starting")
    RuntimeState.BuildingConfig -> localized(language, "Проверка", "Checking")
    RuntimeState.EstablishingVpn -> localized(language, "Туннель", "Tunnel")
    RuntimeState.StartingNativeCore -> localized(language, "Ядро", "Core")
    RuntimeState.VerifyingConnection -> localized(language, "Проверка", "Checking")
    is RuntimeState.Running -> localized(language, "Подключено", "Connected")
    RuntimeState.Stopping -> localized(language, "Остановка", "Stopping")
    is RuntimeState.Error -> shortErrorChip(language)
}

private fun RuntimeState.Error.shortErrorChip(language: AppLanguage): String {
    val normalized = message.lowercase()
    return when {
        "недоступ" in normalized || "unavailable" in normalized -> localized(language, "Недоступен", "Unavailable")
        else -> localized(language, "Ошибка", "Error")
    }
}

private fun ServerPingUiState?.connectionLatencyLabel(language: AppLanguage): String = when {
    this == null -> unavailableLabel(language)
    checking -> "..."
    delayMs != null -> localized(language, "${delayMs} мс", "${delayMs} ms")
    error != null -> unavailableLabel(language)
    else -> unavailableLabel(language)
}

private fun unavailableLabel(language: AppLanguage): String =
    localized(language, "н/д", "n/a")

@Composable
private fun AddProfileMenu(
    importing: Boolean,
    onAddServer: () -> Unit,
    onImportClipboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = Color(0xFF071312).copy(alpha = 0.08f),
                    spotColor = Color(0xFF071312).copy(alpha = 0.10f),
                ),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = localized(language, "Добавить профиль", "Add profile"),
                modifier = Modifier.size(30.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        localized(language, "Добавить вручную", "Add manually"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                onClick = {
                    expanded = false
                    onAddServer()
                },
                modifier = Modifier
                    .width(320.dp)
                    .padding(vertical = 6.dp),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        localized(language, "Добавить из буфера обмена", "Add from clipboard"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                onClick = {
                    expanded = false
                    onImportClipboard()
                },
                enabled = !importing,
                modifier = Modifier
                    .width(320.dp)
                    .padding(vertical = 6.dp),
            )
        }
    }
}
