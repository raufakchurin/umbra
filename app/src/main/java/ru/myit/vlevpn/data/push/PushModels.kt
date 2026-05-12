package ru.myit.vlevpn.data.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushRegisterPayload(
    @SerialName("app_key") val appKey: String,
    @SerialName("install_id") val installId: String,
    val token: String,
    @SerialName("device_secret") val deviceSecret: String,
    val platform: String = "android",
    @SerialName("package_name") val packageName: String,
    @SerialName("partner_id") val providerId: String? = null,
    @SerialName("provider_domain_hash") val providerDomainHash: String? = null,
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val locale: String,
    val timezone: String,
)

@Serializable
data class PushRegisterResponse(
    val status: String = "ok",
    @SerialName("token_id") val tokenId: String = "",
)

@Serializable
data class InAppPushRegisterPayload(
    @SerialName("app_key") val appKey: String,
    @SerialName("install_id") val installId: String,
    @SerialName("device_secret") val deviceSecret: String,
    val platform: String = "android",
    @SerialName("package_name") val packageName: String,
    @SerialName("partner_id") val providerId: String? = null,
    @SerialName("provider_domain_hash") val providerDomainHash: String? = null,
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val locale: String,
    val timezone: String,
)

@Serializable
data class InAppPushRegisterResponse(
    val status: String = "ok",
    @SerialName("registration_id") val registrationId: String = "",
)

@Serializable
data class InAppPushAuthPayload(
    @SerialName("app_key") val appKey: String,
    @SerialName("install_id") val installId: String,
    val timestamp: Long,
    val nonce: String,
    val signature: String,
)

@Serializable
data class InAppPushInboxResponse(
    val messages: List<InAppPushMessage> = emptyList(),
)

@Serializable
data class InAppPushMessage(
    val kind: String,
    val version: String,
    @SerialName("delivery_id") val deliveryId: String,
    @SerialName("campaign_id") val campaignId: String,
    val payload: String,
    val signature: String,
)

@Serializable
data class InAppPushAckPayload(
    @SerialName("app_key") val appKey: String,
    @SerialName("install_id") val installId: String,
    val timestamp: Long,
    val nonce: String,
    val signature: String,
    @SerialName("delivery_ids") val deliveryIds: List<String>,
)

@Serializable
data class InAppPushAckResponse(
    val acknowledged: Int = 0,
)

@Serializable
data class PartnerPushPayload(
    val title: String,
    val message: String,
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("display_config") val displayConfig: PartnerPushDisplayConfig = PartnerPushDisplayConfig(),
    val severity: String,
    @SerialName("push_type") val pushType: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PartnerPushDisplayConfig(
    val theme: String = "glass",
    val icon: String = "bell",
    @SerialName("header_label") val headerLabel: String? = null,
    @SerialName("show_icon") val showIcon: Boolean = true,
    @SerialName("show_close_button") val showCloseButton: Boolean = true,
    @SerialName("show_dismiss_button") val showDismissButton: Boolean = true,
    @SerialName("show_action_button") val showActionButton: Boolean = true,
    @SerialName("show_action_url") val showActionUrl: Boolean = true,
    @SerialName("dismiss_button_label") val dismissButtonLabel: String? = null,
    @SerialName("action_button_label") val actionButtonLabel: String? = null,
    @SerialName("title_size") val titleSize: String = "large",
    @SerialName("message_size") val messageSize: String = "normal",
    @SerialName("message_align") val messageAlign: String = "start",
)
