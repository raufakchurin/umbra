package ru.myit.vlevpn.domain.repository

import ru.myit.vlevpn.data.backend.AppUpdateMetadata
import ru.myit.vlevpn.data.backend.DeviceRegistration
import ru.myit.vlevpn.data.backend.RemoteConfig
import ru.myit.vlevpn.data.backend.ReportUploadResult
import ru.myit.vlevpn.data.backend.RuntimeReport
import ru.myit.vlevpn.data.backend.SubscriptionSnapshot

interface BackendRepository {
    suspend fun registerDevice(): DeviceRegistration
    suspend fun updateSubscription(): SubscriptionSnapshot
    suspend fun checkAppUpdate(): AppUpdateMetadata
    suspend fun fetchRemoteConfig(): RemoteConfig
    suspend fun uploadReport(report: RuntimeReport): ReportUploadResult
}
