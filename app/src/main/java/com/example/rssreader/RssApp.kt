package com.example.rssreader

import android.app.Application
import com.example.rssreader.core.data.RssRepository
import com.example.rssreader.notify.NotificationHelper
import com.example.rssreader.notify.NotifyPrefs
import com.example.rssreader.notify.ReminderScheduler
import com.example.rssreader.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 进程入口：
 *  1. 建通知渠道
 *  2. 首次启动播种内置订阅源；之后每次启动补齐清单里新增的源（按 URL 判重，不重复插）
 *  3. 排上"自动同步"周期任务（内容更新，和通知开关无关）
 *  4. 如果用户开过未读提醒，进程一起来就把提醒任务补排上
 *     —— WorkManager 的周期任务在卸载重建/设备重启后会失效，这里做一次兜底
 */
class RssApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.ensureChannel(this)

        appScope.launch {
            // 首启播种 + 升级时补齐新内置源（按 URL 判重）
            RssRepository.get(this@RssApp).ensureBuiltinFeeds()
        }

        // 方案 B：阅读器自身的后台同步，1 小时一轮，不受通知开关影响
        SyncScheduler.schedule(this)

        if (NotifyPrefs(this).enabled) {
            ReminderScheduler.schedule(this, NotifyPrefs(this).intervalHours)
        }
    }
}
