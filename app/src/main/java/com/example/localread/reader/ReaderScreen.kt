package com.example.localread.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localread.data.READER_THEMES
import com.example.localread.data.ReaderPrefs
import com.example.localread.data.ReaderTheme
import com.example.localread.data.cssColor
import com.example.localread.data.currentReaderTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val BOOK_BASE_URL = "https://appassets.androidplatform.net/books/"
private const val READER_URL = "https://appassets.androidplatform.net/assets/reader/index.html"

private const val MIN_FONT = 15
private const val MAX_FONT = 30

data class TocItem(val label: String, val href: String, val level: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val composeView = LocalView.current

    val book by vm.book.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val todaySeconds by vm.todaySeconds.collectAsStateWithLifecycle()

    var menuVisible by remember { mutableStateOf(false) }
    val menuOpen = rememberUpdatedState(menuVisible)
    val toggleMenu = { menuVisible = !menuVisible }
    val closeMenu = { menuVisible = false }
    var toc by remember { mutableStateOf(emptyList<TocItem>()) }
    var lastCfi by remember { mutableStateOf<String?>(null) }
    var currentHref by remember { mutableStateOf<String?>(null) }
    var sectionLabel by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(0f) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderFraction by remember { mutableStateOf(0f) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var jsReady by remember { mutableStateOf(false) }
    var openSent by remember { mutableStateOf(false) }

    val theme = prefs?.let { currentReaderTheme(it) } ?: READER_THEMES[0]

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val webViewRef = remember { AtomicReference<WebView?>(null) }

    fun eval(js: String) {
        webViewRef.get()?.evaluateJavascript(js, null)
    }

    // 翻页动画与拖拽跟随由 WebView 内 rAF 驱动,但 WebView 在触摸/空闲期间可能停止产生帧
    // (Chromium 已知问题),动画会停摆、拖拽时页面不跟手。宿主侧持续 invalidate 驱动
    // BeginFrame 是社区通行解法。单实例心跳:重复调用先移除旧泵,避免多泵叠加;
    // invalidate 限频 ~60fps,避免高刷屏(120Hz)上无谓的合成风暴。
    val pumpCallbackRef = remember { AtomicReference<Choreographer.FrameCallback?>(null) }

    fun pumpTurnFrames(durationMs: Long = 600L) {
        pumpCallbackRef.get()?.let { Choreographer.getInstance().removeFrameCallback(it) }
        val target = webViewRef.get() ?: return
        val start = SystemClock.elapsedRealtime()
        var lastInvalidate = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastInvalidate >= 7) {
                    lastInvalidate = now
                    target.invalidate()
                }
                if (now - start < durationMs) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        pumpCallbackRef.set(callback)
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun onTapZone(zone: String) {
        when (zone) {
            "left" -> {
                pumpTurnFrames()
                eval("window.reader && window.reader.prev()")
            }
            "right" -> {
                pumpTurnFrames()
                eval("window.reader && window.reader.next()")
            }
            "center" -> toggleMenu()
        }
    }

    // 观察 WebView 手势判定点击分区,不消费事件——WebView 的滑动翻页/链接/选择不受影响。
    // 菜单打开时改在 Main pass 观察:点击若已被菜单控件消费则忽略,点到空白处收起菜单。
    val tapZoneHandler by rememberUpdatedState(::onTapZone)
    val tapZoneModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var up: PointerInputChange? = null
            while (up == null) {
                val pass = if (menuOpen.value) PointerEventPass.Main else PointerEventPass.Initial
                val event = awaitPointerEvent(pass)
                up = event.changes.firstOrNull { it.id == down.id && !it.pressed }
                // 拖拽期间 WebView 内核 rAF 可能停摆,持续刷新泵帧让页面实时跟手;
                // 单实例泵每次重置剩余时长,等效于"最后一次手势事件 + 400ms"
                if (up == null && !menuOpen.value) {
                    val pos = event.changes.firstOrNull { it.id == down.id }?.position ?: down.position
                    if ((pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                        pumpTurnFrames(400L)
                    }
                }
            }
            val change = up ?: return@awaitEachGesture
            val dist = (change.position - down.position).getDistance()
            val dt = change.uptimeMillis - down.uptimeMillis
            if (dist > viewConfiguration.touchSlop) {
                // 拖拽松手:内核会做吸附动画,同样需要泵帧驱动
                pumpTurnFrames()
                return@awaitEachGesture
            }
            if (dist > viewConfiguration.touchSlop * 2 || dt >= 450) return@awaitEachGesture
            if (menuOpen.value) {
                if (!change.isConsumed) closeMenu()
            } else {
                val w = size.width.toFloat()
                tapZoneHandler(
                    when {
                        change.position.x < w / 3 -> "left"
                        change.position.x > w * 2 / 3 -> "right"
                        else -> "center"
                    }
                )
            }
        }
    }

    fun styleJson(p: ReaderPrefs, t: ReaderTheme): String = JSONObject().apply {
        put("fontSize", p.fontSize)
        put("fontFamily", if (p.fontKey == "serif") "serif" else "sans-serif")
        put("lineSpacing", p.lineSpacing.toDouble())
        put("margin", p.marginPx)
        put("flow", p.flow)
        put("bg", cssColor(t.bg))
        put("fg", cssColor(t.fg))
        put("link", cssColor(t.link))
    }.toString()

    fun parseToc(payload: String?): List<TocItem> = runCatching {
        val arr = JSONArray(payload ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TocItem(o.optString("label"), o.optString("href"), o.optInt("level", 0))
        }
    }.getOrDefault(emptyList())

    val bridge = remember {
        ReaderBridge { type, payload ->
            mainHandler.post {
                when (type) {
                    "ready" -> jsReady = true
                    "relocated" -> runCatching {
                        val obj = JSONObject(payload ?: "{}")
                        val cfi = if (obj.isNull("cfi")) null else obj.optString("cfi")
                        val frac = obj.optDouble("fraction", 0.0).toFloat()
                        lastCfi = cfi
                        currentHref = if (obj.isNull("href")) null else obj.optString("href")
                        sectionLabel = obj.optString("label", "")
                        if (!sliderDragging) fraction = frac
                        vm.onRelocated(cfi, frac)
                    }
                    "toc" -> toc = parseToc(payload)
                    "error" -> runCatching {
                        error = JSONObject(payload ?: "{}")
                            .optString("message", "打开书籍失败")
                    }.getOrDefault(Unit)
                }
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.textZoom = 100
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            val debuggable = context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            webViewClient = ReaderWebViewClient { vm.book.value?.filePath }
            addJavascriptInterface(bridge, "LocalReader")
            setBackgroundColor(android.graphics.Color.WHITE)
            loadUrl(READER_URL)
        }.also { webViewRef.set(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            pumpCallbackRef.get()?.let { Choreographer.getInstance().removeFrameCallback(it) }
            webViewRef.set(null)
            webView.destroy()
        }
    }

    // 打开书籍:JS 就绪且书籍加载完成后发送一次
    LaunchedEffect(jsReady, book) {
        val b = book ?: return@LaunchedEffect
        val p = prefs ?: ReaderPrefs()
        if (jsReady && !openSent) {
            openSent = true
            val config = JSONObject().apply {
                put("url", BOOK_BASE_URL + b.id)
                put("lastLocation", b.lastLocation ?: JSONObject.NULL)
                put("style", JSONObject(styleJson(p, currentReaderTheme(p))))
            }
            eval("window.reader.open($config)")
        }
    }

    // 偏好变化实时生效
    LaunchedEffect(prefs) {
        val p = prefs ?: return@LaunchedEffect
        val t = currentReaderTheme(p)
        webView.setBackgroundColor(t.bg.toInt())
        if (openSent) eval("window.reader.setStyle(${styleJson(p, t)})")
    }

    // 阅读时隐藏系统栏,菜单弹出时显示
    LaunchedEffect(menuVisible) {
        val controller = WindowCompat.getInsetsController(
            (context as android.app.Activity).window, composeView,
        )
        if (menuVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 高刷请求:OEM 帧率治理会把未列入白名单的应用钉在 60Hz 档(实测 vivo: normal=60,
    // 滑动中也不升档,preferredDisplayModeId 会被 SPS 服务剥掉)。这里把所有应用侧手段
    // 全部用上:preferredDisplayModeId + preferredRefreshRate 投票 + API 36 的触摸升频
    // 开关;系统仍不采纳时,需要用户在系统设置中为本应用开启高刷新率。
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val window = activity.window
        // 注意用 DisplayManager 拿主屏:Activity.getDisplay() 在窗口 attach 前返回 null,
        // 而组合发生在 attach 之前,用它会整段静默失效
        runCatching {
            val dm = activity.getSystemService(android.hardware.display.DisplayManager::class.java)
            val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                ?: return@runCatching
            val current = display.mode
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .filter { it.refreshRate <= 121f }
                .maxByOrNull { it.refreshRate }
            Log.i("ReaderFPS", "request high refresh: mode=$best current=$current")
            if (best != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                    preferredRefreshRate = best.refreshRate
                }
            }
        }.onFailure { Log.w("ReaderFPS", "mode request failed", it) }
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            runCatching {
                window.setFrameRateBoostOnTouchEnabled(true)
                window.setFrameRatePowerSavingsBalanced(false)
            }
        }
    }

    // 退出前立即落库进度
    androidx.compose.runtime.DisposableEffect(vm) {
        onDispose {
            vm.flushProgress(lastCfi, fraction)
        }
    }

    BackHandler(enabled = menuVisible || showToc || showSettings) {
        when {
            showToc -> showToc = false
            showSettings -> showSettings = false
            else -> menuVisible = false
        }
    }

    val menuBg = Color(theme.menuBg)
    val menuFg = Color(theme.fg)
    val menuSubFg = Color(theme.menuSubFg)
    val accent = Color(theme.link)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(theme.bg))
            .then(tapZoneModifier),
    ) {
        // 阅读时系统栏被隐藏(statusBars 为 0),挖孔屏的摄像头安全区只能靠 displayCutout
        // 上报;不预留的话正文第一行会顶进摄像头。该值是物理挖孔位置,不随菜单开合变化,
        // 因此不会触发 WebView 重排版。Box 背景与正文主题色一致,留出的条带无色差。
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout),
        )

        // 菜单打开时 WebView interop 会消费内容区全部触摸,点内容收不起菜单;
        // 盖一层 scrim 接管点击,顺带阻止菜单开着时误翻页(菜单栏在 scrim 之上,不受影响)
        if (menuVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { closeMenu() }
                    },
            )
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = menuBg, shadowElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = menuFg)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            book?.title ?: "",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = menuFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "今日阅读 ${formatReadingTime(todaySeconds)}",
                            fontSize = 11.sp,
                            color = menuSubFg,
                        )
                    }
                    IconButton(onClick = { showBookMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = menuFg)
                    }
                    DropdownMenu(
                        expanded = showBookMenu,
                        onDismissRequest = { showBookMenu = false },
                        containerColor = menuBg,
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (book?.pinned == true) "取消置顶" else "置顶", color = menuFg) },
                            leadingIcon = { Icon(Icons.Filled.PushPin, null, tint = menuSubFg) },
                            onClick = {
                                showBookMenu = false
                                vm.togglePinned()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除本书", color = menuFg) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = menuSubFg) },
                            onClick = {
                                showBookMenu = false
                                showDeleteConfirm = true
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(color = menuBg, shadowElevation = 12.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sectionLabel.ifBlank { "正文" },
                            fontSize = 12.sp,
                            color = menuSubFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${(((if (sliderDragging) sliderFraction else fraction) * 100).roundToInt())}%",
                            fontSize = 12.sp,
                            color = menuSubFg,
                        )
                    }
                    Slider(
                        value = if (sliderDragging) sliderFraction else fraction,
                        onValueChange = {
                            sliderDragging = true
                            sliderFraction = it
                        },
                        onValueChangeFinished = {
                            eval("window.reader.goToFraction($sliderFraction)")
                            sliderDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = menuSubFg.copy(alpha = 0.25f),
                        ),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MenuAction(Icons.AutoMirrored.Filled.MenuBook, "目录", menuSubFg) { showToc = true }
                        MenuAction(
                            if (prefs?.nightMode == true) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            if (prefs?.nightMode == true) "日间" else "夜间",
                            menuSubFg,
                        ) { vm.updatePrefs { it.copy(nightMode = !it.nightMode) } }
                        MenuAction(Icons.Filled.Settings, "设置", menuSubFg) { showSettings = true }
                    }
                }
            }
        }

        if (book == null || prefs == null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = accent)
            }
        }
    }

    if (showToc) {
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            containerColor = menuBg,
        ) {
            Text(
                "目录",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = menuFg,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            if (toc.isEmpty()) {
                Text("暂无目录", color = menuSubFg, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(Modifier.height(420.dp)) {
                items(toc) { item ->
                    val isCurrent = item.href == currentHref
                    Text(
                        item.label,
                        fontSize = if (item.level == 0) 14.sp else 13.sp,
                        fontWeight = if (item.level == 0) FontWeight.Medium else FontWeight.Normal,
                        color = if (isCurrent) accent else menuFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showToc = false
                                eval("window.reader.goTo(${JSONObject.quote(item.href)})")
                            }
                            .padding(
                                start = (20 + item.level * 18).dp,
                                end = 20.dp,
                                top = 11.dp,
                                bottom = 11.dp,
                            ),
                    )
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = menuBg,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val p = prefs ?: return@Column

                SettingRow("字号", menuSubFg) {
                    IconButton(
                        onClick = { vm.updatePrefs { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(MIN_FONT)) } },
                        enabled = p.fontSize > MIN_FONT,
                    ) { Icon(Icons.Filled.Remove, "减小字号", tint = menuFg) }
                    Text(
                        "${p.fontSize}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = menuFg,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    IconButton(
                        onClick = { vm.updatePrefs { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(MAX_FONT)) } },
                        enabled = p.fontSize < MAX_FONT,
                    ) { Icon(Icons.Filled.Add, "增大字号", tint = menuFg) }
                }

                SettingRow("字体", menuSubFg) {
                    Chip("无衬线", p.fontKey == "sans", menuFg, menuSubFg, accent) {
                        vm.updatePrefs { it.copy(fontKey = "sans") }
                    }
                    Spacer(Modifier.width(10.dp))
                    Chip("衬线", p.fontKey == "serif", menuFg, menuSubFg, accent) {
                        vm.updatePrefs { it.copy(fontKey = "serif") }
                    }
                }

                SettingRow("行距", menuSubFg) {
                    val options = listOf(1.2f to "紧凑", 1.5f to "标准", 1.8f to "宽松", 2.2f to "很大")
                    options.forEachIndexed { i, (value, label) ->
                        if (i > 0) Spacer(Modifier.width(8.dp))
                        Chip(label, p.lineSpacing == value, menuFg, menuSubFg, accent) {
                            vm.updatePrefs { it.copy(lineSpacing = value) }
                        }
                    }
                }

                SettingRow("边距", menuSubFg) {
                    val options = listOf(24 to "窄", 48 to "标准", 80 to "宽")
                    options.forEachIndexed { i, (value, label) ->
                        if (i > 0) Spacer(Modifier.width(8.dp))
                        Chip(label, p.marginPx == value, menuFg, menuSubFg, accent) {
                            vm.updatePrefs { it.copy(marginPx = value) }
                        }
                    }
                }

                SettingRow("翻页", menuSubFg) {
                    Chip("左右翻页", p.flow == "paginated", menuFg, menuSubFg, accent) {
                        vm.updatePrefs { it.copy(flow = "paginated") }
                    }
                    Spacer(Modifier.width(10.dp))
                    Chip("上下滚动", p.flow == "scrolled", menuFg, menuSubFg, accent) {
                        vm.updatePrefs { it.copy(flow = "scrolled") }
                    }
                }

                SettingRow("背景", menuSubFg) {
                    READER_THEMES.take(4).forEachIndexed { i, t ->
                        val selected = !p.nightMode && p.themeIndex == i
                        Box(
                            Modifier
                                .padding(end = 14.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(t.bg))
                                .clickable {
                                    vm.updatePrefs { it.copy(themeIndex = i, nightMode = false) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, null, tint = Color(t.fg))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除本书") },
            text = { Text("删除后书籍文件与阅读记录将被清除,无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteBook { onBack() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("无法打开") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    error = null
                    onBack()
                }) { Text("确定") }
            },
        )
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Icon(icon, label, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun SettingRow(label: String, labelColor: Color, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = labelColor,
            modifier = Modifier.width(52.dp),
        )
        content()
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    fg: Color,
    subFg: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            labelColor = subFg,
            selectedContainerColor = accent.copy(alpha = 0.15f),
            selectedLabelColor = accent,
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = subFg.copy(alpha = 0.4f),
            selectedBorderColor = accent,
        ),
    )
}

internal fun formatReadingTime(seconds: Long): String = when {
    seconds < 60 -> "不足 1 分钟"
    seconds < 3600 -> "${seconds / 60} 分钟"
    else -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分钟"
}
