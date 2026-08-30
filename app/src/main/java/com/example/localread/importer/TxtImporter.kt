package com.example.localread.importer

import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TXT 转制为轻量 EPUB,使 TXT 与 EPUB 复用同一条 foliate-js 渲染管线。
 */
object TxtImporter {

    private val CHAPTER_REGEX = Regex(
        """^\s*(第\s*[0-9零一二三四五六七八九十百千万两]{1,12}\s*[章回节卷集部篇]|序章|楔子|引子|终章|尾声|番外)\s*.*"""
    )

    data class TxtChapter(val title: String, val paragraphs: List<String>)
    data class ConvertedTxt(val epubFile: File, val title: String, val chapterCount: Int)

    fun detectCharset(headBytes: ByteArray): String {
        val detector = UniversalDetector(null)
        detector.handleData(headBytes, 0, headBytes.size)
        detector.dataEnd()
        return detector.detectedCharset ?: "UTF-8"
    }

    /** 纯函数,便于单测:把整本 TXT 的行切成章节 */
    fun splitChapters(lines: List<String>): List<TxtChapter> {
        val chapters = mutableListOf<TxtChapter>()
        val preamble = mutableListOf<String>()
        var title: String? = null
        var paragraphs = mutableListOf<String>()
        var sawMarker = false

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (CHAPTER_REGEX.matches(line)) {
                if (sawMarker) {
                    chapters += TxtChapter(title ?: "未命名章节", paragraphs.toList())
                } else {
                    sawMarker = true
                }
                title = line
                paragraphs = mutableListOf()
            } else if (sawMarker) {
                paragraphs += line
            } else {
                preamble += line
            }
        }

        // 全书都没有章节标记时,按字符量降级切分
        if (!sawMarker) return chunkBySize(lines, 15_000)

        chapters += TxtChapter(title ?: "未命名章节", paragraphs.toList())

        // 书名/作者等短前言并入第一章,长前言保留为"开篇"
        if (preamble.isNotEmpty()) {
            val size = preamble.sumOf { it.length }
            if (size < 500) {
                val first = chapters.first()
                chapters[0] = first.copy(paragraphs = preamble + first.paragraphs)
            } else {
                chapters.add(0, TxtChapter("开篇", preamble))
            }
        }
        return chapters
    }

    /** 无章节标记时按字符量降级切分 */
    private fun chunkBySize(lines: List<String>, targetChars: Int): List<TxtChapter> {
        val chapters = mutableListOf<TxtChapter>()
        var buffer = mutableListOf<String>()
        var chars = 0
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            buffer += line
            chars += line.length
            if (chars >= targetChars) {
                chapters += TxtChapter("第 ${chapters.size + 1} 节", buffer.toList())
                buffer = mutableListOf()
                chars = 0
            }
        }
        if (buffer.isNotEmpty()) {
            chapters += TxtChapter("第 ${chapters.size + 1} 节", buffer.toList())
        }
        if (chapters.isEmpty()) chapters += TxtChapter("开篇", emptyList())
        return chapters
    }

    fun convert(sourceFile: File, outputEpub: File, title: String): ConvertedTxt {
        val head = sourceFile.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            val read = input.read(buf)
            buf.copyOf(read.coerceAtLeast(0))
        }
        val charset = detectCharset(head)

        val lines = mutableListOf<String>()
        BufferedReader(InputStreamReader(sourceFile.inputStream(), charset)).use { reader ->
            reader.forEachLine { lines += it }
        }
        val chapters = splitChapters(lines)

        writeEpub(outputEpub, title, chapters)
        return ConvertedTxt(outputEpub, title, chapters.size)
    }

    private fun writeEpub(output: File, title: String, chapters: List<TxtChapter>) {
        ZipOutputStream(FileOutputStream(output)).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""",
            )

            val manifestItems = StringBuilder()
            val spineItems = StringBuilder()
            val navItems = StringBuilder()
            val ncxItems = StringBuilder()
            chapters.forEachIndexed { index, chapter ->
                val id = "ch${index + 1}"
                val href = "$id.xhtml"
                manifestItems.append(
                    """<item id="$id" href="$href" media-type="application/xhtml+xml"/>"""
                )
                spineItems.append("""<itemref idref="$id"/>""")
                navItems.append("""<li><a href="$href">${escape(chapter.title)}</a></li>""")
                ncxItems.append(
                    """<navPoint id="np${index + 1}" playOrder="${index + 1}">
  <navLabel><text>${escape(chapter.title)}</text></navLabel>
  <content src="$href"/>
</navPoint>""",
                )

                val body = chapter.paragraphs.joinToString(separator = "") { p ->
                    "<p>${escape(p)}</p>"
                }
                put(
                    "OEBPS/$href",
                    """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><meta charset="utf-8"/><title>${escape(chapter.title)}</title></head>
<body><h2>${escape(chapter.title)}</h2>$body</body>
</html>""",
                )
            }

            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="zh">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:${java.util.UUID.randomUUID()}</dc:identifier>
    <dc:title>${escape(title)}</dc:title>
    <dc:language>zh</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifestItems
  </manifest>
  <spine toc="ncx">
$spineItems
  </spine>
</package>""",
            )

            put(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>目录</title></head>
<body>
<nav epub:type="toc" id="toc"><ol>
$navItems
</ol></nav>
</body>
</html>""",
            )

            put(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="urn:uuid:localread"/></head>
<docTitle><text>${escape(title)}</text></docTitle>
<navMap>
$ncxItems
</navMap>
</ncx>""",
            )
        }
    }

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
