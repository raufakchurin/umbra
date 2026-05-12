package ru.myit.vlevpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle

@Composable
fun VleVpnTheme(
    textScale: Float = 1f,
    accentColor: AppAccentColor = AppAccentColor.TEAL,
    backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.LIGHT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(accentColor, backgroundStyle),
        typography = MaterialTheme.typography.scaled(textScale),
        content = content,
    )
}

fun AppAccentColor.previewColor(): Color =
    when (this) {
        AppAccentColor.TEAL -> Color(0xFF136F63)
        AppAccentColor.BLUE -> Color(0xFF245EAD)
        AppAccentColor.VIOLET -> Color(0xFF6C4AB6)
        AppAccentColor.RUBY -> Color(0xFFA83D55)
        AppAccentColor.GRAPHITE -> Color(0xFF455A64)
    }

fun AppBackgroundStyle.previewColor(): Color =
    when (this) {
        AppBackgroundStyle.LIGHT -> Color(0xFFFAFAF8)
        AppBackgroundStyle.MIST -> Color(0xFFF4F8FA)
        AppBackgroundStyle.WARM -> Color(0xFFFBF6F0)
        AppBackgroundStyle.DARK -> Color(0xFF101416)
    }

private fun appColorScheme(accentColor: AppAccentColor, backgroundStyle: AppBackgroundStyle): ColorScheme {
    val accent = accentColor.previewColor()
    return if (backgroundStyle == AppBackgroundStyle.DARK) {
        darkColorScheme(
            primary = accent.lightened(0.28f),
            onPrimary = Color(0xFF071312),
            primaryContainer = accent,
            onPrimaryContainer = Color.White,
            secondary = accent.secondaryDark(),
            tertiary = accent.tertiaryDark(),
            background = Color(0xFF101416),
            onBackground = Color(0xFFE8ECEA),
            surface = Color(0xFF171D20),
            onSurface = Color(0xFFE8ECEA),
            surfaceVariant = Color(0xFF263034),
            onSurfaceVariant = Color(0xFFC5CFCC),
            outline = Color(0xFF83908D),
        )
    } else {
        val background = backgroundStyle.previewColor()
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.lightened(0.82f),
            onPrimaryContainer = accent.darkened(0.45f),
            secondary = accent.secondaryLight(),
            tertiary = accent.tertiaryLight(),
            background = background,
            onBackground = Color(0xFF202124),
            surface = backgroundStyle.surfaceColor(),
            onSurface = Color(0xFF202124),
            surfaceVariant = backgroundStyle.surfaceVariantColor(),
            onSurfaceVariant = Color(0xFF555F5C),
            outline = Color(0xFF6F7976),
        )
    }
}

private fun AppBackgroundStyle.surfaceColor(): Color =
    when (this) {
        AppBackgroundStyle.LIGHT -> Color.White
        AppBackgroundStyle.MIST -> Color(0xFFFFFFFF)
        AppBackgroundStyle.WARM -> Color(0xFFFFFCF8)
        AppBackgroundStyle.DARK -> Color(0xFF171D20)
    }

private fun AppBackgroundStyle.surfaceVariantColor(): Color =
    when (this) {
        AppBackgroundStyle.LIGHT -> Color(0xFFE7ECEA)
        AppBackgroundStyle.MIST -> Color(0xFFE6EEF2)
        AppBackgroundStyle.WARM -> Color(0xFFF0E7DE)
        AppBackgroundStyle.DARK -> Color(0xFF263034)
    }

private fun Color.secondaryLight(): Color =
    mixWith(Color(0xFF5E5A7C), 0.44f)

private fun Color.tertiaryLight(): Color =
    mixWith(Color(0xFF9A5D3A), 0.38f)

private fun Color.secondaryDark(): Color =
    lightened(0.18f).mixWith(Color(0xFFD1CCF3), 0.28f)

private fun Color.tertiaryDark(): Color =
    lightened(0.24f).mixWith(Color(0xFFFFC6A6), 0.22f)

private fun Color.lightened(amount: Float): Color =
    mixWith(Color.White, amount)

private fun Color.darkened(amount: Float): Color =
    mixWith(Color.Black, amount)

private fun Color.mixWith(other: Color, amount: Float): Color {
    val safeAmount = amount.coerceIn(0f, 1f)
    val keep = 1f - safeAmount
    return Color(
        red = red * keep + other.red * safeAmount,
        green = green * keep + other.green * safeAmount,
        blue = blue * keep + other.blue * safeAmount,
        alpha = alpha * keep + other.alpha * safeAmount,
    )
}

private fun Typography.scaled(scale: Float): Typography {
    val safeScale = scale.coerceIn(0.85f, 1.35f)
    if (safeScale == 1f) return this
    return copy(
        displayLarge = displayLarge.scaled(safeScale),
        displayMedium = displayMedium.scaled(safeScale),
        displaySmall = displaySmall.scaled(safeScale),
        headlineLarge = headlineLarge.scaled(safeScale),
        headlineMedium = headlineMedium.scaled(safeScale),
        headlineSmall = headlineSmall.scaled(safeScale),
        titleLarge = titleLarge.scaled(safeScale),
        titleMedium = titleMedium.scaled(safeScale),
        titleSmall = titleSmall.scaled(safeScale),
        bodyLarge = bodyLarge.scaled(safeScale),
        bodyMedium = bodyMedium.scaled(safeScale),
        bodySmall = bodySmall.scaled(safeScale),
        labelLarge = labelLarge.scaled(safeScale),
        labelMedium = labelMedium.scaled(safeScale),
        labelSmall = labelSmall.scaled(safeScale),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    copy(
        fontSize = fontSize.scaled(scale),
        lineHeight = lineHeight.scaled(scale),
    )

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (isUnspecified) this else this * scale
