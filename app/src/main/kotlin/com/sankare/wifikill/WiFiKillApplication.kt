package com.sankare.wifikill

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sankare.wifikill.background.workers.RetentionWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class WiFiKillApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleLocalDataRetention()
    }

    private fun scheduleLocalDataRetention() {
        val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
