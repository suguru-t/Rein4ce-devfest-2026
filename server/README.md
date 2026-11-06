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

## パーツの命名（POST /parts/name）

`Part` をそのまま送ると、Vertex AI の Gemini が名前を付けて `200 {"name": "..."}` を返す。名前が付けられなかったとき（時間切れ・Gemini の失敗・命名が無効）は `503`。アプリはそのとき今の表示のままにする。名前の手がかりは形・距離・日時だけで、位置情報は使わない。

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

起動ログに `PartNamer: Gemini（project=..., location=global, model=gemini-3.5-flash-lite）` と出れば Gemini につながっている。名前の雰囲気は `NamingPrompt.kt` の `NAMING_TONE` の 1 行で替えられる。

## 次にやること（C 担当）

- `TowerStore` を Firestore に置き換える。データベース `(default)`（ネイティブモード、`asia-northeast1`）は作成済みで、Cloud Run のサービスアカウントにも `roles/datastore.user` を付与済み。鍵ファイルは作らない。手元から本物の Firestore につなぐときは、先に `gcloud auth application-default login` を実行する
- `SharePage` の `og:image` を PNG にする（SVG → PNG 変換して Cloud Storage に置く、または画像化エンドポイント）
- 命名を Cloud Run にデプロイして、本番で名前が返ることを確認する（プロジェクト ID をメタデータから取れるかも、ここで初めて確かめる）
- 対戦方式が決まったら `/challenges`
- `PUBLIC_BASE_URL` 環境変数：共有 URL のホスト名を固定したいときに設定
