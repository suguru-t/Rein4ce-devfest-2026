# 本番デプロイの手順書（C 担当）

本番の Cloud Run サービス `road-collector`（東京 `asia-northeast1`、プロジェクト `hackathon-2026-513703`）に、サーバを出すときの手順。
初回のセットアップ（API の有効化、Artifact Registry、認証ヘルパ）は済んでいるので、ここには**2 回目以降の手順**だけを書く。初回は `server/README.md`。

- 本番 URL：`https://road-collector-3v3ltz2gla-an.a.run.app`（アプリの `SERVER_BASE_URL`）
- お試し用（非公開、メンバーのみ）：`road-collector-preview`。本番に出す前にここで確かめる
- 所要時間：ビルド＋デプロイで 5 分、確認を入れて 20〜30 分

## 0. 出す前に

- [ ] 出したい PR がすべて**承認・CI 通過・マージ済み**で、手元の `main` が最新（`git switch main && git pull`）
- [ ] `./gradlew :server:test` が手元で通る
- [ ] 本番に出す中身をチームに一言知らせる（Discord）。アプリから使われているので、落ちるとみんなの確認が止まる
- [ ] 必要なら、先にお試し用で確かめる（手順 A）

## A. お試し用で確かめる（任意だが、大きな変更のときは必ず）

```bash
TOKEN=$(gcloud auth print-access-token)
./gradlew :server:jib \
  --image=asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server:preview \
  -Djib.to.auth.username=oauth2accesstoken -Djib.to.auth.password="$TOKEN"

gcloud run deploy road-collector-preview \
  --image=asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server:preview \
  --region=asia-northeast1 --no-allow-unauthenticated \
  --set-env-vars=TOWER_COLLECTION=towers_preview --quiet
```

お試し用はログインが要るので、呼ぶときは ID トークンを付ける：

```bash
IT=$(gcloud auth print-identity-token)
U=https://road-collector-preview-3v3ltz2gla-an.a.run.app
curl -H "Authorization: Bearer $IT" $U/towers
```

保存先はコレクション `towers_preview` なので、本番の `towers` には混ざらない。

## B. 本番に出す

```bash
# 1. イメージをビルドして push（タグは latest。Docker 不要）
TOKEN=$(gcloud auth print-access-token)
./gradlew :server:jib \
  --image=asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server \
  -Djib.to.auth.username=oauth2accesstoken -Djib.to.auth.password="$TOKEN"

# 2. デプロイ（環境変数は何も要らない。Cloud Run 上では Firestore と Gemini が自動で選ばれる）
gcloud run deploy road-collector \
  --image=asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server \
  --region=asia-northeast1 --allow-unauthenticated \
  --memory=512Mi --min-instances=0 --max-instances=2 --quiet
```

`Service URL` が今までと同じ `https://road-collector-3v3ltz2gla-an.a.run.app` で出ることを確かめる（変わっていたらアプリ側の設定も変える必要がある）。

## C. 本番で確かめる（毎回やる）

```bash
U=https://road-collector-3v3ltz2gla-an.a.run.app

# 起動ログ：保存先と命名の部品が意図どおりか
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="road-collector" AND textPayload=~"TowerStore:|PartNamer:"' \
  --limit 5 --freshness=10m --format="value(textPayload)"
#   期待：TowerStore: firestore（project=hackathon-2026-513703, database=(default), collection=towers）
#         PartNamer: Gemini（project=hackathon-2026-513703, location=global, model=gemini-3.5-flash-lite）

curl -s $U/health                      # ok
curl -s $U/towers                      # 一覧（JSON）。Firestore 化後は再デプロイしても消えない

# 命名（名前が返る。503 なら Gemini 側の設定を疑う）
curl -s -X POST $U/parts/name -H "Content-Type: application/json" \
  -d '{"id":"p1","points":[{"x":-0.5,"y":0},{"x":0,"y":0.3},{"x":0.5,"y":0}],"kind":"ZIGZAG","distanceM":820,"walkedAt":1793952000000}'

# エラーのログが出ていないか
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="road-collector" AND severity>=ERROR' \
  --limit 10 --freshness=10m --format="value(severity,textPayload)"
```

