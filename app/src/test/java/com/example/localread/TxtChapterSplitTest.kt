package com.example.localread

import com.example.localread.importer.TxtImporter.splitChapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChapterSplitTest {

    @Test
    fun `常见章节标记可切分`() {
        val lines = listOf(
            "我的小说",
            "",
            "第一章 初见",
            "少年背起行囊,走出山村。",
            "山外有山。",
            "",
            "第二章 入城",
            "城门口人头攒动。",
            "",
            "第三十回 旧事重提",
            "茶馆里说书人一拍醒木。",
            "",
            "第108章 大结局",
            "故事落幕。",
        )
        val chapters = splitChapters(lines)
        assertEquals(4, chapters.size)
        assertEquals("第一章 初见", chapters[0].title)
        assertEquals("第二章 入城", chapters[1].title)
        assertEquals("第三十回 旧事重提", chapters[2].title)
        assertEquals("第108章 大结局", chapters[3].title)
        assertEquals(3, chapters[0].paragraphs.size) // 短前言"我的小说"并入第一章
    }

    @Test
    fun `卷与特殊章节`() {
        val lines = listOf(
            "序章",
            "一切的开始。",
            "第一卷 风起",
            "正文内容。",
            "番外 春日野餐",
            "甜文。",
            "楔子",
            "引子内容。",
        )
        val chapters = splitChapters(lines)
        assertTrue(chapters.size >= 3)
        assertTrue(chapters.any { it.title.startsWith("序章") })
        assertTrue(chapters.any { it.title.startsWith("番外") })
    }

    @Test
    fun `正文中的第二天不误判为章节`() {
        val lines = listOf(
            "第一章 出发",
            "第二天他出门了。",
            "第二次尝试。",
            "第二天数很关键。",
        )
        val chapters = splitChapters(lines)
        assertEquals(1, chapters.size)
        assertEquals(3, chapters[0].paragraphs.size)
    }

    @Test
    fun `无章节标记时按字数降级切分`() {
        val lines = (1..2000).map { "这是第 $it 行没有章节标记的正文内容,足够长一些。" }
        val chapters = splitChapters(lines)
        assertTrue("应有降级切分的多节: ${chapters.size}", chapters.size > 3)
        assertTrue(chapters.all { it.title.startsWith("第") })
    }

    @Test
    fun `中文数字章节号`() {
        val lines = listOf(
            "第一百零八回 大战红孩儿",
            "正文。",
            "第一千二百三十四章 收官",
            "正文二。",
        )
        val chapters = splitChapters(lines)
        assertEquals(2, chapters.size)
    }
}
