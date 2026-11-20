package com.example.rssreader.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rssreader.R
import com.example.rssreader.core.data.ArticleCounts
import com.example.rssreader.core.data.ArticleWithFeed
import com.example.rssreader.notify.ui.NotificationCenterActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * 文章列表页（主界面）。三件事：
 *  1. 三个标签：全部 / 未读 / 星标，标题上直接带数量（来自 Room 的聚合查询）
 *  2. 支持按订阅源筛选（由订阅源列表页选回来）
 *  3. 刷新、批量已读、进订阅管理、进通知提醒中心，全在右上角菜单里
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ArticleListViewModel
    private lateinit var adapter: ArticleAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabs: TabLayout
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyHint: TextView
    private lateinit var errorBanner: View
    private lateinit var errorText: TextView
    private lateinit var progress: ProgressBar

    private var filteredFeedId: Long? = null
    private var filteredFeedName: String? = null
    private var lastCounts = ArticleCounts(0, 0, 0)

    /**
     * 列表当前条数。刻意不用 adapter.itemCount —— ListAdapter 的 diff 在后台线程算完才回填，
     * 刚 submitList 时 itemCount 仍为 0，会误判成"空列表"从而把空态叠在已有数据上。
     */
    private var listSize = 0

    private val feedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val id = data.getLongExtra(FeedListActivity.EXTRA_FEED_ID, 0L)
        val name = data.getStringExtra(FeedListActivity.EXTRA_FEED_NAME)
        filteredFeedId = if (id > 0L) id else null
        filteredFeedName = if (id > 0L) name else null
        applyFeedFilter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[ArticleListViewModel::class.java]

        toolbar = findViewById(R.id.toolbar)
        tabs = findViewById(R.id.tabs)
        recycler = findViewById(R.id.recycler)
        emptyState = findViewById(R.id.empty_state)
        emptyTitle = findViewById(R.id.empty_title)
        emptyHint = findViewById(R.id.empty_hint)
        errorBanner = findViewById(R.id.error_banner)
        errorText = findViewById(R.id.error_text)
        progress = findViewById(R.id.progress)

        // 系统栏补偿：状态栏高度补成顶部 padding，标题和右上的刷新图标才不会被系统栏盖住
        findViewById<View>(R.id.appbar).applySystemBarTopPadding()
        recycler.applySystemBarBottomPadding()

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = ArticleAdapter(
            onOpen = { item -> openArticle(item) },
            onToggleStar = { item -> viewModel.toggleStar(item.article.id) },
            onLongPress = { view, item -> showArticleMenu(view, item) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        setupTabs()

        findViewById<View>(R.id.error_retry).setOnClickListener { viewModel.refresh() }

        observeState()
    }

    /**
     * 方案 C：每次回到前台都判一次数据新不新鲜（内部有 1 分钟节流）。
     * 从详情页返回、从通知中心返回都会走这里，若超过半小时没同步就静默拉一次。
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshIfStale()
    }

    private fun setupTabs() {
        tabs.addTab(tabs.newTab().setText("全部"))
        tabs.addTab(tabs.newTab().setText("未读"))
        tabs.addTab(tabs.newTab().setText("星标"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setFilter(tab.position)
                refreshEmptyState()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                recycler.scrollToPosition(0)
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.articles.collect { list ->
                        listSize = list.size
                        adapter.submitList(list)
                        refreshEmptyState()
                    }
                }
                launch {
                    viewModel.counts.collect { counts ->
                        lastCounts = counts
                        updateHeader(counts)
                        refreshEmptyState()
                    }
                }
                launch {
                    viewModel.isSyncing.collect { syncing ->
                        progress.visibility = if (syncing) View.VISIBLE else View.GONE
                        // 同步结束后再判一次空态：一开始"空 + 转圈"是藏着空态的
                        refreshEmptyState()
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ListEvent.Message -> toast(event.text)
                            is ListEvent.SyncFinished -> handleSyncFinished(event)
                        }
                    }
                }
            }
        }
    }

    private fun handleSyncFinished(event: ListEvent.SyncFinished) {
        val report = event.report
        toast(report.summary)
        if (report.failedFeeds > 0) {
            errorText.text = buildString {
                append("${report.failedFeeds} 个源更新失败（")
                append(report.failedTitles.joinToString("、"))
                append("），当前显示的是本地缓存")
            }
            errorBanner.visibility = View.VISIBLE
        } else {
            errorBanner.visibility = View.GONE
        }
    }

    private fun updateHeader(counts: ArticleCounts) {
        tabs.getTabAt(0)?.text = "全部 ${counts.total}"
        tabs.getTabAt(1)?.text = "未读 ${counts.unread}"
        tabs.getTabAt(2)?.text = "星标 ${counts.starred}"

        toolbar.title = filteredFeedName ?: "RSS 阅读器"
        toolbar.subtitle = if (counts.total > 0 && counts.unread == 0) {
            "全部已读 · 共 ${counts.total}"
        } else {
            "未读 ${counts.unread} · 共 ${counts.total}"
        }
        if (filteredFeedId != null) {
            toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(
                this, R.drawable.ic_arrow_back_white
            )
        } else {
            toolbar.navigationIcon = null
        }
    }

    private fun refreshEmptyState() {
        val empty = listSize == 0 && progress.visibility != View.VISIBLE
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        if (!empty) return

        when (tabs.selectedTabPosition) {
            1 -> {
                emptyTitle.text = "没有未读文章"
                emptyHint.text = if (lastCounts.total == 0) {
                    "先到订阅管理里加个源，或点右上角刷新一次"
                } else {
                    "全部读完了，干得漂亮"
                }
            }

            2 -> {
                emptyTitle.text = "还没有星标文章"
                emptyHint.text = "在列表右侧点星标，或在文章详情页收藏"
            }

            else -> {
                emptyTitle.text = "还没有文章"
                emptyHint.text = "点右上角刷新一次，或到订阅管理里添加订阅源"
            }
        }
    }

    private fun applyFeedFilter() {
        viewModel.setFeed(filteredFeedId)
        updateHeader(lastCounts)
        recycler.scrollToPosition(0)
    }

    private fun openArticle(item: ArticleWithFeed) {
        viewModel.markRead(item.article.id)
        ArticleDetailActivity.open(this, item.article.id)
    }

    private fun showArticleMenu(anchor: View, item: ArticleWithFeed) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_popup_article, popup.menu)
        popup.menu.findItem(R.id.popup_star).title =
            if (item.article.isStarred) "取消收藏" else "收藏"
        popup.menu.findItem(R.id.popup_read).title =
            if (item.article.isRead) "标为未读" else "标为已读"
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.popup_star -> viewModel.toggleStar(item.article.id)
                R.id.popup_read -> viewModel.markRead(item.article.id, !item.article.isRead)
                R.id.popup_browser -> openInBrowser(item)
            }
            true
        }
        popup.show()
    }

    private fun openInBrowser(item: ArticleWithFeed) {
        val link = item.article.link
        if (link.isNullOrBlank()) {
            toast("这篇文章没有原文链接")
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { toast("没有可用的浏览器") }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> viewModel.refresh()
            R.id.action_mark_all_read -> viewModel.markAllRead()
            R.id.action_feeds -> feedPicker.launch(Intent(this, FeedListActivity::class.java))
            R.id.action_manage_feeds -> startActivity(
                Intent(this, FeedManageActivity::class.java)
            )

            R.id.action_notify_center -> startActivity(
                NotificationCenterActivity.intentFor(this)
            )

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
