package ru.myit.vlevpn.data.backend

import javax.inject.Inject
import javax.inject.Singleton
import ru.myit.vlevpn.domain.model.ProxyProtocol
import ru.myit.vlevpn.domain.model.ServerId
import ru.myit.vlevpn.domain.model.ServerProfile

@Singleton
class FakeBackendApi @Inject constructor() : BackendApi {
    private val fixtureTime = 1_714_000_000_000L

    override suspend fun registerDevice(): DeviceRegistration =
        DeviceRegistration(deviceId = "fake-device-local", issuedAtMillis = fixtureTime)

    override suspend fun updateSubscription(): SubscriptionSnapshot =
        SubscriptionSnapshot(
            servers = listOf(
                ServerProfile(
                    id = ServerId("fixture-vless-local"),
                    name = "Fixture VLESS",
                    protocol = ProxyProtocol.VLESS,
                    host = "vpn.example.invalid",
                    port = 443,
                    credential = "00000000-0000-4000-8000-000000000001",
                    password = "",
                    method = "",
                    transport = "tcp",
                    security = "tls",
                    sni = "vpn.example.invalid",
                    path = "",
                    customJson = "",
                    createdAtMillis = fixtureTime,
                    updatedAtMillis = fixtureTime,
                ),
                ServerProfile(
                    id = ServerId("fixture-shadowsocks-local"),
                    name = "Fixture Shadowsocks",
                    protocol = ProxyProtocol.SHADOWSOCKS,
                    host = "ss.example.invalid",
                    port = 8388,
                    credential = "",
                    password = "local-fixture-password",
                    method = "2022-blake3-aes-128-gcm",
                    transport = "tcp",
                    security = "none",
                    sni = "",
                    path = "",
                    customJson = "",
                    createdAtMillis = fixtureTime,
                    updatedAtMillis = fixtureTime,
                ),
            ),
            updatedAtMillis = fixtureTime,
        )

    override suspend fun checkAppUpdate(): AppUpdateMetadata =
        AppUpdateMetadata(
            latestVersionName = "0.1.0",
            required = false,
            notes = "Fake metadata fixture. No network request was made.",
        )

    override suspend fun fetchRemoteConfig(): RemoteConfig =
        RemoteConfig(
            preferredDns = listOf("1.1.1.1", "8.8.8.8"),
            message = "Fake remote config",
        )

    override suspend fun uploadReport(report: RuntimeReport): ReportUploadResult =
        ReportUploadResult(accepted = true, reference = "fake-report-${report.logCount}")
}
