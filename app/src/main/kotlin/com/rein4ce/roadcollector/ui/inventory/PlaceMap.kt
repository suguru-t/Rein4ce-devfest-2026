package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.core.model.PartPlace
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min

/**
 * 拡大表示の背景に敷く、パーツを歩いた場所の地図。
 * 上に重ねるパーツの図（[FieldbookPart]）と同じ縮尺・位置になるように、カメラの中心と拡大率を決める。
 *
 * - 見るだけ（動かす・拡大するなどの操作はできない）
 * - 野帳の上で道が主役になるよう、色をうすくし、お店や駅の印は消す（地図の見た目の設定。Google のロゴはそのまま）
 * - 下の Google のロゴが図の縮尺と重ならないよう、地図の余白を上下同じだけ取る（上下同じなので、中心はずれない）
 */
@Composable
fun PlaceMap(place: PartPlace, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val figureDp = min(maxWidth.value, maxHeight.value)
        val camera = rememberCameraPositionState(key = "${place.lat},${place.lng},${place.sizeM},$figureDp") {
            position = CameraPosition.fromLatLngZoom(LatLng(place.lat, place.lng), zoomFor(place, figureDp))
        }
        // 縮尺の目盛り（図の下から 12/150）の少し上までロゴを持ち上げる
        val logoLift = (figureDp * 20f / 150f).dp
        GoogleMap(
            cameraPositionState = camera,
            contentPadding = PaddingValues(top = logoLift, bottom = logoLift),
            properties = remember {
                MapProperties(
                    mapType = MapType.NORMAL,
                    mapStyleOptions = MapStyleOptions(FADED_STYLE),
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
 * 図と同じ縮尺になる地図の拡大率。
 * 図は長辺（[PartPlace.sizeM] m）を幅の [FIGURE_ROAD_RATIO] で描くので、1dp あたりのメートルはそこから決まる。
 * Google マップは拡大率 0 で地球 1 周（赤道で約 40,075 km）が 256dp、拡大率が 1 上がるごとに 2 倍になる（緯度が上がると cos の分だけ縮む）。
 *
 * @param figureDp 図（正方形）の 1 辺 [dp]
 */
internal fun zoomFor(place: PartPlace, figureDp: Float): Float {
    val metersPerDp = place.sizeM / (figureDp * FIGURE_ROAD_RATIO)
    val metersPerDpAtZoom0 = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(place.lat)) / 256.0
    return log2(metersPerDpAtZoom0 / metersPerDp).toFloat().coerceIn(2f, 21f)
}

private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

/** 地図をうすく、静かにする見た目の設定（色を抜いて明るく。お店・駅・バスの印は消す） */
private const val FADED_STYLE = """
[
  {"stylers": [{"saturation": -70}, {"lightness": 35}]},
  {"featureType": "poi", "stylers": [{"visibility": "off"}]},
  {"featureType": "transit", "stylers": [{"visibility": "off"}]}
]
"""
