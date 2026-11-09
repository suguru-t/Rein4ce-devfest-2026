package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER

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
 * @param topWindow 高すぎる塔は頂上のあたりだけを映すか（3 分チャレンジ・毎日 1 つずつ積む。決定 77）。
 *   全部を同じ縮尺で入れると読めないほど細くなるとき（[WINDOW_BELOW]）、縮尺を枠の幅で決まる大きさに保ち、
 *   それぞれの塔の頂上を枠の上にそろえて映す。映らない下のほうは波線で切り、「下の ○ m は省略」と書く。
 *   目盛りは塔ごとに本当の高さで振る（共通の標尺は描かない）。押すと画面いっぱいに全体（GalleryScreen の TowerFullView）
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
    topWindow: Boolean = false,
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
        // 紙の色を半分透かして敷く。雨や太陽の光は通るので、その上で絵が読めるように
        drawRoundRect(Fieldbook.Paper.copy(alpha = 0.78f), cornerRadius = CornerRadius(SECTION_CORNER.toPx()))
        val staffW = STAFF_WIDTH_DP.dp.toPx()
        val topPad = TOP_PAD_DP.dp.toPx()
        val slotW = (w - staffW) / max(1, slots.size)
        // 縮尺の上限（幅から）：台と張り出しがスロットの幅に収まる。広い台で積んだタワー（絵が届いているもの）があれば、それに合わせる（決定 66）
        val slotWidthM = slots.mapNotNull { s -> s.tower?.let { sketches[it.id]?.platformWidth } }.maxOfOrNull { slotWidthFor(it) }
            ?.coerceAtLeast(SLOT_WIDTH_M) ?: SLOT_WIDTH_M
        val widthBound = slotW / slotWidthM
        // 下の余白：名札の台（呼び名の高さ＋上下の余白）と、その下にのぞく側面。枠の外には描かないので、ここに収める
        val captionH = measurer.measure("あ", caption).size.height
        val plateH = captionH + 8.dp.toPx()
        val plateDepth = 3.dp.toPx()
        val bottomPad = plateH + plateDepth + 6.dp.toPx()
        val baseY = h - bottomPad
        // 縮尺：いちばん高いもの（記録線も）が上の余白に収まる、と幅の上限の、小さいほう
        val tallest = max(record, slots.maxOfOrNull { it.tower?.height ?: 0f } ?: 0f).coerceAtLeast(1f)
        val fitPxPerM = min((baseY - topPad) / (tallest + 0.3f), widthBound)
        // 頂上だけを映す：全部入れると幅で決まる大きさの [WINDOW_BELOW] より小さくなるとき（決定 77）
        val window = topWindow && fitPxPerM < widthBound * WINDOW_BELOW
        // 頂上だけを映すときの縮尺：枠の幅で決まる大きさ。ただし、あと少しで全部入る塔（その塔だけなら [NEARLY_FIT] 以上の大きさで入る）は、
        // 少し小さくして全部を映す（0.3 m だけ省く、のような半端な切り方をしない）
        val nearlyFit = slots.mapNotNull { it.tower?.height }
            .map { (baseY - topPad) / (it + PART_REACH) }
            .filter { it >= widthBound * NEARLY_FIT && it < widthBound }
        val pxPerM = if (window) min(widthBound, nearlyFit.minOrNull() ?: widthBound) else fitPxPerM
        // 映る高さ [m]（頂上だけを映すとき）
        val windowM = (baseY - topPad) / pxPerM
        fun y(m: Float) = baseY - m * pxPerM
        if (window) {
            drawWindowedSection(
                slots, sketches, selected, progressNow, measurer, caption, tick, note,
                staffW = staffW, topPad = topPad, slotW = slotW, baseY = baseY, plateH = plateH, plateDepth = plateDepth,
                pxPerM = pxPerM, windowM = windowM,
            )
            return@Canvas
        }

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
            // 名札の台：上面が baseY。幅は台（タワーの台の幅。絵がまだなら今までの台）か呼び名の長いほう（スロットに収める）。
            // 自分のはオレンジ、ほかの人のは紺のブロックに白い字。タワーが無い枠は、破線の名札に薄い字
            val label = measurer.measure(s.caption, caption.copy(color = if (t == null) Fieldbook.Muted else Pop.Letter))
            val platformWidth = t?.let { sketches[it.id]?.platformWidth } ?: PLATFORM_WIDTH
            val plateW = max(platformWidth * pxPerM, label.size.width + 18.dp.toPx()).coerceAtMost(slotW - 8.dp.toPx())
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
 * 頂上だけを映す断面図（決定 77）。縮尺は [pxPerM]（枠の幅で決まる大きさ）のまま、それぞれの塔の頂上を枠の上にそろえる。
 * * 塔が [windowM] より低ければ、台から全部をそのまま描く。高ければ、下の映らない所を波線で切り、波線と地面のあいだに「下の ○ m は省略」。
 * 目盛りは塔ごとに、その塔の本当の高さで振る（塔ごとに映る高さが違うので、共通の標尺は描かない）。右上に「押すと全体」
 */
