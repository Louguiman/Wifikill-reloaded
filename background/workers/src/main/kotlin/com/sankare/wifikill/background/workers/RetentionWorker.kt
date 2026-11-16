package com.sankare.wifikill.background.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.usecase.CleanupExpiredDataUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class RetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RetentionWorkerEntryPoint::class.java,
        )
        val result = entryPoint.cleanupExpiredData()
        return if (result is AppResult.Failure) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "local-data-retention"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RetentionWorkerEntryPoint {
    fun cleanupExpiredData(): CleanupExpiredDataUseCase
}
