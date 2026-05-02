package com.tuapp.fintrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tuapp.fintrack.work.PayPeriodAdvanceWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        schedulePayPeriodWorker()
    }

    private fun schedulePayPeriodWorker() {
        val workRequest = PeriodicWorkRequestBuilder<PayPeriodAdvanceWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "pay_period_advance",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
