package com.example.rssreader.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class],
    version = 1,
    exportSchema = true
)
abstract class RssDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao

    abstract fun articleDao(): ArticleDao

    companion object {
        // 注意：文件名不能叫 rss_reader.db —— 旧 Java 版课程工程同包名用的就是这个名字，
        // 覆盖安装时 Room 会读到那份 SQLiteOpenHelper 建的旧库，schema 对不上直接崩。
        const val NAME = "rss_reader_kotlin.db"

        @Volatile
        private var instance: RssDatabase? = null

        fun get(context: Context): RssDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RssDatabase::class.java,
                    NAME
                )
                    // 课程设计场景：版本升级直接重建表，避免维护迁移脚本
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
