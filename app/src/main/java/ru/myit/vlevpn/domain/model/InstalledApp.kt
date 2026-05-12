package ru.myit.vlevpn.domain.model

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)
