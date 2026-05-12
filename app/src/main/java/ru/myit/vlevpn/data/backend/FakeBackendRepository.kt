package ru.myit.vlevpn.data.backend

import javax.inject.Inject
import javax.inject.Singleton
import ru.myit.vlevpn.domain.repository.BackendRepository

@Singleton
class FakeBackendRepository @Inject constructor(
    private val api: BackendApi,
) : BackendRepository {
    override suspend fun registerDevice(): DeviceRegistration = api.registerDevice()
    override suspend fun updateSubscription(): SubscriptionSnapshot = api.updateSubscription()
    override suspend fun checkAppUpdate(): AppUpdateMetadata = api.checkAppUpdate()
    override suspend fun fetchRemoteConfig(): RemoteConfig = api.fetchRemoteConfig()
    override suspend fun uploadReport(report: RuntimeReport): ReportUploadResult = api.uploadReport(report)
}
