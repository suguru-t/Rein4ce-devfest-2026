package com.rein4ce.roadcollector.core.weather

import com.rein4ce.roadcollector.core.model.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 空のようす。道の色と、背景の方眼紙に降らせるもの（雨・雪など）を決める。
 * 細かい天気（世界気象機関の天気コード）を、見た目が変わる単位にまとめたもの。
 */
enum class Sky(val label: String) {
    CLEAR("晴れ"),
    CLOUDY("くもり"),
    FOG("霧"),
    DRIZZLE("霧雨"),
    RAIN("雨"),
    SNOW("雪"),
    THUNDER("雷雨"),
    ;

    /** 雨のなかま（霧雨・雨・雷雨）か。道の色を青くする */
    val isRainy: Boolean get() = this == DRIZZLE || this == RAIN || this == THUNDER

    companion object {
        /**
         * 世界気象機関（WMO）の天気コードを空のようすにする。Open-Meteo が返す `weather_code` はこのコード。
         * 0 快晴・1 晴れ・2 一部くもり・3 くもり・45/48 霧・51〜57 霧雨・61〜67 雨・71〜77 雪・80〜82 にわか雨・85/86 にわか雪・95〜99 雷雨
         */
        fun of(wmoCode: Int): Sky = when (wmoCode) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDER
            else -> CLOUDY
        }
    }
}

/**
 * 歩いたときの天気。**スマホの中だけ**（parts_weather.json）に持つ（Part には入れない。共通のデータ形式を変えないため）。
 *
 * @property code 世界気象機関（WMO）の天気コード
 * @property precipitationMm その 1 時間の降水量 [mm]。雨の強さ（降らせる雨粒の数）に使う
 */
@Serializable
data class WalkWeather(val code: Int, val precipitationMm: Double = 0.0) {
    val sky: Sky get() = Sky.of(code)
}

/**
 * 天気を Open-Meteo（https://open-meteo.com 、キー不要・無料）から取るための URL 作りと、返事の読み取り。
 * 通信は app 側でする。ここは文字列だけを扱うので、Android なしでテストできる。
 *
 * **送る位置は 0.1 度（約 10 km）に丸める。** 天気はそれくらいの範囲なら同じで、歩いた正確な場所を外に出さないため。
 */
object OpenMeteo {
    private const val BASE = "https://api.open-meteo.com/v1/forecast"

    /** 過去の天気をさかのぼって取れる日数（Open-Meteo の天気予報 API は 92 日前まで） */
    const val MAX_PAST_DAYS = 90

    /** 送る前に位置を丸める（0.1 度単位） */
    fun round(deg: Double): Double = (deg * 10).roundToInt() / 10.0

    /** 今の天気を聞く URL */
    fun currentUrl(lat: Double, lng: Double): String =
        "$BASE?latitude=${round(lat)}&longitude=${round(lng)}&current=weather_code,precipitation&timezone=GMT"

    /** [timeMs] を含む 1 時間の天気を聞く URL（時刻は世界時でそろえる） */
    fun hourUrl(lat: Double, lng: Double, timeMs: Long): String {
        val hour = HOUR.format(Instant.ofEpochMilli(timeMs))
        return "$BASE?latitude=${round(lat)}&longitude=${round(lng)}&hourly=weather_code,precipitation" +
            "&start_hour=$hour&end_hour=$hour&timezone=GMT"
    }

    /** [currentUrl] の返事から、今の天気を読む。読めなければ null */
    fun parseCurrent(json: String): WalkWeather? = runCatching {
        val current = AppJson.parseToJsonElement(json).jsonObject["current"]?.jsonObject ?: return null
        weather(current["weather_code"]?.jsonPrimitive?.intOrNull, current["precipitation"]?.jsonPrimitive?.doubleOrNull)
    }.getOrNull()

    /** [hourUrl] の返事から、その 1 時間の天気を読む。読めなければ null */
    fun parseHour(json: String): WalkWeather? = runCatching {
        val hourly: JsonObject = AppJson.parseToJsonElement(json).jsonObject["hourly"]?.jsonObject ?: return null
        val code = hourly["weather_code"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull
        val mm = hourly["precipitation"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
        weather(code, mm)
    }.getOrNull()

    private fun weather(code: Int?, mm: Double?): WalkWeather? = code?.let { WalkWeather(it, mm ?: 0.0) }

    private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00").withZone(ZoneOffset.UTC)
}
