package com.rein4ce.roadcollector.ui.weather

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * 方眼紙の上に降らせる天気（天気アプリの背景のように）。画面の部品の下、方眼紙の上に敷く。
 * - 雨・雷雨：斜めに落ちる雨粒（奥のものほど細く・短く・遅く・うすい）と、紙に当たってできる小さな輪
 * - 霧雨：細かく短い雨粒
 * - 雪：揺れながらゆっくり落ちる白い粒
 * - 霧：紙の色のもやが横に流れて、方眼の線がところどころ薄れる。くもり：上のほうに雲の影がゆっくり流れる
 * - 晴れ・天気が分からないとき：何も描かない
 *
 * 「動きを減らす」設定（アニメーションをオフ）の人には、止まった絵を出す。雷の光る演出は、光の点滅が苦手な人がいるので入れていない。
 */
@Composable
fun WeatherBackdrop(weather: WalkWeather?, modifier: Modifier = Modifier) {
    val sky = weather?.sky ?: return
    if (sky == Sky.CLEAR) return
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val particles = remember(sky, weather.precipitationMm) { Particles(sky, weather.precipitationMm) }
    // 経過時間 [秒]。描くとき（draw）だけで読むので、毎コマ描き直しても画面の部品は作り直さない
    val time = remember { mutableFloatStateOf(0f) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
        }
    }
    Canvas(modifier) { particles.draw(this, time.floatValue) }
}

/** 雨粒・雪の粒などの、動かない値（位置の種・奥行き）。毎コマの位置は時刻から計算する */
private class Particles(private val sky: Sky, precipitationMm: Double) {
    private val count: Int = when (sky) {
        Sky.RAIN -> (90 + precipitationMm * 25).toInt().coerceIn(90, 200)
        Sky.THUNDER -> 200
        Sky.DRIZZLE -> 70
        Sky.SNOW -> (70 + precipitationMm * 20).toInt().coerceIn(70, 150)
        Sky.FOG -> 7
        Sky.CLOUDY -> 4
        Sky.CLEAR -> 0
    }
    private val random = Random(SEED)
    private val x0 = FloatArray(count) { random.nextFloat() }
    private val phase = FloatArray(count) { random.nextFloat() }
    private val depth = FloatArray(count) { random.nextFloat() }

    fun draw(scope: DrawScope, t: Float) = with(scope) {
        when (sky) {
            Sky.RAIN, Sky.THUNDER -> {
                if (sky == Sky.THUNDER) drawRect(Color(0x0F1B2633)) // 雷雨は紙を少し暗く
                rain(t, short = false)
                splashes(t)
            }
            Sky.DRIZZLE -> rain(t, short = true)
            Sky.SNOW -> snow(t)
            Sky.FOG -> fog(t)
            Sky.CLOUDY -> clouds(t)
            Sky.CLEAR -> Unit
        }
    }

