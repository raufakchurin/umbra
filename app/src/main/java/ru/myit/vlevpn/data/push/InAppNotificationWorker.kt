package ru.myit.vlevpn.data.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class InAppNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, InAppNotificationEntryPoint::class.java)
                .inAppNotificationManager()
                .runDueWork()
            Result.success()
        }.getOrElse {
            Result.retry()
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface InAppNotificationEntryPoint {
        fun inAppNotificationManager(): InAppNotificationManager
    }
}
