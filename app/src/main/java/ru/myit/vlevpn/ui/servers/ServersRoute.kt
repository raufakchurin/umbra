package ru.myit.vlevpn.ui.servers

import android.content.ClipboardManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile
import ru.myit.vlevpn.ui.i18n.LocalAppLanguage
import ru.myit.vlevpn.ui.i18n.localized
import ru.myit.vlevpn.ui.shared.TranslucentSnackbarHost

@Composable
fun ServersRoute(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(event.message)
        }
    }
    ServersScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAdd = onAdd,
        onEdit = onEdit,
        onSelect = viewModel::select,
        onDelete = viewModel::delete,
        onDeleteGroup = viewModel::deleteGroup,
        onRefreshGroup = viewModel::refreshGroup,
        onPingGroup = viewModel::pingGroup,
        onToggleGroupAutoUpdateOnLaunch = viewModel::setGroupAutoUpdateOnLaunch,
        onImportClipboard = {
            viewModel.importFromClipboard(context.readClipboardText())
        },
    )
}

@Composable
private fun ServersScreen(
    state: ServersUiState,
    snackbarHostState: SnackbarHostState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSelect: (ServerId) -> Unit,
    onDelete: (ServerId) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onRefreshGroup: (String) -> Unit,
    onPingGroup: (String) -> Unit,
    onToggleGroupAutoUpdateOnLaunch: (String, Boolean) -> Unit,
    onImportClipboard: () -> Unit,
) {
    Scaffold(
        snackbarHost = { TranslucentSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add server")
            }
        },
    ) { padding ->
        ServerProfilesContent(
            state = state,
            onAdd = onAdd,
            onEdit = onEdit,
            onSelect = onSelect,
            onDelete = onDelete,
            onDeleteGroup = onDeleteGroup,
            onRefreshGroup = onRefreshGroup,
            onPingGroup = onPingGroup,
            onToggleGroupAutoUpdateOnLaunch = onToggleGroupAutoUpdateOnLaunch,
            onImportClipboard = onImportClipboard,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        )
    }
}

