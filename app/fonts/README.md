# 同梱しているフォント

採集画面の題字「michitsumi」に使う幾何学サンセリフ。Google Fonts の Poppins SemiBold（Indian Type Foundry、SIL Open Font License 1.1）を、
`michitsumi` に使う 7 文字（m i c h t s u）だけに切り出して `app/src/main/res/font/` に置いている（数 KB。元は 150 KB）。

| ファイル | 元のフォント | ライセンス |
|---|---|---|
| `res/font/poppins_michitsumi.ttf` | [Poppins](https://fonts.google.com/specimen/Poppins) SemiBold | `OFL-Poppins.txt` |

切り出し方（fonttools）：

```
python -m fontTools.subset Poppins-SemiBold.ttf --text="michitsumi" --output-file=poppins_michitsumi.ttf --no-hinting --layout-features='kern' --name-IDs='*'
```

題字の文字を変えるときは、`--text` に新しい文字を入れて切り出し直す。
