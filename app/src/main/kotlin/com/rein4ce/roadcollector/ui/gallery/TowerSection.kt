package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 断面図の 1 枠：タワー（無ければ空の台）と、台の下に書く呼び名（「最長」「あなた」など）。
 * @property mine 自分のタワーなら台と字を道の色（オレンジ）に
 * @property emptyNote タワーが無いときに台の上に書く一言（「まだありません」）
 */
internal data class SectionSlot(
    val tower: TowerSummary?,
    val caption: String,
    val mine: Boolean,
    val emptyNote: String? = null,
)

/**
 * 断面図：いくつかのタワーを、**同じ縮尺**で 1 本の台の上に並べて描く。
 * 左に測量の標尺（1 m ごとの目盛りと、横に伸びる薄い目盛り線）、いちばん高い記録の高さには赤鉛筆の線を 1 本引く。
 * 台は呼び名（「最長」「あなた」）を白抜きで載せた名札のブロック（下に濃い色の側面がのぞく）。
 * 自分のタワーの名札はオレンジ、ほかの人のは紺（パーツの色は種類の色のまま）。
 * 前は測量の断面図の地面のような斜線の枠だったが、ポップな見た目の中で浮いていたのと、呼び名を台の下に別に書いていたため、
 * 台そのものを名札にして 1 つにまとめた。
 *
 * 絵がまだ届いていないタワーは、一覧の高さだけ分かっているので、その高さの枠を破線で立てておく。
 * 置いた点から届く範囲はスロットの幅で切る（隣のタワーの上には描かない）。
 *
 * @param slots    並べる枠（左から）
 * @param sketches id → 中身（置き方）。無ければ破線の枠
 * @param record   記録線を引く高さ [m]（全部の中でいちばん高いタワー）。0 以下なら引かない
 * @param selected 紙片を開いているタワーの id（破線で囲む）
 * @param progress 開いたときの演出（0 で何も立っていない → 1 で全部立つ）。動かさないなら 1。描くときに読む（組み立て直しを減らすため）
 * @param onTap    タワーを押した
 */
