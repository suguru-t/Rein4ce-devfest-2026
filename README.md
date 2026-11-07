# ミチツミ（michitsumi）— デモ版

歩いた軌跡を積み木パーツに変えて、バランスを取りながらタワーを積む Android アプリ。
ハッカソン用の **「少し動くデモ版」** で、4 人で分担する前の共通基盤です。

- アプリ：Kotlin / Jetpack Compose（`app`）
- 共通ロジック：純 Kotlin（`core`）— パーツの形式、軌跡→パーツ変換、dyn4j 物理、サンプル、SVG
- サーバ：Ktor（`server`）— Cloud Run に載せる。タワーの保存・共有ページ・一覧

いまできること：

1. **記録**（旧：採集）：「デモ散歩」を再生するとサンプル軌跡が流れてパーツになる。「ファイルから再生」で実機で録った GPX / JSON も流せる。実機なら「記録開始」で GPS 記録（画面ロック中も続く）。歩きとジョギングだけがパーツになる（30 秒の窓で時速 14 km を超えると車・自転車とみなす。記録中は、超えた状態が 10 秒続いたら自動で記録を止めて、そこまでの分をパーツにする。1 点だけの GPS の飛びでは止めない。再生でも同じ）。パーツができると、AI（サーバの Gemini）が形・距離・日時から名前を付ける（届くまでは「名前を考え中…」。付かなければ「○ m の散歩」のまま）。見出しの題字「ミチツミ」は押すと動く（押したブロックが跳ねる。最後の「ミ」は画面の上のカメラの穴のあたりから落ちてきて収まる）
2. **コレクション**（旧：手持ち）：同梱の見本 5 種類＋記録したパーツ。右上の「選択」で最大 5 本選んで「積む」（「ランダムに選ぶ」で、1〜5 本をおまかせで選ぶこともできる。見本も選ぶ対象）。タップで拡大して名前を付ける（「AI に考えてもらう」で、AI の案を入れることもできる。Maps API キーがあれば、拡大した図の背景に歩いた場所の地図が出る。場所を残す前に採集したパーツは出ない）。長押しで並べ替え中（ドラッグで並べ替え・× で削除）。道の色は歩いたときの天気で変わる（雨のなかまは青、雪は水色、くもり・霧は灰色、晴れはいつものオレンジ）
3. **積む**：ドラッグで位置、15° 回転、「置く」で落下。全部静止したら次へ。落ちたら消える。5 個置き切ったら高さ確定
4. **みんな**：サーバの `/towers` 一覧。完成したタワーは「サーバに送信」で共有ページ URL が返る
5. **背景の天気**：アプリ全体の方眼紙の上に、見ている画面に合わせた天気が出る（雨・雪・霧、くもりは流れる雲、晴れは天気アプリのような太陽。スマホを傾けると太陽が少し動く）。記録は今の天気、コレクションは大きく見ているパーツ（見ていなければ最後に歩いたパーツ）の天気。積む・みんなでは降らない。電池のため、描き直しはゆっくりな空で 1 秒に 30 回、雨・雪で 60 回まで。記録中は止まった絵にする
6. **設定**（記録画面の右上の歯車）：画面の明るさ（端末に合わせる・ライト・ダーク）、背景の天気の動き（出さない・止める・ふつう・なめらかの 4 段階のスライダー。選んでいる段階の説明が下に出る）、記録中は画面を消さない、AI に名前を考えてもらう（オフなら何も送らない）、ニックネーム（「みんな」の作者名）、使い方、アプリについて（版・外に送るもの・使っている素材）。スマホの中だけに保存する。使い方は、開いたときにも出る（「次回から表示しない」にチェックを入れて閉じるまで）
7. **ダークモード**：野帳の色を夜の野帳（黒に近い緑の紙に明るい字）に切り替える。地図も暗い表示になる
8. **起動画面**（Android 12 以降）：積み木のアイコンと、すぐ下に題字「ミチツミ」。最後の「ミ」が右から落ちてきて 1 回はねて収まり、門の形の穴をくぐって記録画面に入る。地の明るさは設定に合わせる（Android 13 以降）

## 動かし方

