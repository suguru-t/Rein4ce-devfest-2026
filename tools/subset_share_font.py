#!/usr/bin/env python3
"""
共有ページ（server/.../SharePage.kt）で使う字だけを、M PLUS Rounded 1c Black から切り出して WOFF にする（決定 95）。
共有ページは、アプリと同じ字体を Google Fonts から読んでいたが、開いた人のブラウザから Google にも通信が行くので、
サーバから配ることにした。

使い方（リポジトリのいちばん上で）:
    python3 tools/subset_share_font.py 元のフォント/MPLUSRounded1c-Black.ttf

書き出し先: server/src/main/resources/fonts/share-rounded.woff
共有ページの言葉を変えたら、切り出し直す（入っていない字は端末の丸い字で出るので、崩れはしないが太さが変わる）。
作者名（人が入れる字）は切り出しに入れない。ページでは作者名だけ端末の字で出す（1 つの名前の中で字体が混ざらないように。アプリと同じ）
"""
import re
import sys
from pathlib import Path

from fontTools import subset

ROOT = Path(__file__).resolve().parent.parent
SHARE_PAGE = ROOT / "server/src/main/kotlin/com/rein4ce/roadcollector/server/SharePage.kt"
PART = ROOT / "core/src/main/kotlin/com/rein4ce/roadcollector/core/model/Part.kt"
OUT = ROOT / "server/src/main/resources/fonts/share-rounded.woff"


def code_text(path: Path) -> str:
    """コメント（// と /** */ の行）を除いたコードの字"""
    lines = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if s.startswith("//") or s.startswith("/*") or s.startswith("*"):
            continue
        lines.append(line)
    return "\n".join(lines)


def kind_labels(path: Path) -> str:
    """パーツの形の名前（「使ったパーツ：」に出す。Kind の label）"""
    return "".join(re.findall(r'\("([^"]+)"\)', path.read_text(encoding="utf-8")))


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    source = Path(sys.argv[1])
    chars = set(code_text(SHARE_PAGE)) | set(kind_labels(PART))
    # 半角の英数字・記号（高さの数字・日付・「m」など）
    chars |= {chr(c) for c in range(0x20, 0x7F)}
    chars = {c for c in chars if not c.isspace() or c == " "}
    text = "".join(sorted(chars))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    options = subset.Options()
    options.flavor = "woff"
    options.layout_features = ["*"]
    options.name_IDs = ["*"]  # 著作権とライセンスの表示（name テーブル）を残す
    font = subset.load_font(str(source), options)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    subset.save_font(font, str(OUT), options)
    print(f"{OUT.relative_to(ROOT)}  {len(text)} 字  {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
