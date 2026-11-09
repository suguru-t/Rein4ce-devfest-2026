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
- [ ] **送るタワーの形（`core/model` の `Tower`・`Placement`）が変わる変更は、新しいアプリを使う前にサーバを出す。** 古いサーバは知らない項目を読み飛ばして保存するので、その間に送ったタワーは、デプロイしたあとも古い形のまま残る（例：#71 の `Placement.scale`。デプロイ前に新しいアプリで送ったタワーは、ずっと 1 倍の大きさで描かれる）。デモの端末に新しいアプリを入れるのも、デプロイのあとにする

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
# 0. 出す前に、今どの版が動いているかを記録しておく（D で戻す先になる）
gcloud run services describe road-collector \
  --project=hackathon-2026-513703 --region=asia-northeast1 \
  --format='yaml(status.latestReadyRevisionName,status.traffic)'
#   status.traffic の revisionName と percent を控える。これが「動作確認済みの版」

# 1. イメージをビルドして push（タグは latest。Docker 不要）
TOKEN=$(gcloud auth print-access-token)
./gradlew :server:jib \
  --image=asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server \
  -Djib.to.auth.username=oauth2accesstoken -Djib.to.auth.password="$TOKEN"

# 2. デプロイ（環境変数は何も要らない。Cloud Run 上では Firestore と Gemini が自動で選ばれる）
gcloud run deploy road-collector \
  --image=asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server \
  --region=asia-northeast1 --allow-unauthenticated \
  --memory=512Mi --max-instances=2 --quiet
```

- `--min-instances` は**ここでは指定しない**。最小台数は版（リビジョン）ごとの設定で、指定しなければ前の版の値が次の版にも引き継がれる。ここで `0` と書いてしまうと、E でデモ用に `1` にした設定が次のデプロイで `0` に戻り、アクセスが途切れたあとの最初の 1 回が起動待ち（アプリの送信は 10 秒で諦めるので失敗しうる）になる。最小台数を変えるのは E と片付けのときだけ
- `Service URL` が今までと同じ `https://road-collector-3v3ltz2gla-an.a.run.app` で出ることを確かめる（変わっていたらアプリ側の設定も変える必要がある）
- D で古い版にトラフィックを固定したあとなら、デプロイしただけでは URL はまだ古い版を向いている。D の後半の手順で最新に切り替える

## C. 本番で確かめる（毎回やる）

```bash
U=https://road-collector-3v3ltz2gla-an.a.run.app

# 起動ログ：保存先と命名の部品が意図どおりか
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="road-collector" AND textPayload=~"TowerStore:|PartNamer:|RateLimit:"' \
  --limit 5 --freshness=10m --format="value(textPayload)"
#   期待：TowerStore: firestore（project=hackathon-2026-513703, database=(default), collection=towers）
#         PartNamer: Gemini（project=hackathon-2026-513703, location=global, model=gemini-3.5-flash-lite）
#         RateLimit: on（1 分あたり 端末ごと / 1 台の合計：投稿 10 / 120、一覧 60 / なし、名前 20 / 120、ニックネーム 10 / 60）
#   回数制限で困ったとき（発表の直前など）は、--update-env-vars=RATE_LIMIT=off で出し直すと数えなくなる（戻すときは --remove-env-vars=RATE_LIMIT）

# トラフィックが新しい版に 100% 向いているか（D で古い版に固定したままだと、ここで古い版の名前が出る）
gcloud run services describe road-collector \
  --project=hackathon-2026-513703 --region=asia-northeast1 \
  --format='yaml(status.latestReadyRevisionName,status.traffic)'

curl -s $U/health                      # ok
curl -s $U/towers                      # 一覧（JSON）。Firestore 化後は再デプロイしても消えない

# 命名（名前が返る。503 ならサーバのログで「パーツの名前を考えるのが時間切れ」「〜に失敗」「パーツの名前なし」「聞き直してもパーツの名前が長すぎる」のどれかを見る）
curl -s -X POST $U/parts/name -H "Content-Type: application/json" \
  -d '{"id":"p1","points":[{"x":-0.5,"y":0},{"x":0,"y":0.3},{"x":0.5,"y":0}],"kind":"ZIGZAG","distanceM":820,"walkedAt":1793952000000}'

# ニックネームの案（名前が返る。503 ならサーバのログで「ニックネームを考えるのが時間切れ」「〜に失敗」「ニックネームなし」「聞き直してもニックネームが長すぎる」のどれかを見る）
curl -s -X POST $U/nickname -H "Content-Type: application/json" \
  -d '{"partNames":["晩秋のジグザグ","深夜の縦線"],"partCount":5,"totalDistanceM":2400}'

# エラーのログが出ていないか。このサーバのログは素のテキスト（logback）で severity が付かないので、
# 本文の ERROR / WARN も検索する。WARN も混ざるので、出ただけで失敗と決めず中身を見る
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="road-collector" AND (severity>=ERROR OR textPayload=~" (ERROR|WARN) ")' \
  --project=hackathon-2026-513703 --limit=20 --freshness=10m \
  --format='yaml(timestamp,resource.labels.revision_name,severity,textPayload,jsonPayload.message,httpRequest.status)'
```

