package com.example.rssreader.core.data

import androidx.room.Embedded

/** 列表项：文章 + 所属源名称（联表查询结果，避免界面层再查一次）。 */
data class ArticleWithFeed(
    @Embedded val article: ArticleEntity,
    val feedTitle: String
)

/** 三个标签页的计数，一条 SQL 聚合出来。 */
data class ArticleCounts(
    val total: Int,
    val unread: Int,
    val starred: Int
)

/** 订阅源列表项：源 + 文章总数 + 未读数。 */
data class FeedWithCount(
    val id: Long,
    val title: String,
    val url: String,
    val builtin: Boolean,
    val lastSync: Long,
    val total: Int,
    val unread: Int
)
