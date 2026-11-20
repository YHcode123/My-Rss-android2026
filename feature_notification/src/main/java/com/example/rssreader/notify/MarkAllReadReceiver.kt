package com.example.rssreader.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rssreader.core.data.RssRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通知栏上的"全部标为已读"动作。
 *
 * 通知点击后会走到这里：用 goAsync() 拿到 10 秒左右的执行窗口，
 * 直接在同一个 Room 数据库上把未读清零，然后取消通知。
 * 这样"通知内操作"就不是打开 App 再点一次，而是真正的一条命令。
 */
class MarkAllReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_MARK_ALL_READ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RssRepository.get(appContext)
                repository.markAllRead()
                withContext(Dispatchers.Main) {
                    NotificationHelper.cancel(appContext)
                    NotificationHelper.resetNotifiedState(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
