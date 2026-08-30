package com.example.localread.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    /** "EPUB" 或 "TXT"(TXT 已转制为 EPUB 存储) */
    val format: String,
    /** 阅读时实际打开的文件绝对路径(EPUB 原文件或 TXT 转制 EPUB) */
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastReadAt: Long? = null,
    /** 上次阅读位置(EPUB CFI) */
    val lastLocation: String? = null,
    /** 全书进度 0..1 */
    val progress: Float = 0f,
    val pinned: Boolean = false,
)

@Entity(tableName = "reading_stats", primaryKeys = ["bookId", "epochDay"])
data class ReadingStatEntity(
    val bookId: String,
    /** LocalDate.toEpochDay(),按天聚合阅读时长 */
    val epochDay: Long,
    val seconds: Long,
)
