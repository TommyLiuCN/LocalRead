package com.example.localread.reader

import android.webkit.JavascriptInterface

/**
 * JS → Native 事件桥。回调运行在 WebView 的 JS 桥线程,接收方必须自行切换到主线程。
 * payload 为 JSON 字符串,统一入口便于排查。
 */
class ReaderBridge(private val onEvent: (type: String, payload: String?) -> Unit) {

    @JavascriptInterface
    fun postEvent(type: String, payload: String?) {
        onEvent(type, payload)
    }
}
