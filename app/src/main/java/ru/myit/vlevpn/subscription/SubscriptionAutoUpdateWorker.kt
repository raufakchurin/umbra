package ru.myit.vlevpn.subscription

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class SubscriptionAutoUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, SubscriptionAutoUpdateEntryPoint::class.java)
                .subscriptionAutoUpdateManager()
                .refreshDueSubscriptions()
            Result.success()
        }.getOrElse {
            Result.retry()
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SubscriptionAutoUpdateEntryPoint {
        fun subscriptionAutoUpdateManager(): SubscriptionAutoUpdateManager
    }
}
