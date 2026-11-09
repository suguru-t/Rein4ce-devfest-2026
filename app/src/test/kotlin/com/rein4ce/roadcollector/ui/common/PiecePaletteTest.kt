package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.graphics.toArgb
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.weather.Sky
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 積み木の配色（決定 74）：色相は天気、明るさは歩いた時間帯 */
class PiecePaletteTest {

    /** 色の 16 進（#RRGGBB）。HSL から作ると丸めで 1 ずれることがあるので、チャンネルごとに 1 まで許す */
    private fun assertHex(expected: String, hsl: Hsl) {
        val argb = hsl.toColor().toArgb()
        val e = expected.removePrefix("#").toInt(16)
        for (shift in listOf(16, 8, 0)) {
            val got = (argb shr shift) and 0xFF
            val want = (e shr shift) and 0xFF
            assertEquals("$expected の ${shift / 8 + 1} 番目の色（実際は ${"%06X".format(argb and 0xFFFFFF)}）", want.toDouble(), got.toDouble(), 1.0)
        }
    }

    @Test
    fun `the day colors are the agreed four`() {
        assertHex("#F7A964", PiecePalette.fillHsl(Sky.CLEAR, Daypart.DAY))
        assertHex("#B7B2C7", PiecePalette.fillHsl(Sky.CLOUDY, Daypart.DAY))
        assertHex("#5C95E0", PiecePalette.fillHsl(Sky.RAIN, Daypart.DAY))
        assertHex("#EDF5F8", PiecePalette.fillHsl(Sky.SNOW, Daypart.DAY))
    }

    @Test
    fun `sunny cloudy and rainy only change lightness through the day`() {
        for ((sky, base) in listOf(Sky.CLEAR to 0.68f, Sky.CLOUDY to 0.74f, Sky.RAIN to 0.62f)) {
            val day = PiecePalette.fillHsl(sky, Daypart.DAY)
            assertEquals(base, day.l, 1e-6f)
            assertEquals(base + 0.07f, PiecePalette.fillHsl(sky, Daypart.MORNING).l, 1e-6f)
            assertEquals(base - 0.08f, PiecePalette.fillHsl(sky, Daypart.EVENING).l, 1e-6f)
            assertEquals(base - 0.16f, PiecePalette.fillHsl(sky, Daypart.NIGHT).l, 1e-6f)
            for (part in Daypart.entries) {
                assertEquals(day.h, PiecePalette.fillHsl(sky, part).h, 0f)
                assertEquals(day.s, PiecePalette.fillHsl(sky, part).s, 0f)
            }
        }
    }

    @Test
    fun `snow stays light and turns a little bluer toward night`() {
        assertEquals(Hsl(195f, 0.40f, 0.97f), PiecePalette.fillHsl(Sky.SNOW, Daypart.MORNING))
        assertEquals(Hsl(195f, 0.45f, 0.95f), PiecePalette.fillHsl(Sky.SNOW, Daypart.DAY))
        assertEquals(Hsl(198f, 0.48f, 0.92f), PiecePalette.fillHsl(Sky.SNOW, Daypart.EVENING))
        assertEquals(Hsl(200f, 0.50f, 0.88f), PiecePalette.fillHsl(Sky.SNOW, Daypart.NIGHT))
    }

    @Test
    fun `each piece is outlined with a darker version of its own color`() {
        val rain = PiecePalette.fillHsl(Sky.RAIN, Daypart.EVENING)
        val rainLine = PiecePalette.outlineHsl(Sky.RAIN, Daypart.EVENING)
        assertEquals(rain.h, rainLine.h, 0f)
        assertEquals(rain.s * 0.8f, rainLine.s, 1e-6f)
        assertEquals(rain.l - 0.16f, rainLine.l, 1e-6f)
        // 雪は明るい地でも形が読めるように、もっと暗く、彩度は 35%
        val snow = PiecePalette.fillHsl(Sky.SNOW, Daypart.NIGHT)
        val snowLine = PiecePalette.outlineHsl(Sky.SNOW, Daypart.NIGHT)
        assertEquals(snow.h, snowLine.h, 0f)
        assertEquals(0.35f, snowLine.s, 1e-6f)
        assertEquals(snow.l - 0.20f, snowLine.l, 1e-6f)
    }

