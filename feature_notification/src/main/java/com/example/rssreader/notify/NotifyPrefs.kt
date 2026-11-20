package com.example.rssreader.notify

import android.content.Context

/**
 * 通知中心的本地设置。用 SharedPreferences 而不是数据库：
 * 这些是设备级的开关（间隔、上次提醒的未读数），不属于任何一篇内容。
 */
class NotifyPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("notify_center", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 提醒间隔（小时），默认 2 小时。 */
    var intervalHours: Int
        get() = prefs.getInt(KEY_INTERVAL, 2)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value).apply()

    /** 上次提醒时的未读数：数字没变就不再打扰用户。 */
    var lastNotifiedUnread: Int
        get() = prefs.getInt(KEY_LAST_UNREAD, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_UNREAD, value).apply()

    var lastNotifyTime: Long
        get() = prefs.getLong(KEY_LAST_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_TIME, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_LAST_UNREAD = "last_unread"
        private const val KEY_LAST_TIME = "last_time"

        val INTERVAL_OPTIONS = listOf(1, 2, 4, 8)
    }
}
