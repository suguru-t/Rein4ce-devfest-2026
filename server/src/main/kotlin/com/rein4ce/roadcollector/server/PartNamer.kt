package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.model.Part
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * パーツに名前を付ける部品。POST /parts/name の中身。POST /nickname（ニックネームを考える）もこれでやる。
 *
 * 名前の手がかりは形・距離・日時だけ（位置情報は受け取らない）。
 * ニックネームの手がかりは、コレクションのパーツの名前・本数・合計距離だけ（[NicknameHints]）。
 * 付けられなかったら null を返す。ルートはそれを 503 にして、アプリは今の表示のままにする。
 *
 * 実装：
 * - [DisabledPartNamer]：いつも null。手元で GCP の設定がないとき
 * - [GeminiPartNamer]：Vertex AI の Gemini を呼ぶ
 *
 * どれを使うかは [PartNamer.fromEnvironment] が決める。
 */
interface PartNamer {
    suspend fun name(part: Part): String?

    /**
     * 返ってきた名前が長すぎたときの聞き直し。[tooLong] は前の案。
     * 既定では同じ質問をもう一度する（偽物の命名はこれでよい）。Gemini 版は前の案を見せて「短く」と頼む
     */
    suspend fun retryName(part: Part, tooLong: String): String? = name(part)

    /** ニックネームを 1 つ考える。考えられなければ null（既定は考えない） */
    suspend fun nickname(hints: NicknameHints): String? = null

    /** ニックネームが長すぎたときの聞き直し。[tooLong] は前の案 */
    suspend fun retryNickname(hints: NicknameHints, tooLong: String): String? = nickname(hints)

    companion object {
        /** 名前の最大文字数（手持ちのカードに収まる長さ）。超えたら 1 回聞き直し、だめなら 503（切り詰めない） */
        const val MAX_NAME_LENGTH = 10

        /** 指示文で頼む目安の長さ。上限ちょうどを頼むと、たまに 11〜12 文字で返ってくるので余裕を持たせる */
        const val TARGET_NAME_LENGTH = 8

        /** ニックネームの最大文字数（アプリの入力欄と同じ）と、指示文で頼む目安の長さ */
        const val MAX_NICKNAME_LENGTH = NicknameHints.MAX_NICKNAME_LENGTH
        const val TARGET_NICKNAME_LENGTH = 8

        /** これより待たせると、アプリの「名前を考え中…」が長すぎる */
        const val TIMEOUT_MS = 3_000L

        private val log = LoggerFactory.getLogger(PartNamer::class.java)

        /**
         * PART_NAMER=gemini か off で明示できる。未指定なら、
         * Cloud Run 上（K_SERVICE がある）では gemini、手元では off。
         */
        fun fromEnvironment(env: (String) -> String? = System::getenv): PartNamer {
            val explicit = env("PART_NAMER")?.trim()?.lowercase()
            val useGemini = when (explicit) {
                "gemini" -> true
                "off" -> false
                null, "" -> !env("K_SERVICE").isNullOrBlank()
                else -> error("PART_NAMER は gemini か off（指定値: $explicit）")
            }
            if (!useGemini) {
                log.info("PartNamer: off（命名なし。POST /parts/name は 503）")
                return DisabledPartNamer
            }
            // 設定が足りなくてもサーバは起動させる（命名だけ無効にして 503 を返す）
            val projectId = GeminiPartNamer.resolveProjectId(env)
            if (projectId == null) {
                log.error("PartNamer: Gemini を使う設定だがプロジェクト ID が分からないので、命名なしで動きます（GOOGLE_CLOUD_PROJECT を設定する）")
                return DisabledPartNamer
            }
            val location = env("GEMINI_LOCATION")?.takeIf { it.isNotBlank() } ?: GeminiPartNamer.DEFAULT_LOCATION
            val model = env("GEMINI_MODEL")?.takeIf { it.isNotBlank() } ?: GeminiPartNamer.DEFAULT_MODEL
            return try {
                GeminiPartNamer.create(projectId, location, model).also {
                    log.info("PartNamer: Gemini（project=$projectId, location=$location, model=$model）")
                }
            } catch (e: Exception) {
                log.error("PartNamer: Gemini の準備に失敗したので、命名なしで動きます: ${e.message}", e)
                DisabledPartNamer
            }
        }
    }
}

/** 名前を付けない。いつも null（＝ 503） */
object DisabledPartNamer : PartNamer {
    override suspend fun name(part: Part): String? = null
}

