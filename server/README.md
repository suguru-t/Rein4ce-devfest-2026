# server — Cloud Run デプロイ手順

Ktor（Kotlin）のサーバ。Jib でイメージを作るので手元に Docker は不要。

## ローカルで動かす

```
./gradlew :server:run
curl http://localhost:8080/
curl http://localhost:8080/towers
```

エミュレータのアプリからは `http://10.0.2.2:8080` で届く（`local.properties` の `SERVER_BASE_URL`）。

## Cloud Run にデプロイ（Day1 にやる）

前提：gcloud CLI、Google Cloud プロジェクト（請求先アカウント有効化済み）。

```bash
PROJECT_ID=<your-project-id>
REGION=asia-northeast1
gcloud config set project $PROJECT_ID

# 1. 必要な API を有効化
gcloud services enable run.googleapis.com artifactregistry.googleapis.com

# 2. イメージ置き場（最初の 1 回だけ）
gcloud artifacts repositories create road-collector \
  --repository-format=docker --location=$REGION

# 3. Jib が push できるように認証ヘルパを設定（最初の 1 回だけ）
gcloud auth configure-docker $REGION-docker.pkg.dev

# 4. イメージをビルドして push（Docker 不要）
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/road-collector/server
./gradlew :server:jib --image=$IMAGE

# 5. Cloud Run にデプロイ
gcloud run deploy road-collector \
  --image=$IMAGE --region=$REGION --allow-unauthenticated \
  --memory=512Mi --min-instances=0
```

出力される URL（`https://road-collector-xxxxx-an.a.run.app`）を、アプリ側 `local.properties` の `SERVER_BASE_URL` に入れる。

デモ当日はコールドスタートを避けるため `--min-instances=1` にし、終わったら 0 に戻す（クレジット節約）。

## 保存先（メモリ / Firestore）

`TowerStore` はインターフェースで、実装が 2 つある。

| 実装 | 使われるとき |
|---|---|
| `MemoryTowerStore` | 手元の既定、テスト。再起動で消える。gcloud なしで動く |
| `FirestoreTowerStore` | Cloud Run 上（環境変数 `K_SERVICE` があると自動で）、または `TOWER_STORE=firestore` |

環境変数：

- `TOWER_STORE`：`firestore` か `memory` で明示する。未指定なら上の表のとおり
- `TOWER_COLLECTION`：コレクション名（既定 `towers`）。手元で試すときは本番とは別の名前にする
- `GOOGLE_CLOUD_PROJECT`：**保存先のプロジェクト**。Cloud Run では実行環境から自動で決まるので不要。**手元では必ず指定する**（未指定だと Java クライアントが gcloud の設定などから決めるため、複数のプロジェクトを使っていると別のプロジェクトを向くことがある）

Firestore では 1 タワー 1 ドキュメント。`json` フィールドにタワー全体が入り、一覧用に `height`・`partCount`・`createdAt`・`author` も持つ。

手元から本物の Firestore につなぐ（データベース `(default)`、ネイティブモード、`asia-northeast1` は作成済み。鍵ファイルは作らない）：

```
gcloud auth application-default login     # 最初の 1 回だけ
GOOGLE_CLOUD_PROJECT=hackathon-2026-513703 TOWER_STORE=firestore TOWER_COLLECTION=towers_localtest ./gradlew :server:run
```

起動ログに `TowerStore: firestore（project=..., database=..., collection=...）` と出れば、**Firestore 版が選ばれて接続先が決まった**ところまでは分かる。この時点ではまだ読み書きしていないので、プロジェクトの間違いや権限不足は、実際に保存・取得して初めて分かる（`/health` は DB を呼ばないので、200 でも DB の確認にはならない）。

接続先・権限・保存の確認は、次の順でやる（Bash / Git Bash 用。JSON はパーツ 1 本だけの最小のタワーで、`createdAt: 0` はサーバが現在時刻に直す）：

