package ru.myit.vlevpn.data.backend

import ru.myit.vlevpn.domain.model.ServerProfile

interface BackendApi {
    suspend fun registerDevice(): DeviceRegistration
    suspend fun updateSubscription(): SubscriptionSnapshot
    suspend fun checkAppUpdate(): AppUpdateMetadata
    suspend fun fetchRemoteConfig(): RemoteConfig
    suspend fun uploadReport(report: RuntimeReport): ReportUploadResult
}

data class DeviceRegistration(
    val deviceId: String,
    val issuedAtMillis: Long,
)

data class SubscriptionSnapshot(
    val servers: List<ServerProfile>,
    val updatedAtMillis: Long,
)

data class AppUpdateMetadata(
    val latestVersionName: String,
    val required: Boolean,
    val notes: String,
)

data class RemoteConfig(
    val preferredDns: List<String>,
    val message: String,
)

data class RuntimeReport(
    val runtimeState: String,
    val logCount: Int,
)

data class ReportUploadResult(
    val accepted: Boolean,
    val reference: String,
)
