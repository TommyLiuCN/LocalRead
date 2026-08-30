// 阅读页封装:隔离 foliate-js 内核 API,与 Native 通过 LocalReader 桥通信
import './vendor/foliate-js/view.js'

const bridge = window.LocalReader || null
const post = (type, payload) => {
    if (!bridge) return
    try {
        bridge.postEvent(type, JSON.stringify(payload ?? null))
    } catch (e) {
        console.error('bridge post failed', type, e)
    }
}

let view = null

const flattenToc = (items, level = 0, out = []) => {
    for (const item of items ?? []) {
        out.push({ label: item.label, href: item.href, level })
        flattenToc(item.subitems, level + 1, out)
    }
    return out
}

const fontFamilyOf = key =>
    key === 'serif'
        ? 'Georgia, "Noto Serif", "Noto Serif CJK SC", serif'
        : '"Noto Sans", "Noto Sans CJK SC", "Source Han Sans SC", sans-serif'

const buildCSS = s => `
    @namespace epub "http://www.idpf.org/2007/ops";
    html {
        font-size: ${s.fontSize}px !important;
        font-family: ${s.fontFamily} !important;
        background: ${s.bg} !important;
        color: ${s.fg} !important;
    }
    body {
        font-family: inherit !important;
        background: ${s.bg} !important;
        color: ${s.fg} !important;
    }
    p, li, blockquote, dd {
        line-height: ${s.lineSpacing} !important;
        text-align: justify;
        widows: 2;
        orphans: 2;
    }
    a { color: ${s.link} !important; }
    pre { white-space: pre-wrap !important; }
    aside[epub|type~="footnote"],
    aside[epub|type~="endnote"],
    aside[epub|type~="rearnote"] {
        display: none;
    }
`

function applyStyle(s) {
    if (!view || !view.renderer) return
    try {
        view.renderer.setStyles?.(buildCSS(s))
        if (s.flow) view.renderer.setAttribute('flow', s.flow)
        // margin 必须带 CSS 单位,无单位会使 paginator 的 grid-template-rows 整条失效,
        // 页眉/页脚行被 stretch 拉出巨大空白
        if (s.margin != null) view.renderer.setAttribute('margin', `${s.margin}px`)
        // 跨章节 iframe 重建的瞬间露出宿主页背景,同步为主题色避免夜间模式白闪
        document.body.style.background = s.bg
    } catch (e) {
        console.error(e)
    }
}

// 点击分区(左/中/右)由原生层在 WebView 手势上判定,这里不处理指针事件。

// 点击翻页与内核自身的 touchend 吸附存在竞态:原生 up 与触摸事件经不同 IPC 进入渲染
// 进程,若 next() 抢在内核 touchend 之前执行,吸附回调会读到动画中途的滚动位置并把
// 页面拉回原地(表现为点击偶尔不翻页)。延迟一拍,让内核吸附先落地、滚动静止后再翻页;
// rAF 按注册顺序执行,吸附回调先注册,因此必定先于延后启动的翻页动画运行。
const deferTurn = run => {
    if (!view) return
    return new Promise(resolve => setTimeout(() => resolve(run()), 80))
}

window.reader = {
    async open(config) {
        try {
            if (view) {
                view.close()
                view.remove()
            }
            view = document.createElement('foliate-view')
            document.body.append(view)

            view.addEventListener('relocate', e => {
                const d = e.detail
                post('relocated', {
                    cfi: d.cfi ?? null,
                    fraction: d.fraction ?? 0,
                    href: d.tocItem?.href ?? null,
                    label: d.tocItem?.label ?? null,
                })
            })

            await view.open(config.url)
            // 翻页/吸附动画在 paginator 上门控,foliate-view 不会转发该属性,须直接设置
            view.renderer?.setAttribute('animated', '')
            applyStyle(config.style)

            const toc = flattenToc(await view.book.toc)
            post('toc', toc)

            // 恢复上次位置;CFI 失效时退回书首
            try {
                await view.init({ lastLocation: config.lastLocation ?? undefined })
            } catch (err) {
                console.error('resume failed', err)
                await view.init({})
            }
        } catch (err) {
            console.error(err)
            post('error', { message: String(err?.message ?? err) })
        }
    },

    setStyle(style) {
        applyStyle(style)
    },

    next() {
        return deferTurn(() => view?.next())
    },

    prev() {
        return deferTurn(() => view?.prev())
    },

    goTo(href) {
        view?.goTo(href)
    },

    goToFraction(fraction) {
        view?.goToFraction(Number(fraction))
    },
}

post('ready')
