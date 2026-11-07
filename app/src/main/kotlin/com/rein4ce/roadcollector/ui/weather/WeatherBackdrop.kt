package com.rein4ce.roadcollector.ui.weather

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * 方眼紙の上に降らせる天気（天気アプリの背景のように）。画面の部品の下、方眼紙の上に敷く。
 * - 雨・雷雨：斜めに落ちる雨粒（奥のものほど細く・短く・遅く・うすい）と、紙に当たってできる小さな輪
 * - 霧雨：細かく短い雨粒
 * - 雪：揺れながらゆっくり落ちる白い粒
 * - 霧：紙の色のもやが横に流れて、方眼の線がところどころ薄れる。くもり：灰色がかった空を、雲がゆっくり横に流れる
 * - 晴れ：天気アプリの晴れの画面のように、上のほうにうすい空の色、白く飛んだ太陽とクリーム色のまぶしさ、
 *   細い光の筋、うすい虹色の輪と六角形のレンズのきらめき。スマホを傾けると太陽ときらめきが少し動く
 * - 天気が分からないとき：何も描かない
 *
 * 「動きを減らす」設定（アニメーションをオフ）の人には、止まった絵を出す。雷の光る演出は、光の点滅が苦手な人がいるので入れていない。
 *
 * 電池のため、描き直す回数を抑える：ゆっくり動く空（くもり・霧・晴れ）は 1 秒に 30 回、雨・雪は 60 回まで
 * （画面が 1 秒に 120 回描ける機種でも、それ以上は描かない）。背景だけを別の層にして、描き直すときに方眼紙や画面の部品は描き直さない。
 *
 * @param still true なら動かさない（その時点の絵のまま止め、傾きのセンサーも止める）。記録中に GPS と一緒に電池を使わないため。
 *   false に戻ると、止めたところから続けて動く
 */
@Composable
fun WeatherBackdrop(weather: WalkWeather?, modifier: Modifier = Modifier, still: Boolean = false) {
    val sky = weather?.sky ?: return
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val animate = !reduceMotion && !still
    val particles = remember(sky, weather.precipitationMm) { Particles(sky, weather.precipitationMm) }
    // 晴れのときだけ、スマホの傾きで太陽ときらめきを少し動かす（天気アプリと同じ）。止めているときは動かさない
    val tilt = rememberTilt(enabled = sky == Sky.CLEAR && animate)
    // 経過時間 [秒] と傾き。描くとき（draw）だけで読むので、描き直しても画面の部品は作り直さない。
    // 傾きはセンサーが変わるたびではなく、次に描き直すときに一緒に入れる（描き直しの回数を増やさない）
    val frame = remember { mutableStateOf(Frame(0f, Offset.Zero)) }
    if (animate) {
        val minNanos = if (sky.isSlow) SLOW_FRAME_NANOS else FAST_FRAME_NANOS
        LaunchedEffect(minNanos) {
            // 止めていた後なら、止めたときの時刻から続ける（絵が飛ばないように）
            val start = withFrameNanos { it } - (frame.value.time * 1e9f).toLong()
            var shown = start
            while (true) withFrameNanos { now ->
                if (now - shown >= minNanos - FRAME_SLACK_NANOS) {
                    frame.value = Frame((now - start) / 1e9f, tilt.value)
                    shown = now
                }
            }
        }
    }
    Canvas(modifier.graphicsLayer()) { frame.value.let { particles.draw(this, it.time, it.tilt) } }
}

/** 描き直す 1 回分の、経過時間 [秒] とスマホの傾き */
private class Frame(val time: Float, val tilt: Offset)

/** ゆっくり動く空（くもり・霧・晴れ）。1 秒に 30 回描けば十分なめらかに見える */
private val Sky.isSlow: Boolean get() = this == Sky.CLOUDY || this == Sky.FOG || this == Sky.CLEAR

/** 描き直しの間隔の下限：ゆっくりな空は 1/30 秒、雨・雪は 1/60 秒 */
private const val SLOW_FRAME_NANOS = 1_000_000_000L / 30
private const val FAST_FRAME_NANOS = 1_000_000_000L / 60

/** 画面の描き替えの時刻は少しずれるので、間隔をこれだけ短く見る（ちょうど 1/60 秒ごとの画面で 1 回飛ばしにならないように） */
private const val FRAME_SLACK_NANOS = 2_000_000L

