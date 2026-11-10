package com.rein4ce.roadcollector.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond

/**
 * 回数制限（1 分ごとに数える）。誰でも叩ける URL なので、AI（Gemini の料金がかかる）と投稿を、続けて叩かれても予算に響かないようにする。
 *
 * - 端末ごと（[perClient]）と、サーバ 1 台の合計（[total]）の 2 つで数える。端末の印は自分で作れる文字列なので、
 *   印を変えながら叩かれると端末ごとの上限はすり抜けられる。合計で頭打ちにして、料金が膨らまないようにする
 * - 数えるのはサーバの台ごと（Cloud Run は最大 2 台）なので、実際の上限はその 2 倍まで行く。料金を抑える目的なら十分
 * - 窓は 1 分ごとに区切る（時計の分の切れ目で数え直す）。切れ目の前後で、短い間に上限の 2 倍まで通ることがあるが、目的には足りる
 *
 * @param now テストで時刻を進めるため
 */
class RateLimiter(
    val perClient: Int,
    val total: Int,
    private val windowMs: Long = 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var windowStart = Long.MIN_VALUE
    private var totalCount = 0

    /** この窓で数えた回数（端末ごと）。窓が変わったら空にするので、覚える数は 1 分に来た端末の数まで */
    private val counts = HashMap<String, Int>()

    /**
     * 1 回ぶん数える。通してよければ null。上限を超えていれば、次の窓までの秒数（Retry-After に入れる。1 以上）
     *
     * @param client 端末の印か IP（[clientKey]）
     */
    @Synchronized
    fun acquire(client: String): Long? {
        val t = now()
        val start = t - Math.floorMod(t, windowMs)
        if (start != windowStart) {
            windowStart = start
            totalCount = 0
            counts.clear()
        }
        val mine = counts[client] ?: 0
        if (mine >= perClient || totalCount >= total) {
            return ((windowStart + windowMs - t + 999) / 1000).coerceAtLeast(1)
        }
        counts[client] = mine + 1
        totalCount++
        return null
    }
}

/**
 * API ごとの回数制限。null の所は数えない。
 * 上限は、ふつうに使って当たらない大きさにしている（Issue #3 の相談・決定 57）
 *
 * - 投稿：積み終えるたびに 1 回（5 個積むのに最短でも 30 秒ほど）
 * - 一覧：「みんな」を開いたとき・リロード・送ったあとの取り直し
 * - パーツの名前（AI）：記録やデモ散歩でパーツができるたびに 1 回
 * - ニックネーム（AI）：「AI に考えてもらう」を押したときだけ
 */
class RateLimits(
    val towersPost: RateLimiter?,
    val towersGet: RateLimiter?,
    val partName: RateLimiter?,
    val nickname: RateLimiter?,
    /** タワー 1 基（共有ページ・JSON）と SVG の絵。Firestore を 1〜3 件読む（決定 88） */
    val towerView: RateLimiter? = null,
    /** PNG の絵（共有ページのプレビュー）。その場で描くので重い（決定 88） */
    val towerImage: RateLimiter? = null,
) {
    /** 起動のログに出す説明 */
    val description: String
        get() = if (this === OFF || listOf(towersPost, towersGet, partName, nickname, towerView, towerImage).all { it == null }) {
            "off"
        } else {
            "on（1 分あたり 端末ごと / 1 台の合計：投稿 ${towersPost.text()}、一覧 ${towersGet.text()}、" +
                "名前 ${partName.text()}、ニックネーム ${nickname.text()}、タワー 1 基 ${towerView.text()}、PNG ${towerImage.text()}）"
        }

    private fun RateLimiter?.text(): String = when {
        this == null -> "なし"
        total == Int.MAX_VALUE -> "$perClient / なし"
        else -> "$perClient / $total"
    }

    companion object {
        /** 数えない（手元で試すときと、環境変数 RATE_LIMIT=off のとき） */
        val OFF = RateLimits(null, null, null, null)

        /** 本番の上限 */
        fun standard(now: () -> Long = System::currentTimeMillis) = RateLimits(
            towersPost = RateLimiter(perClient = 10, total = 120, now = now),
            // 一覧は Firestore を読む回数を TtlCache で抑えているが、印を変えれば端末ごとの上限はすり抜けられるので、合計でも頭打ちにする（決定 88）
            towersGet = RateLimiter(perClient = 60, total = 1200, now = now),
            partName = RateLimiter(perClient = 20, total = 120, now = now),
            nickname = RateLimiter(perClient = 10, total = 60, now = now),
            // 絵はカードが見えるたびに 1 基ずつ取るので、一覧より多めに（決定 88）
            towerView = RateLimiter(perClient = 300, total = 3000, now = now),
            towerImage = RateLimiter(perClient = 30, total = 300, now = now),
        )

        /**
         * 環境変数 RATE_LIMIT が off なら数えない。それ以外（何も書かないときも）は本番の上限。
         * 発表の直前に何かあって止めたいときは、Cloud Run の環境変数に RATE_LIMIT=off を入れて出し直す
         */
        fun fromEnvironment(): RateLimits =
            if (System.getenv("RATE_LIMIT").equals("off", ignoreCase = true)) OFF else standard()
    }
}

/**
 * 数えるときの相手。アプリが送る持ち主の印（[OWNER_HEADER]）があればそれで、無ければ IP で数える。
 * 大学や会場の Wi-Fi では全員が同じ IP に見えるので、IP だけで数えると、ほかの人のぶんまで止めてしまうため。
 * IP は Cloud Run が付ける X-Forwarded-For の最初（送る側が書き足せる値なので、すり抜けには使えるが、合計の上限で頭打ちになる）
 */
internal fun ApplicationCall.clientKey(): String {
    request.header(OWNER_HEADER)?.takeIf { OWNER_ID_PATTERN.matches(it) }?.let { return "owner:$it" }
    val forwarded = request.header(HttpHeaders.XForwardedFor)?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
    return "ip:" + (forwarded ?: request.origin.remoteHost)
}

/**
 * 回数を超えていたら 429（Retry-After に次に通せるまでの秒数）を返して true。超えていなければ数えて false。
 * アプリは 429 を、ほかの失敗と同じに扱う（名前は今の表示のまま、送信は失敗の知らせ）
 */
internal suspend fun ApplicationCall.overLimit(limiter: RateLimiter?): Boolean {
    val wait = limiter?.acquire(clientKey()) ?: return false
    response.header(HttpHeaders.RetryAfter, wait.toString())
    respond(HttpStatusCode.TooManyRequests, "too many requests")
    return true
}
