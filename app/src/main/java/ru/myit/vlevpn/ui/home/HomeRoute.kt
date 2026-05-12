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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import ru.myit.vlevpn.BuildConfig
import ru.myit.vlevpn.runtime.RuntimeState
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.ui.i18n.LocalAppLanguage
import ru.myit.vlevpn.ui.i18n.localized
import ru.myit.vlevpn.ui.servers.ServerProfilesContent
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
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
                        viewModel.markPreparingPermission()
                        permissionLauncher.launch(prepareIntent)
                    } else {
                        viewModel.connect()
                    }
                }
            },
            onAddServer = onAddServer,
            onEditServer = onEditServer,
            onSelectServer = serversViewModel::select,
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
    val statusText = state.runtimeState.homeStatusText(language)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("VLE VPN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
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
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
                            ),
                        ),
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.selectedServer?.let { server ->
                        val protocolName = if (server.subscriptionId.isNotBlank() && server.isOlcRtc) {
                            "VLESS"
                        } else {
                            server.protocol.displayName
                        }
                        "${server.name} · $protocolName"
                    }
                        ?: localized(language, "Сервер не выбран", "No server selected"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    statusText?.let { text ->
                        Text(
                            text = text,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 12.dp),
                            color = if (state.runtimeState is RuntimeState.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PremiumPowerButton(
                        enabled = isRunning || (state.selectedServer != null && !isBusy),
                        running = isRunning,
                        busy = isBusy,
                        label = buttonText,
                        contentDescription = buttonDescription,
                        onClick = onConnectToggle,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(localized(language, "Исходящий", "Uplink"), formatBytes(state.stats.uplinkBytes), Modifier.weight(1f))
            StatCard(localized(language, "Входящий", "Downlink"), formatBytes(state.stats.downlinkBytes), Modifier.weight(1f))
        }

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
            showAddActions = false,
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
            .size(152.dp)
            .alpha(if (enabled) 1f else 0.48f)
            .shadow(26.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.56f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(10.dp)
                .border(2.dp, Color.White.copy(alpha = if (busy) 0.22f else 0.34f), CircleShape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(23.dp)
                .background(glowColor.copy(alpha = 0.18f), CircleShape),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = contentDescription,
                modifier = Modifier.size(54.dp),
                tint = Color.White,
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

private fun RuntimeState.homeStatusText(language: AppLanguage): String? = when (this) {
    RuntimeState.Idle -> null
    RuntimeState.PreparingVpnPermission -> localized(language, "Ожидаем разрешение VPN", "Waiting for VPN permission")
    RuntimeState.StartingForeground -> localized(language, "Запускаем VPN", "Starting VPN")
    RuntimeState.BuildingConfig -> localized(language, "Проверяем серверы", "Checking servers")
    RuntimeState.EstablishingVpn -> localized(language, "Создаем VPN-туннель", "Creating VPN tunnel")
    RuntimeState.StartingNativeCore -> localized(language, "Запускаем VPN-ядро", "Starting VPN core")
    RuntimeState.VerifyingConnection -> localized(language, "Проверяем соединение", "Verifying connection")
    is RuntimeState.Running -> null
    RuntimeState.Stopping -> localized(language, "Останавливаем VPN", "Stopping VPN")
    is RuntimeState.Error -> message
}

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
            modifier = Modifier.size(56.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = localized(language, "Добавить профиль", "Add profile"),
                modifier = Modifier.size(34.dp),
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

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
