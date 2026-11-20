package com.example.rssreader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rssreader.core.data.AddFeedOutcome
import com.example.rssreader.core.data.FeedWithCount
import com.example.rssreader.core.data.RssRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 订阅源相关的状态与操作，订阅源列表页和订阅源管理页共用。 */
class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RssRepository.get(app)
    private val syncing = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _addResults = MutableSharedFlow<AddFeedOutcome>(extraBufferCapacity = 4)
    val addResults: SharedFlow<AddFeedOutcome> = _addResults.asSharedFlow()

    val feeds: StateFlow<List<FeedWithCount>> = repository.observeFeedsWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isSyncing: StateFlow<Boolean> = syncing

    fun refreshAll() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val report = runCatching { repository.syncAll() }.getOrElse { error ->
                syncing.value = false
                _messages.emit("刷新失败：${error.message ?: "网络不可用"}")
                return@launch
            }
            syncing.value = false
            _messages.emit(report.summary)
        }
    }

    fun markFeedRead(feedId: Long) {
        viewModelScope.launch {
            val changed = repository.markFeedRead(feedId)
            _messages.emit(if (changed == 0) "该源没有未读文章" else "已把 $changed 篇标为已读")
        }
    }

    fun deleteFeed(feedId: Long) {
        viewModelScope.launch {
            repository.deleteFeed(feedId)
            _messages.emit("已删除，该源的缓存文章一并清掉")
        }
    }

    fun addFeed(title: String, url: String) {
        viewModelScope.launch {
            _addResults.emit(repository.addFeed(title, url))
        }
    }
}
