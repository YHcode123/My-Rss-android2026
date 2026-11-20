package com.example.rssreader.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.rssreader.core.data.RssRepository
import java.util.concurrent.TimeUnit

/**
 * 阅读器自己的后台同步任务（方案 B）。
 *
 * 和 :feature_notification 里的 ReminderWorker 完全解耦：
 *  - ReminderWorker 负责"提醒"，受通知开关控制，开关关了就不干活；
 *  - 这个 Worker 负责"内容更新"，只要装了这个 App 就会定期跑，用户不用去打开通知开关。
 *
 * 同步结果只写数据库、不弹任何界面提示：后台任务不该打断用户。
 * 断网等失败走 Result.retry()，退避后再说，连续失败两次就放弃本轮（周期任务下一轮会再来）。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val report = runCatching { RssRepository.get(applicationContext).syncAll() }.getOrNull()
            ?: return if (runAttemptCount < 2) Result.retry() else Result.success()

        // 全部源都失败（典型是断网）：重试一次，之后交给下一轮周期
        if (report.successFeeds == 0 && report.failedFeeds > 0) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        return Result.success()
    }
}

/** 自动同步的排班入口，与通知模块的 ReminderScheduler 各自独立。 */
object SyncScheduler {

    const val WORK_NAME = "auto_sync_periodic"

    /** 周期：1 小时。WorkManager 周期任务的下限是 15 分钟，1 小时是省电与新鲜度的折中。 */
    private const val INTERVAL_HOURS = 1L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        // KEEP：已经排过就不动，避免每次启动都重置计时（否则频繁开关 App 会让它永远等不到下一次执行）
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
