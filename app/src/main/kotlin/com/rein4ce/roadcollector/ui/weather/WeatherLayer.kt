package com.rein4ce.roadcollector.ui.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.WeatherMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.minutes

/** 背景の天気を決めるときの、いま見ている画面 */
enum class BackdropScreen { COLLECT, COLLECTION, OTHER }

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
 * - **コレクション**：大きく見ているパーツを歩いたときの天気。見ていないときは、最後に歩いたパーツの天気
 * - **積む・みんな**：降らせない
 *
 * 設定（採集画面の歯車）の「背景の天気の動き」で、なめらか・ふつう・止める・出さないを選べる。
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
    val recording by TrackRecorder.isRecording.collectAsStateWithLifecycle()
    // 設定（歯車）の「背景の天気の動き」
    val motion by app.settings.weatherMotion.collectAsStateWithLifecycle()

    if (screen == BackdropScreen.COLLECT) {
        LaunchedEffect(Unit) {
            while (true) {
                service.refreshCurrent()
                delay(15.minutes)
            }
        }
    }

    val weather = when (screen) {
        BackdropScreen.COLLECT -> current
        BackdropScreen.COLLECTION -> (focus ?: latestWalk(parts)?.id)?.let { repository.weatherOf(it, saved) }
        BackdropScreen.OTHER -> null
    }
    WeatherBackdrop(
        weather = if (motion == WeatherMotion.OFF) null else weather,
        modifier = modifier,
        still = recording || motion == WeatherMotion.STILL,
        smooth = motion == WeatherMotion.SMOOTH,
    )
}

/** 最後に歩いたパーツ（見本は除く） */
private fun latestWalk(parts: List<Part>): Part? =
    parts.filter { SampleParts.byId(it.id) == null }.maxByOrNull { it.walkedAt }
