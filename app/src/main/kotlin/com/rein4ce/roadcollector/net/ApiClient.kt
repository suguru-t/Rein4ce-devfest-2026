package com.rein4ce.roadcollector.net

import android.util.Log
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartNameResponse
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Cloud Run 上のサーバ（:server モジュール）を呼ぶ。
 * URL は local.properties の SERVER_BASE_URL（既定は本番の Cloud Run。ローカルサーバなら http://10.0.2.2:8080）。
 */
object ApiClient {

    val baseUrl: String = BuildConfig.SERVER_BASE_URL.trimEnd('/')

    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    suspend fun ping(): String = client.get("$baseUrl/").body()

    /**
     * [mode] の遊び方の一覧（決定 77）。持ち主の印をヘッダで送ると、自分が送ったタワーに mine が付いて返る（サーバの OWNER_HEADER と同じ名前）。
     * 遊び方を知らない前のサーバは mode を見ずに通常の一覧を返すので、受け取ったあと遊び方で絞る（遊び方の無い行は通常）
     */
    suspend fun listTowers(mode: TowerMode = TowerMode.NORMAL): List<TowerSummary> =
        client.get("$baseUrl/towers") {
            parameter("mode", mode.wire)
            ownerId()?.let { header(OWNER_HEADER, it) }
        }.body<List<TowerSummary>>().filter { it.mode == mode }

    /**
     * タワー 1 基の中身（パーツと置き方）。
     * 同じ URL は Accept に application/json がないと共有ページ（HTML）を返す。ContentNegotiation が Accept を付けてくれるが、
     * この呼び出しはそれに頼っていることが分かるよう明示しておく
     */
    suspend fun getTower(id: String): Tower =
        client.get("$baseUrl/towers/$id") { accept(ContentType.Application.Json) }.body()

    /**
     * 送るときの作者名（設定のニックネーム。空なら null で、「みんな」には「ゲスト」と出る）。
     * アプリの起動時に RoadCollectorApp が設定とつなぐ
     */
    var authorName: () -> String? = { null }

    /**
     * このアプリの持ち主の印（data/OwnerId。はじめて開いたときに作った乱数（前の版から使っていれば端末の id）を、塩を付けてハッシュしたもの。決定 90）。送るタワーに入れ、一覧を取るときにヘッダで送る。
     * アプリの起動時に RoadCollectorApp がつなぐ。null なら送らない（サーバは印なしとして扱う）
     */
    var ownerId: () -> String? = { null }

    /**
     * 送信できたタワーの id を受け取る係（自分のタワーの印。data/MyTowers）。
     * アプリの起動時に RoadCollectorApp がつなぐ
     */
    var onPosted: (TowerCreated, TowerMode) -> Unit = { _, _ -> }

    /**
     * 通常のタワーを送る（POST /towers）。作者名がまだ入っていなければ設定のニックネームを、持ち主の印を入れて送る。送れたら [onPosted] に知らせる。
     * 送ったタワーは誰でも読めるので、パーツを歩いた時刻とタワーを作った時刻は、日付までに丸めてから送る（[withDayOnlyTimes]）
     */
    suspend fun postTower(tower: Tower): TowerCreated =
        client.post("$baseUrl/towers") {
            contentType(ContentType.Application.Json)
            setBody(prepared(tower))
        }.body<TowerCreated>().also { onPosted(it, TowerMode.NORMAL) }

    /**
     * 3 分チャレンジの塔を送る（POST /towers/challenge。決定 77）。結果が出たら自動で送る（ChallengeViewModel）。
     * 作者名・持ち主の印・時刻の丸めは [postTower] と同じ
     */
    suspend fun postChallengeTower(tower: Tower): TowerCreated =
        client.post("$baseUrl/towers/challenge") {
            contentType(ContentType.Application.Json)
            setBody(prepared(tower))
        }.body<TowerCreated>().also { onPosted(it, TowerMode.CHALLENGE) }

    /**
     * 毎日 1 つずつ積むの塔を送る（PUT /towers/daily。決定 77）。サーバは持ち主の印ごとに 1 基を持ち、送るたびに新しい形にする。
     * 積んで止まったら自動で送る（DailyViewModel）
     */
    suspend fun putDailyTower(tower: Tower): TowerCreated =
        client.put("$baseUrl/towers/daily") {
            contentType(ContentType.Application.Json)
            setBody(prepared(tower))
        }.body<TowerCreated>().also { onPosted(it, TowerMode.DAILY) }

    /** 送る形：時刻を日付までに丸め、作者名と持ち主の印を入れる */
    private fun prepared(tower: Tower): Tower = withOwner(withAuthor(withDayOnlyTimes(tower), authorName()), ownerId())

