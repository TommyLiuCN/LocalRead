package com.example.localread.reader

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localread.LocalReadApplication
import com.example.localread.data.PrefsRepository
import com.example.localread.data.ReaderPrefs
import com.example.localread.data.db.BookEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as LocalReadApplication).database
    private val prefsRepo = PrefsRepository(app)

    val prefs: StateFlow<ReaderPrefs?> = prefsRepo.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book

    val todaySeconds: StateFlow<Long> = db.readingStatDao()
        .observeSecondsSince(LocalDate.now().toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun load(bookId: String) {
        viewModelScope.launch { _book.value = db.bookDao().getById(bookId) }
    }

    // ---------- 进度记忆 ----------

    private var progressJob: Job? = null

    fun onRelocated(cfi: String?, fraction: Float) {
        val book = _book.value ?: return
        if (cfi.isNullOrBlank()) return
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(600) // 快速连续翻页时只落库最后位置
            db.bookDao().updateProgress(book.id, cfi, fraction, System.currentTimeMillis())
        }
    }

    /** 退出阅读页时立即落库,避免防抖窗口丢进度 */
    fun flushProgress(cfi: String?, fraction: Float) {
        val book = _book.value ?: return
        if (cfi.isNullOrBlank()) return
        progressJob?.cancel()
        viewModelScope.launch {
            db.bookDao().updateProgress(book.id, cfi, fraction, System.currentTimeMillis())
        }
    }

    // ---------- 阅读时长统计 ----------

    private var resumeAt = 0L
    private var flushedSeconds = 0L
    private var tickerJob: Job? = null

    fun onReaderResumed() {
        resumeAt = SystemClock.elapsedRealtime()
        flushedSeconds = 0L
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                tickReadingTime()
            }
        }
    }

    fun onReaderPaused() {
        tickerJob?.cancel()
        tickReadingTime()
        resumeAt = 0L
    }

    private fun tickReadingTime() {
        if (resumeAt == 0L) return
        val total = (SystemClock.elapsedRealtime() - resumeAt) / 1000
        val delta = total - flushedSeconds
        if (delta <= 0) return
        flushedSeconds += delta
        val book = _book.value ?: return
        viewModelScope.launch {
            db.readingStatDao().addSeconds(book.id, LocalDate.now().toEpochDay(), delta)
        }
    }

    // ---------- 偏好 ----------

    fun updatePrefs(transform: (ReaderPrefs) -> ReaderPrefs) {
        viewModelScope.launch { prefsRepo.update(transform) }
    }

    // ---------- 书籍操作 ----------

    fun togglePinned() {
        val book = _book.value ?: return
        viewModelScope.launch { db.bookDao().setPinned(book.id, !book.pinned) }
    }

    fun deleteBook(onDone: () -> Unit) {
        val book = _book.value ?: return
        viewModelScope.launch {
            db.bookDao().delete(book.id)
            onDone()
        }
    }
}
