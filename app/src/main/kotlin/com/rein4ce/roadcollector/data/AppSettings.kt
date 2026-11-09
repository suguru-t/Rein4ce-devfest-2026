package com.rein4ce.roadcollector.data

import android.content.Context
import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
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

/**
 * 背景の天気（雨・雪・雲・太陽）の動き。なめらかさと電池の持ちのかね合い。
 * 並びは動きの少ない順（設定のスライダーの左から）。保存は名前なので、並べ替えても前の設定は読める
 *
 * @property note 設定でこの段階を選んでいるときに出す説明
 */
enum class WeatherMotion(val label: String, val note: String) {
    /** 背景に天気を出さない（道の色は天気で変わる） */
    OFF("出さない", "背景に天気を出しません。"),

    /** 止まった絵にする（電池にやさしい） */
    STILL("止める", "止まった絵で出します。電池にやさしい設定です。"),

    /** ゆっくりな空（くもり・霧・晴れ）は 30 回、雨・雪は 60 回。初めはこれ */
    NORMAL("ふつう", "雲や太陽はゆっくり、雨や雪はなめらかに動かします。"),

    /** どの天気も 1 秒に 60 回描き直す */
    SMOOTH("なめらか", "どの天気もなめらかに動かします。電池を少し多く使います。"),
}

/**
 * 設定の「デモ」の欄（発表で使う）を出すか。**発表が終わって要らなくなったら、ここを false にするだけでよい。**
 * false にすると、設定に欄が出なくなり、保存してあるデモの天気も読まない（いつも本当の天気）。
 * 見本のパーツはそのまま残る（コレクションの「見本を戻す」でも戻せる）
 */
const val DEMO_SETTINGS = true

/**
 * 発表（デモ）で見せる天気。本当の天気の代わりに、記録・みんな・コレクションの一覧の背景（今の天気）と、デモ散歩でできるパーツの天気をこれにする。
 * 審査のときに、その日の天気に関係なく、雨・雪・夜の月などを見せるため。ふだんは本当の天気（[REAL]）。保存は名前
 *
 * @property weather 見せる天気。本当の天気なら null
 * @property fullMoon 晴れの夜の月を満月にする（本当の満ち欠けに関係なく）
 */
enum class DemoSky(val label: String, val weather: WalkWeather?, val fullMoon: Boolean = false) {
    REAL("本当の天気", null),
    CLEAR("晴れ", WalkWeather(code = 0)),
    CLEAR_NIGHT("晴れの夜（三日月）", WalkWeather(code = 0, isDay = false)),
    FULL_MOON("晴れの夜（満月）", WalkWeather(code = 0, isDay = false), fullMoon = true),
    CLOUDY("くもり", WalkWeather(code = 3)),
    FOG("霧", WalkWeather(code = 45)),
    DRIZZLE("霧雨", WalkWeather(code = 53, precipitationMm = 0.5)),
    RAIN("雨", WalkWeather(code = 63, precipitationMm = 3.0)),
    THUNDER("雷雨", WalkWeather(code = 95, precipitationMm = 5.0)),
    SNOW("雪", WalkWeather(code = 73, precipitationMm = 1.0)),
    ;

    companion object {
        /** 満月の夜の時刻（2024-04-23 23:49 UTC の満月）。[fullMoon] のとき、月の満ち欠けをこの時刻で決める */
        const val FULL_MOON_TIME_MS = 1_713_916_140_000L
    }
}

/**
 * アプリの設定。スマホの中だけに保存する。採集画面の右上の歯車から変える。
 *
 * @param store 保存先。アプリでは SharedPreferences、テストではただの Map
 * @param demoEnabled 設定の「デモ」の欄を使うか（[DEMO_SETTINGS]）。false なら、デモの天気はいつも本当の天気
 */
class AppSettings(private val store: Store, private val demoEnabled: Boolean = DEMO_SETTINGS) {

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

    private val _haptics = MutableStateFlow(readBoolean(KEY_HAPTICS, true))

    /**
     * 押したときなどに端末を軽く振動させるか。初めはオン。
     * アプリの振動は、すべて ui/common/Haptics.kt の rememberHaptics() を通して、ここがオフなら鳴らさない
     */
    val haptics: StateFlow<Boolean> = _haptics

    fun setHaptics(on: Boolean) {
        store.put(KEY_HAPTICS, on.toString())
        _haptics.value = on
    }

    private val _bgmOn = MutableStateFlow(readBoolean(KEY_BGM_ON, true))

    /** BGM を流すか。初めはオン。オフなら、音量（[bgmVolume]）に関係なく流さない（audio/BgmPlayer.kt） */
    val bgmOn: StateFlow<Boolean> = _bgmOn

    fun setBgmOn(on: Boolean) {
        store.put(KEY_BGM_ON, on.toString())
        _bgmOn.value = on
    }

    private val _bgmVolume = MutableStateFlow(
        store.get(KEY_BGM_VOLUME)?.toIntOrNull()?.coerceIn(1, MAX_BGM_VOLUME) ?: DEFAULT_BGM_VOLUME,
    )

