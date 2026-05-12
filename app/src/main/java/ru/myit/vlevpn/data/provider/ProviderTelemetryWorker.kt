package ru.myit.vlevpn.data.provider

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ProviderTelemetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, ProviderTelemetryEntryPoint::class.java)
                .providerTelemetryManager()
                .sendDueProviderCheck()
            Result.success()
        }.getOrElse {
            Result.retry()
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderTelemetryEntryPoint {
        fun providerTelemetryManager(): ProviderTelemetryManager
    }
}
