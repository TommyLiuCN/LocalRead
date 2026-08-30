package com.example.localread.ui.shelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localread.LocalReadApplication
import com.example.localread.data.db.BookEntity
import com.example.localread.importer.BookImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ShelfViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as LocalReadApplication).database

    val books: StateFlow<List<BookEntity>> = db.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            val errors = mutableListOf<String>()
            for (uri in uris) {
                try {
                    val entity = BookImporter.import(getApplication(), uri)
                    db.bookDao().insert(entity)
                    ok++
                } catch (e: Exception) {
                    android.util.Log.e("ShelfImport", "import failed", e)
                    errors += "${e.javaClass.simpleName}: ${e.message ?: "导入失败"}"
                }
            }
            _message.value = buildString {
                if (ok > 0) append("已导入 $ok 本书籍")
                if (errors.isNotEmpty()) {
                    if (ok > 0) append(";")
                    append("导入失败 ${errors.size} 本:${errors.first()}")
                }
            }.ifBlank { null }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun togglePinned(book: BookEntity) {
        viewModelScope.launch { db.bookDao().setPinned(book.id, !book.pinned) }
    }

    fun delete(book: BookEntity) {
        viewModelScope.launch {
            db.bookDao().delete(book.id)
            listOfNotNull(book.filePath, book.coverPath).forEach { path ->
                File(path).delete()
            }
        }
    }
}
