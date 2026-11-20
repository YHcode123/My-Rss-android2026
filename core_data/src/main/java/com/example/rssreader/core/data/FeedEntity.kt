package com.example.rssreader.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订阅源。url 唯一，用来防止重复添加同一个源。
 * builtin 标记首启自动播种的内置源（目前仅作标识，用户仍可自行删除）。
 */
@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val builtin: Boolean = false,
    val lastSync: Long = 0L
)
