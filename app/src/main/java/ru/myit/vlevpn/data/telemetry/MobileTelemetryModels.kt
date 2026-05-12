package ru.myit.vlevpn.data.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MobileTelemetryPayload(
    @SerialName("event_id") val eventId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("captured_at_local") val capturedAtLocal: String,
    @SerialName("app_key") val appKey: String,
    @SerialName("partner_id") val providerId: String? = null,
    @SerialName("provider_domain_hash") val providerDomainHash: String? = null,
    @SerialName("network_path") val networkPath: String = "direct-protected",
    val device: MobileTelemetryDevice,
    val activity: MobileTelemetryActivity,
    val errors: List<MobileTelemetryError>,
    val subscriptions: List<MobileTelemetrySubscription> = emptyList(),
    @SerialName("event_properties") val eventProperties: Map<String, String?> = emptyMap(),
)

@Serializable
data class MobileTelemetryBatchPayload(
    val events: List<MobileTelemetryPayload>,
)

@Serializable
data class MobileTelemetryBatchResponse(
    val status: String = "ok",
    val accepted: Int = 0,
    val duplicates: Int = 0,
    @SerialName("accepted_event_ids") val acceptedEventIds: List<String> = emptyList(),
    @SerialName("duplicate_event_ids") val duplicateEventIds: List<String> = emptyList(),
)

@Serializable
data class MobileTelemetryDevice(
    val hwid: String = "",
    @SerialName("install_id") val installId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: String,
    val platform: String = "android",
    @SerialName("os_name") val osName: String = "Android",
    @SerialName("os_version") val osVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    @SerialName("device_name") val deviceName: String,
    val locale: String,
    val timezone: String,
)

@Serializable
data class MobileTelemetrySubscription(
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("partner_id") val providerId: String? = null,
    @SerialName("provider_domain_hash") val providerDomainHash: String? = null,
    @SerialName("profiles_count") val profilesCount: Int,
    @SerialName("auto_update_on_launch") val autoUpdateOnLaunch: Boolean,
    @SerialName("update_interval_hours") val updateIntervalHours: Int,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("traffic_total_bytes") val trafficTotalBytes: Long? = null,
    @SerialName("traffic_used_bytes") val trafficUsedBytes: Long? = null,
)

@Serializable
data class MobileTelemetryActivity(
    @SerialName("runtime_state") val runtimeState: String,
    @SerialName("vpn_connected") val vpnConnected: Boolean,
    @SerialName("connected_at") val connectedAt: String? = null,
    @SerialName("selected_server_id") val selectedServerId: String? = null,
    @SerialName("selected_server_name") val selectedServerName: String? = null,
    @SerialName("selected_server_protocol") val selectedServerProtocol: String? = null,
    @SerialName("servers_count") val serversCount: Int,
    @SerialName("uplink_bytes") val uplinkBytes: Long,
    @SerialName("downlink_bytes") val downlinkBytes: Long,
    @SerialName("delay_ms") val delayMs: Long? = null,
)

@Serializable
data class MobileTelemetryError(
    val title: String,
    val message: String? = null,
    val severity: String = "error",
    val fingerprint: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    val payload: Map<String, String?>? = null,
)