そのあと**アプリから**：デモ散歩 → パーツに名前が付く → 積む → サーバに送信 → 「みんな」に出る。

送信したタワーの ID（送信結果の `id`、または共有 URL の末尾）で、共有ページと画像をサーバから取れることを確かめてから LINE / X に貼る。プレビューが出ないとき、サーバ側か SNS 側かを切り分けるため：

```bash
ID='送信結果に含まれる実際の id'

# 共有ページが、公開 URL の同じ ID の image.png を参照していること
curl -fsS "$U/towers/$ID" | grep -F '<meta property="og:image"'

# 200 と image/png であること。HEAD（curl -I）には応えないので、GET で本文を捨てる
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "$U/towers/$ID/image.png"
```

最後に共有 URL を LINE / X に貼って絵が出る（見た目と SNS 側の表示は、貼って目で見るしかない）。

- [ ] 上の確認が全部通ったら、Discord に「本番に出した」と知らせる

## D. 問題が出たら、前の版に戻す（1 分）

Cloud Run は古い版（リビジョン）を残しているので、イメージを作り直さずに戻せる。

戻す先は「1 つ前の版」ではなく、**B の 0 で控えた、動作確認済みの版**。環境変数の変更（F）や最小台数の変更（E）でも版は増えるので、直前の版が確認済みとは限らない。

```bash
# 版の一覧（新しい順）。参考に見る
gcloud run revisions list --service road-collector --region asia-northeast1

# 控えておいた版に全部のトラフィックを戻す
gcloud run services update-traffic road-collector --region asia-northeast1 \
  --to-revisions=<控えた版の名前>=100
```

戻したら Discord に知らせる。そのあと直すときは、次の順でやる（古い版への固定は、その後のデプロイにも引き継がれるので、B を出しただけでは URL はまだ古い版を向いている）：

1. 原因を直して、手順 B で修正版をデプロイする
2. トラフィックを最新の版に切り替える
   ```bash
   gcloud run services update-traffic road-collector --region asia-northeast1 --to-latest
   ```
3. 割り当て先を確認する。`status.traffic` が新しい版に 100%、`latestRevision: true` になっていること
   ```bash
   gcloud run services describe road-collector \
     --project=hackathon-2026-513703 --region=asia-northeast1 \
     --format='yaml(status.latestReadyRevisionName,status.traffic)'
   ```
4. 手順 C で実際の動作を確かめる。切り替え直後に問題が出たら、控えておいた版へもう一度戻す

**注意**：Firestore 化より前の版（メモリ保存）に戻すと、保存したタワーが見えなくなる（消えてはいない。Firestore 版に戻せばまた見える）。

## E. デモ当日

```bash
# 前日の夜か当日の朝：常時 1 台を起動しておき、最初のアクセスの待ち時間（10 秒ほど）をなくす
gcloud run services update road-collector --region asia-northeast1 --min-instances=1

# 終わったら戻す（クレジット節約。1 台で 1 日 数十円）
gcloud run services update road-collector --region asia-northeast1 --min-instances=0
```

