package ru.myit.vlevpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import ru.myit.vlevpn.domain.model.AppAccentColor
import ru.myit.vlevpn.domain.model.AppBackgroundStyle

@Composable
fun VleVpnTheme(
    textScale: Float = 1f,
    accentColor: AppAccentColor = AppAccentColor.TEAL,
    backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.LIGHT,
    customAccentColor: Color? = null,
    customBackgroundColor: Color? = null,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(accentColor, backgroundStyle, customAccentColor, customBackgroundColor),
        typography = AppTypography.scaled(textScale),
        content = content,
    )
}

fun AppAccentColor.previewColor(): Color =
    when (this) {
        AppAccentColor.TEAL -> Color(0xFF126F63)
        AppAccentColor.BLUE -> Color(0xFF2F63A8)
        AppAccentColor.VIOLET -> Color(0xFF6A52BE)
        AppAccentColor.RUBY -> Color(0xFFBD4455)
        AppAccentColor.GRAPHITE -> Color(0xFF455A64)
    }

fun AppBackgroundStyle.previewColor(): Color =
    when (this) {
        AppBackgroundStyle.LIGHT -> Color(0xFFF7F8F4)
        AppBackgroundStyle.MIST -> Color(0xFFEEF4F2)
        AppBackgroundStyle.WARM -> Color(0xFFFBF7EF)
        AppBackgroundStyle.DARK -> Color(0xFF0F1416)
    }

private fun appColorScheme(
    accentColor: AppAccentColor,
    backgroundStyle: AppBackgroundStyle,
    customAccentColor: Color?,
    customBackgroundColor: Color?,
): ColorScheme {
    val accent = customAccentColor ?: accentColor.previewColor()
    val useDark = customBackgroundColor?.let { it.luminance() < 0.35f } ?: (backgroundStyle == AppBackgroundStyle.DARK)
    return if (useDark) {
        val background = customBackgroundColor ?: Color(0xFF0F1416)
        darkColorScheme(
            primary = accent.lightened(0.28f),
            onPrimary = Color(0xFF071312),
            primaryContainer = accent,
            onPrimaryContainer = Color.White,
            secondary = accent.secondaryDark(),
            tertiary = accent.tertiaryDark(),
            background = background,
            onBackground = Color(0xFFE8ECEA),
            surface = Color(0xFF171E21),
            onSurface = Color(0xFFE8ECEA),
            surfaceVariant = Color(0xFF263236),
            onSurfaceVariant = Color(0xFFC5CFCC),
            outline = Color(0xFF64726E),
        )
    } else {
        val background = customBackgroundColor ?: backgroundStyle.previewColor()
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.lightened(0.82f),
            onPrimaryContainer = accent.darkened(0.45f),
            secondary = accent.secondaryLight(),
            tertiary = accent.tertiaryLight(),
            background = background,
            onBackground = Color(0xFF111918),
            surface = backgroundStyle.surfaceColor(),
            onSurface = Color(0xFF111918),
            surfaceVariant = backgroundStyle.surfaceVariantColor(),
            onSurfaceVariant = Color(0xFF60706B),
            outline = Color(0xFFDDE5E1),
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
        AppBackgroundStyle.LIGHT -> Color(0xFFEEF4F2)
        AppBackgroundStyle.MIST -> Color(0xFFE8F0EE)
        AppBackgroundStyle.WARM -> Color(0xFFF0E7DE)
        AppBackgroundStyle.DARK -> Color(0xFF263034)
    }

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

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
