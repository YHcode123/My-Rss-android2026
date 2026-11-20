package com.example.rssreader.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rssreader.core.data.RssRepository

/**
 * 定时提醒的核心：先同步（失败也不影响提醒），再统计未读，最后发通知。
 *
 * 三个设计取舍：
 *  1. 同步失败不回退整任务 —— 用户要的是"未读提醒"，用本地缓存的数量照样有意义
 *  2. 未读数没变化就不重复打扰（保存在 NotifyPrefs.lastNotifiedUnread）
 *  3. 通知权限被拒时静默返回成功，不弹任何错误，阅读功能完全不受影响
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = NotifyPrefs(applicationContext)
        if (!prefs.enabled) return Result.success()

        val repository = RssRepository.get(applicationContext)

        // 后台顺手同步一次；失败只影响"新文章"，不影响提醒
        runCatching { repository.syncAll() }

        val unread = repository.unreadCount()

        if (unread <= 0) {
            NotificationHelper.cancel(applicationContext)
            prefs.lastNotifiedUnread = 0
            return Result.success()
        }

        // 权限被拒：静默降级，不报错
        if (!NotificationHelper.canPost(applicationContext)) return Result.success()

        if (unread != prefs.lastNotifiedUnread) {
            NotificationHelper.showUnreadNotification(applicationContext, unread)
            prefs.lastNotifiedUnread = unread
            prefs.lastNotifyTime = System.currentTimeMillis()
        }

        return Result.success()
    }
}
