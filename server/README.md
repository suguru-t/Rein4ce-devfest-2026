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
- `GOOGLE_CLOUD_PROJECT`：プロジェクトを固定したいとき。未指定なら認証情報から決まる

Firestore では 1 タワー 1 ドキュメント。`json` フィールドにタワー全体が入り、一覧用に `height`・`partCount`・`createdAt`・`author` も持つ。

手元から本物の Firestore につなぐ（データベース `(default)`、ネイティブモード、`asia-northeast1` は作成済み。鍵ファイルは作らない）：

```
gcloud auth application-default login     # 最初の 1 回だけ
TOWER_STORE=firestore TOWER_COLLECTION=towers_localtest ./gradlew :server:run
```

起動ログに `TowerStore: FirestoreTowerStore` と出れば Firestore につながっている。Cloud Run のサービスアカウントには `roles/datastore.user` を付与済みなので、デプロイするだけで Firestore 版になる。

## 次にやること（C 担当）

- Firestore 版を Cloud Run にデプロイして、本番で「送信 → 一覧 → 共有ページ」を確認する
- `SharePage` の `og:image` を PNG にする（SVG → PNG 変換して Cloud Storage に置く、または画像化エンドポイント）
- `POST /parts/name`：Vertex AI（Gemini）でパーツに名前と一言を付ける
- 対戦方式が決まったら `/challenges`
- `PUBLIC_BASE_URL` 環境変数：共有 URL のホスト名を固定したいときに設定