fun Context.readClipboardText(): String? {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val item = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
    return item?.coerceToText(this)?.toString()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerProfilesContent(
    state: ServersUiState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSelect: (ServerId) -> Unit,
    onDelete: (ServerId) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onRefreshGroup: (String) -> Unit,
    onPingGroup: (String) -> Unit,
    onToggleGroupAutoUpdateOnLaunch: (String, Boolean) -> Unit,
    onImportClipboard: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    showAddActions: Boolean = true,
) {
    var pendingScrollGroupId by remember { mutableStateOf<String?>(null) }
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        (title ?: localized(language, "Серверы", "Servers")).takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        if (showAddActions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = localized(language, "Добавить профиль", "Add server"))
                    Text(localized(language, "Добавить вручную", "Add manually"))
                }
                OutlinedButton(
                    onClick = onImportClipboard,
                    enabled = !state.importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = localized(language, "Добавить из буфера обмена", "Import from clipboard"),
                    )
                    Text(localized(language, "Добавить из буфера обмена", "Add from clipboard"))
                }
            }
        }
        if (state.groups.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localized(language, "Локальных профилей нет", "No local profiles"))
                    if (showAddActions) {
                        OutlinedButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = localized(language, "Добавить профиль", "Add server"))
                            Text(localized(language, "Добавить профиль", "Add profile"))
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.groups.forEach { group ->
                    key(group.id) {
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        if (pendingScrollGroupId == group.id && state.groups.firstOrNull()?.id == group.id) {
                            LaunchedEffect(group.id, state.groups.firstOrNull()?.id) {
                                bringIntoViewRequester.bringIntoView()
                                pendingScrollGroupId = null
                            }
                        }
                        ServerGroupCard(
                            group = group,
                            selectedServerId = state.selectedServerId,
                            pingResults = state.pingResults,
                            onDeleteGroup = { onDeleteGroup(group.id) },
                            onRefreshGroup = { onRefreshGroup(group.id) },
                            onPingGroup = { onPingGroup(group.id) },
                            onToggleAutoUpdateOnLaunch = { enabled ->
                                onToggleGroupAutoUpdateOnLaunch(group.id, enabled)
                            },
                            onSelect = { serverId ->
                                pendingScrollGroupId = group.id
                                onSelect(serverId)
                            },
                            onEdit = onEdit,
                            onDelete = onDelete,
                            language = language,
                            modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerGroupCard(
    group: ServerGroupUiState,
    selectedServerId: ServerId?,
    pingResults: Map<ServerId, ServerPingUiState>,
    onDeleteGroup: () -> Unit,
    onRefreshGroup: () -> Unit,
    onPingGroup: () -> Unit,
    onToggleAutoUpdateOnLaunch: (Boolean) -> Unit,
    onSelect: (ServerId) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (ServerId) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            if (group.isSubscription) {
                SubscriptionHeader(
                    group = group,
                    language = language,
                    onRefreshGroup = onRefreshGroup,
                    onPingGroup = onPingGroup,
                    onDeleteGroup = onDeleteGroup,
                    onToggleAutoUpdateOnLaunch = onToggleAutoUpdateOnLaunch,
                )
            } else {
                LocalGroupHeader(
                    group = group,
                    language = language,
                    onPingGroup = onPingGroup,
                )
            }
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                group.servers.forEachIndexed { index, server ->
                    if (index > 0) HorizontalDivider()
                    ServerRow(
                        server = server,
                        selected = server.id == selectedServerId,
                        ping = pingResults[server.id],
                        onSelect = { onSelect(server.id) },
                        onEdit = { onEdit(server.id.value) },
                        onDelete = { onDelete(server.id) },
                        showDelete = !group.isSubscription,
                        subscriptionStyle = group.isSubscription,
                        language = language,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionHeader(
    group: ServerGroupUiState,
    language: AppLanguage,
    onRefreshGroup: () -> Unit,
    onPingGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onToggleAutoUpdateOnLaunch: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val link = group.supportUrl.ifBlank { group.webPageUrl }
    val headerColor = MaterialTheme.colorScheme.primary
    val headerBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    val headerAccentColor = MaterialTheme.colorScheme.primaryContainer
    val headerTrackColor = Color.Black.copy(alpha = 0.22f)
    val onHeader = Color.White
    val onHeaderMuted = Color.White.copy(alpha = 0.72f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onHeader,
                )
                Text(
                    group.subtitle(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHeaderMuted,
                )
            }
            IconButton(
                enabled = !group.refreshing,
                onClick = onRefreshGroup,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = localized(language, "Обновить подписку", "Refresh subscription"),
                    tint = if (group.refreshing) onHeader.copy(alpha = 0.38f) else onHeader,
                )
            }
            IconButton(
                enabled = !group.checking,
                onClick = onPingGroup,
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = localized(language, "Проверить пинг", "Check ping"),
                    tint = if (group.checking) onHeader.copy(alpha = 0.38f) else onHeader,
                )
            }
            SubscriptionActionsMenu(
                refreshing = group.refreshing,
                autoUpdateOnLaunchEnabled = group.autoUpdateOnLaunchEnabled,
                language = language,
                onRefresh = onRefreshGroup,
                onToggleAutoUpdateOnLaunch = onToggleAutoUpdateOnLaunch,
                onDelete = onDeleteGroup,
                iconColor = onHeader,
            )
        }

        if (group.totalBytes > 0L || group.expireAtMillis > 0L || link.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBandColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (group.totalBytes > 0L) {
                    TrafficProgressBar(
                        label = group.trafficLabel(),
                        progress = group.trafficProgress(),
                        trackColor = headerTrackColor,
                        progressColor = headerAccentColor,
                        contentColor = onHeader,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        localized(
                            language,
                            "${group.servers.size} конфигов",
                            "${group.servers.size} config(s)",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onHeaderMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (group.expireAtMillis > 0L) {
                    Text(
                        text = localized(
                            language,
                            "Истекает: ${formatDate(group.expireAtMillis)}",
                            "Expires: ${formatDate(group.expireAtMillis)}",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onHeader,
                    )
                }
                if (link.isNotBlank()) {
                    IconButton(onClick = { uriHandler.openUri(link) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = localized(language, "Открыть ссылку поддержки", "Open support link"),
                            tint = onHeader,
                        )
                    }
                }
            }
        }

        if (group.announce.isNotBlank()) {
            HorizontalDivider(color = Color(0xFF9B4E57).copy(alpha = 0.7f))
            Text(
                group.announce,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBandColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onHeader,
            )
        }
    }
}

@Composable
private fun LocalGroupHeader(
    group: ServerGroupUiState,
    language: AppLanguage,
    onPingGroup: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.title, fontWeight = FontWeight.SemiBold)
            Text(
                group.subtitle(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onPingGroup,
            enabled = !group.checking,
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = localized(language, "Проверить пинг", "Check ping"),
            )
        }
    }
}

@Composable
private fun TrafficProgressBar(
    label: String,
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(progressColor),
        )
        Text(
            text = label,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}
@Composable
private fun SubscriptionActionsMenu(
    refreshing: Boolean,
    autoUpdateOnLaunchEnabled: Boolean,
    language: AppLanguage,
    onRefresh: () -> Unit,
    onToggleAutoUpdateOnLaunch: (Boolean) -> Unit,
    onDelete: () -> Unit,
    iconColor: Color = Color.Unspecified,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = localized(language, "Действия с подпиской", "Subscription actions"),
            tint = iconColor,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(localized(language, "Обновить подписку", "Refresh subscription")) },
            enabled = !refreshing,
            onClick = {
                expanded = false
                onRefresh()
            },
        )
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(localized(language, "Обновлять при запуске", "Update on app launch"))
                    Switch(
                        checked = autoUpdateOnLaunchEnabled,
                        onCheckedChange = null,
                    )
                }
            },
            onClick = {
                expanded = false
                onToggleAutoUpdateOnLaunch(!autoUpdateOnLaunchEnabled)
            },
        )
        DropdownMenuItem(
            text = { Text(localized(language, "Удалить подписку", "Delete subscription")) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

@Composable
private fun ServerRow(
    server: ServerProfile,
    selected: Boolean,
    ping: ServerPingUiState?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean,
    subscriptionStyle: Boolean,
    language: AppLanguage,
) {
    val selectedBackground = MaterialTheme.colorScheme.primary
    val rowBackground = if (selected) selectedBackground else Color.Transparent
    val primaryTextColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (selected) Color.White.copy(alpha = 0.76f) else MaterialTheme.colorScheme.onSurfaceVariant
    val actionColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val showDetailsAction = !(subscriptionStyle && server.isOlcRtc)
    val pingColor = when {
        selected -> Color.White.copy(alpha = 0.84f)
        ping?.error == null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(68.dp)
                .background(if (selected) Color.White else Color.Transparent),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        ) {
            Text(
                server.name,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
            )
            Text(
                server.subtitle(subscriptionStyle),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
            )
        }
        Text(
            text = ping.label(language),
            style = MaterialTheme.typography.bodySmall,
            color = pingColor,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (showDetailsAction) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = localized(language, "Открыть настройки профиля", "Open profile settings"),
                    tint = actionColor,
                )
            }
        }
        if (showDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = localized(language, "Удалить сервер", "Delete server"),
                    tint = actionColor,
                )
            }
        }
    }
}

