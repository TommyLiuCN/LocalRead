package com.example.localread.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.localread.data.db.BookEntity
import io.documentnode.epub4j.epub.EpubReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImportException(message: String) : Exception(message)

/** SAF 导入入口:按扩展名与魔数分流 EPUB / TXT,统一产出 BookEntity */
object BookImporter {

    suspend fun import(context: Context, uri: Uri): BookEntity = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri) ?: throw ImportException("无法读取文件名")
        val name = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "").lowercase()

        when {
            ext == "txt" -> importTxt(context, uri, name)
            ext == "epub" || ext == "zip" -> importEpub(context, uri, name)
            else -> {
                // 无扩展名时按魔数兜底:PK 即按 EPUB 处理
                val head = readHead(context, uri)
                if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                    importEpub(context, uri, name)
                } else {
                    throw ImportException("暂不支持该文件格式")
                }
            }
        }
    }

    private fun importEpub(context: Context, uri: Uri, fallbackName: String): BookEntity {
        val id = UUID.randomUUID().toString()
        val file = File(booksDir(context), "$id.epub")
        copyToFile(context, uri, file)

        val parsed = try {
            EpubReader().readEpub(file.inputStream())
        } catch (e: Exception) {
            file.delete()
            throw ImportException("EPUB 解析失败,文件可能已损坏")
        }

        val title = parsed.title?.takeIf { it.isNotBlank() } ?: fallbackName
        val author = parsed.metadata?.authors
            ?.firstOrNull()?.toString()
            ?.takeIf { it.isNotBlank() }
        val coverPath = extractCover(context, parsed, id)
            ?: CoverGenerator.generate(context, title, author, id)

        return BookEntity(
            id = id,
            title = title,
            author = author,
            format = "EPUB",
            filePath = file.absolutePath,
            coverPath = coverPath,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun importTxt(context: Context, uri: Uri, fallbackName: String): BookEntity {
        val id = UUID.randomUUID().toString()
        val txtCopy = File(context.cacheDir, "$id.txt")
        copyToFile(context, uri, txtCopy)
        val epubFile = File(booksDir(context), "$id.epub")
        try {
            TxtImporter.convert(txtCopy, epubFile, fallbackName)
        } catch (e: Exception) {
            epubFile.delete()
            throw ImportException("TXT 解析失败:${e.message ?: "未知错误"}")
        } finally {
            txtCopy.delete()
        }
        val coverPath = CoverGenerator.generate(context, fallbackName, null, id)
        return BookEntity(
            id = id,
            title = fallbackName,
            author = null,
            format = "TXT",
            filePath = epubFile.absolutePath,
            coverPath = coverPath,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun extractCover(context: Context, parsed: io.documentnode.epub4j.domain.Book, id: String): String? {
        extractCoverFile(context, parsed.coverImage ?: return fallbackCover(context, parsed, id), id)
            ?.let { return it }
        return fallbackCover(context, parsed, id)
    }

    /** epub4j 不识别 EPUB3 properties="cover-image",此时取全书最大的位图资源作封面 */
    private fun fallbackCover(context: Context, parsed: io.documentnode.epub4j.domain.Book, id: String): String? {
        val best = runCatching {
            parsed.resources?.all
                ?.filter { it.mediaType?.name?.startsWith("image/") == true }
                ?.maxByOrNull { runCatching { it.size }.getOrDefault(0L) }
        }.getOrNull() ?: return null
        return extractCoverFile(context, best, id)
    }

    private fun extractCoverFile(context: Context, resource: io.documentnode.epub4j.domain.Resource, id: String): String? {
        val data = try {
            resource.data ?: return null
        } catch (e: Exception) {
            return null
        }
        val ext = when (resource.mediaType?.name) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "$id.$ext")
        return try {
            file.writeBytes(data)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun booksDir(context: Context) =
        File(context.filesDir, "books").apply { mkdirs() }

    private fun copyToFile(context: Context, uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("无法打开文件")
        input.use { target.outputStream().use { it.write(input.readBytes()) } }
    }

    private fun readHead(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("无法打开文件")
        input.use {
            val buf = ByteArray(4)
            var read = 0
            while (read < buf.size) {
                val n = it.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            return buf.copyOf(read)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }
}
