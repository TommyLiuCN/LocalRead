package com.example.localread.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query(
        "SELECT * FROM books ORDER BY pinned DESC, " +
            "(CASE WHEN lastReadAt IS NULL THEN 1 ELSE 0 END), lastReadAt DESC"
    )
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "UPDATE books SET lastLocation = :location, progress = :progress, " +
            "lastReadAt = :lastReadAt WHERE id = :id"
    )
    suspend fun updateProgress(id: String, location: String?, progress: Float, lastReadAt: Long)

    @Query("UPDATE books SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)
}

data class DailyStat(val epochDay: Long, val seconds: Long)

data class BookStat(val bookId: String, val seconds: Long)

@Dao
interface ReadingStatDao {

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM reading_stats WHERE epochDay = :epochDay")
    fun observeSecondsSince(epochDay: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM reading_stats")
    fun observeTotalSeconds(): Flow<Long>

    @Query(
        "SELECT epochDay, COALESCE(SUM(seconds), 0) AS seconds FROM reading_stats " +
            "WHERE epochDay >= :fromDay GROUP BY epochDay"
    )
    fun observeDailySince(fromDay: Long): Flow<List<DailyStat>>

    @Query(
        "SELECT bookId, COALESCE(SUM(seconds), 0) AS seconds FROM reading_stats " +
            "GROUP BY bookId ORDER BY seconds DESC LIMIT :limit"
    )
    fun observeTopBooks(limit: Int): Flow<List<BookStat>>

    @Query("SELECT seconds FROM reading_stats WHERE bookId = :bookId AND epochDay = :epochDay")
    suspend fun getSeconds(bookId: String, epochDay: Long): Long?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(stat: ReadingStatEntity)

    @Query(
        "UPDATE reading_stats SET seconds = seconds + :delta " +
            "WHERE bookId = :bookId AND epochDay = :epochDay"
    )
    suspend fun addSecondsRaw(bookId: String, epochDay: Long, delta: Long)

    @Transaction
    suspend fun addSeconds(bookId: String, epochDay: Long, delta: Long) {
        if (delta <= 0) return
        val current = getSeconds(bookId, epochDay)
        if (current == null) {
            insert(ReadingStatEntity(bookId, epochDay, delta))
        } else {
            addSecondsRaw(bookId, epochDay, delta)
        }
    }
}
