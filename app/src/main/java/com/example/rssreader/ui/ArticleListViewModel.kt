package com.example.rssreader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rssreader.core.data.AddFeedOutcome
import com.example.rssreader.core.data.ArticleCounts
import com.example.rssreader.core.data.ArticleDao
import com.example.rssreader.core.data.ArticleWithFeed
import com.example.rssreader.core.data.RssRepository
import com.example.rssreader.core.data.SyncReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 界面层的单向事件（Toast / 错误条 / 同步结果），不放在 State 里，避免重复消费。 */
sealed interface ListEvent {
    data class Message(val text: String) : ListEvent
    data class SyncFinished(val report: SyncReport) : ListEvent
}

/**
 * 文章列表的状态源。界面只订阅 [articles] / [counts] / [isSyncing]，
 * 所有数据都来自 Room 的 Flow —— 这正是"离线也在同一套代码路径"的原因。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RssRepository.get(app)

    private val feedId = MutableStateFlow<Long?>(null)
    private val filter = MutableStateFlow(ArticleDao.FILTER_ALL)
    private val syncing = MutableStateFlow(false)
    private var autoSynced = false

    /** 上次做"新鲜度判断"的时间，用于避免在页面间来回切时反复打网络。 */
    private var lastFreshnessCheck = 0L

    private val _events = MutableSharedFlow<ListEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ListEvent> = _events.asSharedFlow()

    val articles: StateFlow<List<ArticleWithFeed>> =
        combine(feedId, filter) { id, f -> id to f }
            .flatMapLatest { (id, f) -> repository.observeArticles(id, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<ArticleCounts> =
        feedId.flatMapLatest { repository.observeCounts(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ArticleCounts(total = 0, unread = 0, starred = 0)
            )

    val isSyncing: StateFlow<Boolean> = syncing

    init {
        viewModelScope.launch {
            repository.seedBuiltinFeedsIfEmpty()
            // 首次启动（库里还没有任何文章）自动同步一次，省得用户一进来看到空页面
            if (!autoSynced && repository.totalArticles() == 0) {
                autoSynced = true
                refresh()
            }
        }
    }

    fun setFilter(value: Int) {
        filter.value = value
    }

    fun setFeed(id: Long?) {
        feedId.value = id
    }

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val report = runCatching { repository.syncAll() }.getOrElse { error ->
                syncing.value = false
                _events.emit(
                    ListEvent.Message(
                        "刷新失败：${error.message ?: "网络不可用"}，当前展示的是本地缓存"
                    )
                )
                return@launch
            }
            syncing.value = false
            _events.emit(ListEvent.SyncFinished(report))
        }
    }

    /**
     * 方案 C：打开 App / 回到前台时的"新鲜度判断"。
     * 只要距上次成功同步超过 [maxAgeMs]，就静默刷新一次——用户感知是"一进来内容就是新的"。
     * 一分钟内的重复调用直接忽略，避免列表和详情之间来回切时反复打网络。
     */
    fun refreshIfStale(maxAgeMs: Long = STALE_AFTER_MS) {
        val now = System.currentTimeMillis()
        if (now - lastFreshnessCheck < FRESHNESS_CHECK_GAP_MS) return
        lastFreshnessCheck = now
        if (syncing.value) return
        viewModelScope.launch {
            val newest = repository.feeds().maxOfOrNull { it.lastSync } ?: 0L
            if (newest == 0L || now - newest > maxAgeMs) refresh()
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val changed = repository.markAllRead()
            _events.emit(
                ListEvent.Message(
                    if (changed == 0) "本来就没有未读文章" else "已把 $changed 篇标为已读"
                )
            )
        }
    }

    fun markRead(id: Long, read: Boolean = true) {
        viewModelScope.launch { repository.markRead(id, read) }
    }

    fun toggleStar(id: Long) {
        viewModelScope.launch {
            val starred = repository.toggleStar(id)
            _events.emit(ListEvent.Message(if (starred) "已收藏" else "已取消收藏"))
        }
    }

    companion object {
        /** 超过半小时没同步就算"过期"，进 App 时自动拉一次。 */
        private const val STALE_AFTER_MS = 30 * 60 * 1000L

        /** 两次新鲜度判断之间的最小间隔。 */
        private const val FRESHNESS_CHECK_GAP_MS = 60 * 1000L
    }
}
