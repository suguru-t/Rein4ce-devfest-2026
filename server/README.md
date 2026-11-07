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

## 次にやること（C 担当）

- ~~`SharePage` の `og:image` を PNG にする~~ → `GET /towers/{id}/image.png`（1200×630）をその場で描いて返す形で対応済み。Cloud Storage は使わない。文字は描かない（コンテナに日本語フォントがないため）
- Firestore 版を Cloud Run にデプロイして、本番で「送信 → 一覧 → 共有ページ」を確認する
- `POST /parts/name`：Vertex AI（Gemini）でパーツに名前と一言を付ける
- 対戦方式が決まったら `/challenges`
- `PUBLIC_BASE_URL` 環境変数：共有 URL のホスト名を固定したいときに設定