### Android アプリ

1. Android Studio（Narwhal 以降）で、このフォルダを開く
2. Studio が作る `local.properties` に `local.properties.example` の 2 行を足す
   - `MAPS_API_KEY=`（空でも動く。空なら地図の代わりに軌跡を Canvas 描画。下の「地図（Maps API キー）」を参照）
   - `SERVER_BASE_URL=https://road-collector-415368216017.asia-northeast1.run.app`（本番サーバ。下の「サーバの接続先」を参照）
3. Run。エミュレータでも実機でも可（minSdk 26）

### 地図（Maps API キー）

記録画面の地図は Google Maps で、`MAPS_API_KEY` が無いと軌跡だけを Canvas に描く。キーは A が管理している 1 つをチームで共有する（Slack で A から受け取って `local.properties` に書く。**リポジトリには入れない**）。

- デバッグ署名はチーム共通の鍵 `app/debug.keystore`（パスワードは Android 標準と同じ `android`）。全員と GitHub Actions のビルドが同じ署名になるので、キーの「Android アプリ」制限にはこの鍵の SHA-1 だけ登録してある。自分の PC で作った鍵は使わない
- 同じ署名なので、手元のビルドと Actions の APK を同じ端末に入れ替えてもアンインストールは要らない
- **共通の鍵になる前のビルド（2026-11-06 以前）が入っている端末・エミュレータは、最初の 1 回だけアンインストールしてから入れる**（署名が変わるため）。アンインストールするとコレクション（集めたパーツ）も消えるので、残したいものがあれば先にタワーをサーバに送るなどしておく
- キーは「API の制限」で Maps SDK for Android だけに絞ってある（リポジトリが公開で、Actions の APK からキーを取り出せるため。地図の表示は課金されない）
- 鍵を作り直したときは、Google Cloud Console のキーの制限に新しい SHA-1 を登録し直す（`keytool -list -v -keystore app/debug.keystore -storepass android` で出る）

実機で GPS を試すときは、権限ダイアログで「アプリの使用中のみ許可」でよい（バックグラウンド権限は使わない）。
エミュレータで歩くには Extended Controls → Location で GPX を再生するか、アプリ内の「デモ散歩」を使う。

「デモ散歩」メニューの「ファイルから再生（GPX / JSON）」で、実機で録った軌跡ファイルを早回しで流せる（`core/io/TrackFileParser`）。
GPX は `<trkpt lat lon><time>` を読む（精度は不明として扱う）。JSON は `TrackPoint` の配列（`[{"lat":…,"lng":…,"accuracyM":…,"timeMs":…}]`）か、`points` を持つオブジェクト（`{"points":[…]}`。`Track` 全体でも可）。
デモ散歩・ファイル再生とも、時刻は「最後の点 = 今」にずらして流すので、パーツの採集日時は再生した日時になる。

### サーバの接続先

本番サーバ（Cloud Run）：https://road-collector-415368216017.asia-northeast1.run.app

アプリは既定でこの URL につながる。ローカルで `./gradlew :server:run` するときだけ、`local.properties` に `SERVER_BASE_URL=http://10.0.2.2:8080` を書く（10.0.2.2 はエミュレータから見たホスト PC の localhost）。
この値はビルド時にアプリへ埋め込まれるので、書き換えたら Gradle Sync してから Run し直す。

### サーバ（ローカル）

```
./gradlew :server:run
# → http://localhost:8080/   http://localhost:8080/towers
```

Cloud Run へのデプロイは [server/README.md](server/README.md)。

### テスト

```
./gradlew :core:test     # 変換ロジック・物理・SVG・軌跡ファイル読み込み
./gradlew :server:test   # サーバの API（保存・一覧・共有ページ・画像）
./gradlew :app:testDebugUnitTest   # アプリの手持ちの保存（Android なしで動く）
```

`core` は Android に依存しないので、変換ロジックと物理はここで素早く検証できる。

### 自動チェック（GitHub Actions）

main への push とプルリクエストのたびに、`core`・`server`・`app` のテスト、`server` のビルド、`app` の APK ビルドが自動で走る（`.github/workflows/ci.yml`）。結果はリポジトリの Actions タブで見られる。

