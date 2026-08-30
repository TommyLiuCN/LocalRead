# 拾阅 — 本地 EPUB 阅读器(仿微信读书 UI)

一个纯本地、无任何网络权限的 Android 阅读器。UI 交互参照微信读书:书架网格、阅读页点击中央呼出菜单、底部目录/进度条/夜间模式/设置面板、五套阅读主题、阅读时长统计。支持 **EPUB** 与 **TXT**(TXT 自动转制为 EPUB,复用同一渲染管线)。

| 书架 | 阅读菜单 | 目录 | 设置 |
|---|---|---|---|
| ![书架](docs/screenshots/shelf.png) | ![阅读菜单](docs/screenshots/reader_menu.png) | ![目录](docs/screenshots/toc.png) | ![设置](docs/screenshots/settings.png) |

| 夜间模式 | TXT 阅读 | 阅读统计 | 长按操作 |
|---|---|---|---|
| ![夜间](docs/screenshots/night.png) | ![TXT](docs/screenshots/txt_reader.png) | ![统计](docs/screenshots/stats.png) | ![操作](docs/screenshots/book_actions.png) |


## 技术栈

| 层 | 方案 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) |
| 正文渲染 | WebView 内嵌 [foliate-js](https://github.com/johnfactotum/foliate-js) (MIT,vendor 于 `app/src/main/assets/reader/vendor/`) |
| EPUB 元数据 | [epub4j](https://github.com/documentnode/epub4j) (Apache-2.0) |
| TXT 处理 | juniversalchardet 编码检测 + 正则章节切分 + 生成轻量 EPUB |
| 数据 | Room(书架、阅读时长)+ DataStore(排版偏好) |
| 构建 | Gradle 9.5 + AGP 8.13,minSdk 26 / targetSdk 36 |

## 架构速览

```
app/src/main/
├─ assets/reader/            # WebView 侧
│  ├─ index.html / reader.js # 封装 foliate-js:开书、样式注入、点击分区上报
│  └─ vendor/foliate-js/     # 内核源码(锁版本,升级需回归测试)
└─ java/com/example/localread/
   ├─ data/        # Room 实体/DAO、DataStore 偏好、阅读主题定义
   ├─ importer/    # SAF 导入:epub4j 元数据/封面、TXT 转制、默认封面生成
   ├─ reader/      # ReaderActivity、ReaderScreen(菜单/目录/设置)、
   │               # ReaderBridge(JS↔Native)、ReaderWebViewClient(文件桥)
   └─ ui/          # 书架、统计页、App 主题
```

关键机制:

- **文件桥**:`ReaderWebViewClient` 把 `https://appassets.androidplatform.net/assets/...` 映射到 assets、`/books/<id>` 流式映射到私有目录书籍文件,让 foliate-js 的 ES Modules 与 fetch 都工作在同一个 https origin 上。
- **进度记忆**:JS `relocate` 事件 → 防抖 600ms 写 Room(CFI + 全书百分比);退出阅读页立即落库;重开时 `view.init({lastLocation})` 恢复。
- **排版即时生效**:字号/字体/行距/边距/主题全部经 `reader.setStyle(...)` 以 CSS 注入,fotiate 自动重排并锚定原位置。
- **阅读时长**:阅读页每 30s 与 onPause 时结算秒数,按 (书, 天) 聚合进 Room。

## 构建与运行

```bash
# Android Studio 直接打开本目录,或在命令行:
./gradlew assembleDebug          # 产物: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # TXT 章节切分单元测试
```

- `local.properties` 中 `sdk.dir` 指向本机 SDK。
- Gradle 发行版走腾讯镜像(见 `gradle/wrapper/gradle-wrapper.properties`),Maven 仓库走阿里云镜像(见 `settings.gradle.kts`),国内网络可复现。
- 首次运行:点书架右上角 **+** 导入 EPUB/TXT(经系统文件选择器)。

## 功能清单(首版)

- 书架:封面网格、未读/已读百分比、置顶、长按删除、多选导入
- 阅读:左右滑动翻页(foliate 内置跟随手指动画)、点击左/右 1/3 翻页、点击中央呼出菜单
- 目录:章节列表、当前章高亮、点击跳转
- 进度:底部滑条任意跳转、百分比实时显示、重开续读
- 设置:字号、字体(无衬线/衬线)、行距、页边距、左右翻页/上下滚动、5 套背景主题
- 夜间模式:阅读页一键切换
- 统计:今日/累计时长、最近 7 天柱状图、阅读排行
- TXT:GBK/UTF-8 等编码自动识别、`第X章/回/卷` 智能切章、无标记按字数降级切分

## 二期候选

划线与笔记(foliate-js overlayer 已就位)、TTS 听书(tts.js)、书内搜索、仿真卷页动画(需页面截图 + GL)、WiFi 传书、PDF/MOBI。

## 已知限制

- EPUB 内嵌 CSS 的部分样式会被阅读主题覆盖(统一排版,微信读书同策略)。
- 页内超链接可点击跳转,外链在应用内不做处理。
- 无内容选择菜单交互(划线笔记属二期)。

## 许可

本项目代码自行编写;foliate-js(MIT)、epub4j(Apache-2.0)等依赖见上表。未使用任何 GPL 代码。
