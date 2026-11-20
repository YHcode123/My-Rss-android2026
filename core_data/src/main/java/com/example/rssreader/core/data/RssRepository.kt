package com.example.rssreader.core.data

import android.content.Context
import androidx.room.withTransaction
import com.example.rssreader.core.net.ParsedFeed
import com.example.rssreader.core.net.RssFetcher
import com.example.rssreader.core.net.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.net.UnknownHostException

/** 一次全量同步的结果，界面和通知模块都用它来出文案。 */
data class SyncReport(
    val feedCount: Int,
    val successFeeds: Int,
    val failedFeeds: Int,
    val newArticles: Int,
    val failedTitles: List<String>,
    val networkUnavailable: Boolean
) {
    val summary: String
        get() = if (failedFeeds == 0) {
            "更新完成：$successFeeds 个源，新增 $newArticles 篇"
        } else {
            "更新完成：成功 $successFeeds 个源、新增 $newArticles 篇；$failedFeeds 个源失败，已显示本地缓存"
        }
}

data class AddFeedOutcome(
    val success: Boolean,
    val message: String,
    val feedId: Long = 0L,
    val articleCount: Int = 0
)

/**
 * 唯一的业务入口。界面层和通知模块都只跟它打交道，不直接碰 DAO。
 *
 * 设计要点：
 *  - SQLite（Room）是唯一数据源，UI 只 Observe 数据库；网络只负责把增量写进库
 *  - sync 时"老文章只更新内容字段"，isRead / isStarred 永远不会被刷新覆盖
 *  - 单个源失败只记一次失败，其余源继续，界面回退到本地缓存
 */
class RssRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = RssDatabase.get(appContext)
    private val feedDao = db.feedDao()
    private val articleDao = db.articleDao()
    private val parser = RssParser()

    companion object {
        const val MAX_ARTICLES_PER_FEED = 200

        /**
         * 内置订阅源，首个为打包在 assets 里的本地示例源，完全断网也有内容可读。
         * 其余 10 个均为 2026-11 实测可用的真实源（HTTP 200 且能解析出条目），
         * 覆盖综合资讯、科技、开发者博客三类；加好后首屏就有内容，不用用户自己去找地址。
         */
        val BUILTIN_FEEDS: List<Pair<String, String>> = listOf(
            "本地示例源（离线可读）" to "asset://sample_feed.xml",
            "少数派" to "https://sspai.com/feed",
            "阮一峰的网络日志" to "http://www.ruanyifeng.com/blog/atom.xml",
            "美团技术团队" to "https://tech.meituan.com/feed",
            "掘金·前端" to "https://juejin.cn/rss",
            "InfoQ 中文" to "https://www.infoq.cn/feed",
            "IT之家" to "https://www.ithome.com/rss/",
            "开源中国·资讯" to "https://www.oschina.net/news/rss",
            "爱范儿" to "https://www.ifanr.com/feed",
            "Solidot 奇客" to "https://www.solidot.org/index.rss",
            "云风的博客" to "https://blog.codingnow.com/atom.xml",
            "Hacker News" to "https://hnrss.org/frontpage"
        )

        @Volatile
        private var instance: RssRepository? = null

        fun get(context: Context): RssRepository =
            instance ?: synchronized(this) {
                instance ?: RssRepository(context).also { instance = it }
            }
    }

    // ---------- 首次启动：播种内置源 ----------

    suspend fun seedBuiltinFeedsIfEmpty() = withContext(Dispatchers.IO) {
        if (feedDao.count() > 0) return@withContext
        for ((title, url) in BUILTIN_FEEDS) {
            feedDao.insert(FeedEntity(title = title, url = url, builtin = true))
        }
    }

    /**
     * 补齐内置源：只插入"库里还没有的 URL"，已存在的（包括用户改过名字的）一律不动。
     * 用于升级场景——内置源清单变长后，老用户不用清数据也能看到新源。
     * 注意：用户主动删掉的内置源在下次启动时会被补回来，这是有意的取舍。
     */
    suspend fun ensureBuiltinFeeds() = withContext(Dispatchers.IO) {
        val existing = feedDao.queryAll().map { it.url }.toSet()
        for ((title, url) in BUILTIN_FEEDS) {
            if (url !in existing) {
                runCatching { feedDao.insert(FeedEntity(title = title, url = url, builtin = true)) }
            }
        }
    }

    // ---------- 查询 ----------

    fun observeArticles(feedId: Long?, filter: Int): Flow<List<ArticleWithFeed>> =
        articleDao.observeArticles(feedId, filter)

    fun observeCounts(feedId: Long?): Flow<ArticleCounts> =
        articleDao.observeCounts(feedId)

    fun observeFeedsWithCount(): Flow<List<FeedWithCount>> =
        feedDao.observeFeedsWithCount()

    suspend fun feeds(): List<FeedEntity> = withContext(Dispatchers.IO) { feedDao.queryAll() }

    suspend fun feed(id: Long): FeedEntity? = withContext(Dispatchers.IO) { feedDao.queryById(id) }

    suspend fun article(id: Long): ArticleEntity? = withContext(Dispatchers.IO) {
        articleDao.queryById(id)
    }

    /** 详情页打开前就先落库标已读，返回时列表观察 Flow 会自动刷新。 */
    suspend fun markRead(id: Long, read: Boolean = true) = withContext(Dispatchers.IO) {
        articleDao.markRead(id, read)
    }

    suspend fun markAllRead(): Int = withContext(Dispatchers.IO) { articleDao.markAllRead() }

    suspend fun markFeedRead(feedId: Long): Int = withContext(Dispatchers.IO) {
        articleDao.markFeedRead(feedId)
    }

    suspend fun toggleStar(id: Long): Boolean = withContext(Dispatchers.IO) {
        val current = articleDao.isStarred(id) ?: return@withContext false
        val next = !current
        articleDao.setStar(id, next)
        next
    }

    suspend fun unreadCount(): Int = withContext(Dispatchers.IO) { articleDao.unreadCount() }

    suspend fun totalArticles(): Int = withContext(Dispatchers.IO) { articleDao.countAll() }

    // ---------- 同步 ----------

    /**
     * 同步全部源。任何一个源抛异常都只累加失败数，不影响其它源。
     */
    suspend fun syncAll(): SyncReport = withContext(Dispatchers.IO) {
        val feeds = feedDao.queryAll()
        var successFeeds = 0
        var failedFeeds = 0
        var newArticles = 0
        var networkUnavailable = false
        val failedTitles = ArrayList<String>()

        for (feed in feeds) {
            try {
                newArticles += syncFeedInternal(feed.id)
                successFeeds++
            } catch (e: Exception) {
                failedFeeds++
                failedTitles.add(feed.title)
                if (e is UnknownHostException || e is ConnectException) {
                    networkUnavailable = true
                }
            }
        }

        SyncReport(
            feedCount = feeds.size,
            successFeeds = successFeeds,
            failedFeeds = failedFeeds,
            newArticles = newArticles,
            failedTitles = failedTitles,
            networkUnavailable = networkUnavailable
        )
    }

    suspend fun syncFeed(feedId: Long): Int = withContext(Dispatchers.IO) {
        syncFeedInternal(feedId)
    }

    private suspend fun syncFeedInternal(feedId: Long): Int {
        val feed = feedDao.queryById(feedId) ?: return 0
        val document = RssFetcher.fetch(appContext, feed.url)
        val parsed = parser.parse(ByteArrayInputStream(document.bytes), document.charset)
        return persist(feed.id, parsed)
    }

    /**
     * 写入解析结果。返回本次新增（真正新插入）的条数。
     *
     * 关键：insertIgnore 返回 -1 说明 (feedId, guid) 已存在，
     * 此时只 UPDATE 内容字段，绝不碰 isRead / isStarred。
     */
    private suspend fun persist(feedId: Long, parsed: ParsedFeed): Int {
        var inserted = 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (item in parsed.articles) {
                val entity = ArticleEntity(
                    feedId = feedId,
                    guid = item.guid,
                    title = item.title,
                    link = item.link,
                    author = item.author,
                    summary = item.summary,
                    content = item.content,
                    pubDate = item.pubDate,
                    cachedAt = now
                )
                val rowId = articleDao.insertIgnore(entity)
                if (rowId == -1L) {
                    articleDao.updateContent(
                        feedId = feedId,
                        guid = item.guid,
                        title = item.title,
                        link = item.link,
                        author = item.author,
                        summary = item.summary,
                        content = item.content,
                        pubDate = item.pubDate,
                        cachedAt = now
                    )
                } else {
                    inserted++
                }
            }
            feedDao.updateLastSync(feedId, now)
            // 自定义缓存策略：每源保留最近 MAX_ARTICLES_PER_FEED 篇，星标文章永不被淘汰
            articleDao.trimFeed(feedId, MAX_ARTICLES_PER_FEED)
        }
        return inserted
    }

    // ---------- 订阅源增删 ----------

    suspend fun addFeed(title: String, url: String): AddFeedOutcome = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val schemeOk = trimmedUrl.startsWith("http://") ||
            trimmedUrl.startsWith("https://") ||
            trimmedUrl.startsWith(RssFetcher.ASSET_PREFIX)
        if (!schemeOk) {
            return@withContext AddFeedOutcome(false, "地址需以 http://、https:// 或 asset:// 开头")
        }
        if (feedDao.countByUrl(trimmedUrl) > 0) {
            return@withContext AddFeedOutcome(false, "该订阅源已存在，无需重复添加")
        }

        // 先抓一次验证地址是不是真的订阅源，顺便拿到源站标题
        val parsed = try {
            val document = RssFetcher.fetch(appContext, trimmedUrl)
            parser.parse(ByteArrayInputStream(document.bytes), document.charset)
        } catch (e: Exception) {
            return@withContext AddFeedOutcome(false, "无法读取该地址：${e.message ?: "未知错误"}")
        }
        if (parsed.articles.isEmpty()) {
            return@withContext AddFeedOutcome(false, "该地址没有解析出文章，可能不是 RSS/Atom 源")
        }

        val finalTitle = title.trim().ifBlank {
            parsed.title.ifBlank { trimmedUrl }
        }
        val feedId = feedDao.insert(
            FeedEntity(title = finalTitle, url = trimmedUrl, builtin = false)
        )
        val inserted = try {
            persist(feedId, parsed)
        } catch (e: Exception) {
            parsed.articles.size
        }
        AddFeedOutcome(
            success = true,
            message = "已添加「$finalTitle」，拉取到 ${parsed.articles.size} 篇",
            feedId = feedId,
            articleCount = inserted
        )
    }

    /** 删除订阅源；articles 表的外键 CASCADE 会把它的缓存文章一起清掉。 */
    suspend fun deleteFeed(id: Long) = withContext(Dispatchers.IO) {
        feedDao.delete(id)
    }
}
