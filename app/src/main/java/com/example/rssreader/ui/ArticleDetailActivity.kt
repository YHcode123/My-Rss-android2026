package com.example.rssreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.R
import com.example.rssreader.core.data.ArticleEntity
import com.example.rssreader.core.data.RssRepository
import com.example.rssreader.core.util.TimeUtil
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * 文章详情页。
 *
 * 正文完全来自 Room 里缓存的 content 字段，不发起任何网络请求，
 * 所以飞行模式下打开文章和联网时没有任何区别。
 */
class ArticleDetailActivity : AppCompatActivity() {

    private lateinit var repository: RssRepository
    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var contentView: TextView
    private lateinit var linkHint: TextView
    private lateinit var cacheHint: TextView

    private var article: ArticleEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_detail)

        findViewById<View>(R.id.appbar).applySystemBarTopPadding()

        repository = RssRepository.get(this)

        toolbar = findViewById(R.id.toolbar)
        titleView = findViewById(R.id.title)
        metaView = findViewById(R.id.meta)
        contentView = findViewById(R.id.content)
        linkHint = findViewById(R.id.link_hint)
        cacheHint = findViewById(R.id.cache_hint)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, 0L)
        if (articleId <= 0L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val loaded = repository.article(articleId)
            if (loaded == null) {
                Toast.makeText(this@ArticleDetailActivity, "文章不存在或已被清理", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            article = loaded
            // 兜底：从任何入口打开都保证标为已读（重复调用无副作用）
            repository.markRead(articleId)
            render(loaded)
        }
    }

    private fun render(item: ArticleEntity) {
        titleView.text = item.title

        lifecycleScope.launch {
            val feedName = repository.feed(item.feedId)?.title
            metaView.text = buildString {
                feedName?.let {
                    append(it)
                    append("　·　")
                }
                item.author?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append("　·　")
                }
                append(if (item.pubDate > 0) TimeUtil.absolute(item.pubDate) else "时间未知")
            }
        }

        val body = item.content?.takeIf { it.isNotBlank() } ?: item.summary
        if (body.isNullOrBlank()) {
            contentView.text = "（这篇源站没有提供正文，可点右上角用浏览器打开原文）"
        } else {
            contentView.text = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY)
            contentView.movementMethod = LinkMovementMethod.getInstance()
        }

        if (!item.link.isNullOrBlank()) {
            linkHint.visibility = TextView.VISIBLE
            linkHint.text = "查看原文：${item.link}"
            linkHint.setOnClickListener { openInBrowser(item.link!!) }
        } else {
            linkHint.visibility = TextView.GONE
        }

        cacheHint.text = if (item.cachedAt > 0) {
            "正文已缓存在本地 · ${TimeUtil.absolute(item.cachedAt)}"
        } else {
            "正文来自本地数据库"
        }

        invalidateOptionsMenu()
    }

    private fun openInBrowser(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_article_detail, menu)
        menu.findItem(R.id.action_star).icon = androidx.core.content.ContextCompat.getDrawable(
            this,
            if (article?.isStarred == true) R.drawable.ic_star_filled else R.drawable.ic_star_outline_white
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val current = article
        when (item.itemId) {
            R.id.action_star -> if (current != null) {
                lifecycleScope.launch {
                    val starred = repository.toggleStar(current.id)
                    article = current.copy(isStarred = starred)
                    invalidateOptionsMenu()
                    Toast.makeText(
                        this@ArticleDetailActivity,
                        if (starred) "已收藏" else "已取消收藏",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            R.id.action_share -> if (current != null) {
                val target = current.link?.takeIf { it.isNotBlank() } ?: current.title
                startActivity(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${current.title}\n$target")
                    }
                )
            }

            R.id.action_browser -> if (current != null) {
                val link = current.link
                if (link.isNullOrBlank()) {
                    Toast.makeText(this, "这篇文章没有原文链接", Toast.LENGTH_SHORT).show()
                } else {
                    openInBrowser(link)
                }
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        const val EXTRA_ARTICLE_ID = "extra_article_id"

        fun open(context: Context, articleId: Long) {
            context.startActivity(
                Intent(context, ArticleDetailActivity::class.java)
                    .putExtra(EXTRA_ARTICLE_ID, articleId)
            )
        }
    }
}
