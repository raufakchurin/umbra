package ru.myit.vlevpn.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.myit.vlevpn.ui.i18n.appText

@Composable
fun LogsRoute(
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LogsScreen(
        state = state,
        onCopyLogs = {
            context.copyToClipboard("VLE VPN logs", state.plainText)
        },
        onCopyConfig = {
            context.copyToClipboard("VLE VPN config", state.generatedConfigPreview.orEmpty())
        },
        onClear = viewModel::clear,
    )
}

@Composable
private fun LogsScreen(
    state: LogsUiState,
    onCopyLogs: () -> Unit,
    onCopyConfig: () -> Unit,
    onClear: () -> Unit,
) {
    val copyLogsText = appText("Скопировать логи", "Copy logs")
    val clearText = appText("Очистить", "Clear")
    val copyConfigText = appText("Скопировать конфиг", "Copy config")
    val noLogsText = appText("Логов пока нет", "No logs")
    val noConfigText = appText("Сгенерированный конфиг пока отсутствует", "No generated config yet")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(appText("Логи", "Logs"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopyLogs, enabled = state.logs.isNotEmpty()) {
                Icon(Icons.Default.ContentCopy, contentDescription = copyLogsText)
                Text(copyLogsText)
            }
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = clearText)
                Text(clearText)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.logs.isEmpty()) {
                    Text(noLogsText)
                } else {
                    state.logs.forEach { entry ->
                        Text("${entry.level}: ${entry.message}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(appText("Превью Xray config", "Generated config preview"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopyConfig, enabled = !state.generatedConfigPreview.isNullOrBlank()) {
                Icon(Icons.Default.ContentCopy, contentDescription = copyConfigText)
                Text(copyConfigText)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(
                text = state.generatedConfigPreview ?: noConfigText,
                modifier = Modifier
                    .padding(14.dp)
                    .horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun Context.copyToClipboard(label: String, value: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
}