    /** 斜めに落ちる雨粒。頭（下の端）が落ちていき、尾は右上に伸びる（風は右から） */
    private fun DrawScope.rain(t: Float, short: Boolean) {
        val w = size.width
        val h = size.height
        for (i in 0 until count) {
            val d = depth[i]
            val len = (if (short) lerp(5f, 11f, d) else lerp(11f, 26f, d)).dp.toPx()
            val speed = h * (if (short) lerp(0.3f, 0.55f, d) else lerp(0.6f, 1.15f, d)) // 1 秒に落ちる距離
            val travel = h + len
            val y = (phase[i] * travel + t * speed) % travel // 頭の高さ（0 が上端）
            val x = wrap(x0[i] * w - y * SLANT, w)
            drawLine(
                color = RainInk.copy(alpha = lerp(0.12f, if (short) 0.28f else 0.4f, d)),
                start = Offset(x, y),
                end = Offset(x + len * SLANT, y - len),
                strokeWidth = lerp(0.8f, 1.7f, d).dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    /** 紙に当たった雨粒の輪。一つずつ、広がりながら消え、別の場所にまた出る */
    private fun DrawScope.splashes(t: Float) {
        val w = size.width
        val h = size.height
        for (i in 0 until SPLASHES) {
            val period = 0.9f + 0.5f * hash(i, 0)
            val local = (t + period * hash(i, 1)) / period
            val cycle = floor(local).toInt()
            val u = local - cycle // 0 → 1 で広がって消える
            val center = Offset(hash(i, cycle * 2 + 2) * w, hash(i, cycle * 2 + 3) * h)
            val r = lerp(1f, 7f, u).dp.toPx()
            drawOval(
                color = RainInk.copy(alpha = 0.28f * (1f - u)),
                topLeft = Offset(center.x - r, center.y - r * 0.55f),
                size = androidx.compose.ui.geometry.Size(2 * r, 2 * r * 0.55f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }

    /** 揺れながらゆっくり落ちる雪。明るい紙の上でも見えるよう、白い粒に濃い水色のふちと、うすい影を付ける */
    private fun DrawScope.snow(t: Float) {
        val w = size.width
        val h = size.height
        for (i in 0 until count) {
            val d = depth[i]
            val r = lerp(2.0f, 4.4f, d).dp.toPx()
            val speed = h * lerp(0.04f, 0.11f, d)
            val travel = h + 2 * r
            val y = (phase[i] * travel + t * speed) % travel - r
            val sway = sin(t * lerp(0.5f, 1.0f, d) + phase[i] * 6.28f) * lerp(6f, 16f, d).dp.toPx()
            val x = wrap(x0[i] * w + sway, w)
            val alpha = lerp(0.55f, 1f, d)
            drawCircle(SnowShadow.copy(alpha = alpha * 0.18f), radius = r * 1.15f, center = Offset(x + r * 0.25f, y + r * 0.35f))
            drawCircle(Color.White.copy(alpha = alpha), radius = r, center = Offset(x, y))
            drawCircle(SnowEdge.copy(alpha = alpha * 0.9f), radius = r, center = Offset(x, y), style = Stroke(width = 1.1.dp.toPx()))
        }
    }

    /**
     * 霧：紙の色のもや。全体を少し紙の色でおおい、その上を濃いもやの塊が横に流れる。
     * 明るい紙の上では白いもやは見えないので、方眼の線を隠す（線がところどころ薄れて流れる）ことで霧に見せる
     */
    private fun DrawScope.fog(t: Float) {
        val w = size.width
        val h = size.height
        drawRect(FogPaper.copy(alpha = 0.3f))
        for (i in 0 until count) {
            val bandH = h * lerp(0.10f, 0.2f, depth[i])
            val cy = h * (0.05f + 0.9f * phase[i])
            val cx = wrap(x0[i] * w + t * w * lerp(0.012f, 0.03f, depth[i]), w + bandH * 6) - bandH * 3
            drawOval(
                brush = Brush.radialGradient(
                    listOf(FogPaper.copy(alpha = 0.9f), FogPaper.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = bandH * 2.6f,
                ),
                topLeft = Offset(cx - bandH * 3f, cy - bandH * 1.2f),
                size = androidx.compose.ui.geometry.Size(bandH * 6f, bandH * 2.4f),
            )
        }
    }

    /** 上のほうをゆっくり流れる雲の影 */
    private fun DrawScope.clouds(t: Float) {
        val w = size.width
        val h = size.height
        for (i in 0 until count) {
            val r = w * lerp(0.35f, 0.6f, depth[i])
            val cx = wrap(x0[i] * w + t * w * lerp(0.006f, 0.015f, depth[i]), w + 2 * r) - r
            val cy = h * lerp(0.0f, 0.18f, phase[i])
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CloudShade.copy(alpha = 0.10f), CloudShade.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }

    companion object {
        /** 同じ天気なら毎回同じ並び（開くたびに模様が変わらないように） */
        private const val SEED = 7

        /** 雨の傾き（横に進む量 ÷ 下に進む量。約 10 度） */
        private const val SLANT = 0.18f

        /** 同時に見える雨粒の輪の数 */
        private const val SPLASHES = 14

        /** 雨粒の色：紙の上で見える、青みのある墨 */
        private val RainInk = Color(0xFF4F6F96)

        /** 雪の粒のふちと影 */
        private val SnowEdge = Color(0xFF5F86A8)
        private val SnowShadow = Color(0xFF2F4A62)

        /** 雲の影 */
        private val CloudShade = Color(0xFF55606B)

        /** 霧のもや（方眼紙の地の色。線を隠す） */
        private val FogPaper = Fieldbook.Paper
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 0〜[w] に折り返す（画面の端から出た粒を、反対の端から出す） */
private fun wrap(x: Float, w: Float): Float = ((x % w) + w) % w

/** 番号から決まる 0〜1 の値（毎回同じ）。雨粒の輪を出す場所を、決まった順に変えていくのに使う */
private fun hash(i: Int, j: Int): Float {
    var h = i * 374_761_393 + j * 668_265_263
    h = (h xor (h ushr 13)) * 1_274_126_177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / Int.MAX_VALUE.toFloat()
}
