"""
起動画面（Android 12 以降）のアイコン app/src/main/res/drawable/splash_icon.xml を作る。
上に積み木のアイコン（ic_launcher_foreground と同じ形）、すぐ下に題字「ミチツミ」（採集画面の題字と同じ積み木）。
起動画面のアイコンで見えるのは、288dp の絵のまん中の直径 192dp の円の中だけなので、その中に収める。

使い方（fonttools が要る。pip install fonttools）:
  python3 tools/make_splash_icon.py app/src/main/res/font/mplus_rounded_michitsumi.ttf \
      app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/splash_icon.xml \
      1.55 34 4 7.5 23 -9 6 -16 -70 260 60
  数字は順に：アイコンの倍率、題字のブロックの大きさ [dp]、ブロックの間、角の丸み、字の大きさ、最後のブロックを浮かせる量（負で上）、
  アイコンと題字の間、全体を上下にずらす量（負で上）、最後のブロックが落ち始める位置（上へ・右へ [dp]、負で上）と傾き [度]。
  落ち始める位置と傾きは splash_icon_animated.xml で 0 に戻すので、変えたら合わせる。
  最後のブロックは「drop」という名前のグループに入れてあり、動かす絵（splash_icon_animated.xml）が落としてはねさせる
"""
import sys, re
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
font_path, icon_path, out_path = sys.argv[1:4]
ICON_SCALE, SIZE, GAP, R, FONT, LIFT, SPACE, OFFSET, DROP_START, DROP_DX, DROP_ROT = (float(v) for v in sys.argv[4:15])
font = TTFont(font_path); gs = font.getGlyphSet(); cmap = font.getBestCmap()
C = 144.0  # 起動画面のアイコンの絵は 288×288dp。見えるのはまん中の直径 192dp の円の中だけ
def f(v): return ("%.2f" % v).rstrip("0").rstrip(".")
def rrect(x, y, w, h, r):
    return ("M%s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 -%s,%s h-%s a%s,%s 0 0 1 -%s,-%s v-%s a%s,%s 0 0 1 %s,-%s z" %
            (f(x + r), f(y), f(w - 2*r), f(r), f(r), f(r), f(r), f(h - 2*r), f(r), f(r), f(r), f(r), f(w - 2*r), f(r), f(r), f(r), f(r), f(h - 2*r), f(r), f(r), f(r), f(r)))
def glyph_path(ch, cx, cy):
    g = cmap[ord(ch)]
    bp = BoundsPen(gs); gs[g].draw(bp); gx0, gy0, gx1, gy1 = bp.bounds
    s = FONT / 1000.0
    pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    gs[g].draw(TransformPen(pen, (s, 0, 0, -s, cx - (gx0 + gx1) / 2 * s, cy + (gy0 + gy1) / 2 * s)))
    return pen.getCommands()
# アイコンの積み木（ic_launcher_foreground、108×108 の中の x 27〜81・y 22〜90）
icon_xml = open(icon_path, encoding="utf-8").read()
icon_paths = re.findall(r'<path\s+android:fillColor="([^"]+)"\s+android:pathData="([^"]+)"\s*/>', icon_xml)
art_w, art_h, art_cx, art_top = 54.0, 68.0, 54.0, 22.0
title_h = SIZE - min(LIFT, 0.0)  # 浮かせた分（LIFT は負）も高さに入れる
total = art_h * ICON_SCALE + SPACE + title_h
top = C - total / 2 + OFFSET  # アイコンの上側（L 字）は細いので、全体を少し上に寄せると円に大きく収まる
tx = C - art_cx * ICON_SCALE; ty = top - art_top * ICON_SCALE
out = ['<?xml version="1.0" encoding="utf-8"?>',
       '<!-- 起動画面（Android 12 以降）のアイコン：上に積み木のアイコン（ic_launcher_foreground と同じ形）、すぐ下に題字「ミチツミ」。',
       '     見えるのはまん中の直径 192dp の円の中だけなので、その中に収めてある。題字の字の形は res/font/mplus_rounded_michitsumi.ttf（OFL）から作った。tools/make_splash_icon.py で作り直せる -->',
       '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
       '    android:width="288dp" android:height="288dp" android:viewportWidth="288" android:viewportHeight="288">',
       '    <group android:scaleX="%s" android:scaleY="%s" android:translateX="%s" android:translateY="%s">' % (f(ICON_SCALE), f(ICON_SCALE), f(tx), f(ty))]
for color, d in icon_paths:
    out.append('        <path android:fillColor="%s" android:pathData="%s"/>' % (color, d))
out.append('    </group>')
blocks = [("ミ", "#E07A2A", -5, 0), ("チ", "#2A9D8F", 3, 1), ("ツ", "#F2C14E", -2, 0), ("ミ", "#4A4E69", 8, LIFT)]
x0 = C - (4 * SIZE + 3 * GAP) / 2
cy0 = top + art_h * ICON_SCALE + SPACE - min(LIFT, 0.0) + SIZE / 2
far = 0.0
for i, (ch, color, tilt, lift) in enumerate(blocks):
    x = x0 + i * (SIZE + GAP); cy = cy0 + lift; y = cy - SIZE / 2; cx = x + SIZE / 2
    lifted = lift < 0
    last = i == len(blocks) - 1
    if last:
        # 最後のブロックは、右上（DROP_DX・DROP_START）から回りながら（DROP_ROT）落ちてくる。動かすのは splash_icon_animated.xml
        out.append('    <group android:name="drop" android:pivotX="%s" android:pivotY="%s" android:translateX="%s" android:translateY="%s" android:rotation="%s">'
                   % (f(cx), f(cy), f(DROP_DX), f(DROP_START), f(DROP_ROT)))
    out.append('    <group android:rotation="%s" android:pivotX="%s" android:pivotY="%s">' % (f(tilt), f(cx), f(cy)))
    dy, a = (SIZE * 0.12, 0.22) if lifted else (SIZE * 0.04, 0.14)
    out.append('        <path android:fillColor="#000000" android:fillAlpha="%s" android:pathData="%s"/>' % (f(a), rrect(x, y + dy, SIZE, SIZE, R)))
    out.append('        <path android:fillColor="%s" android:pathData="%s"/>' % (color, rrect(x, y, SIZE, SIZE, R)))
    out.append('        <path android:fillColor="#F3F6F0" android:pathData="%s"/>' % glyph_path(ch, cx, cy))
    out.append('    </group>')
    if last:
        out.append('    </group>')
    for (px, py) in [(x + R, y + SIZE + dy), (x + SIZE - R, y + SIZE + dy), (x, y + R), (x + SIZE, y + SIZE - R)]:
        far = max(far, ((px - C) ** 2 + (py - C) ** 2) ** 0.5)
out.append('</vector>')
open(out_path, "w", encoding="utf-8").write("\n".join(out) + "\n")
print("art top %.1f, title center %.1f, farthest block point from center %.1f dp (must be < 96)" % (top, cy0, far))
print("gate (hole) center from the middle: dx 0, dy %.1f dp  -> MainActivity の GATE_DY_DP" % (top + 37 * ICON_SCALE - C))