当日の朝に、手順 C の確認をもう一度やる。

### 変なタワーが投稿されたとき（消し方）

`POST /towers` は誰でも叩けるので、デモ当日にいたずらや壊れたタワーが「みんな」に並ぶことがある（壊れたデータは #58 の検査で 400 になるが、中身がまともな「変なタワー」は通る）。消すのは Firestore の文書を 1 つ消すだけ。サーバの再起動やデプロイは要らない。

```bash
# 1. 消したいタワーの id を知る（「みんな」のカードをタップすると開く共有 URL の末尾、または一覧）
curl -s $U/towers

# 2. Firestore の文書を消す（REST。gcloud に Firestore の文書を消すコマンドは無い）
ID='消したいタワーの id'
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  "https://firestore.googleapis.com/v1/projects/hackathon-2026-513703/databases/(default)/documents/towers/$ID?currentDocument.exists=true"
#   200 なら消えた。末尾の currentDocument.exists=true は「その id の文書があるときだけ消す」の条件で、
#   id を打ち間違えると 404（No document to update）で止まる。これが無いと、無い id でも 200 が返って気づけない。
#   消す前に GET（-X DELETE と末尾の条件を外す）で中身を見て、消したいタワーか確かめる

# 3. 一覧から消えたこと
curl -s $U/towers | grep -c "$ID"     # 0
```

- コンソールからでもよい：Firestore → データ → `towers` → 文書の id → 「文書を削除」
- 消したあとも、アプリの「みんな」は開き直す（取り直す）まで前の一覧を出す。共有ページと PNG は 404 になる
- 共有 URL が LINE や X に貼られていた場合、そのプレビュー（題名と絵）は相手側に保存されているので、消してもしばらく残る。開くと 404 になる
- 本番の `towers` を消すのはこの手順だけ。試し打ちのデータは、手元のサーバ（メモリ版）か `towers_localtest` で作り、本番には入れない

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

## 初回の本番デプロイ（#6 Firestore ＋ #14 og:image ＋ #11 命名）のメモ

- マージの順は #6 → #14 → #11。衝突は「両方残す」だけでは済まず、次の調整が要った：

  | 統合 | 衝突したファイル | 解消内容 |
  |---|---|---|
  | #6 | `README.md` | main の app の検証記録と、#6 の server の検証記録を組み合わせる |
  | #14 | `server/README.md` | 「次にやること」で PNG の項目を対応済みにし、Firestore の項目は main のものを残す |
  | #11 | `Application.kt`・`gradle/libs.versions.toml`・`server/build.gradle.kts`・`README.md`・`server/README.md` | 依存と説明は両機能を取り込む。`module()` の宣言は `store` と `namer` の両引数を受ける**1 つ**に統合する（同名の宣言を 2 つ残さない）。README の古い「TowerStore はいまはメモリ」の説明は消す |

- 統合後は、テストが保存先（`MemoryTowerStore`）と命名（偽物か `DisabledPartNamer`）を両方 `module()` に注入し、外部サービスにつながない状態で CI が通ることを確かめる
- 出したあと、本番の「みんな」は空のまま（Firestore 化前のタワーはメモリにあって消えている）。デモ用のタワーをアプリから何個か送っておく
- 共有 URL を LINE / X に貼って絵が出るかは、本番に出て初めて確かめられる（お試し用はログインが要るため）

## 終わったあとの片付け（デモが終わってから）

```bash
gcloud run services delete road-collector-preview --region asia-northeast1 --quiet
gcloud artifacts docker tags delete asia-northeast1-docker.pkg.dev/hackathon-2026-513703/road-collector/server:preview --quiet
gcloud run services update road-collector --region asia-northeast1 --min-instances=0
```

Firestore の `towers_preview`・`towers_localtest` はコンソールから消す（本番の `towers` は、E の「変なタワーが投稿されたとき」の手順以外では消さない）。
