package com.ecut.campuspocket.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ecut.campuspocket.MainActivity
import com.ecut.campuspocket.R
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingSign
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingTask
import com.ecut.campuspocket.navigation.Routes

object ReminderNotifier {

    const val CHANNEL_SIGN = "chaoxing_sign"
    const val CHANNEL_TASK = "chaoxing_task"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SIGN,
                "学习通签到提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测到进行中的课堂签到时提醒"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASK,
                "学习通作业/考试提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "发现新的待完成作业或考试时提醒"
            }
        )
    }

    fun notifySign(context: Context, sign: ChaoxingSign) {
        val title = "《${sign.courseName}》正在签到"
        val text = sign.title.ifBlank { "请尽快打开学习通完成签到" }
        post(
            context = context,
            channel = CHANNEL_SIGN,
            id = stableId("sign", sign.key),
            title = title,
            text = text,
            highPriority = true,
            group = "chaoxing_sign"
        )
    }

    fun notifyTask(context: Context, task: ChaoxingTask) {
        val label = if (task.type == "exam") "考试" else "作业"
        val title = "《${task.courseName}》有新的$label"
        val text = buildString {
            append(task.title)
            if (task.detail.isNotBlank()) append(" · ").append(task.detail)
        }
        post(
            context = context,
            channel = CHANNEL_TASK,
            id = stableId("task", task.key),
            title = title,
            text = text,
            highPriority = false,
            group = "chaoxing_task"
        )
    }

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        highPriority: Boolean,
        group: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, Routes.CHAOXING)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_frozen_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(
                if (highPriority) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_REMINDER
            )
            .setGroup(group)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
        if (highPriority) {
            val summary = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_frozen_monochrome)
                .setContentTitle("学习通签到提醒")
                .setContentText("有新的课堂签到活动")
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            runCatching {
                NotificationManagerCompat.from(context).notify(stableId("sign_summary", group), summary)
            }
        }
    }

    private fun stableId(prefix: String, key: String): Int =
        (prefix + "|" + key).hashCode() and 0x7fffffff
}