private fun DrawScope.drawWindowedSection(
    slots: List<SectionSlot>,
    sketches: Map<String, Tower>,
    selected: String?,
    progressNow: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    caption: TextStyle,
    tick: TextStyle,
    note: TextStyle,
    staffW: Float,
    topPad: Float,
    slotW: Float,
    baseY: Float,
    plateH: Float,
    plateDepth: Float,
    pxPerM: Float,
    windowM: Float,
) {
    val w = size.width
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    val step = tickStepM(pxPerM, minGapPx = 16.dp.toPx())
    // 地面（墨の太い線）
    drawLine(Fieldbook.Ink, Offset(staffW, baseY), Offset(w, baseY), 2.dp.toPx())
    slots.forEachIndexed { i, s ->
        val cx = staffW + slotW * (i + 0.5f)
        val left = cx - slotW / 2
        val t = s.tower
        val label = measurer.measure(s.caption, caption.copy(color = if (t == null) Fieldbook.Muted else Pop.Letter))
        val platformWidth = t?.let { sketches[it.id]?.platformWidth } ?: PLATFORM_WIDTH
        val plateW = max(platformWidth * pxPerM, label.size.width + 18.dp.toPx()).coerceAtMost(slotW - 8.dp.toPx())
        val plate = Rect(Offset(cx - plateW / 2, baseY), Size(plateW, plateH))
        if (t == null) {
            drawRoundRect(Fieldbook.Mark, plate.topLeft, plate.size, CornerRadius(PLATE_CORNER_DP.dp.toPx()), style = Stroke(width = 1.dp.toPx(), pathEffect = dash))
        } else {
            drawPlate(plate, face = if (s.mine) Pop.OrangeDeep else Pop.Navy, depth = plateDepth)
        }
        drawText(label, topLeft = Offset(cx - label.size.width / 2, plate.top + (plateH - label.size.height) / 2))
        if (t == null) {
            s.emptyNote?.let {
                val n = measurer.measure(it, note)
                drawText(n, topLeft = Offset(cx - n.size.width / 2, baseY - n.size.height - 6.dp.toPx()))
            }
            return@forEachIndexed
        }
        // この塔で映さない下の高さ [m]。頂上（いちばん上のパーツが届く所）を枠の上にそろえる
        val skipped = max(0f, t.height + PART_REACH - windowM)
        fun sy(m: Float) = baseY - (m - skipped) * pxPerM
        // 波線で切る所（省いたときだけ）。波線と地面のあいだに「下の ○ m は省略」を書くので、その字の高さぶん上で切る
        // （前は波線の上、塔の絵に重ねて書いていて、パーツと重なって読みにくかった）
        val skippedLabel = measurer.measure("下の ${"%.1f".format(Locale.US, skipped)} m は省略", note)
        val amp = 2.5.dp.toPx()
        val cutY = baseY - skippedLabel.size.height - amp - 4.dp.toPx()
        val bottom = if (skipped > 0f) cutY else baseY + 1f

        // 塔ごとの目盛り（本当の高さ）。スロットの左の端に字、右へ薄い点線
        val first = (ceil(skipped / step) * step).toInt().coerceAtLeast(step)
        var m = first
        while (m <= t.height + PART_REACH) {
            val yy = sy(m.toFloat())
            if (yy < topPad) break
            if (yy < bottom - 4.dp.toPx()) {
                drawLine(Fieldbook.GridMajor, Offset(left + 2.dp.toPx(), yy), Offset(left + slotW - 2.dp.toPx(), yy), 1.dp.toPx(), pathEffect = dash)
                val tl = measurer.measure("${m}m", tick)
                drawText(tl, topLeft = Offset(left + 3.dp.toPx(), yy - tl.size.height - 1.dp.toPx()))
            }
            m += step
        }

        val sketch = sketches[t.id]
        val revealTop = bottom - (bottom - sy(t.height + PART_REACH)) * progressNow
        clipRect(left = left, top = revealTop.coerceAtLeast(0f), right = left + slotW, bottom = bottom) {
            if (sketch == null) {
                drawRoundRect(
                    color = Fieldbook.Mark,
                    topLeft = Offset(cx - PLATFORM_WIDTH / 2 * pxPerM, sy(t.height)),
                    size = Size(PLATFORM_WIDTH * pxPerM, (t.height - skipped).coerceAtLeast(0f) * pxPerM + (baseY - bottom)),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            } else {
                drawTowerParts(sketch, at = { x, yy -> Offset(cx + x * pxPerM, sy(yy)) }, pxPerM = pxPerM)
            }
        }
        if (skipped > 0f) {
            // 波線（省いた印）と、その下（地面とのあいだ）に「下の ○ m は省略」
            val path = Path()
            val waveW = 6.dp.toPx()
            var x = left + 4.dp.toPx()
            path.moveTo(x, cutY)
            var up = true
            while (x < left + slotW - 4.dp.toPx()) {
                x += waveW
                path.lineTo(x, cutY + if (up) -amp else amp)
                up = !up
            }
            drawPath(path, Fieldbook.Ink, style = Stroke(width = 1.5.dp.toPx()))
            drawText(skippedLabel, topLeft = Offset(cx - skippedLabel.size.width / 2, cutY + amp + 1.dp.toPx()))
        }
        if (t.id == selected) {
            drawRoundRect(
                color = Pop.TealLine,
                topLeft = Offset(left + 2.dp.toPx(), topPad),
                size = Size(slotW - 4.dp.toPx(), baseY - topPad + plateH + plateDepth + 3.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }
    // 押すと全体が見られることを、右上に一言
    val hint = measurer.measure("押すと全体", note)
    drawText(hint, topLeft = Offset(w - hint.size.width - 6.dp.toPx(), 2.dp.toPx()))
}

/**
 * 頂上だけを映すようにする境目（決定 77）。全部を同じ縮尺で入れたときの大きさが、枠の幅で決まる大きさのこれより小さいとき。
 * 通常の塔（4 m くらいまで）は全部入る。3 分チャレンジや毎日 1 つずつ積むで高くなった塔（7〜8 m より上）から、頂上だけを映す
 */
private const val WINDOW_BELOW = 0.55f

/** 頂上だけを映すときも、この大きさ（枠の幅で決まる大きさに対する割合）以上で全部入る塔は、縮尺を少し下げて全部を映す */
private const val NEARLY_FIT = 0.7f

/**
 * 名札の台を、画面のほかのブロック（ui/common/Pop.kt の popBlock）と同じ形で描く：角の丸い面の下に [depth] だけ濃い側面がのぞく。
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
private val SLOT_WIDTH_M get() = slotWidthFor(PLATFORM_WIDTH)

/** 台の幅 [platformWidth] のタワーを 1 スロットに入れるのに必要な幅 [m] */
private fun slotWidthFor(platformWidth: Float): Float = platformWidth + 0.8f * MAX_PART_SCALE