    /** BGM の音量（1〜[MAX_BGM_VOLUME]）。端末のメディアの音量に、さらにこの割合を掛ける */
    val bgmVolume: StateFlow<Int> = _bgmVolume

    fun setBgmVolume(level: Int) {
        val v = level.coerceIn(1, MAX_BGM_VOLUME)
        store.put(KEY_BGM_VOLUME, v.toString())
        _bgmVolume.value = v
    }

    private val _sfxOn = MutableStateFlow(readBoolean(KEY_SFX_ON, true))

    /**
     * 効果音（ボタンを押したとき・題字や画面名の積み木を押したとき・道を 1 本集めたとき）を鳴らすか。初めはオン。
     * BGM とは別に切り替えられる。鳴らすかは鳴らす瞬間に読む（audio/SoundEffects.kt）
     */
    val sfxOn: StateFlow<Boolean> = _sfxOn

    fun setSfxOn(on: Boolean) {
        store.put(KEY_SFX_ON, on.toString())
        _sfxOn.value = on
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

    // デモを使わない（DEMO_SETTINGS が false）なら、前に選んだ天気が保存されていても読まない（設定の欄が無く、戻せないため）
    private val _demoSky = MutableStateFlow(if (demoEnabled) readEnum(KEY_DEMO_SKY, DemoSky.REAL) else DemoSky.REAL)

    /** 発表（デモ）で見せる天気。ふだんは本当の天気 */
    val demoSky: StateFlow<DemoSky> = _demoSky

    fun setDemoSky(sky: DemoSky) {
        if (!demoEnabled) return
        store.put(KEY_DEMO_SKY, sky.name)
        _demoSky.value = sky
    }

    private val _partFilter = MutableStateFlow(
        PartFilter(
            sort = readEnum(KEY_PART_SORT, PartSort.CUSTOM),
            sky = readEnumOrNull<Sky>(KEY_PART_SKY),
            favoritesOnly = readBoolean(KEY_PART_FAVORITES_ONLY, false),
        ),
    )

    /** コレクションと「パーツを選ぶ」の窓の並びと絞り込み（共通）。初めは、いつもの順で絞り込みなし */
    val partFilter: StateFlow<PartFilter> = _partFilter

    fun setPartFilter(filter: PartFilter) {
        store.put(KEY_PART_SORT, filter.sort.name)
        store.put(KEY_PART_SKY, filter.sky?.name.orEmpty())
        store.put(KEY_PART_FAVORITES_ONLY, filter.favoritesOnly.toString())
        _partFilter.value = filter
    }

    private val _howToSeen = MutableStateFlow(readBoolean(KEY_HOWTO_SEEN, false))

    /** 開いたときに出す「使い方」で「次回から表示しない」を選んだか（true なら出さない。設定の「使い方」ではいつでも見られる） */
    val howToSeen: StateFlow<Boolean> = _howToSeen

    fun setHowToSeen(seen: Boolean) {
        store.put(KEY_HOWTO_SEEN, seen.toString())
        _howToSeen.value = seen
    }

    /** 保存した値が読めない（前の版の値など）ときは [fallback] */
    private inline fun <reified E : Enum<E>> readEnum(key: String, fallback: E): E = readEnumOrNull<E>(key) ?: fallback

    /** 保存した列挙の値。保存していない・知らない名前なら null（「すべて」のように、無いことに意味がある値に使う） */
    private inline fun <reified E : Enum<E>> readEnumOrNull(key: String): E? =
        store.get(key)?.let { saved -> enumValues<E>().firstOrNull { it.name == saved } }

    private fun readBoolean(key: String, fallback: Boolean): Boolean = store.get(key)?.toBooleanStrictOrNull() ?: fallback

    companion object {
        /** ニックネームの長さの上限。「みんな」の一覧の 1 行に収まる長さ */
        const val MAX_NICKNAME_LENGTH = NicknameHints.MAX_NICKNAME_LENGTH

        /** BGM の音量の段階の数と、初めの音量（まん中） */
        const val MAX_BGM_VOLUME = 10
        const val DEFAULT_BGM_VOLUME = 5

        private const val KEY_THEME = "theme_mode"
        private const val KEY_WEATHER_MOTION = "weather_motion"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_AI_NAMING = "ai_naming"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_BGM_ON = "bgm_on"
        private const val KEY_BGM_VOLUME = "bgm_volume"
        private const val KEY_SFX_ON = "sfx_on"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_HOWTO_SEEN = "howto_seen"
        private const val KEY_DEMO_SKY = "demo_sky"
        private const val KEY_PART_SORT = "part_sort"
        private const val KEY_PART_SKY = "part_sky"
        private const val KEY_PART_FAVORITES_ONLY = "part_favorites_only"

        /** 前後の空白・改行を落とし、上限の文字数で切る（絵文字を途中で切らないよう、コードポイント単位） */
        fun cleanNickname(name: String): String {
            val oneLine = name.replace(Regex("\\s+"), " ").trim()
            return if (oneLine.codePointCount(0, oneLine.length) <= MAX_NICKNAME_LENGTH) oneLine
            else oneLine.substring(0, oneLine.offsetByCodePoints(0, MAX_NICKNAME_LENGTH)).trim()
        }
    }
}