    /**
     * パーツの名前を AI（サーバの Gemini）に考えてもらう（POST /parts/name）。
     * 名前が付かなかった（サーバが 503 を返した）・通信できなかったときは null。回数の上限を超えた（429）ときも同じ。そのときは今の表示のままにする（decisions.md の 7）
     */
    suspend fun namePart(part: Part): String? = try {
        client.post("$baseUrl/parts/name") {
            contentType(ContentType.Application.Json)
            // 持ち主の印は、サーバが回数を数えるのにだけ使う（会場の Wi-Fi で全員が同じ IP に見えても、ほかの人のぶんまで止められないように。決定 57）
            ownerId()?.let { header(OWNER_HEADER, it) }
            setBody(namingRequest(part))
        }.body<PartNameResponse>().name
    } catch (e: CancellationException) {
        throw e // 画面を閉じて取りやめたときは、失敗として扱わない
    } catch (e: Exception) {
        Log.w("ApiClient", "naming failed", e)
        null
    }

    /**
     * ニックネームを AI（サーバの Gemini）に考えてもらう（POST /nickname）。
     * 手がかりは、パーツの名前・本数・合計距離だけ（[NicknameHints]。場所は送らない）。
     * 考えられなかった（サーバが 503 を返した）・通信できなかったときは null。そのときは入力欄をそのままにする
     */
    suspend fun suggestNickname(hints: NicknameHints): String? = try {
        client.post("$baseUrl/nickname") {
            contentType(ContentType.Application.Json)
            ownerId()?.let { header(OWNER_HEADER, it) } // 回数を数えるのにだけ使う（上と同じ）
            setBody(hints)
        }.body<PartNameResponse>().name
    } catch (e: CancellationException) {
        throw e // 窓を閉じて取りやめたときは、失敗として扱わない
    } catch (e: Exception) {
        Log.w("ApiClient", "nickname failed", e)
        null
    }
}

/**
 * 命名 API に送るパーツ。名前の手がかりは形・距離・日時だけなので（decisions.md の 8）、今付いている名前は送らない
 * （自分で付けた名前を外に出さない。サーバの指示文も名前は使わない）
 */
internal fun namingRequest(part: Part): Part = part.copy(name = null)

/** 送るタワーに作者名を入れる。もう入っていればそのまま。[author] が空なら入れない（「みんな」ではゲスト） */
internal fun withAuthor(tower: Tower, author: String?): Tower =
    if (!tower.author.isNullOrBlank() || author.isNullOrBlank()) tower else tower.copy(author = author)

/** 送るタワーに持ち主の印を入れる。[owner] が無ければ入れない（サーバは印なしとして保存する） */
internal fun withOwner(tower: Tower, owner: String?): Tower =
    if (owner.isNullOrBlank()) tower else tower.copy(ownerId = owner)

/**
 * 送るタワーの、パーツを歩いた時刻と、タワーを作った時刻を日付まで（日本時間の 0 時）に丸める。送ったタワーは誰でも読める（GET /towers/{id}）ので、
 * 何時何分に歩いたか（ふだん家を出る時刻など）が分からないようにする。作った時刻も、歩いてすぐ積んで送ると歩いた時刻が分かるので丸める
 * （「みんな」に出す日時も日付だけにした。一覧は 1 週間ごとに入れ替える予定なので、日付で足りる。新しい順の並びで同じ日のタワーは高い順）。
 * 分からない時刻（0 以下）はそのまま（サーバが受け取った時刻を入れる）。AI の名前付け（[namingRequest]）は「夕方」などを手がかりにするので、
 * こちらは丸めない（決定 47）
 */
internal fun withDayOnlyTimes(tower: Tower, zone: ZoneId = JAPAN): Tower =
    tower.copy(
        parts = tower.parts.map { p -> if (p.walkedAt <= 0L) p else p.copy(walkedAt = startOfDay(p.walkedAt, zone)) },
        createdAt = if (tower.createdAt <= 0L) tower.createdAt else startOfDay(tower.createdAt, zone),
    )

/** [epochMs] の日の 0 時（[zone] の時間で） */
private fun startOfDay(epochMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMs).atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()

/** 歩いた時刻を日付に丸めるときの時間帯（日本時間） */
private val JAPAN: ZoneId = ZoneId.of("Asia/Tokyo")

/** 一覧を取るときに持ち主の印を送るヘッダ（サーバの TowerValidation.kt の OWNER_HEADER と同じ） */
internal const val OWNER_HEADER = "X-Owner-Id"
