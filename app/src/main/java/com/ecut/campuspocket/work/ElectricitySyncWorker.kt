package com.ecut.campuspocket.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.remote.AdapterNotConfiguredException
import com.ecut.campuspocket.network.SessionExpiredException
import com.ecut.campuspocket.ui.SessionState
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 每小时采样一次电费。
 *
 * 电费接口配置完成前不会被调度；Worker 本身保持可编译、可测试。
 */
class ElectricitySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val source = AppContainer.electricitySource ?: return Result.success()
        return try {
            val sample = source.fetch()
            AppContainer.electricityRepository.addSample(
                lightingKwh = sample.lightingKwh,
                acKwh = sample.acKwh,
                balance = sample.balance
            )
            Result.success()
        } catch (_: AdapterNotConfiguredException) {
            Result.success()
        } catch (_: SessionExpiredException) {
            SessionState.reportExpired()
            Result.failure()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "electricity_sync_hourly"

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<ElectricitySyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
