package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.graphics.Color
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartColors
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.weather.Sky
import java.time.Instant
import java.time.ZoneId

/**
 * 歩いた時間帯（決定 74）。積み木（パーツ）の色の明るさを決める。
 * 境目は仮の案のまま：朝 5〜10 時・昼 10〜16 時・夕方 16〜19 時・夜 19〜5 時（端末の時刻帯で）
 */
enum class Daypart(val label: String) {
    MORNING("朝"),
    DAY("昼"),
    EVENING("夕方"),
    NIGHT("夜"),
    ;

    companion object {
        /** [hour]（0〜23）の時間帯 */
        fun ofHour(hour: Int): Daypart = when (hour) {
            in 5 until 10 -> MORNING
            in 10 until 16 -> DAY
            in 16 until 19 -> EVENING
            else -> NIGHT
        }

        /**
         * パーツを歩いた時間帯。決める時刻は、記録の真ん中の時刻（仮の案）：歩き始めた時刻（[startedAt]。`PartPlace.startedAt`）と
         * 歩き終えた時刻（[Part.walkedAt]）のまん中。歩き始めた時刻が無い古いパーツは歩き終えた時刻、
         * 歩いた時刻が分からないパーツ（0）は昼
         */
        fun of(part: Part, startedAt: Long?, zone: ZoneId = ZoneId.systemDefault()): Daypart {
            val end = part.walkedAt
            if (end <= 0L) return DAY
            val mid = if (startedAt != null && startedAt in 1..end) startedAt + (end - startedAt) / 2 else end
            return ofHour(Instant.ofEpochMilli(mid).atZone(zone).hour)
        }
    }
}

/** 色を HSL で持つ（[h] 色相 0〜360、[s] 彩度・[l] 明るさ 0〜1） */
data class Hsl(val h: Float, val s: Float, val l: Float) {
    fun toColor(): Color = Color.hsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/**
 * 積み木 1 つの色：面（[fill]）と、それを囲む線（[outline]）。
 * [ghostAlpha] は、狙っている間のうすい影の濃さ。ふつうは 0.45。雪はほぼ白で、うすくすると方眼紙の上で見えないので濃くする（[GHOST_ALPHA_SNOW]）
 */
data class PieceColors(val fill: Color, val outline: Color, val ghostAlpha: Float = GHOST_ALPHA)

/** 狙っている間のうすい影の濃さ（ふつう） */
const val GHOST_ALPHA = 0.45f

/** 雪の道の、狙っている間の影の濃さ。ほぼ白い面と明るい線なので、ふつうの濃さでは見えなかった（B の希望で濃くした） */
const val GHOST_ALPHA_SNOW = 0.85f

/**
 * 積み木（パーツ）の配色（決定 74）。**色相は天気、明るさは歩いた時間帯**で決める。コレクションの図・積む画面（通常・毎日 1 つずつ積む）の
 * 積み木に使う。全部の積み木に共通の黒い線は使わず、それぞれ自分の色を暗くした線で囲む。
 *
 * - 基準（昼）：晴れ HSL(28, 90%, 68%)＝#F7A964・くもり (255, 16%, 74%)＝#B7B2C7・雨 (214, 68%, 62%)＝#5C95E0・雪 (195, 45%, 95%)＝#EDF5F8
 * - 晴れ・くもり・雨は、色相と彩度はそのまま、明るさだけを時間帯で動かす：朝 +7・昼 ±0・夕方 −8・夜 −16
 * - 雪は、明るさを大きく下げるとくもりと見分けにくいので、明るさは小さく動かし、夜に向かって少し青みを強める
 *   （朝 (195, 40%, 97%)・昼 (195, 45%, 95%)・夕方 (198, 48%, 92%)・夜 (200, 50%, 88%)）
 * - 線：晴れ・くもり・雨は面の明るさから 16 下げ、彩度を 0.8 倍。雪は明るさを 20 下げ、彩度は 35%（明るい地でも形が読めるように）
 * - 天気は画面に出す 4 つ（`Sky.shown`。決定 73）で決める。天気が分からないパーツは晴れ
 */
object PiecePalette {
    /** 面の色（HSL） */
    fun fillHsl(sky: Sky?, daypart: Daypart): Hsl = when (sky?.shown) {
        Sky.SNOW -> when (daypart) {
            Daypart.MORNING -> Hsl(195f, 0.40f, 0.97f)
            Daypart.DAY -> Hsl(195f, 0.45f, 0.95f)
            Daypart.EVENING -> Hsl(198f, 0.48f, 0.92f)
            Daypart.NIGHT -> Hsl(200f, 0.50f, 0.88f)
        }
        Sky.RAIN -> Hsl(214f, 0.68f, 0.62f + shift(daypart))
        Sky.CLOUDY -> Hsl(255f, 0.16f, 0.74f + shift(daypart))
        else -> Hsl(28f, 0.90f, 0.68f + shift(daypart))
    }

    /** 線の色（HSL）。面の色を暗くしたもの */
    fun outlineHsl(sky: Sky?, daypart: Daypart): Hsl {
        val fill = fillHsl(sky, daypart)
        return if (sky?.shown == Sky.SNOW) fill.copy(s = 0.35f, l = fill.l - 0.20f) else fill.copy(s = fill.s * 0.8f, l = fill.l - 0.16f)
    }

    fun colors(sky: Sky?, daypart: Daypart): PieceColors = PieceColors(
        fillHsl(sky, daypart).toColor(),
        outlineHsl(sky, daypart).toColor(),
        ghostAlpha = if (sky?.shown == Sky.SNOW) GHOST_ALPHA_SNOW else GHOST_ALPHA,
    )

    /** 時間帯で動かす明るさ（晴れ・くもり・雨） */
    private fun shift(daypart: Daypart): Float = when (daypart) {
        Daypart.MORNING -> 0.07f
        Daypart.DAY -> 0f
        Daypart.EVENING -> -0.08f
        Daypart.NIGHT -> -0.16f
    }
}

/**
 * すべりやすさを、その元になった天気（画面に出す 4 つ。決定 73）に戻す。天気が分からなくなったパーツ（毎日 1 つずつ積むの塔に残っている、
 * コレクションから消したパーツ）の色と、天気のボーナスの札の色と名前に使う
 */
val Grip.shownSky: Sky
    get() = when (this) {
        Grip.NORMAL -> Sky.CLEAR
        Grip.CLOUDY -> Sky.CLOUDY
        Grip.WET -> Sky.RAIN
        Grip.ICY -> Sky.SNOW
    }

/** 積む画面の積み木の線の太さ（形の外側に出る幅）[dp] */
const val PIECE_OUTLINE_DP = 1.5f

/** 送る塔に入れる形（"#RRGGBB" の塗りと縁。決定 95）にする */
fun PieceColors.toPartColors(): PartColors = PartColors(fill.toHex(), outline.toHex())

/** 送られてきた塔の色（"#RRGGBB"）から、描くときの色にする。形の違う値なら null（形ごとの色で描く） */
fun PartColors.toPieceColors(): PieceColors? {
    val f = parseHex(fill) ?: return null
    val o = parseHex(outline) ?: return null
    return PieceColors(f, o)
}

private fun Color.toHex(): String {
    fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return String.format(java.util.Locale.US, "#%02X%02X%02X", ch(red), ch(green), ch(blue))
}

private fun parseHex(s: String): Color? =
    if (Regex("^#[0-9A-Fa-f]{6}$").matches(s)) Color(0xFF000000 or s.substring(1).toLong(16)) else null