そのあと**アプリから**：デモ散歩 → パーツに名前が付く → 積む → サーバに送信 → 「みんな」に出る → 共有 URL を LINE / X に貼って絵が出る。

- [ ] 上の確認が全部通ったら、Discord に「本番に出した」と知らせる

## D. 問題が出たら、前の版に戻す（1 分）

Cloud Run は古い版（リビジョン）を残しているので、イメージを作り直さずに戻せる。

```bash
# 版の一覧（新しい順）。ACTIVE が今動いているもの
gcloud run revisions list --service road-collector --region asia-northeast1

# 1 つ前の版に全部のトラフィックを戻す
gcloud run services update-traffic road-collector --region asia-northeast1 \
  --to-revisions=<前の版の名前>=100
```

戻したら Discord に知らせて、原因を直してから手順 B をやり直す。直った版を出したら、トラフィックを最新に戻す：

```bash
gcloud run services update-traffic road-collector --region asia-northeast1 --to-latest
```

**注意**：Firestore 化より前の版（メモリ保存）に戻すと、保存したタワーが見えなくなる（消えてはいない。Firestore 版に戻せばまた見える）。

## E. デモ当日

```bash
# 前日の夜か当日の朝：常時 1 台を起動しておき、最初のアクセスの待ち時間（10 秒ほど）をなくす
gcloud run services update road-collector --region asia-northeast1 --min-instances=1

# 終わったら戻す（クレジット節約。1 台で 1 日 数十円）
gcloud run services update road-collector --region asia-northeast1 --min-instances=0
```

当日の朝に、手順 C の確認をもう一度やる。

## F. 設定を変えるだけのとき（コードの変更なし）

環境変数は、イメージを作り直さずに変えられる。変えると新しい版が自動で立ち上がる。

```bash
# 例：Gemini のモデルを替える
gcloud run services update road-collector --region asia-northeast1 \
  --update-env-vars GEMINI_MODEL=gemini-3.5-flash,GEMINI_LOCATION=asia-northeast1
# 元に戻す
gcloud run services update road-collector --region asia-northeast1 \
  --remove-env-vars GEMINI_MODEL,GEMINI_LOCATION
```

使える環境変数は `server/README.md`（`TOWER_STORE` / `TOWER_COLLECTION` / `PART_NAMER` / `GEMINI_MODEL` / `GEMINI_LOCATION` / `PUBLIC_BASE_URL`）。

## 初回の本番デプロイ（#6 Firestore ＋ #11 命名 ＋ #14 og:image）のメモ

- マージの順：#6 → #11 → #14。#11 は #6 と `Application.kt`・`libs.versions.toml`・`server/build.gradle.kts`・README 2 つで衝突するが、**どれも「両方残す」**で直る。`module()` は `store` と `namer` の両方を受け取る形にする（お試し用で確認済み）
- #14 は `Routes.kt` の別の場所なので衝突しない見込み
- 出したあと、本番の「みんな」は空のまま（Firestore 化前のタワーはメモリにあって消えている）。デモ用のタワーをアプリから何個か送っておく
- 共有 URL を LINE / X に貼って絵が出るかは、本番に出て初めて確かめられる（お試し用はログインが要るため）

## 終わったあとの片付け（デモが終わってから）

```bash
gcloud run services delete road-collector-preview --region asia-northeast1 --quiet
gcloud artifacts docker tags delete asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server:preview --quiet
gcloud run services update road-collector --region asia-northeast1 --min-instances=0
```

Firestore の `towers_preview`・`towers_localtest` はコンソールから消す（本番の `towers` は消さない）。
