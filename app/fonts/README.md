# 同梱しているフォント

ホームの題字「ミチツミ」と、「みんな」の画面（みんなのタワー・ランキング・あなたのタワー）とコレクションの画面（一覧・拡大表示・積む画面の「パーツを選ぶ」の窓）、記録画面の画面名「みちのきろく」、ホームのボタン、設定の窓と使い方に使う太い丸ゴシック。
Google Fonts の M PLUS Rounded 1c Black（Coji Morishita、SIL Open Font License 1.1）を、使う字だけに切り出して `app/src/main/res/font/` に置いている（元は 3.6 MB）。

| ファイル | 使う所 | 元のフォント | ライセンス |
|---|---|---|---|
| `res/font/mplus_rounded_michitsumi.ttf`（2 KB） | 題字「ミチツミ」（`ui/collect/TitleBlocks.kt`。ホームと、開いたときのタイトル） | [M PLUS Rounded 1c](https://fonts.google.com/specimen/M+PLUS+Rounded+1c) Black | `OFL-MPLUSRounded1c.txt`（著作権表示と OFL 1.1 の本文） |
| `res/font/mplus_rounded_ui.ttf`（131 KB） | 「みんな」・コレクション・ホーム・設定・積む画面（モードを選ぶ画面と遊ぶ画面）の字（`ui/common/Pop.kt` の `Pop.Rounded`） | 同上 | 同上 |

切り出し方（fonttools）：

```
python -m fontTools.subset MPLUSRounded1c-Black.ttf --text="ミチツミ" --output-file=mplus_rounded_michitsumi.ttf --no-hinting --layout-features='' --name-IDs='*'
```

題字の文字を変えるときは、`--text` に新しい文字を入れて切り出し直す。

「みんな」・コレクション・ホーム・積む画面のフォントは、`tools/subset_rounded_font.py` で切り出す（いまは 440 字）。
`ui/gallery`・`ui/inventory`・`ui/common/Pop.kt`・`ui/HomeScreen.kt`・`ui/TitleIntro.kt`（ホームの一言）・積む画面の情報の字（`ui/game/StackScreen.kt`）・「ほかの道」のボタン（`ui/game/ChangeButton.kt`）・3 分チャレンジの画面と「回す」のボタン（`ui/game/ChallengeScreen.kt`・`RotateButtons.kt`）・積む画面のモードを選ぶ画面（`ui/game/ModeSelect.kt`）・毎日 1 つずつ積む画面（`ui/game/DailyScreen.kt`）・設定の窓と使い方（`ui/settings/SettingsDialog.kt`・`HowToContent.kt`）・設定の選択肢の名前（`data/AppSettings.kt`）・名前を付ける窓（`ui/common/NameField.kt`）の `.kt` にある文字列（"…" の中身。コメントは除く）と、並び・天気の名前（`data/PartFilter.kt`・`core/.../weather/Weather.kt`）・3 分チャレンジの形の名前（`core/.../challenge/ChallengeRun.kt`）の字に、
半角の英数字・記号と、数字まわりの記号（`—‹›★☆〜・（）` など）を足して切り出す。
画面に新しい言葉を足したら、切り出し直す（入っていない字は端末の字体で出るので、崩れはしないが太さが変わる）。
作者名・パーツの名前のように、人や AI が入れる字には使わない。

```
pip install fonttools
python3 tools/subset_rounded_font.py MPLUSRounded1c-Black.ttf
```
