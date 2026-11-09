# 同梱しているフォント

採集画面の題字「ミチツミ」と、「みんな」の画面（みんなのタワー・ランキング・あなたのタワー）に使う太い丸ゴシック。
Google Fonts の M PLUS Rounded 1c Black（Coji Morishita、SIL Open Font License 1.1）を、使う字だけに切り出して `app/src/main/res/font/` に置いている（元は 3.6 MB）。

| ファイル | 使う所 | 元のフォント | ライセンス |
|---|---|---|---|
| `res/font/mplus_rounded_michitsumi.ttf`（2 KB） | 題字「ミチツミ」（`ui/collect/TitleBlocks.kt`） | [M PLUS Rounded 1c](https://fonts.google.com/specimen/M+PLUS+Rounded+1c) Black | `OFL-MPLUSRounded1c.txt`（著作権表示と OFL 1.1 の本文） |
| `res/font/mplus_rounded_gallery.ttf`（49 KB） | 「みんな」の画面の字（`ui/gallery/Pop.kt`） | 同上 | 同上 |

切り出し方（fonttools）：

```
python -m fontTools.subset MPLUSRounded1c-Black.ttf --text="ミチツミ" --output-file=mplus_rounded_michitsumi.ttf --no-hinting --layout-features='' --name-IDs='*'
```

題字の文字を変えるときは、`--text` に新しい文字を入れて切り出し直す。

「みんな」の画面のフォントは、`ui/gallery` の `.kt` にある文字列（"…" の中身。コメントは除く）の字と、半角の英数字・記号、
数字まわりの記号（`—‹›★〜/・（）` など）を `--text-file` に集めて切り出す（220 字ほど）。
画面に新しい言葉を足したら、切り出し直す（入っていない字は端末の字体で出るので、崩れはしないが太さが変わる）。
作者名のように人が入れる字には使わない。

```
python -m fontTools.subset MPLUSRounded1c-Black.ttf --text-file=gallery_chars.txt --output-file=mplus_rounded_gallery.ttf --no-hinting --layout-features='' --name-IDs='*'
```
