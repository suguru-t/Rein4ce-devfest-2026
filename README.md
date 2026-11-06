# 道の採集家（Road Collector）— デモ版

歩いた軌跡を積み木パーツに変えて、バランスを取りながらタワーを積む Android アプリ。
ハッカソン用の **「少し動くデモ版」** で、4 人で分担する前の共通基盤です。

- アプリ：Kotlin / Jetpack Compose（`app`）
- 共通ロジック：純 Kotlin（`core`）— パーツの形式、軌跡→パーツ変換、dyn4j 物理、サンプル、SVG
- サーバ：Ktor（`server`）— Cloud Run に載せる。タワーの保存・共有ページ・一覧

いまできること：

1. **採集**：「デモ散歩」を再生するとサンプル軌跡が流れてパーツになる。「ファイルから再生」で実機で録った GPX / JSON も流せる。実機なら「記録開始」で GPS 記録（画面ロック中も続く）
2. **手持ち**：同梱サンプル 5 種類＋採集したパーツ。最大 5 個選んで「積む」
3. **積む**：ドラッグで位置、15° 回転、「置く」で落下。全部静止したら次へ。落ちたら消える。5 個置き切ったら高さ確定
4. **みんな**：サーバの `/towers` 一覧。完成したタワーは「サーバに送信」で共有ページ URL が返る

## 動かし方

### Android アプリ

1. Android Studio（Narwhal 以降）で、このフォルダを開く
2. Studio が作る `local.properties` に `local.properties.example` の 2 行を足す
   - `MAPS_API_KEY=`（空でも動く。空なら地図の代わりに軌跡を Canvas 描画）
   - `SERVER_BASE_URL=https://road-collector-415368216017.asia-northeast1.run.app`（本番サーバ。下の「サーバの接続先」を参照）
3. Run。エミュレータでも実機でも可（minSdk 26）

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

- この APK は本番サーバにつながる。地図は表示されず、軌跡を Canvas で描く（`MAPS_API_KEY` なし）
- 手元でビルドしたアプリが入っている端末では、署名が違うので、いったんアンインストールしてから入れる

## 構成と担当

```
core/   com.rein4ce.roadcollector.core
  model/     Part, Pt, Kind, TrackPoint, Tower, Placement  … チーム共通のデータ形式（変更時は全員に周知）
  geo/       TrackFilter, Projection, Simplify(RDP), TrackToPart, ConversionParams, TrackTime … B
  io/        TrackFileParser（GPX / JSON の軌跡ファイル読み込み）                         … A
  physics/   PartGeometry, PartBodyFactory, StackWorld(dyn4j)                       … D
  sample/    SampleTracks(5 本の軌跡), SampleParts(同梱パーツ)
  svg/       TowerSvg（共有ページ用の絵）

app/    com.rein4ce.roadcollector
  collect/       LocationRecordingService（フォアグラウンドサービス）, TrackRecorder, DemoWalkPlayer … A
  ui/collect/    採集画面（地図 or Canvas、デモ散歩、結果ダイアログ）                          … A
  ui/inventory/  手持ち一覧・選択                                                           … B
  ui/game/       積み上げ画面・進行（StackViewModel）                                         … D
  ui/gallery/    みんなのタワー                                                             … C
  net/           ApiClient（Ktor client）                                                  … C
  data/          PartRepository（JSON ファイル保存。Room に替えるならここ）, GameSession
  ui/common/     PartDrawing（パーツの描画。物理と同じ PartGeometry を使う）

server/ com.rein4ce.roadcollector.server                                                  … C
  Application.kt  Ktor 起動（PORT 環境変数）
  Routes.kt       GET /, /health, /towers, POST /towers, GET /towers/{id}, /towers/{id}/image.svg
  TowerStore.kt   いまはメモリ。Firestore に置き換える
  SharePage.kt    OGP 付き共有ページ
```

### 各担当の「次にやること」

- **A（採集）**：Android 実機で歩いて `ConversionParams` の数値（最低 150 m、精度 30 m、最小移動 5 m、最大速度 5 m/s、ε 10 m（GPS が粗いときは精度に合わせて大きくする）、頂点 24 個）を確認。特に Android の精度値で ε がどこまで上がるか（結果ダイアログに出る）。Maps API キーを入れて地図表示を確認（GPX / JSON の読み込みと、iPhone の GPX 6 本での調整は済み。Issue #1）
- **B（変換・手持ち）**：`TrackToPart.classify` の精度（実データで棒/L/U/輪/ジグザグがどう出るか）。手持ち画面の見た目、削除、並び替え
- **C（サーバ）**：Day1 に Cloud Run へ Hello World をデプロイ（server/README.md）。`TowerStore` を Firestore に。`og:image` を PNG に。`POST /parts/name`（Vertex AI / Gemini）。対戦方式が決まれば `/challenges`
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

## 検証状況（正直に。2026-11-06 時点）

- `core`（変換・物理・サンプル・SVG・軌跡ファイル読み込み）：ユニットテスト 34 本が通る
- `app`：手持ちの保存と並び順のユニットテスト 19 本が通る。エミュレータ（Android 17）で「デモ散歩 → 手持ち → 積む → サーバに送信 → みんな」が一通り動くことを確認済み。途中で落ちず、本番サーバに保存され、共有ページも開ける。Maps API キーを入れたときの地図表示も、エミュレータで確認済み（Issue #1）。**実機での GPS 記録はまだ確認していない**
- `server`：API のテスト 5 本が通る。Cloud Run にデプロイ済みで、`GET /`、`/health`、`/towers` が応答する
- 上の 3 つのビルドとテストは、push のたびに GitHub Actions でも確認する（「自動チェック」の節）
- `gradle/libs.versions.toml` の版はすべて公開済みのもの。Studio が新しい版を勧めてきたら上げて構わない

## 設定・権限の要点

- Android 14 以降：`FOREGROUND_SERVICE_LOCATION` 権限と `foregroundServiceType="location"` が必須（設定済み）
- `ACCESS_BACKGROUND_LOCATION` は使わない。記録は必ずアプリを開いた状態のボタンから開始する
- `usesCleartextTraffic="true"` はローカルサーバ（http://10.0.2.2）用。Cloud Run（https）だけになったら外してよい
- `local.properties` は git に入れない（API キー）
