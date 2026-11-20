package com.example.rssreader.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
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
 * 订阅源管理页：添加（先抓一次验证是不是真订阅源，再入库并立刻拉文章）、删除（级联清缓存）。
 */
class FeedManageActivity : AppCompatActivity() {

    private lateinit var viewModel: FeedViewModel
    private lateinit var adapter: FeedManageAdapter
    private lateinit var emptyState: TextView
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_manage)

        findViewById<View>(R.id.appbar).applySystemBarTopPadding()

        viewModel = ViewModelProvider(this)[FeedViewModel::class.java]

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        emptyState = findViewById(R.id.empty_state)

        adapter = FeedManageAdapter(onDelete = { feed -> confirmDelete(feed) })
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@FeedManageActivity)
            adapter = this@FeedManageActivity.adapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.feeds.collect { feeds ->
                        adapter.submitList(feeds)
                        emptyState.visibility = if (feeds.isEmpty()) View.VISIBLE else View.GONE
                        toolbar.subtitle = "${feeds.size} 个订阅源"
                    }
                }
                launch { viewModel.messages.collect { toast(it) } }
                launch {
                    viewModel.addResults.collect { outcome ->
                        toast(outcome.message)
                        if (outcome.success) emptyState.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun confirmDelete(feed: FeedWithCount) {
        AlertDialog.Builder(this)
            .setTitle("删除「${feed.title}」？")
            .setMessage("它的 ${feed.total} 篇缓存文章会一起删除（数据库外键级联）。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> viewModel.deleteFeed(feed.id) }
            .show()
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_feed, null)
        val inputTitle = view.findViewById<EditText>(R.id.input_title)
        val inputUrl = view.findViewById<EditText>(R.id.input_url)

        val dialog = AlertDialog.Builder(this)
            .setTitle("添加订阅源")
            .setView(view)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = inputUrl.text.toString().trim()
                if (url.isEmpty()) {
                    toast("请填写 RSS 地址")
                    return@setOnClickListener
                }
                dialog.dismiss()
                toast("正在验证并拉取…")
                viewModel.addFeed(inputTitle.text.toString().trim(), url)
            }
        }
        dialog.show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_feed_manage, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_feed) {
            showAddDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
