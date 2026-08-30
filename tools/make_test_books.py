# -*- coding: utf-8 -*-
"""生成测试用 EPUB 与 TXT 书籍文件"""
import zipfile, io, os, struct, zlib

OUT = r"D:\Project\AndroidProject\testbooks"
os.makedirs(OUT, exist_ok=True)

CONTAINER = '''<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>'''

def cover_png():
    """纯 python 画一张渐变+文字条封面 PNG (360x500)"""
    W, H = 360, 500
    rows = []
    top = (61, 109, 229); bottom = (30, 60, 140)
    # 简单渐变 + 中央白色块模拟书名区
    for y in range(H):
        t = y / H
        r = int(top[0] + (bottom[0]-top[0])*t)
        g = int(top[1] + (bottom[1]-top[1])*t)
        b = int(top[2] + (bottom[2]-top[2])*t)
        row = b''
        for x in range(W):
            if 60 <= x < 300 and 180 <= y < 260:
                row += bytes((255, 255, 255))
            else:
                row += bytes((r, g, b))
        rows.append(b'\x00' + row)
    raw = b''.join(rows)

    def chunk(typ, data):
        c = struct.pack('>I', len(data)) + typ + data
        c += struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)
        return c

    ihdr = struct.pack('>IIBBBBB', W, H, 8, 2, 0, 0, 0)
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', ihdr)
            + chunk(b'IDAT', zlib.compress(raw, 6))
            + chunk(b'IEND', b''))

def chapter(title, paragraphs):
    body = ''.join(f'<p>{p}</p>' for p in paragraphs)
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><meta charset="utf-8"/><title>{title}</title></head>
<body>
<h2>{title}</h2>
{body}
</body>
</html>'''

ch1 = chapter('第一章 山村少年', [
    '清晨的雾气还没有散去,山脚下的小村庄已经升起了炊烟。',
    '少年阿远背着竹篓,沿着青石板路往山上走去。露水打湿了他的裤脚,他却毫不在意。',
    '村里的老人常说,山的那一边是另一个世界。阿远从小就想去看看,那个世界到底是什么模样。',
    '这一天,他在山涧边捡到了一块温润的玉佩。玉佩贴在掌心,隐隐有暖意流转。',
    '阿远不知道,这块玉佩将彻底改变他的命运。',
] * 6)

ch2 = chapter('第二章 初入江湖', [
    '离开山村的那天,阿远只带了一把柴刀和半袋干粮。',
    '官道上人来人往,有挑担的货郎,也有佩剑的江湖客。阿远第一次见到这么多陌生人。',
    '在镇上的茶棚里,他听说了一个词——武林。',
    '刀光剑影,快意恩仇。少年握紧了拳头,心里燃起一团火。',
    '夜幕降临,他枕着玉佩睡在破庙里,梦里全是山外的风景。',
] * 6)

ch3 = chapter('第三章 玉佩之秘', [
    '玉佩在月圆之夜发出了微光。',
    '一道苍老的声音在阿远脑海中响起:"小娃娃,你我有缘。"',
    '原来玉佩中封印着一位前辈的残魂。前辈自称云隐子,曾是名动天下的剑客。',
    '从这一夜起,阿远白日赶路,夜里修行。引气入体、锤炼筋骨,寒来暑往,从不间断。',
    '三年之后,阿远站在山巅,长剑出鞘,寒光凛冽。他知道,属于自己的江湖,终于来了。',
] * 6)

nav = '''<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>目录</title></head>
<body><nav epub:type="toc" id="toc"><ol>
<li><a href="ch1.xhtml">第一章 山村少年</a></li>
<li><a href="ch2.xhtml">第二章 初入江湖</a></li>
<li><a href="ch3.xhtml">第三章 玉佩之秘</a></li>
</ol></nav></body></html>'''

opf = '''<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="zh">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:test-book-001</dc:identifier>
    <dc:title>山海行纪</dc:title>
    <dc:creator>测试作者</dc:creator>
    <dc:language>zh</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="cover-image" href="cover.png" media-type="image/png" properties="cover-image"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch3" href="ch3.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
    <itemref idref="ch3"/>
  </spine>
</package>'''

ncx = '''<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="urn:uuid:test-book-001"/></head>
<docTitle><text>山海行纪</text></docTitle>
<navMap>
<navPoint id="np1" playOrder="1"><navLabel><text>第一章 山村少年</text></navLabel><content src="ch1.xhtml"/></navPoint>
<navPoint id="np2" playOrder="2"><navLabel><text>第二章 初入江湖</text></navLabel><content src="ch2.xhtml"/></navPoint>
<navPoint id="np3" playOrder="3"><navLabel><text>第三章 玉佩之秘</text></navLabel><content src="ch3.xhtml"/></navPoint>
</navMap>
</ncx>'''

epub_path = os.path.join(OUT, '山海行纪.epub')
with zipfile.ZipFile(epub_path, 'w') as z:
    z.writestr(zipfile.ZipInfo('mimetype'), 'application/epub+zip', zipfile.ZIP_STORED)
    z.writestr('META-INF/container.xml', CONTAINER)
    z.writestr('OEBPS/cover.png', cover_png())
    z.writestr('OEBPS/ch1.xhtml', ch1)
    z.writestr('OEBPS/ch2.xhtml', ch2)
    z.writestr('OEBPS/ch3.xhtml', ch3)
    z.writestr('OEBPS/nav.xhtml', nav)
    z.writestr('OEBPS/content.opf', opf)
    z.writestr('OEBPS/toc.ncx', ncx)
print('EPUB:', epub_path, os.path.getsize(epub_path), 'bytes')

# TXT:带章节标记的中文小说
lines = ['仙路独行', '作者:佚名', '']
for i in range(1, 9):
    lines.append(f'第{i}章 历练')
    for j in range(30):
        lines.append(f'这是第{i}章第{j}段的内容。少年在江湖中不断历练,风霜雨雪都是修行,刀剑拳脚皆是学问。')
    lines.append('')
txt_path = os.path.join(OUT, '仙路独行.txt')
with open(txt_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))
print('TXT:', txt_path, os.path.getsize(txt_path), 'bytes')
