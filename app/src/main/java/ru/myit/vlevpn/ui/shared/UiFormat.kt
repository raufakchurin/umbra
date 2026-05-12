package ru.myit.vlevpn.ui.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.myit.vlevpn.runtime.RuntimeState

fun runtimeStateLabel(state: RuntimeState): String = when (state) {
    RuntimeState.Idle -> "Idle"
    RuntimeState.PreparingVpnPermission -> "Preparing VPN permission"
    RuntimeState.StartingForeground -> "Starting foreground service"
    RuntimeState.BuildingConfig -> "Building Xray config"
    RuntimeState.EstablishingVpn -> "Establishing VPN"
    RuntimeState.StartingNativeCore -> "Starting runtime core"
    RuntimeState.VerifyingConnection -> "Verifying connection"
    is RuntimeState.Running -> "Running"
    RuntimeState.Stopping -> "Stopping"
    is RuntimeState.Error -> "Error: ${state.message}"
}

fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = listOf("KB", "MB", "GB")
    var scaled = value / 1024.0
    var index = 0
    while (scaled >= 1024 && index < units.lastIndex) {
        scaled /= 1024
        index += 1
    }
    return "%.1f %s".format(java.util.Locale.US, scaled, units[index])
}

@Composable
fun IconTextButton(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(enabled = enabled, onClick = onClick) {
        Icon(icon, contentDescription = text)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
