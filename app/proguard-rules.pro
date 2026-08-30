# WebView 里的 JS 通过反射调用 bridge,Release 混淆时保留
-keepclassmembers class com.example.localread.reader.ReaderBridge$* {
    public *;
}
-keep class com.example.localread.reader.ReaderBridge { public *; }

# epub4j 使用 javax.xml 解析
-dontwarn nl.siegmann.epublib.**
-dontwarn io.documentnode.**