    @Test
    fun `folded weathers use the shown weather's colors and unknown weather is sunny`() {
        assertEquals(PiecePalette.colors(Sky.CLOUDY, Daypart.DAY), PiecePalette.colors(Sky.FOG, Daypart.DAY))
        assertEquals(PiecePalette.colors(Sky.RAIN, Daypart.NIGHT), PiecePalette.colors(Sky.DRIZZLE, Daypart.NIGHT))
        assertEquals(PiecePalette.colors(Sky.RAIN, Daypart.NIGHT), PiecePalette.colors(Sky.THUNDER, Daypart.NIGHT))
        assertEquals(PiecePalette.colors(Sky.CLEAR, Daypart.MORNING), PiecePalette.colors(null, Daypart.MORNING))
        // 毎日 1 つずつ積むの塔に残ったすべりやすさから戻す天気
        assertEquals(listOf(Sky.CLEAR, Sky.CLOUDY, Sky.RAIN, Sky.SNOW), Grip.entries.map { it.shownSky })
    }

    @Test
    fun `the aiming shadow of a snowy road is darker than the others`() {
        // 雪はほぼ白で、ふつうの濃さ（0.45）では方眼紙の上で見えないので、影を濃くする
        assertEquals(GHOST_ALPHA_SNOW, PiecePalette.colors(Sky.SNOW, Daypart.DAY).ghostAlpha, 0f)
        for (sky in listOf(Sky.CLEAR, Sky.CLOUDY, Sky.RAIN, null)) {
            assertEquals(GHOST_ALPHA, PiecePalette.colors(sky, Daypart.DAY).ghostAlpha, 0f)
        }
        assert(GHOST_ALPHA_SNOW > GHOST_ALPHA)
    }

    @Test
    fun `dayparts follow the tentative boundaries`() {
        val expected = mapOf(
            0 to Daypart.NIGHT, 4 to Daypart.NIGHT, 5 to Daypart.MORNING, 9 to Daypart.MORNING,
            10 to Daypart.DAY, 15 to Daypart.DAY, 16 to Daypart.EVENING, 18 to Daypart.EVENING,
            19 to Daypart.NIGHT, 23 to Daypart.NIGHT,
        )
        for ((hour, part) in expected) assertEquals("$hour 時", part, Daypart.ofHour(hour))
    }

    @Test
    fun `the daypart is taken at the middle of the walk`() {
        fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 10, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
        val part = Part("p", listOf(Pt(0f, 0f), Pt(1f, 0f)), walkedAt = at(11))
        // 8:00 に歩き始めて 11:00 に止めた：まん中の 9:30 は朝
        assertEquals(Daypart.MORNING, Daypart.of(part, startedAt = at(8), zone = ZoneOffset.UTC))
        // 歩き始めた時刻が無い古いパーツは、止めた時刻（11:00 は昼）
        assertEquals(Daypart.DAY, Daypart.of(part, startedAt = null, zone = ZoneOffset.UTC))
        // 歩き始めが止めた時刻よりあとの壊れた値は使わない
        assertEquals(Daypart.DAY, Daypart.of(part, startedAt = at(20), zone = ZoneOffset.UTC))
        // 18:30〜19:30 に歩いた：まん中の 19:00 は夜
        assertEquals(Daypart.NIGHT, Daypart.of(part.copy(walkedAt = at(19, 30)), startedAt = at(18, 30), zone = ZoneOffset.UTC))
        // 歩いた時刻が分からないパーツは昼
        assertEquals(Daypart.DAY, Daypart.of(part.copy(walkedAt = 0L), startedAt = null, zone = ZoneOffset.UTC))
    }
}
