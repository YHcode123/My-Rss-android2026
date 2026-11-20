package com.example.rssreader.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    companion object {
        const val FILTER_ALL = 0
        const val FILTER_UNREAD = 1
        const val FILTER_STARRED = 2
    }

    /**
     * 列表查询：可选按源筛选 + 可选按已读/星标筛选，排序固定为时间倒序。
     * 过滤条件全部下推到 SQL，界面层不做二次筛选。
     */
    @Query(
        """
        SELECT a.*, f.title AS feedTitle
        FROM articles a
        LEFT JOIN feeds f ON f.id = a.feedId
        WHERE (:feedId IS NULL OR a.feedId = :feedId)
          AND (
                :filter = 0
             OR (:filter = 1 AND a.isRead = 0)
             OR (:filter = 2 AND a.isStarred = 1)
          )
        ORDER BY a.pubDate DESC, a.id DESC
        """
    )
    fun observeArticles(feedId: Long?, filter: Int): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) AS unread,
               COALESCE(SUM(CASE WHEN isStarred = 1 THEN 1 ELSE 0 END), 0) AS starred
        FROM articles
        WHERE (:feedId IS NULL OR feedId = :feedId)
        """
    )
    fun observeCounts(feedId: Long?): Flow<ArticleCounts>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun queryById(id: Long): ArticleEntity?

    /**
     * 返回 -1 表示 (feedId, guid) 已存在（被唯一索引挡掉），调用方据此走"只更新内容"的分支。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(article: ArticleEntity): Long

    /**
     * 只更新内容字段。刻意不含 isRead / isStarred —— 这是刷新不覆盖阅读状态的实现要点。
     */
    @Query(
        """
        UPDATE articles
        SET title = :title,
            link = :link,
            author = :author,
            summary = :summary,
            content = :content,
            pubDate = :pubDate,
            cachedAt = :cachedAt
        WHERE feedId = :feedId AND guid = :guid
        """
    )
    suspend fun updateContent(
        feedId: Long,
        guid: String,
        title: String,
        link: String?,
        author: String?,
        summary: String?,
        content: String?,
        pubDate: Long,
        cachedAt: Long
    )

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun markRead(id: Long, read: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead(): Int

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId AND isRead = 0")
    suspend fun markFeedRead(feedId: Long): Int

    @Query("SELECT isStarred FROM articles WHERE id = :id")
    suspend fun isStarred(id: Long): Boolean?

    @Query("UPDATE articles SET isStarred = :starred WHERE id = :id")
    suspend fun setStar(id: Long, starred: Boolean)

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    suspend fun unreadCount(): Int

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun countAll(): Int

    /**
     * 自定义缓存策略：每个源只保留最近 [keep] 篇，更旧的已读文章被清理，
     * 但已星标的文章永远保留（用户主动收藏的内容不能因为缓存淘汰而消失）。
     */
    @Query(
        """
        DELETE FROM articles
        WHERE feedId = :feedId
          AND isStarred = 0
          AND id NOT IN (
              SELECT id FROM articles WHERE feedId = :feedId ORDER BY pubDate DESC LIMIT :keep
          )
        """
    )
    suspend fun trimFeed(feedId: Long, keep: Int): Int
}
