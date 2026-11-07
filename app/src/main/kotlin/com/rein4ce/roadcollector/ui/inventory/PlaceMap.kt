package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.PartPlace
import kotlin.math.cos
import kotlin.math.log2

/**
 * 拡大表示の背景に敷く、パーツを歩いた場所の地図。
 * 上に重ねるパーツの図（[FieldbookPart]）と同じ縮尺・位置になるように、カメラの中心と拡大率を決める。
 *
 * - 見るだけ（動かす・拡大するなどの操作はできない）
 * - 野帳の上で道が主役になるよう、白黒にしてお店や駅の印を消す。うすさは呼ぶ側で紙の色を重ねて出す（[MAP_FADE]）
 * - 地図は図（幅いっぱいの正方形）より [belowDp] だけ下に長くする。Google のロゴ（地図の左下。隠してはいけない）を
 *   その帯に置いて、上に重ねる道や縮尺がロゴを隠さないようにするため。中心は図の中心に合わせる
 * - 図と同じ縮尺にできない場所（[mapCanMatch] が false）では呼ばない
 *
 * @param belowDp 図の下に足す帯の高さ [dp]
 */
@Composable
fun PlaceMap(place: PartPlace, belowDp: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val figureDp = maxWidth.value
        val camera = rememberCameraPositionState(key = "${place.lat},${place.lng},${place.sizeM},$figureDp,$belowDp") {
            position = CameraPosition.fromLatLngZoom(
                cameraTarget(place, figureDp, belowDp),
                zoomFor(place, figureDp).coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM),
            )
        }
        GoogleMap(
            cameraPositionState = camera,
            // ダークモードでは、明るさを反転した地図に（上に重ねる紙の色も暗くなるので、暗くうすい地図になる）
            properties = remember(Fieldbook.dark) {
                MapProperties(
                    mapType = MapType.NORMAL,
                    mapStyleOptions = MapStyleOptions(if (Fieldbook.dark) FADED_DARK_STYLE else FADED_STYLE),
                    isBuildingEnabled = false,
                    isIndoorEnabled = false,
                )
            },
            uiSettings = remember {
                MapUiSettings(
                    compassEnabled = false,
                    indoorLevelPickerEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    rotationGesturesEnabled = false,
                    scrollGesturesEnabled = false,
                    scrollGesturesEnabledDuringRotateOrZoom = false,
                    tiltGesturesEnabled = false,
                    zoomControlsEnabled = false,
                    zoomGesturesEnabled = false,
                )
            },
        )
    }
}

/**
 * 地図の中心。地図は図より [belowDp] だけ下に長いので、地図の中心は図の中心より帯の半分だけ南（下）になる。
 */
internal fun cameraTarget(place: PartPlace, figureDp: Float, belowDp: Float): LatLng {
    val metersPerDp = place.sizeM / (figureDp * FIGURE_ROAD_RATIO)
    val southM = belowDp / 2 * metersPerDp
    return LatLng(place.lat - southM / Projection.M_PER_DEG_LAT, place.lng)
}

/**
 * 図と同じ縮尺になる地図の拡大率。
 * 図は長辺（[PartPlace.sizeM] m）を幅の [FIGURE_ROAD_RATIO] で描くので、1dp あたりのメートルはそこから決まる。
 * Google マップは拡大率 0 で地球 1 周（赤道で約 40,075 km）が 256dp、拡大率が 1 上がるごとに 2 倍になる（緯度が上がると cos の分だけ縮む）。
 *
 * @param figureDp 図（正方形）の 1 辺 [dp]
 */
internal fun zoomFor(place: PartPlace, figureDp: Float): Float {
    val metersPerDp = place.sizeM / (figureDp * FIGURE_ROAD_RATIO)
    val metersPerDpAtZoom0 = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(place.lat)) / 256.0
    return log2(metersPerDpAtZoom0 / metersPerDp).toFloat()
}

/**
 * 図と同じ縮尺まで地図を拡大できるか。
 * とても短い散歩（図の幅が 300dp なら、長辺がおよそ 12 m 未満）は、地図をいちばん拡大しても図より縮尺が小さく、道と地図がずれるので、地図を出さない
 */
internal fun mapCanMatch(place: PartPlace, figureDp: Float): Boolean = zoomFor(place, figureDp) <= MAX_MAP_ZOOM

/** Google マップ（ふつうの地図）の拡大率の範囲 */
private const val MIN_MAP_ZOOM = 2f
private const val MAX_MAP_ZOOM = 21f

private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

/**
 * 地図を静かにする見た目の設定：色を抜いて白黒にし、お店・駅・バスの印と名前は消す。
 * うすさは、上に重ねる紙の色（[MAP_FADE]）で出す（色を細かく指定しても、地図側で暗く描かれてしまうため）
 */
private const val FADED_STYLE = """
[
  {"stylers": [{"saturation": -100}]},
  {"elementType": "labels.icon", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi", "elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "transit", "elementType": "labels", "stylers": [{"visibility": "off"}]}
]
"""

/** [FADED_STYLE] の明るさを反転したもの（ダークモード用） */
private const val FADED_DARK_STYLE = """
[
  {"stylers": [{"saturation": -100}, {"invert_lightness": true}]},
  {"elementType": "labels.icon", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi", "elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "transit", "elementType": "labels", "stylers": [{"visibility": "off"}]}
]
"""

/** 地図の上に重ねる紙の色の濃さ（0 で地図そのまま、1 で紙だけ） */
internal const val MAP_FADE = 0.55f