@Composable
internal fun TowerSection(
    slots: List<SectionSlot>,
    sketches: Map<String, Tower>,
    record: Float,
    selected: String?,
    progress: () -> Float,
    onTap: (TowerSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 図の中の字は図の一部なので、文字の大きさの設定では大きくしない（大きくすると目盛りや呼び名がぶつかる）
    val tick = TextStyle(fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = with(density) { 9.dp.toSp() }, color = Fieldbook.Muted)
    val caption = TextStyle(fontFamily = Pop.Rounded, fontSize = with(density) { 13.dp.toSp() }, fontWeight = FontWeight.Black, color = Fieldbook.Ink)
    val small = TextStyle(fontFamily = Pop.Rounded, fontSize = with(density) { 9.dp.toSp() }, fontWeight = FontWeight.Black)
    val note = TextStyle(fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = with(density) { 10.dp.toSp() }, color = Fieldbook.Muted)
    val description = buildString {
        append("断面図。")
        slots.forEach { s ->
            val t = s.tower
            if (t == null) append("${s.caption}：${s.emptyNote ?: "なし"}。")
            else append("${s.caption}：高さ ${"%.2f".format(Locale.US, t.height)} メートル、パーツ ${t.partCount} 本。")
        }
    }
    Canvas(
        modifier
            // 枠の外には描かない（小さい画面で枠がとても低くなっても、上の切り替えに重ならない）
            .clipToBounds()
            .semantics { contentDescription = description }
            .pointerInput(slots) {
                detectTapGestures { pos ->
                    val slot = slotAt(pos.x, size.width.toFloat(), slots.size, STAFF_WIDTH_DP.dp.toPx())
                    slots.getOrNull(slot)?.tower?.let(onTap)
                }
            },
    ) {
        // 演出の進み。描くときだけ読む（組み立ての中で読むと、動いている間は画面全体を組み立て直すことになる）
        val progressNow = progress()
        val w = size.width
        val h = size.height
        // 枠がとても低いとき（小さい画面・大きい文字）は、つぶれた絵を出すより何も描かない。下の 2 行が残る
        if (h < MIN_HEIGHT_DP.dp.toPx()) return@Canvas
        // 紙の色を半分透かして敷く。方眼の線は消えるが（paperBand）、雨や太陽の光は通るので、その上で絵が読めるように
        drawRoundRect(Fieldbook.Paper.copy(alpha = 0.78f), cornerRadius = CornerRadius(SECTION_CORNER.toPx()))
        val staffW = STAFF_WIDTH_DP.dp.toPx()
        val topPad = TOP_PAD_DP.dp.toPx()
        val slotW = (w - staffW) / max(1, slots.size)
        // 縮尺の上限（幅から）：台と張り出しがスロットの幅に収まる
        val widthBound = slotW / SLOT_WIDTH_M
        // 下の余白：名札の台（呼び名の高さ＋上下の余白）と、その下にのぞく側面。枠の外には描かないので、ここに収める
        val captionH = measurer.measure("あ", caption).size.height
        val plateH = captionH + 8.dp.toPx()
        val plateDepth = 3.dp.toPx()
        val bottomPad = plateH + plateDepth + 6.dp.toPx()
        val baseY = h - bottomPad
        // 縮尺：いちばん高いもの（記録線も）が上の余白に収まる、と幅の上限の、小さいほう
        val tallest = max(record, slots.maxOfOrNull { it.tower?.height ?: 0f } ?: 0f).coerceAtLeast(1f)
        val pxPerM = min((baseY - topPad) / (tallest + 0.3f), widthBound)
        fun y(m: Float) = baseY - m * pxPerM

        // 標尺：目盛りと、右へ伸びる薄い点線（演出中は下から伸びる）。
        // 刻みは、目盛りの間が 16dp 以上空く 1・2・5・10 m… から選ぶ（変に高いタワーが来ても数字が重ならない）
        val step = tickStepM(pxPerM, minGapPx = 16.dp.toPx())
        val meters = ceil(tallest).toInt()
        val shownTop = baseY - (baseY - y(meters.toFloat())) * progressNow
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        drawLine(Fieldbook.Mark, Offset(staffW, topPad), Offset(staffW, baseY), 1.dp.toPx())
        for (m in step..meters step step) {
            val yy = y(m.toFloat())
            // 演出中でまだ伸びていない所と、上の余白からはみ出す目盛りは描かない
            if (yy < shownTop || yy < topPad) continue
            drawLine(Fieldbook.Mark, Offset(staffW - 4.dp.toPx(), yy), Offset(staffW, yy), 1.dp.toPx())
            drawLine(Fieldbook.GridMajor, Offset(staffW, yy), Offset(w, yy), 1.dp.toPx(), pathEffect = dash)
            val label = measurer.measure("${m}m", tick)
            drawText(label, topLeft = Offset(staffW - 6.dp.toPx() - label.size.width, yy - label.size.height / 2))
        }

        // 地面（墨の太い線。全部の台をつなぐ）。名札の台はこの上に重ねて描く
        drawLine(Fieldbook.Ink, Offset(staffW, baseY), Offset(w, baseY), 2.dp.toPx())

        // タワー（名札の台の上に立てる。演出中は台から上へ伸びる）
        slots.forEachIndexed { i, s ->
            val cx = staffW + slotW * (i + 0.5f)
            val t = s.tower
            // 名札の台：上面が baseY。幅は台（PLATFORM_WIDTH）か呼び名の長いほう（スロットに収める）。
            // 自分のはオレンジ、ほかの人のは紺のブロックに白い字。タワーが無い枠は、破線の名札に薄い字
            val label = measurer.measure(s.caption, caption.copy(color = if (t == null) Fieldbook.Muted else Pop.Letter))
            val plateW = max(PLATFORM_WIDTH * pxPerM, label.size.width + 18.dp.toPx()).coerceAtMost(slotW - 8.dp.toPx())
            val plate = Rect(Offset(cx - plateW / 2, baseY), Size(plateW, plateH))
            if (t == null) {
                drawRoundRect(Fieldbook.Mark, plate.topLeft, plate.size, CornerRadius(PLATE_CORNER_DP.dp.toPx()), style = Stroke(width = 1.dp.toPx(), pathEffect = dash))
            } else {
                drawPlate(plate, face = if (s.mine) Pop.OrangeDeep else Pop.Navy, depth = plateDepth)
            }
            drawText(label, topLeft = Offset(cx - label.size.width / 2, plate.top + (plateH - label.size.height) / 2))
            if (t != null) {
                val sketch = sketches[t.id]
                val revealTop = baseY - (baseY - y(t.height + PART_REACH)) * progressNow
                clipRect(left = cx - slotW / 2, top = revealTop, right = cx + slotW / 2, bottom = baseY + 1f) {
                    if (sketch == null) {
                        // 絵がまだ：高さだけ分かっているので、その高さの枠を破線で
                        drawRoundRect(
                            color = Fieldbook.Mark,
                            topLeft = Offset(cx - PLATFORM_WIDTH / 2 * pxPerM, y(t.height)),
                            size = Size(PLATFORM_WIDTH * pxPerM, t.height * pxPerM),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                        )
                    } else {
                        // 紙片の絵と同じ描き方（TowerSketch.drawTowerParts）。描き方を変えるときは 1 か所で済む
                        drawTowerParts(sketch, at = { x, yy -> Offset(cx + x * pxPerM, baseY - yy * pxPerM) }, pxPerM = pxPerM)
                    }
                }
            } else if (s.emptyNote != null) {
                val n = measurer.measure(s.emptyNote, note)
                drawText(n, topLeft = Offset(cx - n.size.width / 2, baseY - n.size.height - 6.dp.toPx()))
            }
            // 開いているタワーの破線の枠（ボタンと同じ青緑）。名札の台まで囲む
            if (t != null && t.id == selected) {
                drawRoundRect(
                    color = Pop.TealLine,
                    topLeft = Offset(cx - slotW / 2 + 2.dp.toPx(), topPad),
                    size = Size(slotW - 4.dp.toPx(), baseY - topPad + plateH + plateDepth + 3.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }

        // 記録線：赤鉛筆で 1 本。右端の上に「最長 3.45」
        if (record > 0f && progressNow >= 1f) {
            val ry = y(record)
            drawLine(Fieldbook.Needle, Offset(staffW, ry), Offset(w, ry), 1.2.dp.toPx())
            val label = measurer.measure("最長 ${"%.2f".format(Locale.US, record)}", small.copy(color = Fieldbook.Needle))
            drawText(label, topLeft = Offset(w - label.size.width, ry - label.size.height - 1.dp.toPx()))
        }
    }
}

/**
 * 名札の台を、画面のほかのブロック（Pop.kt の popBlock）と同じ形で描く：角の丸い面の下に [depth] だけ濃い側面がのぞく。
 * [face] は紺（ほかの人）かオレンジ（自分）。上に呼び名を白い字で載せる
 */
private fun DrawScope.drawPlate(r: Rect, face: androidx.compose.ui.graphics.Color, depth: Float) {
    val corner = CornerRadius(PLATE_CORNER_DP.dp.toPx())
    drawRoundRect(Pop.side(face), topLeft = Offset(r.left, r.top + depth), size = r.size, cornerRadius = corner)
    drawRoundRect(face, topLeft = r.topLeft, size = r.size, cornerRadius = corner)
}

/** 名札の台の角の丸さ [dp] */
private const val PLATE_CORNER_DP = 6f

/**
 * 標尺の刻み [m]：1・2・5・10・20・50… のうち、目盛りの間が [minGapPx] 以上空く最小のもの。
 * ふつうのタワー（4 m まで）では 1 m。変に高いタワー（サーバは 100 m まで受け付ける）が来ても数字が重ならない
 */
internal fun tickStepM(pxPerM: Float, minGapPx: Float): Int {
    val steps = intArrayOf(1, 2, 5, 10, 20, 50, 100)
    return steps.firstOrNull { it * pxPerM >= minGapPx } ?: steps.last()
}

/** 押した x がどのスロットか（標尺の上は -1） */
internal fun slotAt(x: Float, width: Float, count: Int, staffWidthPx: Float = 0f): Int {
    if (count <= 0 || x < staffWidthPx) return -1
    val slotW = (width - staffWidthPx) / count
    return ((x - staffWidthPx) / slotW).toInt().coerceIn(0, count - 1)
}

/** 標尺の幅と、上の余白（記録線の字のぶん）[dp]。下の余白は台の厚みと呼び名の高さから描くときに決める */
private const val STAFF_WIDTH_DP = 30f
private const val TOP_PAD_DP = 16f

/** これより低い枠には描かない（小さい画面で枠がつぶれたとき。GalleryScreen の断面図） */
private const val MIN_HEIGHT_DP = 80f

/** 1 スロットに必要な幅 [m]：台 1.4 m と、台からはみ出して静止したパーツのぶん（いちばん大きいパーツで見る） */
private val SLOT_WIDTH_M = PLATFORM_WIDTH + 0.8f * MAX_PART_SCALE
