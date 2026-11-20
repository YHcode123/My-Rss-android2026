package com.example.rssreader.notify

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
import com.example.rssreader.notify.ui.NotificationCenterActivity

/**
 * 通知的创建与发送。这里刻意把"未读数"作为通知内容，
 * 并在通知上挂两个动作：全部标为已读（直接改数据库）、打开阅读器。
 */
object NotificationHelper {

    const val CHANNEL_ID = "unread_reminder"
    const val NOTIFICATION_ID = 1001

    const val ACTION_MARK_ALL_READ = "com.example.rssreader.notify.action.MARK_ALL_READ"

    private const val REQUEST_MARK_ALL_READ = 2001
    private const val REQUEST_OPEN_APP = 2002
    private const val REQUEST_OPEN_CENTER = 2003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "未读文章提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "定时提醒你有多少篇未读文章，可直接在通知上全部标为已读"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 通知权限是否可用：Android 13 起需要运行时权限，用户拒绝即降级。 */
    fun canPost(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showUnreadNotification(context: Context, unread: Int) {
        if (!canPost(context)) return
        ensureChannel(context)

        val markAllRead = PendingIntent.getBroadcast(
            context,
            REQUEST_MARK_ALL_READ,
            Intent(context, MarkAllReadReceiver::class.java).setAction(ACTION_MARK_ALL_READ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openCenter = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_CENTER,
            Intent(context, NotificationCenterActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val content = if (unread > 0) {
            "点开「全部标为已读」可一键清零，不影响已星标的文章"
        } else {
            "所有文章都已读完"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_rss)
            .setContentTitle("你有 $unread 篇未读文章")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openCenter ?: openApp ?: markAllRead)
            .addAction(0, "全部标为已读", markAllRead)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** 清除"已提醒过的未读数"，让下一次定时任务重新提醒。 */
    fun resetNotifiedState(context: Context) {
        NotifyPrefs(context).lastNotifiedUnread = -1
    }
}
