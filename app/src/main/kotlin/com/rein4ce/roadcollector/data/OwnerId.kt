package com.rein4ce.roadcollector.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.rein4ce.roadcollector.BuildConfig
import java.security.MessageDigest
import java.util.UUID

/**
 * C 担当：このアプリの「持ち主の印」。タワーを送るときに入れ、一覧を取るときにヘッダで送る。
 * サーバはこれで「あなたが送ったタワーか」だけを答える（印そのものは誰にも返さない。core/model/Tower.kt）。
 *
 * 印は、はじめて開いたときに 1 回だけ決めて settings に残し、あとはそれを使う（決定 90）。
 * - 新しく入れたアプリは、乱数から作る。アプリを消すと一緒に消える（`allowBackup="false"`）ので、入れ直したら新しい印になる。
 *   前は端末の `ANDROID_ID` から作っていて、同じ端末に入れ直すと、まだ遊んでいないのに前に送ったタワーが「あなた」として出ていた
 * - 前の版でタワーを送ったことがあるアプリ（この端末から送った記録 `MyTowers` がある）は、前と同じ決め方
 *   （`ANDROID_ID`。取れない端末は、残しておいた乱数）で作り、送ったタワーの「あなた」を残す
 * どちらも、アプリだけの塩を付けて SHA-256 にする。サーバに届くのは 64 桁の 16 進で、元の値は分からない
 */
object OwnerId {

    /**
     * このアプリの印（64 桁の 16 進）。
     * @param sentBefore この端末から、前にタワーを送ったことがあるか（`MyTowers` に id がある）。印をまだ残していないときだけ見る
     */
    fun of(context: Context, sentBefore: Boolean): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MARK, null)
        val mark = decide(saved, sentBefore, legacy = { legacySeed(context) }, fresh = { UUID.randomUUID().toString() })
        // タワーを送る前に必ず残っているよう、すぐ書く（起動のときの 1 回だけ）
        if (saved != mark) prefs.edit().putString(KEY_MARK, mark).commit()
        // 手元の確認用（サーバに入れた印と見比べる）。デバッグ版だけ、しかも印そのものは出さず先頭の 8 桁だけ
        if (BuildConfig.DEBUG) Log.d("OwnerId", "owner=${mark.take(8)}…")
        return mark
    }

    /**
     * 印を決める。残してある印（形が正しいもの）があればそれ。無ければ、前に送ったことがあれば前の決め方 [legacy]、
     * 無ければ新しい乱数 [fresh] に塩を付けてハッシュする
     */
    internal fun decide(saved: String?, sentBefore: Boolean, legacy: () -> String, fresh: () -> String): String =
        saved?.takeIf { MARK_PATTERN.matches(it) } ?: hash(if (sentBefore) legacy() else fresh())

    /** 値に塩を付けてハッシュする。同じ値なら同じ印。印は 64 桁の 16 進（サーバの OWNER_ID_PATTERN と同じ形） */
    internal fun hash(seed: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$SALT:$seed".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 前の版の印の元（決定 32）：端末の `ANDROID_ID`（Android 8 以降、アプリの署名ごとに違う値）。
     * 取れない端末では、前に 1 回だけ作って settings に残した乱数（無ければ作る）
     */
    private fun legacySeed(context: Context): String {
        val androidId = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
        return androidId?.takeIf { it.isNotBlank() } ?: fallbackSeed(context)
    }

    private fun fallbackSeed(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.getString(KEY_FALLBACK, null)?.let { return it }
        val made = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_FALLBACK, made).apply()
        return made
    }

    /** アプリだけの塩。変えると全員の印が変わり、送信済みのタワーの「あなた」が外れるので変えない */
    private const val SALT = "michitsumi-owner-v1"
    private const val KEY_FALLBACK = "owner_seed"

    /** 決めた印を残す名前（決定 90） */
    private const val KEY_MARK = "owner_mark"

    private val MARK_PATTERN = Regex("^[0-9a-f]{64}$")
}