```bash
BASE_URL='http://localhost:8080'

# 1. 保存（201 と、応答 JSON に id が返る）
curl -sS -i -X POST "$BASE_URL/towers" -H 'Content-Type: application/json' \
  -d '{"parts":[{"id":"p1","points":[{"x":-0.5,"y":0},{"x":0.5,"y":0}],"kind":"BAR"}],"placements":[{"partId":"p1","x":0,"y":0.1,"angleRad":0}],"height":0.2,"createdAt":0}'

# 2. 返ってきた id で取得（200。JSON で返る）。ID は 1 の応答の id に置き換える
ID='1 の応答の id'
curl -sS -i -H 'Accept: application/json' "$BASE_URL/towers/$ID"
# 3. 一覧に出る（200）
curl -sS -i "$BASE_URL/towers"
# 4. サーバを再起動して、もう一度 2 を実行 → 同じ id で取得できれば Firestore に残っている
```

| 確認 | 分かること |
|---|---|
| 起動ログに firestore と出る | Firestore 版が選ばれ、接続先が決まった |
| `/health` が 200 | Web サーバが応答している（DB は未確認） |
| `POST /towers` が 201 | 書き込みの権限があり、保存できた |
| 返った id で `GET` が 200 | 読み取りの権限があり、保存したものを取り出せた |
| 再起動後も同じ id で取れる | メモリではなく Firestore に残っている |

Cloud Run のサービスアカウントには `roles/datastore.user` を付与済みなので、デプロイするだけで Firestore 版になる。

## パーツの命名（POST /parts/name）

`Part` をそのまま送ると、Vertex AI の Gemini が名前を付けて `200 {"name": "..."}` を返す。名前が付けられなかったとき（時間切れ・Gemini の失敗・命名が無効・聞き直しても 10 文字を超えた）は `503`。名前は 10 文字まで。Gemini が長い名前を返したら、前の案を見せて 1 回だけ聞き直し、それでも長ければ切り詰めずに 503（途中で切れた名前を出さない。最悪 3 秒 × 2 で終わる）。アプリはそのとき今の表示のままにする。名前の手がかりは形・距離・日時だけで、位置情報は使わない。

| 実装 | 使われるとき |
|---|---|
| `DisabledPartNamer` | 手元の既定、テスト。いつも 503 |
| `GeminiPartNamer` | Cloud Run 上（`K_SERVICE` があると自動で）、または `PART_NAMER=gemini` |

環境変数：

- `PART_NAMER`：`gemini` か `off`。未指定なら上の表のとおり
- `GOOGLE_CLOUD_PROJECT`：Vertex AI を呼ぶプロジェクト。Cloud Run ではメタデータから自動で取るので不要。**手元では必須**
- `GEMINI_MODEL`：既定 `gemini-3.5-flash-lite`
- `GEMINI_LOCATION`：既定 `global`（3.5-flash-lite は東京にはない）

手元で本物の Gemini を試す（Vertex AI API は有効化済み。Cloud Run のサービスアカウントは編集者なので、権限の追加は不要）：

```
gcloud auth application-default login     # 最初の 1 回だけ
PART_NAMER=gemini GOOGLE_CLOUD_PROJECT=hackathon-2026-513703 ./gradlew :server:run
curl -X POST localhost:8080/parts/name -H "Content-Type: application/json" \
  -d '{"id":"p1","points":[{"x":-0.5,"y":0},{"x":0,"y":0.3},{"x":0.5,"y":0}],"kind":"ZIGZAG","distanceM":820,"walkedAt":1793952000000}'
```

起動ログの `PartNamer: Gemini（project=..., location=global, model=gemini-3.5-flash-lite）` は、**Gemini 版のクライアントを作れて接続先の設定が決まった**ことを示す。この時点では命名 API の成功は未確認（モデル名の間違い・権限不足・割当切れは、実際に呼んで初めて分かる）。上の `curl` で `POST /parts/name` を呼び、200 と名前が返ることを確認する。503 のときはサーバのログを見る：「パーツの名前を考えるのが時間切れ」「パーツの名前を考えるのに失敗」「パーツの名前なし（命名が無効か、空の応答）」「聞き直してもパーツの名前が長すぎる」のどれかが所要時間つきで出る（`POST /nickname` なら「パーツの名前」が「ニックネーム」になる）。

