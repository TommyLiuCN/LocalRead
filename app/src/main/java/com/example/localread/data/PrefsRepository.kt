package com.example.localread.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_prefs")

/** 阅读页排版偏好,经 CSS 注入 WebView 即时生效 */
data class ReaderPrefs(
    val fontSize: Int = 21,
    val fontKey: String = "sans",      // sans / serif
    val lineSpacing: Float = 1.5f,
    val marginPx: Int = 48,            // paginator margin
    val flow: String = "paginated",    // paginated / scrolled
    val themeIndex: Int = 0,
    val nightMode: Boolean = false,
)

class PrefsRepository(private val context: Context) {

    private object Keys {
        val fontSize = intPreferencesKey("font_size")
        val fontKey = stringPreferencesKey("font_key")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val marginPx = intPreferencesKey("margin_px")
        val flow = stringPreferencesKey("flow")
        val themeIndex = intPreferencesKey("theme_index")
        val nightMode = booleanPreferencesKey("night_mode")
    }

    val prefs: Flow<ReaderPrefs> = context.dataStore.data.map { p ->
        ReaderPrefs(
            fontSize = p[Keys.fontSize] ?: 21,
            fontKey = p[Keys.fontKey] ?: "sans",
            lineSpacing = p[Keys.lineSpacing] ?: 1.5f,
            marginPx = p[Keys.marginPx] ?: 48,
            flow = p[Keys.flow] ?: "paginated",
            themeIndex = p[Keys.themeIndex] ?: 0,
            nightMode = p[Keys.nightMode] ?: false,
        )
    }

    suspend fun update(transform: (ReaderPrefs) -> ReaderPrefs) {
        context.dataStore.edit { p ->
            val current = ReaderPrefs(
                fontSize = p[Keys.fontSize] ?: 21,
                fontKey = p[Keys.fontKey] ?: "sans",
                lineSpacing = p[Keys.lineSpacing] ?: 1.5f,
                marginPx = p[Keys.marginPx] ?: 48,
                flow = p[Keys.flow] ?: "paginated",
                themeIndex = p[Keys.themeIndex] ?: 0,
                nightMode = p[Keys.nightMode] ?: false,
            )
            val next = transform(current)
            p[Keys.fontSize] = next.fontSize
            p[Keys.fontKey] = next.fontKey
            p[Keys.lineSpacing] = next.lineSpacing
            p[Keys.marginPx] = next.marginPx
            p[Keys.flow] = next.flow
            p[Keys.themeIndex] = next.themeIndex
            p[Keys.nightMode] = next.nightMode
        }
    }
}
