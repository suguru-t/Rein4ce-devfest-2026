package com.rein4ce.roadcollector.ui.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.core.weather.WalkWeather
import com.rein4ce.roadcollector.data.DemoSky
import com.rein4ce.roadcollector.data.WeatherMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.minutes

/** 背景の天気を決めるときの、いま見ている画面 */
enum class BackdropScreen { HOME, COLLECT, COLLECTION, STACK, GALLERY, OTHER }

/**
 * コレクションで大きく見ているパーツの id（見ていなければ null）。コレクションの画面が入れて、背景の天気がそれを見る。
 * 画面ごとに天気の決め方が違うので、画面の外に置いている
 */
object BackdropFocus {
    val partId = MutableStateFlow<String?>(null)
}

/**
 * アプリ全体の方眼紙に降らせる天気。見ている画面に合わせて決める：
 * - **採集**：今の天気（スマホの最後の位置、無ければ最後に歩いた場所）。画面を開いている間、15 分ごとに聞き直す
 * - **コレクション**：一覧では、採集と同じ今の天気。カードを押して大きく見ている間だけ、そのパーツを歩いたときの天気
 *   （その道を歩いた日の空は、大きく見たときの楽しみにする。分からないパーツなら、その間は何も降らせない）
 * - **みんな**：採集と同じ今の天気（タワーを歩いたときの天気はサーバに無いので、今の天気を出す）
 * - **積む**：モードを選ぶ画面では、採集と同じ今の天気（遊んでいる間は降らせない。今の積む画面は地を塗っていて、天気が隠れるため）
 *
 * 設定（ホームの歯車）の「背景の天気の動き」で、なめらか・ふつう・止める・出さないを選べる。
 * 設定の「デモの天気」（発表用）を選んでいると、今の天気の代わりに（ホーム・記録・積むのモードを選ぶ画面・みんな・コレクションの一覧）、選んだ天気を出す（満月を選ぶと、どの画面の月も満月）。
 * 記録中（デモ再生中も）は、どの画面でも止まった絵にする。記録中は採集画面を開いたまま歩くことが多く、GPS と一緒に電池を使い続けないため。
 *
 * 方眼紙（`fieldbookGrid`）の上、画面の部品の下に敷く（AppNav）。
 */
@Composable
fun WeatherLayer(screen: BackdropScreen, modifier: Modifier = Modifier) {
    val app = RoadCollectorApp.from(LocalContext.current)
    val repository = app.partRepository
    val service = app.weatherService
    val current by service.current.collectAsStateWithLifecycle()
    val saved by repository.weather.collectAsStateWithLifecycle()
    val parts by repository.parts.collectAsStateWithLifecycle()
    val focus by BackdropFocus.partId.collectAsStateWithLifecycle()
    // 発表用の「デモの天気」
    val demo by app.settings.demoSky.collectAsStateWithLifecycle()

    val showsCurrent = screen != BackdropScreen.OTHER
    if (showsCurrent) {
        // ホーム・採集・コレクション・みんなを行き来しても聞き直しは増えない（WeatherService が 15 分以内なら聞き直さない）
        LaunchedEffect(Unit) {
            while (true) {
                service.refreshCurrent()
                delay(15.minutes)
            }
        }
    }

    // コレクションで大きく見ているパーツ（一覧のときは null で、今の天気）
    val shownId = if (screen == BackdropScreen.COLLECTION) focus else null
    val weather = when (screen) {
        // 今の天気。「デモの天気」を選んでいれば、本当の天気の代わりにそれを出す
        BackdropScreen.HOME, BackdropScreen.COLLECT, BackdropScreen.STACK, BackdropScreen.GALLERY -> demo.weather ?: current
        BackdropScreen.COLLECTION -> if (shownId != null) repository.weatherOf(shownId, saved) else demo.weather ?: current
        BackdropScreen.OTHER -> null
    }
    // 晴れの夜に満月を出すかを決める時刻：今の天気なら今、大きく見ているパーツならそのパーツを歩いた時刻
    val walkedAt = shownId?.let { id -> parts.firstOrNull { it.id == id }?.walkedAt }
    // 大きく見ている間は、同じ天気を窓の紙（ZoomWeather）で動かしている。背景は窓とまわりの暗い幕にほとんど隠れるので、
    // 止まった絵にして、同じ天気を 2 か所で描き直さない（#90 の A のレビュー）。閉じると、止めたところから続けて動く
    SettingsBackdrop(weather, walkedAt, modifier, still = shownId != null)
}

/**
 * コレクションの拡大表示の窓の紙（図のまわり）に降らせる、そのパーツを歩いたときの天気。
 * 背景の天気は、窓とまわりの暗い幕に隠れて見えにくいので、窓の中にも降らせる。図（地図と道）は不透明なので、ふさがない（決定 21 の表の追記）。
 * 画面いっぱいの天気の上のほうを窓の大きさで切り取るので、雨の速さや雲・太陽の大きさは背景と同じ（雲と太陽は窓の上のほうに出る）。
 * 窓の紙には方眼の線が無く、霧（線を薄めて見せる）が見えず雨・雪も目立たないので、雨・雪を濃くし、霧を灰色のもやにする。
 * 設定の「背景の天気の動き」（出さない・止める・なめらか）と、記録中は止まった絵にすることも背景と同じ
 *
 * @param walkedAt そのパーツを歩いた時刻（晴れの夜に満月かを決める）。分からなければ 0
 */
@Composable
fun ZoomWeather(weather: WalkWeather?, walkedAt: Long, modifier: Modifier = Modifier) {
    SettingsBackdrop(weather, walkedAt, modifier, sceneHeight = LocalConfiguration.current.screenHeightDp.dp, plainPaper = true)
}

/**
 * 設定に合わせて天気を描く（背景と拡大表示の窓で共通）。
 * 「背景の天気の動き」が「出さない」なら描かず、「止める」か記録中（デモ再生中も）なら止まった絵、「なめらか」なら 1 秒に 60 回。
 * 「デモの天気」で満月を選んでいれば、どの画面の月も満月にする
 *
 * @param walkedAt 晴れの夜に満月かを決める時刻。null か 0 なら今
 * @param still 設定によらず止まった絵にする（コレクションで大きく見ている間の背景）
 */
@Composable
private fun SettingsBackdrop(
    weather: WalkWeather?,
    walkedAt: Long?,
    modifier: Modifier,
    sceneHeight: Dp? = null,
    plainPaper: Boolean = false,
    still: Boolean = false,
) {
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    val recording by TrackRecorder.isRecording.collectAsStateWithLifecycle()
    val motion by settings.weatherMotion.collectAsStateWithLifecycle()
    val demo by settings.demoSky.collectAsStateWithLifecycle()
    WeatherBackdrop(
        weather = if (motion == WeatherMotion.OFF) null else weather,
        moonTimeMs = if (demo.fullMoon) DemoSky.FULL_MOON_TIME_MS else walkedAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
        modifier = modifier,
        still = still || recording || motion == WeatherMotion.STILL,
        smooth = motion == WeatherMotion.SMOOTH,
        sceneHeight = sceneHeight,
        plainPaper = plainPaper,
    )
}