/**
 * スマホの傾き（-1〜1 の x・y。まっすぐ持つと 0 付近）。重力センサーの値をなめらかにして入れておく。
 * 読んでも描き直しは起きない（描き直すときに読む）。使わないとき（[enabled] が false）はセンサーを聞かない（電池のため）
 */
@Composable
private fun rememberTilt(enabled: Boolean): Tilt {
    val context = LocalContext.current
    val tilt = remember { Tilt() }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // 画面の横（x）と縦（y）の向きの重力 ÷ 9.8。左右に傾けると x、前後に倒すと y が変わる
                val x = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val y = ((event.values[1] / SensorManager.GRAVITY_EARTH) - 0.7f).coerceIn(-1f, 1f)
                val old = tilt.value
                tilt.value = Offset(old.x + (x - old.x) * 0.12f, old.y + (y - old.y) * 0.12f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}

/** 傾きの入れ物。センサーも描き直しも画面のスレッドで動くので、ただの変数でよい */
private class Tilt {
    var value: Offset = Offset.Zero
}

/** 雨粒・雪の粒などの、動かない値（位置の種・奥行き）。毎コマの位置は時刻から計算する */
private class Particles(private val sky: Sky, precipitationMm: Double) {
    private val count: Int = when (sky) {
        Sky.RAIN -> (90 + precipitationMm * 25).toInt().coerceIn(90, 200)
        Sky.THUNDER -> 200
        Sky.DRIZZLE -> 70
        Sky.SNOW -> (70 + precipitationMm * 20).toInt().coerceIn(70, 150)
        Sky.FOG -> 7
        Sky.CLOUDY -> 7
        Sky.CLEAR -> 0
    }
    private val random = Random(SEED)
    private val x0 = FloatArray(count) { random.nextFloat() }
    private val phase = FloatArray(count) { random.nextFloat() }
    private val depth = FloatArray(count) { random.nextFloat() }

    /** 雲を描く順（奥から）。毎コマ並べ替えないように、最初に決めておく */
    private val backToFront: IntArray = (0 until count).sortedBy { depth[it] }.toIntArray()

    /** 光の筋・六角形の形を入れる入れ物（毎コマ作り直さないで使い回す） */
    private val path = Path()

    fun draw(scope: DrawScope, t: Float, tilt: Offset = Offset.Zero) = with(scope) {
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
            Sky.CLEAR -> sun(t, tilt)
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

    /**
     * 晴れ（天気アプリの晴れの画面に近づける）。
     * - 紙の上のほうに、うすく空の色を差す（白い太陽が、空を背にまぶしく見えるように）
     * - 太陽：まん中は白く飛び、外へクリーム色の光がなめらかに広がる（黄色く塗った円にはしない）
     * - 細くうすい光の筋がゆっくり回る
     * - レンズのきらめき：太陽から画面のまん中を通る線の上に、六角形のうすい光（色は少しずつ違う）
     * - スマホを傾けると、太陽は傾けた向きへ、きらめきは反対へ少し動く（[tilt]）。止めて持っていても、ゆっくり揺れる
     */
    private fun DrawScope.sun(t: Float, tilt: Offset) {
        val w = size.width
        val h = size.height
        val pulse = 0.95f + 0.05f * sin(t * 0.6f)
        // 空の色（上の 4 割）と、太陽の側だけ少し明るい空
        drawRect(
            brush = Brush.verticalGradient(
                0f to Sky1.copy(alpha = 0.75f),
                0.2f to Sky1.copy(alpha = 0.4f),
                0.45f to Sky1.copy(alpha = 0f),
            ),
        )
        val sway = Offset(sin(t * 0.09f) * 6.dp.toPx(), sin(t * 0.07f) * 3.dp.toPx())
        val sun = Offset(w * 0.82f, h * 0.05f) + Offset(tilt.x * 18.dp.toPx(), -tilt.y * 10.dp.toPx()) + sway
        fun bloom(radius: Float, color: Color, alpha: Float, center: Offset = sun) = drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.15f to color.copy(alpha = alpha * 0.8f),
                0.35f to color.copy(alpha = alpha * 0.42f),
                0.6f to color.copy(alpha = alpha * 0.15f),
                0.8f to color.copy(alpha = alpha * 0.04f),
                1f to color.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        bloom(w * 1.0f, SunWarm, 0.3f * pulse) // 空いっぱいに広がる暖かい光
        bloom(210.dp.toPx(), SunWarm, 0.55f * pulse) // 太陽のまわりの暖かいまぶしさ
        bloom(140.dp.toPx(), SunCream, 0.9f * pulse) // まぶしさ
        // 細くうすい光の筋（白っぽい）
        val spin = t * 1.5f
        val disc = 30.dp.toPx()
        for (i in 0 until RAY_COUNT) {
            val angle = Math.toRadians((i * 360.0 / RAY_COUNT) + spin + (hash(i, 40) - 0.5f) * 10f)
            val length = disc * lerp(4.5f, 8f, hash(i, 41)) * (0.9f + 0.1f * sin(t * 0.7f + i))
            val half = Math.toRadians(lerp(0.6f, 1.3f, hash(i, 42)).toDouble())
            val ray = path.apply {
                reset()
                moveTo(sun.x + cos(angle - half).toFloat() * disc * 0.5f, sun.y + sin(angle - half).toFloat() * disc * 0.5f)
                lineTo(sun.x + cos(angle).toFloat() * length, sun.y + sin(angle).toFloat() * length)
                lineTo(sun.x + cos(angle + half).toFloat() * disc * 0.5f, sun.y + sin(angle + half).toFloat() * disc * 0.5f)
                close()
            }
            drawPath(
                ray,
                brush = Brush.radialGradient(
                    0f to SunCream.copy(alpha = 0.7f),
                    0.6f to SunCream.copy(alpha = 0.25f),
                    1f to SunCream.copy(alpha = 0f),
                    center = sun,
                    radius = length,
                ),
            )
        }
        // 太陽：白く飛んだまん中から、縁を作らずに外へ溶ける（くっきりした丸にすると、貼ったように見える）
        val core = disc * 3f
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White,
                0.22f to Color.White,
                0.4f to Color.White.copy(alpha = 0.8f),
                0.6f to SunCream.copy(alpha = 0.4f),
                0.8f to SunCream.copy(alpha = 0.12f),
                1f to SunCream.copy(alpha = 0f),
                center = sun,
                radius = core,
            ),
            radius = core,
            center = sun,
        )
        // レンズのきらめき：太陽 → 画面のまん中（傾きと反対へずれる）→ その先
        val center = Offset(w / 2, h / 2) - Offset(tilt.x * 40.dp.toPx(), -tilt.y * 30.dp.toPx())
        val axis = center - sun
        // 虹色の輪：太陽から少し先に中心を置いた、うすい虹色の輪（内側が紫、外側が赤）。天気アプリの晴れのきらめき
        val ringCenter = sun + axis * 0.12f
        val ringR = 150.dp.toPx()
        val band = 13.dp.toPx()
        val outer = ringR + band
        fun at(r: Float) = (r / outer).coerceIn(0f, 1f)
        val ringAlpha = 0.075f * (0.85f + 0.15f * sin(t * 0.4f)) // ごくうすく（目立つと不自然）
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                at(ringR - band) to Color.Transparent,
                at(ringR - band * 0.65f) to Color(0xFF9B7BFF).copy(alpha = ringAlpha * 0.6f),
                at(ringR - band * 0.35f) to Color(0xFF6FA8FF).copy(alpha = ringAlpha),
                at(ringR - band * 0.05f) to Color(0xFF6FE0A8).copy(alpha = ringAlpha),
                at(ringR + band * 0.25f) to Color(0xFFFFE36A).copy(alpha = ringAlpha),
                at(ringR + band * 0.55f) to Color(0xFFFFA64D).copy(alpha = ringAlpha * 0.9f),
                at(ringR + band * 0.8f) to Color(0xFFFF6B6B).copy(alpha = ringAlpha * 0.6f),
                1f to Color.Transparent,
                center = ringCenter,
                radius = outer,
            ),
            radius = outer,
            center = ringCenter,
        )
        for ((k, f) in FLARE_SET.withIndex()) {
            val c = sun + axis * f.at
            val r = f.radiusDp.dp.toPx()
            val a = f.alpha * (0.85f + 0.15f * sin(t * 0.5f + k))
            if (f.hexagon) {
                val hex = path.apply {
                    reset()
                    for (n in 0 until 6) {
                        val ang = Math.toRadians(30.0 + n * 60.0)
                        val p = Offset(c.x + cos(ang).toFloat() * r, c.y + sin(ang).toFloat() * r)
                        if (n == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                drawPath(
                    hex,
                    brush = Brush.radialGradient(
                        0f to f.color.copy(alpha = a * 0.5f),
                        0.75f to f.color.copy(alpha = a),
                        1f to f.color.copy(alpha = a * 0.6f),
                        center = c,
                        radius = r,
                    ),
                )
            } else {
                bloom(r, f.color, a, center = c)
            }
        }
    }

    /**
     * くもり：上のほうを、雲がゆっくり横に流れる（左から右へ。奥の雲ほど小さく、遅く、うすい）。
     * 雲は、ふわっとした丸をいくつか重ねた塊（外へやわらかく溶ける）。下側に少し影を付けて、明るい紙の上でも形が分かるようにする。
     * 空は少し灰色がかった色にする
     */
    private fun DrawScope.clouds(t: Float) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                0f to Overcast.copy(alpha = 0.6f),
                0.25f to Overcast.copy(alpha = 0.3f),
                0.5f to Overcast.copy(alpha = 0f),
            ),
        )
        // 奥の雲から描く
        for (i in backToFront) {
            val d = depth[i]
            val scale = lerp(55f, 120f, d).dp.toPx() // 雲の大きさ
            val speed = lerp(4f, 14f, d).dp.toPx() // 1 秒に流れる距離
            val span = w + scale * 4
            val cx = (x0[i] * span + t * speed) % span - scale * 2
            val cy = h * lerp(0.02f, 0.3f, phase[i]) + sin(t * 0.05f + i) * 4.dp.toPx()
            val alpha = lerp(0.45f, 0.9f, d)
            // 塊をつくる丸（数と並びは雲ごとに決まっている）
            for (k in 0 until PUFFS) {
                val px = cx + (hash(i, 60 + k) - 0.5f) * scale * 2.4f
                val py = cy + (hash(i, 80 + k) - 0.6f) * scale * 0.6f
                val r = scale * lerp(0.45f, 0.85f, hash(i, 100 + k))
                // 下側の影
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(CloudShade.copy(alpha = alpha * 0.22f), CloudShade.copy(alpha = 0f)),
                        center = Offset(px, py + r * 0.25f),
                        radius = r * 1.1f,
                    ),
                    radius = r * 1.1f,
                    center = Offset(px, py + r * 0.25f),
                )
                // 雲の白（ふんわり外へ溶ける）
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to CloudWhite.copy(alpha = alpha),
                        0.55f to CloudWhite.copy(alpha = alpha * 0.85f),
                        1f to CloudWhite.copy(alpha = 0f),
                        center = Offset(px, py),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(px, py),
                )
            }
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

        /** 雲の下側の影、雲の白、くもりの空の色、雲 1 つをつくる丸の数 */
        private val CloudShade = Color(0xFF55606B)
        private val CloudWhite = Color(0xFFFBFCFD)
        private val Overcast = Color(0xFFB9C3CC)
        private const val PUFFS = 6

        /** 空の色、太陽のまわりのクリーム色のまぶしさ、空いっぱいの暖かい光 */
        private val Sky1 = Color(0xFFAED3F1)
        private val SunCream = Color(0xFFFFF4D6)
        private val SunWarm = Color(0xFFFFE3A3)

        /** 光の筋の数 */
        private const val RAY_COUNT = 10

        /**
         * レンズのきらめき。[at] は太陽から画面のまん中までを 1 としたときの位置。
         * [hexagon] なら六角形（カメラの絞りの形。天気アプリのきらめきに近い）、そうでなければぼんやりした丸
         */
        private class Flare(val at: Float, val radiusDp: Float, val color: Color, val alpha: Float, val hexagon: Boolean = false)

        private val FLARE_SET = listOf(
            Flare(0.2f, 10f, Color(0xFFFFF0C2), 0.45f),
            Flare(0.34f, 18f, Color(0xFFB8E3D6), 0.22f, hexagon = true),
            Flare(0.46f, 8f, Color(0xFFFFD9B0), 0.35f, hexagon = true),
            Flare(0.62f, 30f, Color(0xFFD6CCF5), 0.16f, hexagon = true),
            Flare(0.9f, 14f, Color(0xFFFFE7A8), 0.22f),
            Flare(1.25f, 52f, Color(0xFFB8E3D6), 0.1f, hexagon = true),
        )

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
