package com.example.rssreader.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文章。三类状态存在同一张表里：
 *  - 内容字段（title/summary/content/pubDate…）：每次同步都会被源站最新内容刷新
 *  - isRead / isStarred：用户状态，同步时绝不被覆盖（离线缓存 + 已读未读的核心）
 *  - cachedAt：写入本地的时间，详情页用来显示"正文已缓存在本地"
 *
 * 唯一索引 (feedId, guid) 保证同一篇文章反复拉取不会重复入库。
 */
@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["pubDate"]),
        Index(value = ["isRead"]),
        Index(value = ["isStarred"])
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val feedId: Long,
    val guid: String,
    val title: String,
    val link: String? = null,
    val author: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val pubDate: Long = 0L,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val cachedAt: Long = 0L
)
