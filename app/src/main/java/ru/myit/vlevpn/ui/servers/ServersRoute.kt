package ru.myit.vlevpn.ui.servers

import android.content.ClipboardManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val clip = manager.primaryClip?.takeIf { it.itemCount > 0 } ?: return null
    val values = buildList {
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            item.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            item.htmlText?.takeIf { it.isNotBlank() }?.let(::add)
            item.uri?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            item.intent?.dataString?.takeIf { it.isNotBlank() }?.let(::add)
            item.coerceToText(this@readClipboardText)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }
    }
    return values.distinct().joinToString(separator = "\n").ifBlank { null }
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
    titleBadge: String? = null,
    showAddActions: Boolean = true,
) {
    var pendingScrollGroupId by remember { mutableStateOf<String?>(null) }
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        (title ?: localized(language, "Серверы", "Servers")).takeIf { it.isNotBlank() }?.let { resolvedTitle ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    resolvedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                titleBadge?.takeIf { it.isNotBlank() }?.let { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(13.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    val shape = RoundedCornerShape(26.dp)
    val railColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF071312).copy(alpha = 0.06f),
                spotColor = Color(0xFF071312).copy(alpha = 0.08f),
            )
            .border(1.dp, Color.White.copy(alpha = 0.82f), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (group.isSubscription) {
                        drawRect(
                            color = railColor,
                            size = Size(6.dp.toPx(), size.height),
                        )
                    }
                }
                .padding(start = if (group.isSubscription) 6.dp else 0.dp),
        ) {
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
                    val selected = server.id == selectedServerId
                    val previousSelected = index > 0 && group.servers[index - 1].id == selectedServerId
                    if (index > 0 && !selected && !previousSelected) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    ServerRow(
                        server = server,
                        selected = selected,
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
    val subtitle = group.subtitle(language)
    val headerAccentColor = MaterialTheme.colorScheme.primary
    val headerTrackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 10.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                enabled = !group.refreshing,
                onClick = onRefreshGroup,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = localized(language, "Обновить подписку", "Refresh subscription"),
                    tint = if (group.refreshing) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                enabled = !group.checking,
                onClick = onPingGroup,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = localized(language, "Проверить пинг", "Check ping"),
                    tint = if (group.checking) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                )
            }
            SubscriptionActionsMenu(
                refreshing = group.refreshing,
                autoUpdateOnLaunchEnabled = group.autoUpdateOnLaunchEnabled,
                language = language,
                onRefresh = onRefreshGroup,
                onToggleAutoUpdateOnLaunch = onToggleAutoUpdateOnLaunch,
                onDelete = onDeleteGroup,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            subtitle,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (group.totalBytes > 0L || group.expireAtMillis > 0L || link.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (group.totalBytes > 0L) {
                    TrafficProgressBar(
                        label = group.trafficLabel(),
                        progress = group.trafficProgress(),
                        trackColor = headerTrackColor,
                        progressColor = headerAccentColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        localized(
                            language,
                            "${group.servers.size} конфигов",
                            "${group.servers.size} config(s)",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (link.isNotBlank()) {
                    IconButton(onClick = { uriHandler.openUri(link) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = localized(language, "Открыть ссылку поддержки", "Open support link"),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (group.announce.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    group.announce,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
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
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
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
            style = MaterialTheme.typography.labelSmall,
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
    val selectedBackground = MaterialTheme.colorScheme.primaryContainer
    val rowBackground = if (selected) selectedBackground else Color.Transparent
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val actionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val showDetailsAction = !(subscriptionStyle && server.isOlcRtc)
    val pingColor = when {
        ping?.error == null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val displayName = server.rowDisplayName()
    val leadingFlag = server.leadingFlagEmoji()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!subscriptionStyle) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(54.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
        }
        ServerMarker(
            text = server.markerText(displayName),
            color = server.markerColor(displayName),
            flag = leadingFlag,
            modifier = Modifier.padding(start = 10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 8.dp),
        ) {
            Text(
                displayName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                server.subtitle(subscriptionStyle),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ping.label(language).takeIf { it.isNotBlank() }?.let { pingLabel ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pingColor,
                )
            }
        }
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

@Composable
private fun ServerMarker(
    text: String,
    color: Color,
    flag: String?,
    modifier: Modifier = Modifier,
) {
    if (flag != null) {
        Box(
            modifier = modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = flag,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
        }
        return
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private fun ServerPingUiState?.label(language: AppLanguage): String = when {
    this == null -> ""
    checking -> "..."
    delayMs != null -> localized(language, "${delayMs} мс", "${delayMs} ms")
    error != null -> localized(language, "н/д", "n/a")
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

private val serverMarkerPalette = listOf(
    Color(0xFF0E7A70),
    Color(0xFF304E9B),
    Color(0xFF8E4C9A),
    Color(0xFFB54A3B),
    Color(0xFF426A30),
    Color(0xFF545D6D),
)

private fun ServerProfile.rowDisplayName(): String {
    val trimmed = name.trim()
    val withoutEmoji = trimmed.withoutDisplayEmoji()
    val withoutLeadingSymbol = withoutEmoji.dropWhile { !it.isLetterOrDigit() }.trimStart()
    return withoutLeadingSymbol.ifBlank {
        trimmed.ifBlank {
            host.ifBlank { protocol.displayName }
        }
    }
}

private fun ServerProfile.leadingFlagEmoji(): String? = name.trimStart().displayEmoji()

private fun String.displayEmoji(): String? =
    firstFlagEmojiToken() ?: firstEmojiToken()

private fun String.withoutDisplayEmoji(): String {
    val emoji = displayEmoji() ?: return trim()
    return replace(emoji, "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.leadingCountryFlagEmoji(): String? {
    if (isBlank()) return null
    val first = codePointAt(0)
    if (!first.isRegionalIndicatorSymbol()) return null
    val firstSize = Character.charCount(first)
    if (length <= firstSize) return null
    val second = codePointAt(firstSize)
    if (!second.isRegionalIndicatorSymbol()) return null
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private fun Int.isRegionalIndicatorSymbol(): Boolean = this in 0x1F1E6..0x1F1FF

private fun String.firstFlagEmojiToken(): String? {
    leadingCountryFlagEmoji()?.let { return it }
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        if (codePoint.isFlagEmojiCodePoint()) {
            return emojiTokenAt(index)
        }
        index += Character.charCount(codePoint)
    }
    return null
}

private fun String.firstEmojiToken(): String? {
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        if (codePoint.isRegionalIndicatorSymbol()) {
            val nextIndex = index + Character.charCount(codePoint)
            if (nextIndex < length) {
                val next = codePointAt(nextIndex)
                if (next.isRegionalIndicatorSymbol()) {
                    return substring(index, nextIndex + Character.charCount(next))
                }
            }
        }
        if (codePoint.isEmojiCodePoint()) {
            return emojiTokenAt(index)
        }
        index += Character.charCount(codePoint)
    }
    return null
}

private fun Int.isFlagEmojiCodePoint(): Boolean =
    isRegionalIndicatorSymbol() ||
        this == 0x1F3F3 ||
        this == 0x1F3F4 ||
        this == 0x1F38C ||
        this == 0x1F6A9

private fun String.emojiTokenAt(startIndex: Int): String {
    var end = startIndex + Character.charCount(codePointAt(startIndex))
    end = consumeEmojiSuffix(end)
    while (end < length) {
        val next = codePointAt(end)
        if (next == 0x200D) {
            val joinedIndex = end + Character.charCount(next)
            if (joinedIndex < length) {
                val joined = codePointAt(joinedIndex)
                if (joined.isEmojiCodePoint()) {
                    end = joinedIndex + Character.charCount(joined)
                    end = consumeEmojiSuffix(end)
                    continue
                }
            }
        }
        break
    }
    return substring(startIndex, end)
}

private fun String.consumeEmojiSuffix(startIndex: Int): Int {
    var end = startIndex
    while (end < length) {
        val codePoint = codePointAt(end)
        if (
            codePoint.isEmojiVariationSelector() ||
            codePoint.isEmojiSkinToneModifier() ||
            codePoint.isEmojiTagCharacter() ||
            codePoint == 0x20E3
        ) {
            end += Character.charCount(codePoint)
        } else {
            break
        }
    }
    return end
}

private fun Int.isEmojiCodePoint(): Boolean =
    isRegionalIndicatorSymbol() ||
        this in 0x1F000..0x1FAFF ||
        this in 0x2600..0x27BF ||
        this in 0x2B00..0x2BFF ||
        this in 0x2300..0x23FF ||
        this == 0x3030 ||
        this == 0x303D ||
        this == 0x3297 ||
        this == 0x3299

private fun Int.isEmojiVariationSelector(): Boolean = this == 0xFE0E || this == 0xFE0F

private fun Int.isEmojiSkinToneModifier(): Boolean = this in 0x1F3FB..0x1F3FF

private fun Int.isEmojiTagCharacter(): Boolean = this in 0xE0020..0xE007F

private fun ServerProfile.markerText(displayName: String): String {
    val initials = displayName
        .split(Regex("[\\s|#._\\-]+"))
        .mapNotNull { part -> part.firstOrNull { it.isLetterOrDigit() }?.toString() }
        .take(2)
        .joinToString(separator = "")
    return initials
        .ifBlank { displayName.take(2) }
        .uppercase(Locale.getDefault())
        .take(2)
}

private fun ServerProfile.markerColor(displayName: String): Color {
    val value = displayName.lowercase(Locale.getDefault())
    return when {
        "герман" in value || "germany" in value || "deutsch" in value -> Color(0xFFD8212F)
        "нидер" in value || "nether" in value || "holland" in value -> Color(0xFF3755A3)
        "фин" in value || "finland" in value -> Color(0xFF2C62B0)
        "рос" in value || "russia" in value -> Color(0xFFB9333C)
        "usa" in value || "сша" in value || "gemini" in value -> Color(0xFF3159A2)
        "lte" in value || "обход" in value -> Color(0xFF126F63)
        else -> serverMarkerPalette[Math.floorMod(id.value.hashCode(), serverMarkerPalette.size)]
    }
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
