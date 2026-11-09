#!/usr/bin/env python3
"""
ポップな見た目の画面（「みんな」・コレクション・ホーム）で使う太い丸ゴシックを、使う字だけに切り出す
（app/src/main/res/font/mplus_rounded_ui.ttf を作り直す）。

画面の字は、下の SOURCES の .kt にある文字列（"…" の中身。コメントは除く）から集める。
それに、半角の英数字・記号と、数字まわりの記号（EXTRA）を足す。
画面に新しい言葉を足したら、これを流し直す（入っていない字は端末の字体で出るので、崩れはしないが太さが変わる）。
作者名・パーツの名前のように、人や AI が入れる字には、このフォントを使わない。

使い方（リポジトリのいちばん上で）:
    python3 tools/subset_rounded_font.py 元のフォント/MPLUSRounded1c-Black.ttf

元のフォントは Google Fonts の M PLUS Rounded 1c Black（3.6 MB。リポジトリには入れない）:
    https://github.com/google/fonts/raw/main/ofl/mplusrounded1c/MPLUSRounded1c-Black.ttf
要るもの: Python 3 と fonttools（pip install fonttools）
"""

import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main/kotlin/com/rein4ce/roadcollector"
OUT = ROOT / "app/src/main/res/font/mplus_rounded_ui.ttf"

# 字を集める .kt（フォルダなら中の .kt 全部）
SOURCES = [
    APP / "ui/gallery",
    APP / "ui/inventory",
    # ポップな見た目の共通の部品（「再読み込み」など）
    APP / "ui/common/Pop.kt",
    # ホームのボタン（画面の名前と一言）
    APP / "ui/HomeScreen.kt",
    # 並びの名前（いつもの順・長い順…）
    APP / "data/PartFilter.kt",
    # 天気の名前（晴れ・霧雨…）
    ROOT / "core/src/main/kotlin/com/rein4ce/roadcollector/core/weather/Weather.kt",
]

# 半角の英数字・記号と、数字まわりの記号
EXTRA = "".join(chr(c) for c in range(0x20, 0x7F)) + "—‹›★☆▾〜・（）、。「」：！？　×−＋…"

STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)


def chars_in(path: Path) -> set[str]:
    text = BLOCK_COMMENT.sub("", path.read_text(encoding="utf-8"))
    found: set[str] = set()
    for line in text.splitlines():
        # 行のコメント（//）より前の文字列だけを見る。文字列の中の「//」（URL など）はコメントと見なさない
        masked = STRING.sub(lambda m: "_" * len(m.group(0)), line)
        comment = masked.find("//")
        for m in STRING.finditer(line):
            if comment >= 0 and m.start() > comment:
                continue
            # ${…}・$name（変数の中身）と、\n などの書き方は画面にそのまま出ないので外す
            s = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*|\\.", "", m.group(1))
            found.update(s)
    return found


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    chars: set[str] = set(EXTRA)
    for s in SOURCES:
        for f in sorted(s.rglob("*.kt") if s.is_dir() else [s]):
            chars |= chars_in(f)
    chars.discard("\n")
    text = "".join(sorted(chars))
    with tempfile.NamedTemporaryFile("w", suffix=".txt", encoding="utf-8", delete=False) as t:
        t.write(text)
    subprocess.run(
        [
            sys.executable, "-m", "fontTools.subset", str(src),
            f"--text-file={t.name}", f"--output-file={OUT}",
            "--no-hinting", "--layout-features=", "--name-IDs=*",
        ],
        check=True,
    )
    from fontTools.ttLib import TTFont
    cmap = TTFont(OUT).getBestCmap()
    missing = "".join(c for c in text if ord(c) not in cmap and not c.isspace())
    print(f"{OUT}  {len(text)} 字  {OUT.stat().st_size / 1e3:.0f} KB")
    if missing:
        print(f"元のフォントに無い字（端末の字体で出る）: {missing}")


if __name__ == "__main__":
    main()
