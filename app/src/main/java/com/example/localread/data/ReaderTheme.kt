package com.example.localread.data

/**
 * 阅读主题:颜色同时用于 WebView 内 CSS 与 Compose 菜单配色。
 * 颜色以 0xFFRRGGBB 存储;CSS 用 %06X 转十六进制。
 */
data class ReaderTheme(
    val name: String,
    val bg: Long,
    val fg: Long,
    val link: Long,
    val menuBg: Long,
    val menuSubFg: Long,
)

val READER_THEMES = listOf(
    ReaderTheme("白色", 0xFFFFFFFF, 0xFF1F1F1F, 0xFF3D6DE8, 0xFFF7F7F7, 0xFF8A8A8A),
    ReaderTheme("米黄", 0xFFF6F0E4, 0xFF43382A, 0xFF9A7B3F, 0xFFF6F0E4, 0xFF9A8C77),
    ReaderTheme("护眼绿", 0xFFCBE6CE, 0xFF233829, 0xFF2F6E44, 0xFFC2E0C6, 0xFF5B7361),
    ReaderTheme("羊皮纸", 0xFFE8DCC3, 0xFF4A3F2D, 0xFF8C6E3A, 0xFFE2D5B9, 0xFF8A7B60),
    ReaderTheme("夜间", 0xFF121417, 0xFFA8ADB5, 0xFF7286C6, 0xFF1B1D22, 0xFF7C8087),
)

fun currentReaderTheme(prefs: ReaderPrefs): ReaderTheme {
    val index = if (prefs.nightMode) 4 else prefs.themeIndex.coerceIn(0, 3)
    return READER_THEMES[index]
}

fun cssColor(argb: Long): String = "#%06X".format(0xFFFFFF and argb.toInt())
