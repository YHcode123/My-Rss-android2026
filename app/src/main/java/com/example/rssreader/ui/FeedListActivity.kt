package com.example.rssreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rssreader.R
import com.example.rssreader.core.data.FeedWithCount
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * 订阅源列表页。点某个源 → 回到文章列表并只看这个源的文章；
 * 长按 → 该源全部标为已读 / 删除。
 */
class FeedListActivity : AppCompatActivity() {

    private lateinit var viewModel: FeedViewModel
    private lateinit var adapter: FeedAdapter
    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_list)

        findViewById<View>(R.id.appbar).applySystemBarTopPadding()

        viewModel = ViewModelProvider(this)[FeedViewModel::class.java]

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        emptyState = findViewById(R.id.empty_state)

        adapter = FeedAdapter(
            onClick = { feed -> returnSelection(feed) },
            onLongClick = { view, feed -> showFeedMenu(view, feed) }
        )
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@FeedListActivity)
            adapter = this@FeedListActivity.adapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.feeds.collect { feeds ->
                        adapter.submitList(feeds)
                        emptyState.visibility = if (feeds.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.messages.collect { text -> toast(text) }
                }
            }
        }
    }

    private fun returnSelection(feed: FeedWithCount) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_FEED_ID, feed.id).putExtra(EXTRA_FEED_NAME, feed.title)
        )
        finish()
    }

    private fun showFeedMenu(anchor: View, feed: FeedWithCount) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_popup_feed, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.popup_feed_read -> viewModel.markFeedRead(feed.id)
                R.id.popup_feed_delete -> confirmDelete(feed)
            }
            true
        }
        popup.show()
    }

    private fun confirmDelete(feed: FeedWithCount) {
        AlertDialog.Builder(this)
            .setTitle("删除「${feed.title}」？")
            .setMessage("它的 ${feed.total} 篇缓存文章会一起删除，已星标的文章也会消失。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> viewModel.deleteFeed(feed.id) }
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_feed_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_manage -> startActivity(Intent(this, FeedManageActivity::class.java))
            R.id.action_refresh_all -> viewModel.refreshAll()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        const val EXTRA_FEED_ID = "extra_feed_id"
        const val EXTRA_FEED_NAME = "extra_feed_name"
    }
}
