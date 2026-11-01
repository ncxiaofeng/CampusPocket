package com.ecut.campuspocket.data

import android.content.Context
import java.util.UUID

/**
 * 轻量本地设置与提醒去重记录。
 *
 * 凭据/密码不放在这里：会话 Cookie 在 SQLCipher 库里，密码在 [CredentialVault]。
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("campus_pocket_prefs", Context.MODE_PRIVATE)

    /** 启动后自动检查更新，默认开启。 */
    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, value).apply()

    /** 是否启用「加密保存账号密码 + 会话失效时自动登录」。默认关闭。 */
    var autoLoginEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOGIN, value).apply()

    /** 学习通后台提醒开关（登录后可开，默认开）。 */
    var chaoxingReminders: Boolean
        get() = prefs.getBoolean(KEY_CHAOXING_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_CHAOXING_REMINDERS, value).apply()

    /** 学习通稳定设备标识，用于官方 UA 与接口识别。 */
    val chaoxingUniqueId: String
        get() {
            val existing = prefs.getString(KEY_CHAOXING_UNIQUE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_CHAOXING_UNIQUE_ID, created).apply()
            return created
        }

    /** 自动登录失败后的冷却截止时间，避免反复触发风控。 */
    var autoLoginBlockedUntil: Long
        get() = prefs.getLong(KEY_AUTO_LOGIN_BLOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_AUTO_LOGIN_BLOCKED_UNTIL, value).apply()

    /** 天气位置：device / guanglan / qinglan / fuzhou。 */
    var weatherLocation: String
        get() = prefs.getString(KEY_WEATHER_LOCATION, "device").orEmpty()
        set(value) = prefs.edit().putString(KEY_WEATHER_LOCATION, value).apply()

    /** 慧生活登录手机号，仅用于界面回显。 */
    var huiLifePhone: String
        get() = prefs.getString(KEY_HUILIFE_PHONE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HUILIFE_PHONE, value).apply()

    /**
     * 用户主动扫过二维码的 798 设备。
     *
     * 只有这份集合里的设备才显示「启动」；其余设备只读状态。
     * 这是产品规则，不是服务端权限校验。
     */
    val huiLifeScannedDevices: Set<String>
        get() = prefs.getStringSet(KEY_HUILIFE_SCANNED, emptySet()).orEmpty().toSet()

    @Synchronized
    fun markHuiLifeDeviceScanned(deviceId: String): Set<String> {
        val current = huiLifeScannedDevices.toMutableSet()
        if (current.add(deviceId)) {
            prefs.edit().putStringSet(KEY_HUILIFE_SCANNED, current).apply()
        }
        return current
    }

    @Synchronized
    fun removeHuiLifeScannedDevice(deviceId: String): Set<String> {
        val current = huiLifeScannedDevices.toMutableSet()
        if (current.remove(deviceId)) {
            prefs.edit().putStringSet(KEY_HUILIFE_SCANNED, current).apply()
        }
        return current
    }

    /** 上次完整拉取作业/考试的时间，避免每 15 分钟都请求全部课程。 */
    var chaoxingTasksFetchedAt: Long
        get() = prefs.getLong(KEY_CHAOXING_TASKS_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CHAOXING_TASKS_AT, value).apply()

    /** 首次完整同步时只记录已有作业，不一次性弹一堆旧提醒。 */
    var chaoxingTasksSeeded: Boolean
        get() = prefs.getBoolean(KEY_CHAOXING_TASKS_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_CHAOXING_TASKS_SEEDED, value).apply()

    /**
     * 记录一个已提醒的条目；返回 true 表示这是第一次出现。
     *
     * 用有界集合，避免 SharedPreferences 无限增长。
     */
    @Synchronized
    fun markNotified(bucket: String, id: String, maxSize: Int = 240): Boolean {
        if (id.isBlank()) return false
        val key = "$KEY_NOTIFIED_PREFIX$bucket"
        val current = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (!current.add(id)) return false
        while (current.size > maxSize) {
            current.remove(current.first())
        }
        prefs.edit().putStringSet(key, current).apply()
        return true
    }

    @Synchronized
    fun clearNotified(bucket: String) {
        prefs.edit().remove("$KEY_NOTIFIED_PREFIX$bucket").apply()
    }

    companion object {
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_AUTO_LOGIN_BLOCKED_UNTIL = "auto_login_blocked_until"
        private const val KEY_CHAOXING_REMINDERS = "chaoxing_reminders"
        private const val KEY_CHAOXING_UNIQUE_ID = "chaoxing_unique_id"
        private const val KEY_CHAOXING_TASKS_AT = "chaoxing_tasks_at"
        private const val KEY_CHAOXING_TASKS_SEEDED = "chaoxing_tasks_seeded"
        private const val KEY_WEATHER_LOCATION = "weather_location"
        private const val KEY_HUILIFE_PHONE = "huilife_phone"
        private const val KEY_HUILIFE_SCANNED = "huilife_scanned_devices"
        private const val KEY_NOTIFIED_PREFIX = "notified_"
    }
}
