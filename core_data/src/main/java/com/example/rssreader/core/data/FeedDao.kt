package com.example.rssreader.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds ORDER BY builtin DESC, id ASC")
    suspend fun queryAll(): List<FeedEntity>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun queryById(id: Long): FeedEntity?

    @Query("SELECT COUNT(*) FROM feeds WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun count(): Int

    @Insert
    suspend fun insert(feed: FeedEntity): Long

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE feeds SET lastSync = :time WHERE id = :id")
    suspend fun updateLastSync(id: Long, time: Long)

    @Query("UPDATE feeds SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    /**
     * 订阅源列表 + 文章总数 + 未读数。
     * SUM 在没有任何文章时返回 NULL，因此用 COALESCE 兜成 0。
     */
    @Query(
        """
        SELECT f.id AS id,
               f.title AS title,
               f.url AS url,
               f.builtin AS builtin,
               f.lastSync AS lastSync,
               COUNT(a.id) AS total,
               COALESCE(SUM(CASE WHEN a.isRead = 0 THEN 1 ELSE 0 END), 0) AS unread
        FROM feeds f
        LEFT JOIN articles a ON a.feedId = f.id
        GROUP BY f.id
        ORDER BY f.builtin DESC, f.id ASC
        """
    )
    fun observeFeedsWithCount(): Flow<List<FeedWithCount>>
}
