package com.example.localread.reader

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * 文件桥:把书籍文件与 reader 静态资源映射到统一的 https origin,
 * 让 foliate-js 的 ES Modules 与 fetch 正常工作,书籍大文件走流式响应。
 */
class ReaderWebViewClient(
    private val bookFileProvider: () -> String?,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null
        val path = url.path ?: return notFound()
        return try {
            when {
                path.startsWith("/books/") -> serveBook()
                path.startsWith("/assets/") -> serveAsset(view, path.removePrefix("/assets/"))
                else -> notFound()
            }
        } catch (e: Exception) {
            notFound()
        }
    }

    private fun serveBook(): WebResourceResponse {
        val path = bookFileProvider() ?: return notFound()
        val file = File(path)
        if (!file.exists()) return notFound()
        return WebResourceResponse("application/epub+zip", null, FileInputStream(file))
    }

    private fun serveAsset(view: WebView, relPath: String): WebResourceResponse {
        val stream = view.context.assets.open(relPath)
        val mime = mimeOf(relPath)
        val encoding = if (mime.startsWith("text/") || mime == "application/json") "utf-8" else null
        return WebResourceResponse(mime, encoding, stream)
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)),
    )

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "html", "xhtml" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true

    private companion object {
        const val HOST = "appassets.androidplatform.net"
    }
}
