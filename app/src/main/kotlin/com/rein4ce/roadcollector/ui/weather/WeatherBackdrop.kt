package com.rein4ce.roadcollector.ui.weather

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.weather.Moon
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
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
 * - 晴れの夜（[WalkWeather.isDay] が false）：太陽の代わりに、夜の空の色と月、ゆっくりまたたく星。月はふだん三日月で、満月の夜だけ満月（[Moon]）。
 *   月も傾けると少し動く。ほかの天気の夜は、雲で月は見えないので、空の色だけ夜にする
 * - 天気が分からないとき：何も描かない
 *
 * ダークモードでは、雨粒を明るい青に、空・雲の色を夜の色にして、画面いっぱいに広がる光をおさえる（[Fieldbook.dark]）。
 *
 * 「動きを減らす」設定（アニメーションをオフ）の人には、止まった絵を出す。雷の光る演出は、光の点滅が苦手な人がいるので入れていない。
 *
 * 電池のため、描き直す回数を抑える：ゆっくり動く空（くもり・霧・晴れ）は 1 秒に 30 回、雨・雪は 60 回まで
 * （画面が 1 秒に 120 回描ける機種でも、それ以上は描かない）。背景だけを別の層にして、描き直すときに方眼紙や画面の部品は描き直さない。
 *
 * @param smooth true なら、ゆっくりな空も 1 秒に 60 回まで描き直す（設定の「なめらか」）
 * @param moonTimeMs 晴れの夜に、満月の夜かを決める時刻（記録は今、コレクションは歩いた時刻）
 * @param still true なら動かさない（その時点の絵のまま止め、傾きのセンサーも止める）。記録中に GPS と一緒に電池を使わないため。
 *   false に戻ると、止めたところから続けて動く
 */
