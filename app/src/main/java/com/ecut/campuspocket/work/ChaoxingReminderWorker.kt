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
import com.ecut.campuspocket.data.notifications.ReminderNotifier
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingSign
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingTask
import com.ecut.campuspocket.data.repository.ChaoxingRepository
import com.ecut.campuspocket.network.ChaoxingSessionExpiredException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 每 15 分钟检查一次学习通签到活动（WorkManager 允许的最短周期），
 * 每小时完整刷新一次作业/考试。
 */
class ChaoxingReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = AppContainer.preferences
        if (!prefs.chaoxingReminders) return Result.success()
        val repository = AppContainer.chaoxingRepository
        if (!repository.hasSession()) return Result.success()

        return try {
            val now = System.currentTimeMillis()
            val tasksDue = now - prefs.chaoxingTasksFetchedAt > TASK_REFRESH_MS
            if (tasksDue) {
                val snapshot = repository.loadSnapshot()
                prefs.chaoxingTasksFetchedAt = now
                notifySigns(snapshot.signs)
                val tasks = snapshot.works + snapshot.exams
                if (prefs.chaoxingTasksSeeded) {
                    notifyTasks(tasks.filter { it.needsAction })
                } else {
                    // 第一次只建立基线，不把历史作业全部弹一遍。
                    tasks.forEach { prefs.markNotified(ChaoxingRepository.BUCKET_TASK, it.key) }
                    prefs.chaoxingTasksSeeded = true
                }
            } else {
                notifySigns(repository.loadSigns())
            }
            Result.success()
        } catch (_: ChaoxingSessionExpiredException) {
            // Cookie 失效时保持安静，等用户打开 App 再引导登录。
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.success()
        }
    }

    private fun notifySigns(signs: List<ChaoxingSign>) {
        val prefs = AppContainer.preferences
        signs.filter { it.isOngoing }.forEach { sign ->
            if (prefs.markNotified(ChaoxingRepository.BUCKET_SIGN, sign.key)) {
                ReminderNotifier.notifySign(applicationContext, sign)
            }
        }
    }

    private fun notifyTasks(tasks: List<ChaoxingTask>) {
        val prefs = AppContainer.preferences
        tasks.forEach { task ->
            if (prefs.markNotified(ChaoxingRepository.BUCKET_TASK, task.key)) {
                ReminderNotifier.notifyTask(applicationContext, task)
            }
        }
    }

    companion object {
        private const val UNIQUE_NAME = "chaoxing_reminder_15m"
        private const val TASK_REFRESH_MS = 60 * 60 * 1000L

        fun ensureScheduled(context: Context) {
            if (!AppContainer.preferences.chaoxingReminders) return
            val request = PeriodicWorkRequestBuilder<ChaoxingReminderWorker>(
                15,
                TimeUnit.MINUTES
            )
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
