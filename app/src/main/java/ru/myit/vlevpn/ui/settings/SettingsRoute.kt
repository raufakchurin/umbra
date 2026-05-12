package ru.myit.vlevpn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle
import ru.myit.vlevpn.domain.model.AutoConnectMode
import ru.myit.vlevpn.domain.model.AppLanguage
import ru.myit.vlevpn.domain.model.AppTextSize
import ru.myit.vlevpn.domain.model.InstalledApp
import ru.myit.vlevpn.domain.model.PingProtocol
import ru.myit.vlevpn.ui.i18n.LocalAppLanguage
import ru.myit.vlevpn.ui.i18n.appText
import ru.myit.vlevpn.ui.i18n.localized
import ru.myit.vlevpn.ui.theme.previewColor

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(SettingsSection.Menu) }
    SettingsScreen(
        state = state,
        section = section,
        onOpenSection = { section = it },
        onBack = { section = section.backTarget() },
        onSavePorts = viewModel::savePorts,
        onSaveDns = viewModel::saveDns,
        onUpdateTun = viewModel::updateTun,
        onUpdateLogs = viewModel::updateLogs,
        onPingProtocol = viewModel::updatePingProtocol,
        onAutoConnectMode = viewModel::updateAutoConnectMode,
        onUpdateInterface = viewModel::updateInterface,
        onUpdateAppearance = viewModel::updateAppearance,
        onToggleExcludedApp = viewModel::toggleExcludedApp,
        onRefreshApps = viewModel::refreshInstalledApps,
        onResetSettings = viewModel::resetSettings,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    section: SettingsSection,
    onOpenSection: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onSavePorts: (String, String, String) -> Unit,
    onSaveDns: (String) -> Unit,
    onUpdateTun: (String, Boolean, Boolean, Boolean) -> Unit,
    onUpdateLogs: (Boolean, Boolean) -> Unit,
    onPingProtocol: (PingProtocol) -> Unit,
    onAutoConnectMode: (AutoConnectMode) -> Unit,
    onUpdateInterface: (AppLanguage, AppTextSize) -> Unit,
    onUpdateAppearance: (AppAccentColor, AppBackgroundStyle) -> Unit,
    onToggleExcludedApp: (String, Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onResetSettings: () -> Unit,
) {
    val settings = state.settings
    var socksPort by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
    var httpPort by remember(settings.httpPort) { mutableStateOf(settings.httpPort.toString()) }
    var dnsPort by remember(settings.localDnsPort) { mutableStateOf(settings.localDnsPort.toString()) }
    var mtu by remember(settings.tunMtu) { mutableStateOf(settings.tunMtu.toString()) }
    var dns by remember(settings.dnsServers) { mutableStateOf(settings.dnsServers.joinToString(",")) }
    val language = settings.appLanguage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (section != SettingsSection.Menu) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                text = if (section == SettingsSection.Menu) {
                    localized(language, "Настройки", "Settings")
                } else {
                    section.title(language)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        state.message?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when (section) {
            SettingsSection.Menu -> SettingsMenu(onOpenSection, onResetSettings)
            SettingsSection.Interface -> InterfaceSection(
                language = settings.appLanguage,
                textSize = settings.appTextSize,
                accentColor = settings.appAccentColor,
                backgroundStyle = settings.appBackgroundStyle,
                onUpdateInterface = onUpdateInterface,
                onUpdateAppearance = onUpdateAppearance,
            )
            SettingsSection.Advanced -> AdvancedSettingsSection(onOpenSection)
            SettingsSection.Ports -> PortsSection(
                socksPort = socksPort,
                httpPort = httpPort,
                dnsPort = dnsPort,
                onSocksPort = { socksPort = it },
                onHttpPort = { httpPort = it },
                onDnsPort = { dnsPort = it },
                onSavePorts = { onSavePorts(socksPort, httpPort, dnsPort) },
            )
            SettingsSection.Vpn -> VpnSection(
                mtu = mtu,
                onMtu = { mtu = it },
                xrayTunModeEnabled = settings.xrayTunModeEnabled,
                routeAllTraffic = settings.routeAllTraffic,
                allowIpv6 = settings.allowIpv6,
                onUpdateTun = onUpdateTun,
            )
            SettingsSection.DnsLogs -> DnsLogsSection(
                dns = dns,
                logsEnabled = settings.logsEnabled,
                debugConfigPreviewEnabled = settings.debugConfigPreviewEnabled,
                onDns = { dns = it },
                onSaveDns = { onSaveDns(dns) },
                onUpdateLogs = onUpdateLogs,
            )
            SettingsSection.Ping -> PingSection(
                selected = settings.pingProtocol,
                onPingProtocol = onPingProtocol,
            )
            SettingsSection.AutoConnect -> AutoConnectSection(
                selected = settings.autoConnectMode,
                onAutoConnectMode = onAutoConnectMode,
            )
            SettingsSection.Routing -> RoutingSection(
                apps = state.installedApps,
                selectedPackages = settings.excludedAppPackages,
                loading = state.installedAppsLoading,
                onToggleExcludedApp = onToggleExcludedApp,
                onRefreshApps = onRefreshApps,
            )
            SettingsSection.UrlCommands -> UrlCommandsSection()
            SettingsSection.SubscriptionHeaders -> SubscriptionHeadersSection()
        }
    }
}

@Composable
private fun SettingsMenu(
    onOpenSection: (SettingsSection) -> Unit,
    onResetSettings: () -> Unit,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current

    SettingsSection.entries.filter { it.isTopLevel }.forEach { section ->
        SettingsSectionButton(
            section = section,
            onOpenSection = onOpenSection,
        )
    }
    ResetSettingsButton(onClick = { showResetConfirmation = true })

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(localized(language, "Сбросить настройки?", "Reset settings?")) },
            text = {
                Text(
                    localized(
                        language,
                        "Все настройки вернутся к значениям по умолчанию.\nСерверы и подписки останутся.",
                        "All settings will return to defaults.\nServers and subscriptions will stay.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onResetSettings()
                    },
                ) {
                    Text(localized(language, "Сбросить", "Reset"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(localized(language, "Отмена", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun ResetSettingsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(appText("Сбросить настройки по умолчанию", "Reset settings to defaults"))
    }
}

@Composable
private fun AdvancedSettingsSection(onOpenSection: (SettingsSection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = appText(
                "Эти параметры влияют на VPN runtime, локальные прокси-порты, DNS и диагностику. Обычно их не нужно менять без причины.",
                "These settings affect the VPN runtime, local proxy ports, DNS and diagnostics. Usually you do not need to change them.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        listOf(
            SettingsSection.Ports,
            SettingsSection.Vpn,
            SettingsSection.DnsLogs,
        ).forEach { section ->
            SettingsSectionButton(
                section = section,
                onOpenSection = onOpenSection,
            )
        }
    }
}

@Composable
private fun SettingsSectionButton(
    section: SettingsSection,
    onOpenSection: (SettingsSection) -> Unit,
) {
    val language = LocalAppLanguage.current
    OutlinedButton(
        onClick = { onOpenSection(section) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(section.title(language))
                Text(
                    section.description(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = section.title(language))
        }
    }
}

@Composable
private fun InterfaceSection(
    language: AppLanguage,
    textSize: AppTextSize,
    accentColor: AppAccentColor,
    backgroundStyle: AppBackgroundStyle,
    onUpdateInterface: (AppLanguage, AppTextSize) -> Unit,
    onUpdateAppearance: (AppAccentColor, AppBackgroundStyle) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(appText("Интерфейс", "Interface"), style = MaterialTheme.typography.titleMedium)
            Text(
                appText(
                    "Язык, размер текста, цвет интерфейса и фон применяются сразу ко всему приложению.",
                    "Language, text size, interface color and background are applied to the whole app immediately.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            SettingsOptionGroup(
                title = appText("Язык", "Language"),
                options = AppLanguage.entries,
                selected = language,
                label = { option ->
                    when (option) {
                        AppLanguage.RU -> "Русский"
                        AppLanguage.EN -> "English"
                    }
                },
                onSelect = { selectedLanguage -> onUpdateInterface(selectedLanguage, textSize) },
            )

            SettingsOptionGroup(
                title = appText("Размер текста", "Text size"),
                options = AppTextSize.entries,
                selected = textSize,
                label = { option -> option.label(language) },
                onSelect = { selectedSize -> onUpdateInterface(language, selectedSize) },
            )

            AppearanceOptionGroup(
                title = appText("Цвет интерфейса", "Interface color"),
                options = AppAccentColor.entries,
                selected = accentColor,
                label = { option -> option.label(language) },
                swatch = { option -> option.previewColor() },
                onSelect = { selectedAccent -> onUpdateAppearance(selectedAccent, backgroundStyle) },
            )

            AppearanceOptionGroup(
                title = appText("Фон приложения", "App background"),
                options = AppBackgroundStyle.entries,
                selected = backgroundStyle,
                label = { option -> option.label(language) },
                swatch = { option -> option.previewColor() },
                onSelect = { selectedBackground -> onUpdateAppearance(accentColor, selectedBackground) },
                circleSwatch = false,
            )
        }
    }
}

@Composable
private fun <T> AppearanceOptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    swatch: (T) -> Color,
    onSelect: (T) -> Unit,
    circleSwatch: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        options.forEach { option ->
            val isSelected = option == selected
            OutlinedButton(
                onClick = { onSelect(option) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isSelected) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (circleSwatch) 24.dp else 34.dp, height = 24.dp)
                            .clip(if (circleSwatch) CircleShape else RoundedCornerShape(8.dp))
                            .background(swatch(option))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                shape = if (circleSwatch) CircleShape else RoundedCornerShape(8.dp),
                            ),
                    )
                    Text(
                        label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsOptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        options.forEach { option ->
            val isSelected = option == selected
            OutlinedButton(
                onClick = { onSelect(option) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isSelected) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PortsSection(
    socksPort: String,
    httpPort: String,
    dnsPort: String,
    onSocksPort: (String) -> Unit,
    onHttpPort: (String) -> Unit,
    onDnsPort: (String) -> Unit,
    onSavePorts: () -> Unit,
) {
    val saveText = appText("Сохранить порты", "Save ports")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PortField("SOCKS", socksPort, onSocksPort)
            PortField("HTTP", httpPort, onHttpPort)
            PortField("Local DNS", dnsPort, onDnsPort)
            Button(onClick = onSavePorts) {
                Icon(Icons.Default.Save, contentDescription = saveText)
                Text(saveText)
            }
        }
    }
}

@Composable
private fun VpnSection(
    mtu: String,
    onMtu: (String) -> Unit,
    xrayTunModeEnabled: Boolean,
    routeAllTraffic: Boolean,
    allowIpv6: Boolean,
    onUpdateTun: (String, Boolean, Boolean, Boolean) -> Unit,
) {
    val saveText = appText("Сохранить VPN", "Save VPN")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PortField("TUN MTU", mtu, onMtu)
            SettingSwitch("Xray TUN mode", xrayTunModeEnabled) {
                onUpdateTun(mtu, it, routeAllTraffic, allowIpv6)
            }
            SettingSwitch("Route all traffic", routeAllTraffic) {
                onUpdateTun(mtu, xrayTunModeEnabled, it, allowIpv6)
            }
            SettingSwitch("IPv6 routes", allowIpv6) {
                onUpdateTun(mtu, xrayTunModeEnabled, routeAllTraffic, it)
            }
            Button(onClick = { onUpdateTun(mtu, xrayTunModeEnabled, routeAllTraffic, allowIpv6) }) {
                Icon(Icons.Default.Save, contentDescription = saveText)
                Text(saveText)
            }
        }
    }
}

@Composable
private fun DnsLogsSection(
    dns: String,
    logsEnabled: Boolean,
    debugConfigPreviewEnabled: Boolean,
    onDns: (String) -> Unit,
    onSaveDns: () -> Unit,
    onUpdateLogs: (Boolean, Boolean) -> Unit,
) {
    val saveText = appText("Сохранить DNS", "Save DNS")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = dns,
                onValueChange = onDns,
                label = { Text(appText("DNS-серверы через запятую", "DNS servers, comma-separated")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSaveDns) {
                Icon(Icons.Default.Save, contentDescription = saveText)
                Text(saveText)
            }
            SettingSwitch(appText("Runtime-логи", "Runtime logs"), logsEnabled) {
                onUpdateLogs(it, debugConfigPreviewEnabled)
            }
            SettingSwitch(appText("Debug-превью конфига", "Debug config preview"), debugConfigPreviewEnabled) {
                onUpdateLogs(logsEnabled, it)
            }
        }
    }
}

@Composable
private fun PingSection(
    selected: PingProtocol,
    onPingProtocol: (PingProtocol) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(appText("Протокол проверки", "Check protocol"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PingProtocolPicker(
                selected = selected,
                onSelect = onPingProtocol,
            )
        }
    }
}

@Composable
private fun AutoConnectSection(
    selected: AutoConnectMode,
    onAutoConnectMode: (AutoConnectMode) -> Unit,
) {
    val language = LocalAppLanguage.current
    val modes = listOf(
        AutoConnectMode.FIRST_AVAILABLE,
        AutoConnectMode.LOWEST_PING,
        AutoConnectMode.FIRST_FROM_TOP_FIVE,
    )

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localized(language, "Автоподключение", "Auto connect"), style = MaterialTheme.typography.titleMedium)
            Text(
                localized(
                    language,
                    "Если сценарий включен, кнопка Start сначала проверяет последний выбранный конфиг. Если он не отвечает на пинг, приложение подбирает новый сервер по выбранному сценарию.",
                    "When a scenario is enabled, Start first checks the last selected profile. If it does not answer ping, the app picks another server by the selected scenario.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                localized(language, "Пинг выполняется протоколом из раздела \"Пинг\".", "Ping uses the protocol from the Ping section."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            modes.forEach { mode ->
                AutoConnectModeRow(
                    mode = mode,
                    selected = selected == mode,
                    onToggle = { enabled ->
                        onAutoConnectMode(if (enabled) mode else AutoConnectMode.DISABLED)
                    },
                )
            }
        }
    }
}

@Composable
private fun AutoConnectModeRow(
    mode: AutoConnectMode,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val language = LocalAppLanguage.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!selected) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(mode.title(language), style = MaterialTheme.typography.bodyLarge)
            Text(
                mode.description(language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = selected,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun RoutingSection(
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    loading: Boolean,
    onToggleExcludedApp: (String, Boolean) -> Unit,
    onRefreshApps: () -> Unit,
) {
    val language = LocalAppLanguage.current
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(normalized, ignoreCase = true) ||
                    app.packageName.contains(normalized, ignoreCase = true)
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(localized(language, "Исключения из VPN", "VPN exclusions"), style = MaterialTheme.typography.titleMedium)
            Text(
                localized(
                    language,
                    "Выбранные приложения будут работать напрямую, без туннелирования через VPN.",
                    "Selected apps will use the network directly without VPN tunneling.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(localized(language, "Поиск приложения", "Search app")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(onClick = onRefreshApps, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (loading) {
                        localized(language, "Загрузка...", "Loading...")
                    } else {
                        localized(language, "Обновить список приложений", "Refresh app list")
                    },
                )
            }
            Text(
                localized(language, "Исключено: ${selectedPackages.size}", "Excluded: ${selectedPackages.size}"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (!loading && filteredApps.isEmpty()) {
                Text(
                    localized(language, "Приложения не найдены", "No apps found"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            filteredApps.take(MAX_ROUTING_APPS_IN_SECTION).forEachIndexed { index, app ->
                RoutingAppRow(
                    app = app,
                    excluded = app.packageName in selectedPackages,
                    onToggle = { onToggleExcludedApp(app.packageName, it) },
                )
                if (index != filteredApps.lastIndex.coerceAtMost(MAX_ROUTING_APPS_IN_SECTION - 1)) {
                    HorizontalDivider()
                }
            }
            if (filteredApps.size > MAX_ROUTING_APPS_IN_SECTION) {
                Text(
                    localized(
                        language,
                        "Показаны первые $MAX_ROUTING_APPS_IN_SECTION приложений, уточните поиск.",
                        "Showing the first $MAX_ROUTING_APPS_IN_SECTION apps. Narrow the search.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RoutingAppRow(
    app: InstalledApp,
    excluded: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!excluded) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = excluded, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName + if (app.isSystemApp) " · system" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UrlCommandsSection() {
    val clipboard = LocalClipboardManager.current
    val language = LocalAppLanguage.current
    val groups = listOf(
        UrlCommandGroup(
            title = localized(language, "Запустить туннель", "Start tunnel"),
            commands = listOf(
                UrlCommand("vlevpn://connect"),
                UrlCommand("vlevpn://open"),
            ),
        ),
        UrlCommandGroup(
            title = localized(language, "Остановить соединение", "Stop connection"),
            commands = listOf(
                UrlCommand("vlevpn://disconnect"),
                UrlCommand("vlevpn://close"),
            ),
        ),
        UrlCommandGroup(
            title = localized(language, "Переключить соединение", "Toggle connection"),
            commands = listOf(UrlCommand("vlevpn://toggle")),
        ),
        UrlCommandGroup(
            title = localized(language, "Добавить конфигурацию", "Add configuration"),
            commands = listOf(
                UrlCommand(
                    "vlevpn://import/{base64}",
                    localized(language, "Импорт base64 с автоопределением типа", "Import base64 with type auto-detection"),
                ),
                UrlCommand("vlevpn://add/{url}", localized(language, "Добавить напрямую по URL", "Add directly by URL")),
            ),
        ),
        UrlCommandGroup(
            title = localized(language, "Маршрутизация", "Routing"),
            commands = listOf(
                UrlCommand("vlevpn://routing/add/{base64}", localized(language, "Добавить исключения приложений", "Add app exclusions")),
                UrlCommand(
                    "vlevpn://routing/onadd/{base64}",
                    localized(language, "Добавить и применить через переподключение", "Add and apply by reconnecting"),
                ),
            ),
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = group.title.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        group.commands.forEachIndexed { index, command ->
                            UrlCommandRow(
                                command = command,
                                onCopy = { clipboard.setText(AnnotatedString(command.url)) },
                            )
                            if (index != group.commands.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(localized(language, "Примечание", "Note"), style = MaterialTheme.typography.titleMedium)
                Text(
                    localized(
                        language,
                        "Эти URL-схемы можно использовать в быстрых командах, автоматизациях и других приложениях для управления туннелем.",
                        "These URL schemes can be used in shortcuts, automations and other apps to control the tunnel.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun UrlCommandRow(
    command: UrlCommand,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(command.url, style = MaterialTheme.typography.bodyLarge)
            command.description?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL command")
        }
    }
}

@Composable
private fun SubscriptionHeadersSection() {
    val language = LocalAppLanguage.current
    val items = listOf(
        "profile-title" to localized(language, "Название подписки. Значение base64:... будет декодировано.", "Subscription title. A base64:... value will be decoded."),
        "profile-update-interval" to localized(language, "Период автообновления в часах. Если отсутствует, используется 12 часов.", "Auto-update interval in hours. If absent, 12 hours is used."),
        "update-always: true" to localized(language, "Включает для подписки обновление при холодном запуске приложения.", "Enables subscription update on cold app launch."),
        "profile-update-always: true" to localized(language, "Совместимый алиас для update-always.", "Compatible alias for update-always."),
        "subscription-userinfo" to localized(language, "Трафик и срок действия: upload, download, total, expire.", "Traffic and expiry: upload, download, total, expire."),
        "support-url" to localized(language, "Ссылка поддержки, которая отображается в шапке подписки.", "Support link shown in the subscription header."),
        "profile-web-page-url" to localized(language, "Резервная ссылка страницы подписки.", "Fallback subscription web page URL."),
        "announce" to localized(language, "Текст объявления в шапке подписки. Значение base64:... будет декодировано.", "Announcement text in the subscription header. A base64:... value will be decoded."),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localized(language, "Функции подписок", "Subscription features"), style = MaterialTheme.typography.titleMedium)
                Text(
                    localized(
                        language,
                        "Приложение использует только параметры, которые пришли в headers ответа подписки.",
                        "The app uses only parameters that came from subscription response headers.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                items.forEach { (name, description) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(
                            description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PortField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PingProtocolPicker(
    selected: PingProtocol,
    onSelect: (PingProtocol) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.displayName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PingProtocol.entries.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text(protocol.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(protocol)
                    },
                )
            }
        }
    }
}

private enum class SettingsSection(
    val titleRu: String,
    val titleEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
) {
    Menu("Настройки", "Settings", "", ""),
    AutoConnect("Автоподключение", "Auto connect", "Автовыбор конфига перед запуском VPN", "Choose server before VPN start"),
    Routing("Маршрутизация", "Routing", "Исключение приложений из VPN", "Exclude apps from VPN"),
    Ping("Пинг", "Ping", "Протокол проверки локаций", "Server check protocol"),
    UrlCommands("URL-команды", "URL commands", "Быстрые команды и автоматизации", "Shortcuts and automations"),
    SubscriptionHeaders("Функции подписок", "Subscription features", "Headers подписки и автообновление", "Subscription headers and auto-update"),
    Interface("Интерфейс", "Interface", "Язык, текст, цвета и фон", "Language, text, colors and background"),
    Advanced("Расширенные настройки", "Advanced settings", "Порты, VPN mode, DNS и логи", "Ports, VPN mode, DNS and logs"),
    Ports("Local ports", "Local ports", "SOCKS, HTTP and local DNS ports", "SOCKS, HTTP and local DNS ports"),
    Vpn("VPN mode", "VPN mode", "TUN mode, routes and MTU", "TUN mode, routes and MTU"),
    DnsLogs("DNS and logs", "DNS and logs", "DNS servers and runtime logging", "DNS servers and runtime logging"),
}

private fun SettingsSection.title(language: AppLanguage): String =
    localized(language, titleRu, titleEn)

private fun SettingsSection.description(language: AppLanguage): String =
    localized(language, descriptionRu, descriptionEn)

private fun AppTextSize.label(language: AppLanguage): String =
    when (this) {
        AppTextSize.SMALL -> localized(language, "Маленький", "Small")
        AppTextSize.NORMAL -> localized(language, "Обычный", "Normal")
        AppTextSize.LARGE -> localized(language, "Крупный", "Large")
        AppTextSize.EXTRA_LARGE -> localized(language, "Очень крупный", "Extra large")
    }

private fun AppAccentColor.label(language: AppLanguage): String =
    when (this) {
        AppAccentColor.TEAL -> localized(language, "Бирюзовый", "Teal")
        AppAccentColor.BLUE -> localized(language, "Синий", "Blue")
        AppAccentColor.VIOLET -> localized(language, "Фиолетовый", "Violet")
        AppAccentColor.RUBY -> localized(language, "Рубиновый", "Ruby")
        AppAccentColor.GRAPHITE -> localized(language, "Графитовый", "Graphite")
    }

private fun AppBackgroundStyle.label(language: AppLanguage): String =
    when (this) {
        AppBackgroundStyle.LIGHT -> localized(language, "Светлый", "Light")
        AppBackgroundStyle.MIST -> localized(language, "Холодный светлый", "Mist")
        AppBackgroundStyle.WARM -> localized(language, "Теплый светлый", "Warm")
        AppBackgroundStyle.DARK -> localized(language, "Темный", "Dark")
    }

private fun AutoConnectMode.title(language: AppLanguage): String =
    when (this) {
        AutoConnectMode.DISABLED -> localized(language, "Выключено", "Disabled")
        AutoConnectMode.FIRST_AVAILABLE -> localized(language, "Первый доступный", "First available")
        AutoConnectMode.LOWEST_PING -> localized(language, "Наименьший пинг", "Lowest ping")
        AutoConnectMode.FIRST_FROM_TOP_FIVE -> localized(language, "Первый из топ 5", "First from top 5")
    }

private fun AutoConnectMode.description(language: AppLanguage): String =
    when (this) {
        AutoConnectMode.DISABLED -> localized(
            language,
            "Start подключает выбранный вручную конфиг без автоподбора.",
            "Start connects the manually selected profile without auto-picking.",
        )
        AutoConnectMode.FIRST_AVAILABLE -> localized(
            language,
            "Проверяет пинг и подключается к первому серверу, который ответил.",
            "Checks ping and connects to the first server that responds.",
        )
        AutoConnectMode.LOWEST_PING -> localized(
            language,
            "Проверяет все серверы и выбирает минимальное время отклика.",
            "Checks all servers and selects the lowest response time.",
        )
        AutoConnectMode.FIRST_FROM_TOP_FIVE -> localized(
            language,
            "Берет 5 серверов с лучшим пингом и выбирает первый из них по порядку списка.",
            "Takes 5 servers with the best ping and selects the first one by list order.",
        )
    }

private val SettingsSection.isTopLevel: Boolean
    get() = this in setOf(
        SettingsSection.Interface,
        SettingsSection.Advanced,
        SettingsSection.Routing,
        SettingsSection.Ping,
        SettingsSection.AutoConnect,
        SettingsSection.UrlCommands,
        SettingsSection.SubscriptionHeaders,
    )

private fun SettingsSection.backTarget(): SettingsSection =
    when (this) {
        SettingsSection.Ports,
        SettingsSection.Vpn,
        SettingsSection.DnsLogs,
        -> SettingsSection.Advanced
        else -> SettingsSection.Menu
    }

private const val MAX_ROUTING_APPS_IN_SECTION = 120

private data class UrlCommandGroup(
    val title: String,
    val commands: List<UrlCommand>,
)

private data class UrlCommand(
    val url: String,
    val description: String? = null,
)