@Composable
fun WeatherBackdrop(
    weather: WalkWeather?,
    modifier: Modifier = Modifier,
    still: Boolean = false,
    smooth: Boolean = false,
    moonTimeMs: Long = System.currentTimeMillis(),
) {
    val sky = weather?.sky ?: return
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val animate = !reduceMotion && !still
    // 月の形は日によって変わるだけなので、時刻は日の単位で見る（毎回作り直さない）
    val moonPhase = remember(moonTimeMs / DAY_MS) { Moon.phase(moonTimeMs) }
    val particles = remember(sky, weather.precipitationMm, weather.isDay, moonPhase) {
        Particles(sky, weather.precipitationMm, weather.isDay, moonPhase)
    }
    // 晴れのときだけ、スマホの傾きで太陽ときらめき（夜は月）を少し動かす（天気アプリと同じ）。止めているときは動かさない
    val tilt = rememberTilt(enabled = sky == Sky.CLEAR && animate)
    // 経過時間 [秒] と傾き。描くとき（draw）だけで読むので、描き直しても画面の部品は作り直さない。
    // 傾きはセンサーが変わるたびではなく、次に描き直すときに一緒に入れる（描き直しの回数を増やさない）
    val frame = remember { mutableStateOf(Frame(0f, Offset.Zero)) }
    if (animate) {
        // ゆっくりな空は 1 秒に 30 回まで。設定で「なめらか」なら、どの天気も 60 回まで
        val minNanos = if (sky.isSlow && !smooth) SLOW_FRAME_NANOS else FAST_FRAME_NANOS
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

/** 1 日 [ミリ秒]（月の形を決め直す単位） */
private const val DAY_MS = 86_400_000L

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
private class Particles(
    private val sky: Sky,
    precipitationMm: Double,
    /** 昼か。夜の晴れは月と星、夜のほかの天気は空の色だけ夜にする */
    private val isDay: Boolean = true,
    /** 月の位相（0 新月・0.5 満月。[Moon.phase]） */
    private val moonPhase: Double = 0.5,
) {
    private val count: Int = when (sky) {
        Sky.RAIN -> (90 + precipitationMm * 25).toInt().coerceIn(90, 200)
        Sky.THUNDER -> 200
        Sky.DRIZZLE -> 70
        Sky.SNOW -> (70 + precipitationMm * 20).toInt().coerceIn(70, 150)
        Sky.FOG -> 7
        Sky.CLOUDY -> 7
        Sky.CLEAR -> if (isDay) 0 else STARS
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
        // 夜は、晴れなら月と星。ほかの天気は雲で月が見えないので、上のほうの空の色だけ夜にしてから、雨・雪などを描く
        if (!isDay) {
            if (sky == Sky.CLEAR) {
                moon(t, tilt)
                return@with
            }
            nightTint(strong = false)
        }
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

    /** 夜の空の色（上ほど濃く、画面のなかほどで消える）。[strong] は晴れの夜（月と星の地）。ほかの天気は、雲の下なのでうすく */
    private fun DrawScope.nightTint(strong: Boolean) {
        val a = if (strong) 1f else 0.6f
        drawRect(
            brush = Brush.verticalGradient(
                0f to NightSky.copy(alpha = 0.85f * a),
                0.25f to NightSky.copy(alpha = 0.5f * a),
                0.5f to NightSky.copy(alpha = 0f),
            ),
        )
    }

    /**
     * 晴れの夜：夜の空の色、ゆっくりまたたく星、上の右寄りに月とまわりの光。
     * 月はふだん三日月で、本当の満月の夜（前後 1 日）だけ満月にする。スマホを傾けると、太陽と同じく月が少し動く。
     *
     * 太陽（白く飛んだまん中と、ふちを作らないまぶしさ）に合わせて、月も写真に近い見た目にする：
     * 海（暗い模様）とクレーターのある面、三日月の欠けた側はうっすら見える（地球の照り返し）、光と影の境はぼかす。
     * 月の面は細かいので、最初に 1 枚の絵に描いておき、毎コマはその絵を置くだけにする
     */
    private fun DrawScope.moon(t: Float, tilt: Offset) {
        val w = size.width
        val h = size.height
        nightTint(strong = true)
        val sway = Offset(sin(t * 0.09f) * 6.dp.toPx(), sin(t * 0.07f) * 3.dp.toPx())
        // 太陽の所（右上）だと、上の電池などの表示や、記録画面の「0 m」・歯車にかかる（ダークの満月だと字が読めない）。
        // そのため、上の時計と電池のあいだの空いている所（カメラの穴より右）に置き、下の見出しの行にかからない高さにする
        val c = Offset(w * 0.64f, 46.dp.toPx()) + Offset(tilt.x * 18.dp.toPx(), -tilt.y * 10.dp.toPx()) + sway
        val r = 32.dp.toPx()
        // 星：上の 4 割に散らし、上ほど多く・明るく。1 つずつ違う速さでゆっくりまたたく。
        // 月の後ろの星は描かない（三日月の欠けた側はうすく透けるので、星が見えてしまう）
        val hidden = r * 1.08f
        for (i in 0 until count) {
            val y = h * 0.42f * phase[i] * phase[i]
            val x = w * x0[i]
            if ((Offset(x, y) - c).getDistanceSquared() < hidden * hidden) continue
            val twinkle = 0.55f + 0.45f * sin(t * lerp(0.4f, 1.2f, depth[i]) + i * 1.7f)
            val fade = 1f - (y / (h * 0.42f))
            drawCircle(StarLight.copy(alpha = (0.35f + 0.6f * depth[i]) * twinkle * fade), radius = lerp(0.8f, 1.8f, depth[i]).dp.toPx(), center = Offset(x, y))
        }
        val full = Moon.isFull(moonPhase)
        // まわりの光：空に広がるうすい光と、月のすぐまわりのにじみ（満月ほど明るい）
        val halo = 150.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                0f to MoonHalo.copy(alpha = (if (full) 0.3f else 0.2f) * MoonGlow),
                0.25f to MoonHalo.copy(alpha = (if (full) 0.14f else 0.09f) * MoonGlow),
                0.6f to MoonHalo.copy(alpha = 0.04f * MoonGlow),
                1f to MoonHalo.copy(alpha = 0f),
                center = c,
                radius = halo,
            ),
            radius = halo,
            center = c,
        )
        // 三日月は、光っている側だけがにじむ（欠けた側のまわりは光らない）
        val tiltRad = CRESCENT_TILT * Math.PI.toFloat() / 180f
        val glowAt = if (full) c else c + Offset(cos(tiltRad), sin(tiltRad)) * (r * 0.55f)
        val bleed = if (full) r * 1.6f else r * 1.25f
        drawCircle(
            brush = Brush.radialGradient(
                0f to MoonHalo.copy(alpha = 0f),
                (if (full) 0.55f else 0.45f) to MoonHalo.copy(alpha = (if (full) 0.45f else 0.3f) * MoonGlow),
                (if (full) 0.68f else 0.65f) to MoonHalo.copy(alpha = (if (full) 0.2f else 0.12f) * MoonGlow),
                1f to MoonHalo.copy(alpha = 0f),
                center = glowAt,
                radius = bleed,
            ),
            radius = bleed,
            center = glowAt,
        )
        val image = moonImage(r, full)
        drawImage(image, topLeft = Offset(c.x - image.width / 2f, c.y - image.height / 2f))
    }

    /** 月の絵（大きさか形が変わったときだけ描き直す） */
    private val moonCache = CachedImage()

    private fun DrawScope.moonImage(r: Float, full: Boolean): ImageBitmap {
        val side = (r * 2f).toInt() + 4
        return moonCache.get(this, key = side to full, sidePx = side) { moonFace(r, full) }
    }

    /**
     * 月の面を、この絵のまん中に半径 [r] で描く。
     * 面（明るい地・海・クレーター・ふちのかげり）を 1 枚に描いてから、見える所の形（満月は丸、三日月は光の当たる所＋うっすらの欠けた側）で切り抜く
     */
    private fun DrawScope.moonFace(r: Float, full: Boolean) {
        val c = center
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            // 明るい地（高地）
            drawCircle(MoonSurface, radius = r, center = c)
            // 海：ふちのぼけた暗い模様を重ねて、見慣れた月の模様（うさぎ）にする
            for (m in MARIA) {
                val at = c + Offset(m.x * r, m.y * r)
                val spread = m.size * r
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to MoonMare.copy(alpha = m.alpha),
                        0.4f to MoonMare.copy(alpha = m.alpha * 0.85f),
                        0.75f to MoonMare.copy(alpha = m.alpha * 0.4f),
                        1f to MoonMare.copy(alpha = 0f),
                        center = at,
                        radius = spread,
                    ),
                    radius = spread,
                    center = at,
                )
            }
            // 細かいクレーター：うすい小さな点を散らし、面のざらつきにする（ふちを描くと泡のように見えるので描かない）。毎回同じ並び
            val random = Random(MOON_SEED)
            repeat(CRATER_COUNT) {
                val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                val dist = kotlin.math.sqrt(random.nextFloat()) * 0.92f
                val at = c + Offset(cos(angle) * dist * r, sin(angle) * dist * r)
                val dot = lerp(0.015f, 0.045f, random.nextFloat() * random.nextFloat()) * r
                drawCircle(MoonCrater.copy(alpha = lerp(0.06f, 0.14f, random.nextFloat())), radius = dot, center = at)
            }
            // 明るいクレーター（ティコ・コペルニクス）と、ティコから伸びるうすい筋
            val tycho = c + Offset(-0.12f * r, 0.72f * r)
            for (i in 0 until 9) {
                val angle = (i * 40f + 12f) * Math.PI.toFloat() / 180f
                val length = r * lerp(0.35f, 0.75f, hash(i, 77))
                drawLine(
                    MoonSurface.copy(alpha = 0.22f),
                    start = tycho,
                    end = tycho + Offset(cos(angle) * length, sin(angle) * length),
                    strokeWidth = r * 0.03f,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.045f, center = tycho)
            drawCircle(Color.White.copy(alpha = 0.6f), radius = r * 0.035f, center = c + Offset(-0.33f * r, -0.12f * r))
            // ふちのかげり（丸い玉に見えるように、ふちだけ少し暗く）
            drawCircle(
                brush = Brush.radialGradient(
                    0f to MoonLimb.copy(alpha = 0f),
                    0.75f to MoonLimb.copy(alpha = 0f),
                    1f to MoonLimb.copy(alpha = 0.22f),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            // 見える所の形で切り抜く（この層の濃さを、下の面に掛ける）
            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { blendMode = BlendMode.DstIn })
            if (full) {
                drawCircle(Color.Black, radius = r, center = c)
            } else {
                // 欠けた側：地球の照り返しで、うっすら見える
                drawCircle(Color.Black.copy(alpha = EARTHSHINE), radius = r, center = c)
                // 光の当たる所：光と影の境を少しずつ広げて重ね、ぼかす。夕方の三日月のように、光る側を右下に向ける
                rotate(degrees = CRESCENT_TILT, pivot = c) {
                    // 広い形から順に重ね、重なった濃さが境から光の側へ同じ割合で濃くなるようにする
                    var covered = 0f
                    for (step in FEATHER_STEPS downTo 0) {
                        val target = 1f - step / (FEATHER_STEPS + 1f)
                        val alpha = 1f - (1f - target) / (1f - covered)
                        covered = target
                        litPart(path, c, r, CRESCENT_PHASE + step * FEATHER_PHASE)
                        drawPath(path, Color.Black.copy(alpha = alpha))
                    }
                }
            }
            canvas.restore()
            canvas.restore()
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

    /**
     * 揺れながらゆっくり落ちる雪。ふちのぼけた白い粒に、うすい青灰色のにじみを付ける（明るい紙の上でも見えるように）。
     * 前は白い丸に濃い水色のふちを描いていたが、明るい紙の上では輪っか（泡や水玉）に見えたので、ふちの線はやめた。
     * 粒の絵は最初に 1 枚だけ作り（[flakeImage]）、毎コマはその絵を大きさを変えて置くだけにする
     */
    private fun DrawScope.snow(t: Float) {
        val w = size.width
        val h = size.height
        val image = flakeImage()
        for (i in 0 until count) {
            val d = depth[i]
            val r = lerp(2.0f, 4.4f, d).dp.toPx()
            val speed = h * lerp(0.04f, 0.11f, d)
            // 絵は、白い芯（半径 r）のまわりににじみがあるので、芯の FLAKE_SPREAD 倍の大きさで置く。
            // 上下で折り返す範囲も、にじみの端まで含める（芯の大きさだと、にじみの端が縁で一瞬出たり消えたりする）
            val half = r * FLAKE_SPREAD
            val travel = h + 2 * half
            val y = (phase[i] * travel + t * speed) % travel - half
            val sway = sin(t * lerp(0.5f, 1.0f, d) + phase[i] * 6.28f) * lerp(6f, 16f, d).dp.toPx()
            val x = wrap(x0[i] * w + sway, w)
            val alpha = lerp(0.55f, 1f, d)
            // 小数の位置のまま置く（整数の px に丸めると、ゆっくりした横揺れが 1 px ずつの段になり、低い密度の端末ではカクつく）
            val zoom = half * 2 / image.width
            withTransform({
                translate(x - half, y - half)
                scale(zoom, zoom, pivot = Offset.Zero)
            }) {
                drawImage(image, alpha = alpha)
            }
        }
    }

    /** 雪の粒の絵。ライトとダークでにじみの濃さを変えるので、切り替わったら描き直す */
    private val flakeCache = CachedImage()

    private fun DrawScope.flakeImage(): ImageBitmap {
        val dark = Fieldbook.dark
        val side = FLAKE_IMAGE_PX
        return flakeCache.get(this, key = dark, sidePx = side) {
            val c = center
            val outer = side / 2f
            // まわりのうすい青灰色のにじみ（明るい紙の上で、白い粒の形が見えるように。暗い地では白がよく見えるので弱く）
            val shade = if (dark) 0.12f else 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to SnowShade.copy(alpha = shade),
                    0.55f to SnowShade.copy(alpha = shade * 0.6f),
                    1f to SnowShade.copy(alpha = 0f),
                    center = c,
                    radius = outer,
                ),
                radius = outer,
                center = c,
            )
            // 白い芯。ふちはぼかす（くっきりした丸だと、貼った丸や泡に見える）
            val core = outer / FLAKE_SPREAD
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White,
                    0.6f to Color.White,
                    1f to Color.White.copy(alpha = 0f),
                    center = c,
                    radius = core * 1.15f,
                ),
                radius = core * 1.15f,
                center = c,
            )
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
        // ダークモードでは、太陽の光を全体に弱める。右上の歩いた長さと歯車は明るい字なので、白く飛んだ光の上では読めなくなるため
        // （暗い紙が白っぽくかすまないようにもなる）
        val glow = if (Fieldbook.dark) 0.4f else 1f
        bloom(w * 1.0f, SunWarm, 0.3f * pulse * glow) // 空いっぱいに広がる暖かい光
        bloom(210.dp.toPx(), SunWarm, 0.55f * pulse * glow) // 太陽のまわりの暖かいまぶしさ
        bloom(140.dp.toPx(), SunCream, 0.9f * pulse * glow) // まぶしさ
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
                    0f to SunCream.copy(alpha = 0.7f * glow),
                    0.6f to SunCream.copy(alpha = 0.25f * glow),
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
                0f to Color.White.copy(alpha = glow),
                0.22f to Color.White.copy(alpha = glow),
                0.4f to Color.White.copy(alpha = 0.8f * glow),
                0.6f to SunCream.copy(alpha = 0.4f * glow),
                0.8f to SunCream.copy(alpha = 0.12f * glow),
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
        // 夜は雲も空も暗くする（夜の空に白い雲が浮かないように）
        val overcast = if (isDay) Overcast else NightOvercast
        val cloudWhite = if (isDay) CloudWhite else NightCloud
        val cloudShade = if (isDay) CloudShade else NightSky
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                0f to overcast.copy(alpha = 0.6f),
                0.25f to overcast.copy(alpha = 0.3f),
                0.5f to overcast.copy(alpha = 0f),
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
                        listOf(cloudShade.copy(alpha = alpha * 0.22f), cloudShade.copy(alpha = 0f)),
                        center = Offset(px, py + r * 0.25f),
                        radius = r * 1.1f,
                    ),
                    radius = r * 1.1f,
                    center = Offset(px, py + r * 0.25f),
                )
                // 雲の白（ふんわり外へ溶ける）
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to cloudWhite.copy(alpha = alpha),
                        0.55f to cloudWhite.copy(alpha = alpha * 0.85f),
                        1f to cloudWhite.copy(alpha = 0f),
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
        private val RainInk: Color get() = if (Fieldbook.dark) Color(0xFF8FB4E0) else Color(0xFF4F6F96)

        /** 雪の粒のまわりのにじみの色（青灰色） */
        private val SnowShade = Color(0xFF5F86A8)

        /** 雪の粒の絵：白い芯の何倍の大きさまでにじみを描くか、絵の一辺 [px] */
        private const val FLAKE_SPREAD = 1.9f
        private const val FLAKE_IMAGE_PX = 64

        /** 雲の下側の影、雲の白、くもりの空の色、雲 1 つをつくる丸の数 */
        private val CloudShade: Color get() = if (Fieldbook.dark) Color(0xFF0B0F12) else Color(0xFF55606B)
        private val CloudWhite: Color get() = if (Fieldbook.dark) Color(0xFF9AA5B0) else Color(0xFFFBFCFD)
        private val Overcast: Color get() = if (Fieldbook.dark) Color(0xFF34404B) else Color(0xFFB9C3CC)
        private const val PUFFS = 6

        /** 空の色、太陽のまわりのクリーム色のまぶしさ、空いっぱいの暖かい光 */
        private val Sky1: Color get() = if (Fieldbook.dark) Color(0xFF203854) else Color(0xFFAED3F1)
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

        /** 晴れの夜の星の数 */
        private const val STARS = 46

        /** 夜の空の色（上のほうに敷く）。ライトでも星と月が見えるよう、くすんだ紺 */
        private val NightSky: Color get() = if (Fieldbook.dark) Color(0xFF0A1424) else Color(0xFF55678C)

        /** 夜のくもりの空と雲の色 */
        private val NightOvercast: Color get() = if (Fieldbook.dark) Color(0xFF1C2635) else Color(0xFF6E7C96)
        private val NightCloud: Color get() = if (Fieldbook.dark) Color(0xFF55606F) else Color(0xFFB7C0CE)

        /** 星の色と、月のまわりの光の強さ（ダークでは右上の明るい字が読めるよう弱める） */
        private val StarLight = Color(0xFFF2F5FF)
        private val MoonGlow: Float get() = if (Fieldbook.dark) 0.7f else 1f

        /** 月の色：明るい地（少し暖かい白）、海（青みの灰色）、クレーター、ふちのかげり、まわりの光（少し青い白） */
        private val MoonSurface = Color(0xFFF1EEE6)
        private val MoonMare = Color(0xFF8A8C93)
        private val MoonCrater = Color(0xFF9C9A92)
        private val MoonLimb = Color(0xFF6F6E6A)
        private val MoonHalo = Color(0xFFE6ECF5)

        /** 月の海：月の半径を 1 とした位置（右が +x、下が +y）・大きさ・濃さ。北を上にした、日本から見た満月の模様 */
        private class Mare(val x: Float, val y: Float, val size: Float, val alpha: Float)

        private val MARIA = listOf(
            Mare(-0.30f, -0.45f, 0.32f, 0.34f), // 雨の海
            Mare(-0.12f, -0.36f, 0.18f, 0.22f), // 雨の海と晴れの海のあいだ
            Mare(-0.56f, -0.30f, 0.28f, 0.28f), // 嵐の大洋（北）
            Mare(-0.64f, 0.0f, 0.32f, 0.3f), // 嵐の大洋
            Mare(-0.50f, 0.28f, 0.26f, 0.26f), // 嵐の大洋（南）
            Mare(-0.52f, 0.52f, 0.13f, 0.28f), // 湿りの海
            Mare(-0.22f, 0.42f, 0.2f, 0.22f), // 雲の海
            Mare(-0.20f, -0.80f, 0.16f, 0.18f), // 氷の海
            Mare(0.10f, -0.80f, 0.15f, 0.16f), // 氷の海
            Mare(0.18f, -0.38f, 0.2f, 0.34f), // 晴れの海
            Mare(-0.02f, -0.16f, 0.13f, 0.22f), // 蒸気の海
            Mare(0.34f, -0.08f, 0.24f, 0.34f), // 静かの海
            Mare(0.24f, -0.22f, 0.16f, 0.2f), // 晴れの海と静かの海のあいだ
            Mare(0.72f, -0.30f, 0.14f, 0.4f), // 危難の海
            Mare(0.56f, 0.24f, 0.18f, 0.3f), // 豊かの海
            Mare(0.46f, 0.10f, 0.14f, 0.2f), // 静かの海と豊かの海のあいだ
            Mare(0.36f, 0.40f, 0.12f, 0.26f), // 神酒の海
        )

        /** 細かいクレーターの数と並びの種 */
        private const val CRATER_COUNT = 40
        private const val MOON_SEED = 11

        /** 三日月の形（位相。0.16 は新月から約 5 日）と、光る側の向き（右から時計回りに。右下へ） */
        private const val CRESCENT_PHASE = 0.16
        private const val CRESCENT_TILT = 28f

        /** 三日月の欠けた側の見え方（地球の照り返し） */
        private const val EARTHSHINE = 0.08f

        /** 光と影の境のぼかし：位相を [FEATHER_PHASE] ずつ進めた形を [FEATHER_STEPS] 枚重ねる（1 枚で 2 px ほど） */
        private const val FEATHER_STEPS = 8
        private const val FEATHER_PHASE = 0.004

        /** 霧のもや（方眼紙の地の色。線を隠す） */
        private val FogPaper: Color get() = Fieldbook.Paper
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 月の光の当たる所の形を [path] に入れる（光る側を右にした形。[phase] は 0 新月・0.25 半月・0.5 満月）。
 * 右半分の丸と、光と影の境（縦長の半分のだ円）で囲む。境は、三日月では右へ、半月を過ぎると左へふくらむ
 */
