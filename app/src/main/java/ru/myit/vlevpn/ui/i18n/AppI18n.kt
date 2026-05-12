package ru.myit.vlevpn.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import ru.myit.vlevpn.domain.model.AppLanguage

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.RU }

@Composable
fun appText(ru: String, en: String): String =
    when (LocalAppLanguage.current) {
        AppLanguage.RU -> ru
        AppLanguage.EN -> en
    }

fun localized(language: AppLanguage, ru: String, en: String): String =
    when (language) {
        AppLanguage.RU -> ru
        AppLanguage.EN -> en
    }
