package ru.myit.vlevpn.ui.navigation

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.myit.vlevpn.domain.model.InAppForegroundMessage
import ru.myit.vlevpn.core.url.toSafeActionUri
import ru.myit.vlevpn.domain.model.AppSettings
import ru.myit.vlevpn.ui.home.HomeRoute
import ru.myit.vlevpn.ui.i18n.LocalAppLanguage
import ru.myit.vlevpn.ui.i18n.appText
import ru.myit.vlevpn.ui.logs.LogsRoute
import ru.myit.vlevpn.ui.servers.ServerEditorRoute
import ru.myit.vlevpn.ui.settings.SettingsRoute
import ru.myit.vlevpn.ui.splash.StartupSplashOverlay
import ru.myit.vlevpn.ui.theme.VleVpnTheme
import kotlinx.coroutines.delay

@Composable
fun VleVpnApp(
    viewModel: AppUiViewModel = hiltViewModel(),
) {
    var showStartupSplash by remember { mutableStateOf(true) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val foregroundMessage by viewModel.inAppMessage.collectAsStateWithLifecycle()
    val remoteBrandingEnabled = !settings.localBrandingOverrideEnabled &&
        settings.remoteBrandingProviderId.isNotBlank()
    val brandingImagePath = if (remoteBrandingEnabled) settings.remoteBrandingImagePath else ""

    LaunchedEffect(Unit) {
        delay(1400)
        showStartupSplash = false
    }

    VleVpnTheme(
        textScale = settings.appTextSize.scale,
        accentColor = settings.appAccentColor,
        backgroundStyle = settings.appBackgroundStyle,
        customAccentColor = if (remoteBrandingEnabled) parseHexColor(settings.remoteBrandingAccentColor) else null,
        customBackgroundColor = if (remoteBrandingEnabled) parseHexColor(settings.remoteBrandingBackgroundColor) else null,
    ) {
        CompositionLocalProvider(LocalAppLanguage provides settings.appLanguage) {
            Box(Modifier.fillMaxSize()) {
                PartnerBrandingBackdrop(
                    settings = settings,
                    enabled = remoteBrandingEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
                VleVpnNavigation(hasBrandingImage = brandingImagePath.isNotBlank())
                foregroundMessage?.let { message ->
                    InAppForegroundMessageOverlay(
                        message = message,
                        onDismiss = { viewModel.clearInAppMessage(message.deliveryId) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                StartupSplashOverlay(
                    visible = showStartupSplash,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PartnerBrandingBackdrop(
    settings: AppSettings,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val path = if (enabled) settings.remoteBrandingImagePath else ""
    val bitmap = remember(path) {
        path
            .takeIf { it.isNotBlank() }
            ?.let { BitmapFactory.decodeFile(it) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.blur(((settings.remoteBrandingBlurPercent.coerceIn(5, 90) / 100f) * 28f).dp),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun parseHexColor(value: String): Color? {
    if (!Regex("^#[0-9a-fA-F]{6}$").matches(value.trim())) return null
    val raw = value.removePrefix("#").toLongOrNull(16) ?: return null
    return Color(0xFF00000000 or raw)
}

@Composable
private fun VleVpnNavigation(hasBrandingImage: Boolean) {
    val navController = rememberNavController()
    val navItems = listOf(
        TopLevelRoute.Home,
        TopLevelRoute.Settings,
        TopLevelRoute.Logs,
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = navItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (hasBrandingImage) {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                } else {
                    MaterialTheme.colorScheme.background
                },
            ),
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.Home.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopLevelRoute.Home.route) {
                HomeRoute(
                    onAddServer = { navController.navigate("serverEditor/new") },
                    onEditServer = { id -> navController.navigate("serverEditor/$id") },
                )
            }
            composable("serverEditor/{serverId}") {
                ServerEditorRoute(
                    onDone = { navController.popBackStack() },
                    viewModel = hiltViewModel(),
                )
            }
            composable(TopLevelRoute.Settings.route) {
                SettingsRoute()
            }
            composable(TopLevelRoute.Logs.route) {
                LogsRoute()
            }
        }
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.48f to MaterialTheme.colorScheme.background.copy(alpha = 0.76f),
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            FloatingBottomNavigationBar(
                items = navItems,
                isSelected = { item -> currentDestination?.hierarchy?.any { it.route == item.route } == true },
                onSelect = { item ->
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun FloatingBottomNavigationBar(
    items: List<TopLevelRoute>,
    isSelected: (TopLevelRoute) -> Boolean,
    onSelect: (TopLevelRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .height(72.dp)
            .shadow(
                elevation = 28.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF071312).copy(alpha = 0.14f),
                spotColor = Color(0xFF071312).copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.72f), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            FloatingBottomNavigationItem(
                item = item,
                selected = isSelected(item),
                onSelect = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FloatingBottomNavigationItem(
    item: TopLevelRoute,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label(),
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label(),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private sealed class TopLevelRoute(
    val route: String,
    val labelRu: String,
    val labelEn: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Home : TopLevelRoute("home", "Главная", "Home", Icons.Default.Home)
    data object Settings : TopLevelRoute("settings", "Настройки", "Settings", Icons.Default.Settings)
    data object Logs : TopLevelRoute("logs", "Логи", "Logs", Icons.AutoMirrored.Filled.Article)
}

@Composable
private fun TopLevelRoute.label(): String = appText(labelRu, labelEn)

@Composable
private fun InAppForegroundMessageOverlay(
    message: InAppForegroundMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = message.displayConfig
    val accent = when (message.severity) {
        "critical" -> Color(0xFFE11D48)
        "warning" -> Color(0xFFF59E0B)
        "success" -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }
    val actionUri = message.actionUrl.toSafeActionUri()
    val notificationIcon = notificationIcon(config.icon)
    val openFailedText = appText("Не удалось открыть ссылку", "Unable to open link")
    val headerLabel = config.headerLabel.takeUnless { it.isNullOrBlank() } ?: appText("Новое уведомление", "New notification")
    val dismissLabel = config.dismissButtonLabel.takeUnless { it.isNullOrBlank() } ?: appText("Закрыть", "Close")
    val actionLabel = config.actionButtonLabel.takeUnless { it.isNullOrBlank() } ?: appText("Открыть ссылку", "Open link")
    val showActionButton = config.showActionButton && actionUri != null
    val showDismissButton = config.showDismissButton || !showActionButton
    val showCloseButton = config.showCloseButton || (!showDismissButton && !showActionButton)
    val colors = notificationWindowColors(config.theme)
    val textAlign = if (config.messageAlign == "center") TextAlign.Center else TextAlign.Start

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = colors.scrimAlpha))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .shadow(34.dp, RoundedCornerShape(30.dp), clip = false)
                .border(
                    width = 1.dp,
                    color = colors.border,
                    shape = RoundedCornerShape(30.dp),
                ),
            shape = RoundedCornerShape(30.dp),
            color = colors.surface,
            tonalElevation = 10.dp,
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    if (config.showIcon) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                notificationIcon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.size(14.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                    ) {
                        Text(
                            text = headerLabel,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = message.title,
                            modifier = Modifier.padding(top = 6.dp),
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = if (config.titleSize == "normal") {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.headlineSmall
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showCloseButton) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = appText("Закрыть", "Close"),
                                tint = colors.onSurface.copy(alpha = 0.64f),
                            )
                        }
                    }
                }
                Text(
                    text = message.message,
                    color = colors.onSurface.copy(alpha = 0.84f),
                    style = if (config.messageSize == "large") {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                if (config.showActionUrl && !message.actionUrl.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                    ) {
                        Text(
                            text = message.actionUrl,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (showActionButton) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (showDismissButton) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(
                                    text = dismissLabel,
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, actionUri).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                runCatching { context.startActivity(intent) }
                                    .onSuccess { onDismiss() }
                                    .onFailure {
                                        Toast.makeText(context, openFailedText, Toast.LENGTH_SHORT).show()
                                    }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                text = actionLabel,
                                modifier = Modifier.padding(vertical = 5.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (showDismissButton) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = dismissLabel,
                            modifier = Modifier.padding(vertical = 5.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private data class NotificationWindowColors(
    val surface: Color,
    val onSurface: Color,
    val border: Color,
    val scrimAlpha: Float,
)

@Composable
private fun notificationWindowColors(theme: String): NotificationWindowColors =
    when (theme) {
        "dark" -> NotificationWindowColors(
            surface = Color(0xE6121D2A),
            onSurface = Color(0xFFF8FAFC),
            border = Color.White.copy(alpha = 0.16f),
            scrimAlpha = 0.5f,
        )
        "light" -> NotificationWindowColors(
            surface = MaterialTheme.colorScheme.surface,
            onSurface = MaterialTheme.colorScheme.onSurface,
            border = Color(0xFFD7E2EA),
            scrimAlpha = 0.38f,
        )
        else -> NotificationWindowColors(
            surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            onSurface = MaterialTheme.colorScheme.onSurface,
            border = Color.White.copy(alpha = 0.46f),
            scrimAlpha = 0.42f,
        )
    }

private fun notificationIcon(icon: String): ImageVector =
    when (icon) {
        "info" -> Icons.Default.Info
        "warning" -> Icons.Default.Warning
        "shield" -> Icons.Default.Security
        else -> Icons.Default.Notifications
    }