private fun litPart(path: Path, c: Offset, r: Float, phase: Double) {
    val k = cos(2 * Math.PI * phase).toFloat()
    val rx = r * abs(k)
    path.reset()
    path.arcTo(Rect(c, r), -90f, 180f, forceMoveTo = true)
    path.arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (k > 0) -180f else 180f, forceMoveTo = false)
    path.close()
}

/** 0〜[w] に折り返す（画面の端から出た粒を、反対の端から出す） */
private fun wrap(x: Float, w: Float): Float = ((x % w) + w) % w

/** 番号から決まる 0〜1 の値（毎回同じ）。雨粒の輪を出す場所を、決まった順に変えていくのに使う */
private fun hash(i: Int, j: Int): Float {
    var h = i * 374_761_393 + j * 668_265_263
    h = (h xor (h ushr 13)) * 1_274_126_177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / Int.MAX_VALUE.toFloat()
}


/**
 * 一度だけ描いて取っておく絵（月の面・雪の粒）。[get] の key が前と同じなら、取っておいた絵を返し、違えば描き直す。
 * 細かい絵を毎コマ描かず、毎コマは取っておいた絵を置くだけにするため
 */
private class CachedImage {
    private var image: ImageBitmap? = null
    private var key: Any? = null

    /** 一辺 [sidePx] の正方形の絵を [draw] で描いて返す。[key] が前と同じなら描き直さない */
    fun get(scope: DrawScope, key: Any, sidePx: Int, draw: DrawScope.() -> Unit): ImageBitmap {
        image?.takeIf { this.key == key }?.let { return it }
        val made = ImageBitmap(sidePx, sidePx)
        CanvasDrawScope().draw(scope, scope.layoutDirection, androidx.compose.ui.graphics.Canvas(made), Size(sidePx.toFloat(), sidePx.toFloat()), draw)
        image = made
        this.key = key
        return made
    }
}
