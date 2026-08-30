package com.example.localread

import io.documentnode.epub4j.epub.EpubReader
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class EpubParseDebugTest {

    @Test
    fun parseTestBook() {
        val f = File("../testbooks/book1.epub")
        if (!f.exists()) {
            println("book1.epub 不存在,跳过")
            return
        }
        val book = EpubReader().readEpub(f.inputStream())
        println("title=${book.title}")
        println("authors=${book.metadata?.authors}")
        println("cover=${book.coverImage?.href}")
    }

    @Test
    fun parseTinyEpub() {
        // 最小 EPUB 结构,排除测试书籍本身的问题
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bid">x</dc:identifier>
    <dc:title>t</dc:title>
    <dc:language>zh</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
  <spine><itemref idref="c1"/></spine>
</package>"""
        val container = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
        val chapter = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>c</title></head><body><p>hi</p></body></html>"""
        val bytes = buildEpub(
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to container.toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/c1.xhtml" to chapter.toByteArray(),
            )
        )
        val book = EpubReader().readEpub(ByteArrayInputStream(bytes))
        println("tiny title=${book.title}")
    }

    private fun buildEpub(entries: Map<String, ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val zip = java.util.zip.ZipOutputStream(out)
        for ((name, data) in entries) {
            zip.putNextEntry(java.util.zip.ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
        zip.close()
        return out.toByteArray()
    }
}