ビルドした APK は、各実行結果ページの Artifacts にある `app-debug.apk` からダウンロードできる（GitHub へのログインが必要。14 日間保存）。

- この APK は本番サーバにつながる。リポジトリの Secrets に `MAPS_API_KEY` が入っていれば地図も出る（無ければ軌跡を Canvas で描く）
- 署名はチーム共通の `app/debug.keystore` なので、手元でビルドしたアプリが入っている端末にもそのまま上書きで入る

## 構成と担当

```
core/   com.rein4ce.roadcollector.core
  model/     Part, Pt, Kind, TrackPoint, Tower, Placement  … チーム共通のデータ形式（変更時は全員に周知）
  geo/       TrackFilter, Projection, Simplify(RDP), TrackToPart, ConversionParams, TrackTime … B
  io/        TrackFileParser（GPX / JSON の軌跡ファイル読み込み）                         … A
  physics/   PartGeometry, PartBodyFactory, StackWorld(dyn4j)                       … D
  sample/    SampleTracks(5 本の軌跡), SampleParts(同梱パーツ)
  weather/   Sky, WalkWeather, OpenMeteo（天気コードの読み替え・天気を聞く URL と返事の読み取り）         … B
  svg/       TowerSvg（共有ページ用の絵）

app/    com.rein4ce.roadcollector
  collect/       LocationRecordingService（フォアグラウンドサービス）, TrackRecorder, DemoWalkPlayer … A
  ui/collect/    記録画面（旧：採集。地図 or Canvas、デモ散歩、結果ダイアログ、題字 TitleBlocks）          … A
  ui/inventory/  手持ち一覧・選択                                                           … B
  ui/weather/    背景の天気（方眼紙の上に降らせる雨・雪など。見ている画面で決める）と、天気データの出どころの表示                  … B
  ui/game/       積み上げ画面・進行（StackViewModel）                                         … D
  ui/gallery/    みんなのタワー                                                             … C
  net/           ApiClient（Ktor client）                                                  … C
  data/          PartRepository（JSON ファイル保存。Room に替えるならここ）, GameSession, WeatherService（天気を取ってくる。B）, AppSettings（設定。B）
  ui/common/     PartDrawing（パーツの描画。物理と同じ PartGeometry を使う）, FramedDialog（見出しの帯と「×」の付いた枠）, Notes（崩れない注記）, PaperBand（方眼の上の字の下地）
  ui/settings/   設定（SettingsDialog）、ニックネーム、アプリについて                                  … B

server/ com.rein4ce.roadcollector.server                                                  … C
  Application.kt  Ktor 起動（PORT 環境変数）
  Routes.kt       GET /, /health, /towers, POST /towers, GET /towers/{id}, /towers/{id}/image.svg
  PartRoutes.kt   POST /parts/name（パーツの命名。失敗は 503）
  PartNamer.kt    命名の部品。Gemini 版と、命名なし（手元の既定）
  TowerStore.kt   保存先のインターフェースとメモリ版。FirestoreTowerStore.kt が Firestore 版
  SharePage.kt    OGP 付き共有ページ
```

### 各担当の「次にやること」

- **A（採集）**：Android 実機で歩いて `ConversionParams` の数値（最低 150 m、精度 30 m、最小移動 5 m、最大速度 5 m/s、歩き・ジョギングの上限 3.9 m/s（時速 14 km。30 秒の窓の直線距離で測る。記録中に超えた状態が 10 秒続いたら自動で止めて、そこまでをパーツにする）、ε 10 m（GPS が粗いときは精度に合わせて大きくする）、頂点 24 個）を確認。特に Android の精度値で ε がどこまで上がるか（結果ダイアログに出る）。Maps API キーを入れて地図表示を確認（GPX / JSON の読み込みと、iPhone の GPX 6 本での調整は済み。Issue #1）
- **B（変換・手持ち）**：`TrackToPart.classify` の精度（実データで棒/L/U/輪/ジグザグがどう出るか）。手持ち画面の見た目、削除、並び替え
- **C（サーバ）**：`TowerStore` の Firestore 版を Cloud Run にデプロイして確認。`og:image` を PNG に。`POST /parts/name`（Vertex AI / Gemini）。対戦方式が決まれば `/challenges`
- **D（ゲーム）**：操作感（ドラッグ感度、回転の刻み）、台の幅、摩擦などの調整。静止判定の待ち時間。対戦モード（交互置き or 非同期スコアバトル）