名前の雰囲気は `NamingPrompt.kt` の `NAMING_TONE` の 1 行で替えられる。

## 遊び方ごとの URL（決定 77）

「みんな」は遊び方（通常・3 分チャレンジ・毎日 1 つずつ積む）ごとに別のランキング。送る URL と Firestore のコレクションも分ける。

| 遊び方 | 送る | 一覧 | コレクション |
|---|---|---|---|
| 通常 | `POST /towers` → 201 | `GET /towers`（`?mode=normal` と同じ） | `towers` |
| 3 分チャレンジ | `POST /towers/challenge` → 201。決まりの ID（`rule`）が要る | `GET /towers?mode=challenge` | `towers_challenge` |
| 毎日 1 つずつ積む | `PUT /towers/daily` → 200。持ち主の印ごとに 1 基を新しくする（同じ id のまま）。`ownerId`・`lastDay`・`streakDays` が要る | `GET /towers?mode=daily` | `towers_daily` |

共有ページ・JSON・絵（`GET /towers/{id}`…）は、どの遊び方のタワーでも id で取れる（通常・3 分チャレンジ・毎日の順に探す）。`POST /towers` に通常でないタワー（`mode` が challenge・daily）を送ると 400。

## 受け付けるタワー（POST /towers の検査）

`POST /towers` は認証なしで誰でも叩けるので、壊れたタワーは保存前に `400` で断る（`TowerValidation.kt`。本文に理由が英語で入る）。壊れたタワーが 1 基入ると、「みんな」の絵や共有ページの PNG がそのタワーで落ちるため。上限はゲームのルール（5 本）より余裕を見てあり、アプリが正しく作ったタワーは弾かない。

| 断るもの | 上限・条件 |
|---|---|
| パーツ・配置が空、または多すぎ | パーツの種類は 1〜100 個（`MAX_TOWER_PARTS`。決定 66）、配置は 1〜500 個（`MAX_TOWER_PLACEMENTS`。同じパーツを何度も置けるので、種類より多い。決定 77） |
| 3 分チャレンジの決まりの ID が無い・長すぎ | 1〜40 文字（`MAX_RULE_LENGTH`） |
| 毎日 1 つずつ積むの持ち主の印・最後に積んだ日・連続日数が無い・おかしい | 印は必須、日は `yyyy-mm-dd`、連続日数は 1〜36500 |
| 台の幅（`platformWidth`）がおかしい | 0.5〜10 m（`MIN_PLATFORM_WIDTH`〜`MAX_PLATFORM_WIDTH`。無ければ 1.4 m） |
| パーツの id が空・重複 | |
| パーツの頂点が 0 個・多すぎ | 1〜50 個（`MAX_PART_POINTS`。命名 API と同じ） |
| 座標・高さ・太さが NaN・無限・巨大 | 絶対値 300 以下（`MAX_COORDINATE`。天気のボーナスで塔ごと最大 3 倍にするので、100 から上げた。決定 68）。太さは 0 より大きい。高さは 0 以上 |
| 配置の partId がパーツに無い | |
| 作者名・パーツの名前が長すぎ | 作者 20 文字、名前 30 文字（絵文字も 1 文字） |
| JSON として読めない（型違い、`NaN` の字など） | `400 body must be a Tower` |

## 次にやること（C 担当）

- ~~`SharePage` の `og:image` を PNG にする~~ → `GET /towers/{id}/image.png`（1200×630）をその場で描いて返す形で対応済み。Cloud Storage は使わない。文字は描かない（コンテナに日本語フォントがないため）。確認は GET で行う（HEAD には応えないので `curl -I` は 404 になる）：`curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "$BASE_URL/towers/$ID/image.png"` が `200 image/png` ならよい
- Firestore 版を Cloud Run にデプロイして、本番で「送信 → 一覧 → 共有ページ」を確認する
- 命名を Cloud Run にデプロイして、本番で名前が返ることを確認する（プロジェクト ID をメタデータから取れるかも、ここで初めて確かめる）
- 対戦方式が決まったら `/challenges`
- `PUBLIC_BASE_URL` 環境変数：共有 URL のホスト名を固定したいときに設定
