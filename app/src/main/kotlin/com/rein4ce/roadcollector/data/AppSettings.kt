package com.rein4ce.roadcollector.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 画面の明るさ（ライト・ダーク）の決め方 */
enum class ThemeMode(val label: String) {
    /** 端末の設定（ダークテーマ）に合わせる。初めはこれ */
    SYSTEM("端末に合わせる"),
    LIGHT("ライト"),
    DARK("ダーク"),
    ;

    /** この決め方で、今ダークにするか。[systemDark] は端末がダークテーマか */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 背景の天気（雨・雪・雲・太陽）の動き。なめらかさと電池の持ちのかね合い */
enum class WeatherMotion(val label: String, val note: String) {
    /** どの天気も 1 秒に 60 回描き直す */
    SMOOTH("なめらか", "どの天気も 1 秒に 60 回描き直します"),

    /** ゆっくりな空（くもり・霧・晴れ）は 30 回、雨・雪は 60 回。初めはこれ */
    NORMAL("ふつう", "雲や太陽は 1 秒に 30 回、雨と雪は 60 回描き直します"),

    /** 止まった絵にする（電池にやさしい） */
    STILL("止める", "止まった絵にします。電池にやさしい設定です"),

    /** 背景に天気を出さない（道の色は天気で変わる） */
    OFF("出さない", "背景には出しません。道の色は天気で変わります"),
}

/**
 * アプリの設定。スマホの中だけに保存する。採集画面の右上の歯車から変える。
 *
 * @param store 保存先。アプリでは SharedPreferences、テストではただの Map
 */
class AppSettings(private val store: Store) {

    /** 文字列を 1 つずつ保存する場所 */
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String)
    }

    /** アプリからはこちら（settings という名前の SharedPreferences） */
    constructor(context: Context) : this(object : Store {
        private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    })

    private val _themeMode = MutableStateFlow(readEnum(KEY_THEME, ThemeMode.SYSTEM))

    /** 画面の明るさ。変えると、すぐにアプリ全体の色が変わる */
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        store.put(KEY_THEME, mode.name)
        _themeMode.value = mode
    }

    private val _weatherMotion = MutableStateFlow(readEnum(KEY_WEATHER_MOTION, WeatherMotion.NORMAL))

    /** 背景の天気の動き */
    val weatherMotion: StateFlow<WeatherMotion> = _weatherMotion

    fun setWeatherMotion(motion: WeatherMotion) {
        store.put(KEY_WEATHER_MOTION, motion.name)
        _weatherMotion.value = motion
    }

    private val _keepScreenOn = MutableStateFlow(readBoolean(KEY_KEEP_SCREEN_ON, false))

    /** 記録中は画面を消さない（地図を見ながら歩くとき用）。初めはオフ（電池のため） */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    fun setKeepScreenOn(on: Boolean) {
        store.put(KEY_KEEP_SCREEN_ON, on.toString())
        _keepScreenOn.value = on
    }

    private val _aiNaming = MutableStateFlow(readBoolean(KEY_AI_NAMING, true))

    /**
     * AI（サーバの Gemini）にパーツの名前を考えてもらうか。初めはオン。
     * オフなら、採集の直後に名前を聞かず、コレクションの「AI に考えてもらう」も出さない（形・距離・日時をサーバに送らない）
     */
    val aiNaming: StateFlow<Boolean> = _aiNaming

    fun setAiNaming(on: Boolean) {
        store.put(KEY_AI_NAMING, on.toString())
        _aiNaming.value = on
    }

    private val _nickname = MutableStateFlow(store.get(KEY_NICKNAME)?.let(::cleanNickname).orEmpty())

    /** 「みんなのタワー」に作者名として出すニックネーム。空なら名無し */
    val nickname: StateFlow<String> = _nickname

    /** 前後の空白を落とし、[MAX_NICKNAME_LENGTH] 文字までにして保存する */
    fun setNickname(name: String) {
        val cleaned = cleanNickname(name)
        store.put(KEY_NICKNAME, cleaned)
        _nickname.value = cleaned
    }

    /** 保存した値が読めない（前の版の値など）ときは [fallback] */
    private inline fun <reified E : Enum<E>> readEnum(key: String, fallback: E): E =
        store.get(key)?.let { saved -> enumValues<E>().firstOrNull { it.name == saved } } ?: fallback

    private fun readBoolean(key: String, fallback: Boolean): Boolean = store.get(key)?.toBooleanStrictOrNull() ?: fallback

    companion object {
        /** ニックネームの長さの上限。「みんな」の一覧の 1 行に収まる長さ */
        const val MAX_NICKNAME_LENGTH = 12

        private const val KEY_THEME = "theme_mode"
        private const val KEY_WEATHER_MOTION = "weather_motion"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_AI_NAMING = "ai_naming"
        private const val KEY_NICKNAME = "nickname"

        /** 前後の空白・改行を落とし、上限の文字数で切る（絵文字を途中で切らないよう、コードポイント単位） */
        fun cleanNickname(name: String): String {
            val oneLine = name.replace(Regex("\\s+"), " ").trim()
            return if (oneLine.codePointCount(0, oneLine.length) <= MAX_NICKNAME_LENGTH) oneLine
            else oneLine.substring(0, oneLine.offsetByCodePoints(0, MAX_NICKNAME_LENGTH)).trim()
        }
    }
}
