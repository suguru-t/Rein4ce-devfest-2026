# 同梱しているフォント

採集画面の題字「ミチツミ」に使う太い丸ゴシック。Google Fonts の M PLUS Rounded 1c Black（Coji Morishita、SIL Open Font License 1.1）を、
`ミ` `チ` `ツ` の 3 文字だけに切り出して `app/src/main/res/font/` に置いている（数 KB。元は 2 MB 以上）。

| ファイル | 元のフォント | ライセンス |
|---|---|---|
| `res/font/mplus_rounded_michitsumi.ttf` | [M PLUS Rounded 1c](https://fonts.google.com/specimen/M+PLUS+Rounded+1c) Black | `OFL-MPLUSRounded1c.txt`（著作権表示と OFL 1.1 の本文） |

切り出し方（fonttools）：

```
python -m fontTools.subset MPLUSRounded1c-Black.ttf --text="ミチツミ" --output-file=mplus_rounded_michitsumi.ttf --no-hinting --layout-features='' --name-IDs='*'
```

題字の文字を変えるときは、`--text` に新しい文字を入れて切り出し直す。