## データ形式（ここだけは全員で守る）

```kotlin
data class Part(
    val id: String,
    val points: List<Pt>,     // 正規化済み。バウンディングボックス中心が原点、長辺 1.0、y 上向き。最大 24 個
    val thickness: Float,     // 太さ（長辺に対する比率、初期値 0.15）
    val closed: Boolean,      // 輪なら true
    val kind: Kind,           // BAR / L / U / RING / ZIGZAG / OTHER（表示用）
    val distanceM: Int,
    val walkedAt: Long,
    val name: String? = null, // Gemini 命名（任意）
)
```

生の GPS 点列（`TrackPoint`）は `Part` に入れない。サーバにも送らない。

パーツを歩いた場所（形の中心の緯度・経度と長辺の長さ。`PartPlace`）も `Part` には入れない。コレクションの拡大表示の背景に地図を敷くためだけに、スマホの中の別のファイル（`parts_places.json`）に持つ。`Part` はタワーと一緒にサーバへ送られて共有ページに出るので、入れると歩いた場所が分かってしまうため。

パーツを歩いたときの天気（`WalkWeather`。世界気象機関の天気コードと降水量）も、`Part` には入れずスマホの中（`parts_weather.json`）に持つ（共通のデータ形式を変えないため）。天気は採集のあとに Open-Meteo（キー不要・無料）から取る。送る位置は 0.1 度（約 10 km）に丸めて、歩いた正確な場所は外に出さない。電波が無くて取れなかったパーツは、次にアプリを開いたときに聞き直す（90 日前まで）。天気データは [Open-Meteo.com](https://open-meteo.com/)（CC BY 4.0）。アプリでも、コレクションの拡大表示で天気を出すところに出どころを添えている（`ui/weather/WeatherCredit.kt`）。

## 検証状況（正直に。2026-11-06 時点）

- `core`（変換・物理・パーツの形・サンプル・SVG・軌跡ファイル読み込み）：ユニットテスト 57 本が通る
- `app`：手持ちの保存・並び順・名前の文字数・地図の縮尺・合計距離の出し方・注記のつなぎ方・天気の保存と取得・みんなのタワーの絵・速すぎたときの自動停止・AI の名前の保存と送る中身・おまかせの選び方・設定の保存・タワーの作者名・みんなのタワーの先読みのユニットテスト 87 本が通る。エミュレータ（Android 17）で「デモ散歩 → コレクション → 積む → サーバに送信 → みんな」が一通り動くことを確認済み。途中で落ちず、本番サーバに保存され、共有ページも開ける。Maps API キーを入れたときの地図表示も、エミュレータで確認済み（Issue #1）。**実機での GPS 記録はまだ確認していない**
- `server`：API・保存・命名・共有画像・受け付けるタワーの検査のテスト 48 本が通る。Cloud Run にデプロイ済みで、`GET /`、`/health`、`/towers` が応答する。Firestore 版の保存先・共有画像の PNG・命名 API も、2026-11-07 に本番（Cloud Run）に出した（revision `road-collector-00005-tzk`）
- 上の 3 つのビルドとテストは、push のたびに GitHub Actions でも確認する（「自動チェック」の節）
- `gradle/libs.versions.toml` の版はすべて公開済みのもの。Studio が新しい版を勧めてきたら上げて構わない

## 設定・権限の要点

- Android 14 以降：`FOREGROUND_SERVICE_LOCATION` 権限と `foregroundServiceType="location"` が必須（設定済み）
- `ACCESS_BACKGROUND_LOCATION` は使わない。記録は必ずアプリを開いた状態のボタンから開始する
- `usesCleartextTraffic="true"` はローカルサーバ（http://10.0.2.2）用。Cloud Run（https）だけになったら外してよい
- `local.properties` は git に入れない（API キー）