private fun ServerPingUiState?.label(language: AppLanguage): String = when {
    this == null -> ""
    checking -> "..."
    delayMs != null -> localized(language, "${delayMs} мс", "${delayMs} ms")
    error != null -> "n/a"
    else -> ""
}

private fun ServerProfile.subtitle(subscriptionStyle: Boolean): String =
    if (subscriptionStyle && isOlcRtc) {
        "VLESS | JSON"
    } else if (protocol == ru.myit.vlevpn.domain.model.ProxyProtocol.OLCRTC) {
        listOf(
            protocol.displayName,
            olcRtcCarrierLabel(host),
            transport.ifBlank { "transport" },
        ).joinToString(" | ")
    } else if (subscriptionStyle) {
        "${protocol.displayName} | JSON"
    } else {
        "${protocol.displayName} | ${if (isCustomJson) "JSON" else host.ifBlank { "custom JSON" }}"
    }

private fun olcRtcCarrierLabel(carrier: String): String = when (carrier.lowercase(Locale.US)) {
    "wbstream" -> "WB Stream"
    "telemost" -> "Telemost"
    "jazz" -> "Jazz"
    else -> carrier.ifBlank { "carrier" }
}

private fun ServerGroupUiState.subtitle(language: AppLanguage): String {
    val imported = importedAtMillis.takeIf { it > 0L }?.let(::formatDateTime)
    val update = if (isSubscription) {
        localized(
            language,
            "Автообновление - ${updateIntervalHours.takeIf { it > 0 } ?: 12} ч.",
            "Auto-update - ${updateIntervalHours.takeIf { it > 0 } ?: 12} h",
        )
    } else {
        null
    }
    val parts = listOfNotNull(imported, update)
    return if (parts.isEmpty()) {
        localized(language, "${servers.size} конфигов", "${servers.size} config(s)")
    } else {
        parts.joinToString(" | ")
    }
}

private fun ServerGroupUiState.trafficProgress(): Float {
    if (totalBytes <= 0L) return 0f
    return ((uploadBytes + downloadBytes).toDouble() / totalBytes.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}

private fun ServerGroupUiState.trafficLabel(): String =
    if (totalBytes > 0L) {
        "${formatTraffic(uploadBytes + downloadBytes)}/${formatTraffic(totalBytes)}"
    } else {
        "${servers.size} config(s)"
    }

private fun formatTraffic(bytes: Long): String {
    val gb = bytes.toDouble() / 1_073_741_824.0
    val value = if (gb >= 10) "%.0f".format(Locale.US, gb) else "%.1f".format(Locale.US, gb)
    return "${value.removeSuffix(".0")} GB"
}

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
