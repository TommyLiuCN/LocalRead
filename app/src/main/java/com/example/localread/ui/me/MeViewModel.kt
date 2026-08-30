package com.example.localread.ui.me

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localread.LocalReadApplication
import com.example.localread.data.db.BookStat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RankedBook(val rank: Int, val title: String, val seconds: Long)

class MeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as LocalReadApplication).database
    private val today = LocalDate.now().toEpochDay()

    val todaySeconds: StateFlow<Long> = db.readingStatDao()
        .observeSecondsSince(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val totalSeconds: StateFlow<Long> = db.readingStatDao()
        .observeTotalSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val dailyStats: StateFlow<List<Pair<Long, Long>>> =
        db.readingStatDao().observeDailySince(today - 6)
            .map { daily ->
                (today - 6..today).map { day ->
                    day to (daily.firstOrNull { it.epochDay == day }?.seconds ?: 0L)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topBooks: StateFlow<List<RankedBook>> =
        db.readingStatDao().observeTopBooks(5)
            .combine(db.bookDao().observeAll()) { stats, books ->
                stats.mapIndexed { index, stat ->
                    RankedBook(
                        rank = index + 1,
                        title = books.firstOrNull { it.id == stat.bookId }?.title ?: "已删除书籍",
                        seconds = stat.seconds,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
