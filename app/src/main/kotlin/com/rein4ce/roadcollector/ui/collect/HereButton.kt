package com.rein4ce.roadcollector.ui.collect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/** 地図の上の 1 点（緯度・経度）。Google マップの LatLng は Android の外（ユニットテスト）で作れないので、自分の形で持つ */
internal data class MapSpot(val lat: Double, val lng: Double)

/**
 * 「今いる場所へ戻る」ボタンを押したときに、地図のまん中へ移す所（#112・決定 70）。
 *
 * - 記録中（デモ散歩の再生中も）で点があれば、最後の点（地図の上の黄色の人。デモ散歩では再生している道の先）
 * - そうでなければ、記録していない間の今いる場所（[idle]。止まった黄色の人。止めたあとで旗が出ているときも、今いる場所へ）
 * - どちらも無ければ null（位置の許可が無い・まだ場所が分からない）
 */
internal fun hereTarget(points: List<TrackPoint>, recording: Boolean, playingDemo: Boolean, idle: MapSpot?): MapSpot? {
    val last = points.lastOrNull()
    if ((recording || playingDemo) && last != null) return MapSpot(last.lat, last.lng)
    return idle
}

/**
 * 今いる場所へ戻すときの地図の拡大。今の拡大が [HERE_MIN_ZOOM] より引いていたら（町全体を見ていたら）、歩く人のまわりが分かる
 * [HERE_ZOOM] まで寄せる。それより寄っていたら、今の拡大のまま（Google マップの現在地のボタンと同じ考え）
 */
internal fun hereZoom(current: Float): Float = if (current < HERE_MIN_ZOOM) HERE_ZOOM else current

/** これより引いていたら、戻すときに寄せる */
internal const val HERE_MIN_ZOOM = 14f

/** 寄せるときの拡大（地図を開いたときと同じ） */
internal const val HERE_ZOOM = 16f

/** 戻すときに地図を動かす時間 [ms] */
internal const val HERE_ANIMATION_MS = 600

/**
 * 記録画面の地図の右下（拡大・縮小のボタンの上）に置く「今いる場所へ戻る」ボタン（#112・決定 70）。
 * Google マップの現在地のボタンと同じく、地図を指でずらして別の所を見ていても、押すと今いる場所を地図のまん中に戻す。
 *
 * 見た目は、アプリのほかの積み木のボタン（積む画面の「チェンジ」など）と同じ形：紙の面に青緑の縁、青緑の照準のアイコン（[AppIcons.MyLocation]）。
 * 場所がまだ分からない（位置の許可はあるが、まだ届いていない）ときは灰色にして押せない。
 * 位置の許可が無いときは押せて、押すと許可を聞く（[onClick] の側で）
 *
 * @param ready 押せるか（戻す所が分かっている、または許可を聞ける）
 * @param label 読み上げの説明
 */
@Composable
internal fun HereButton(ready: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val ink = if (ready) Pop.TealLine else Fieldbook.Muted
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, enabled = ready, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (ready) onClick { onClick(); true } else disabled()
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 40.dp, height = 43.dp)
                .popBlock(face = Fieldbook.Card, corner = 10.dp, depth = 3.dp, edge = ink),
        ) {
            Icon(AppIcons.MyLocation, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        }
    }
}
