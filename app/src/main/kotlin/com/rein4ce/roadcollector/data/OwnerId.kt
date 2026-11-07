package com.rein4ce.roadcollector.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.rein4ce.roadcollector.BuildConfig
import java.security.MessageDigest
import java.util.UUID

/**
 * C 担当：このスマホの「持ち主の印」。タワーを送るときに入れ、一覧を取るときにヘッダで送る。
 * サーバはこれで「あなたが送ったタワーか」だけを答える（印そのものは誰にも返さない。core/model/Tower.kt）。
 *
 * 元にするのは端末の `ANDROID_ID`（Android 8 以降、同じ署名のアプリなら、アプリを消して入れ直しても同じ値）。
 * そのまま送らず、アプリだけの塩を付けて SHA-256 にする。サーバに届くのは 64 桁の 16 進で、元の id は分からない。
 * 端末を替えると値は変わる（ログインは無いので、そこまでは追わない）。
 *
 * `ANDROID_ID` が取れない端末では、1 回だけ作った乱数を settings に残して使う（アプリを消すと変わる）。
 */
object OwnerId {

    /** この端末の印（64 桁の 16 進） */
    fun of(context: Context): String {
        // ANDROID_ID は Android 8 以降、アプリの署名ごとに違う値（adb の settings get secure android_id とは一致しない）
        val androidId = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
        val seed = androidId?.takeIf { it.isNotBlank() } ?: fallbackSeed(context)
        val mark = hash(seed)
        // 手元の確認用（サーバに入れた印と見比べる）。デバッグ版だけ、しかも印そのものは出さず先頭の 8 桁だけ
        if (BuildConfig.DEBUG) Log.d("OwnerId", "owner=${mark.take(8)}…")
        return mark
    }

    /** 端末の id に塩を付けてハッシュする。同じ id なら同じ値。印は 64 桁の 16 進（サーバの OWNER_ID_PATTERN と同じ形） */
    internal fun hash(seed: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$SALT:$seed".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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
}