/**
 * ルートから呼ぶときの安全策をまとめたもの。どれか 1 つでも当たれば null（失敗）。
 *
 * - [timeoutMs] を超えたら打ち切る（聞き直しも含めて、1 回ごと）
 * - 例外は握りつぶしてログに出す（命名が落ちてもサーバは落とさない）
 * - 前後の空白・かぎ括弧・引用符を取り、空なら失敗
 * - [PartNamer.MAX_NAME_LENGTH] 文字を超えていたら、**切り詰めずに 1 回だけ聞き直す**（前の案を見せて「短く」と頼む）。
 *   それでも長ければ失敗。切り詰めると「夕暮れの七曲りブロッ」のように言葉の途中で切れた名前になるため（#53 で B が発見）。
 *   文字数は絵文字なども 1 文字と数える（コードポイント単位）
 *
 * 503 の原因（時間切れ・失敗・名前なし・長すぎ）をアクセスログだけでは見分けられないので、それぞれ所要時間つきでログに出す。
 * 3 秒が適切かはこのログで判断する。リクエスト自体のキャンセルは失敗に数えず、そのまま上に投げる。
 * 最悪でも [timeoutMs] × 2 で終わる（アプリの通信の期限 10 秒に収まる）。
 */
suspend fun PartNamer.nameSafely(part: Part, timeoutMs: Long = PartNamer.TIMEOUT_MS): String? =
    safely("パーツの名前", PartNamer.MAX_NAME_LENGTH, timeoutMs, first = { name(part) }, retry = { retryName(part, it) })

/** ニックネームを考える。安全策は [nameSafely] と同じ（長さの上限は [PartNamer.MAX_NICKNAME_LENGTH]） */
suspend fun PartNamer.nicknameSafely(hints: NicknameHints, timeoutMs: Long = PartNamer.TIMEOUT_MS): String? =
    safely("ニックネーム", PartNamer.MAX_NICKNAME_LENGTH, timeoutMs, first = { nickname(hints) }, retry = { retryNickname(hints, it) })

/** [nameSafely] と [nicknameSafely] の中身。[what] はログに出す呼び名 */
private suspend fun safely(
    what: String,
    maxLength: Int,
    timeoutMs: Long,
    first: suspend () -> String?,
    retry: suspend (String) -> String?,
): String? {
    val log = LoggerFactory.getLogger(PartNamer::class.java)
    val startedAt = System.nanoTime()
    fun elapsedMs() = (System.nanoTime() - startedAt) / 1_000_000

    val firstName = attempt(log, what, timeoutMs, ::elapsedMs) { first() } ?: return null
    if (!isTooLong(firstName, maxLength)) {
        log.info("${what}を考えるのに成功（${elapsedMs()} ms）")
        return firstName
    }
    log.info("${what}が長すぎる（${nameLength(firstName)} 文字「$firstName」、${elapsedMs()} ms）。1 回だけ聞き直す")
    val second = attempt(log, what, timeoutMs, ::elapsedMs) { retry(firstName) } ?: return null
    if (isTooLong(second, maxLength)) {
        log.warn("聞き直しても${what}が長すぎる（${nameLength(second)} 文字「$second」、${elapsedMs()} ms）。切り詰めずに、なしにする")
        return null
    }
    log.info("${what}を考えるのに成功（聞き直して ${elapsedMs()} ms）")
    return second
}

/** 1 回ぶんの呼び出し：期限・例外・掃除をまとめて、使える名前か null */
private suspend fun attempt(log: org.slf4j.Logger, what: String, timeoutMs: Long, elapsedMs: () -> Long, call: suspend () -> String?): String? {
    // call 自身も「名前なし」として null を返すので、時間切れの null と区別するために包む
    val outcome: NamingOutcome? = withTimeoutOrNull(timeoutMs) {
        try {
            NamingOutcome.Named(call())
        } catch (e: CancellationException) {
            throw e // 時間切れ・キャンセルは withTimeoutOrNull に任せる
        } catch (e: Exception) {
            log.warn("${what}を考えるのに失敗（${elapsedMs()} ms）: ${e.message}", e)
            NamingOutcome.Failed
        }
    }
    val raw = when (outcome) {
        null -> {
            log.warn("${what}を考えるのが時間切れ（上限 $timeoutMs ms、実測 ${elapsedMs()} ms）")
            return null
        }
        NamingOutcome.Failed -> return null
        is NamingOutcome.Named -> outcome.raw
    }
    val name = raw?.let(::cleanName)
    if (name == null) log.info("${what}なし（${elapsedMs()} ms。命名が無効か、空の応答）")
    return name
}

/** [attempt] の中の結果。時間切れ（withTimeoutOrNull の null）と「名前なし」を見分けるため */
private sealed interface NamingOutcome {
    data class Named(val raw: String?) : NamingOutcome
    data object Failed : NamingOutcome
}

/** 前後の空白・かぎ括弧・引用符を取り、最初の行だけにする。空なら null。長さはここでは見ない（[isTooLong]） */
internal fun cleanName(raw: String): String? {
    val trimmed = raw.lineSequence().firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.trim('「', '」', '『', '』', '"', '\'', '“', '”')
        ?.trim()
        ?: return null
    return trimmed.ifEmpty { null }
}

/** 名前の文字数。絵文字（サロゲートペア）も 1 文字と数える。アプリの RenameDialog の数え方と同じ */
internal fun nameLength(name: String): Int = name.codePointCount(0, name.length)

internal fun isTooLong(name: String, maxLength: Int = PartNamer.MAX_NAME_LENGTH): Boolean = nameLength(name) > maxLength
