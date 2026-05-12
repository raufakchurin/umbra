package ru.myit.vlevpn.data.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class MobileTelemetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, MobileTelemetryEntryPoint::class.java)
                .mobileTelemetryManager()
                .runDueWork()
            Result.success()
        }.getOrElse {
            if (it is MobileTelemetryPermanentException) Result.failure() else Result.retry()
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MobileTelemetryEntryPoint {
        fun mobileTelemetryManager(): MobileTelemetryManager
    }
}
