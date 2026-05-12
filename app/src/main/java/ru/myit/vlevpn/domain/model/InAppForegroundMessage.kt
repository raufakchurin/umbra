package ru.myit.vlevpn.domain.model

data class InAppForegroundMessage(
    val deliveryId: String,
    val title: String,
    val message: String,
    val actionUrl: String? = null,
    val severity: String,
    val pushType: String,
    val displayConfig: InAppMessageDisplayConfig = InAppMessageDisplayConfig(),
)

data class InAppMessageDisplayConfig(
    val theme: String = "glass",
    val icon: String = "bell",
    val headerLabel: String? = null,
    val showIcon: Boolean = true,
    val showCloseButton: Boolean = true,
    val showDismissButton: Boolean = true,
    val showActionButton: Boolean = true,
    val showActionUrl: Boolean = true,
    val dismissButtonLabel: String? = null,
    val actionButtonLabel: String? = null,
    val titleSize: String = "large",
    val messageSize: String = "normal",
    val messageAlign: String = "start",
)
