package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.dp

/**
 * 下のタブのアイコン。Material の標準セットに「足跡」「ノート」「積み木」「トロフィー」が無いので、24×24 で自作
 * （タブバーの左の、ホームへ戻るボタンの「家」も、線の太さをそろえるためここで作る）。
 * 選ばれていないタブは「線画」。選ばれたタブの「塗り」は、部品ごとに動かすので ui/TabIcons.kt が下の形（〜Path）から描く
 * （足跡の塗りは、注記の足跡のマーク（WalkNote）にも使う）。
 * 色は呼び出し側（タブバー）が tint で付けるので、ここでは黒で描く。
 */
object AppIcons {

    private const val STROKE = 1.8f

    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(build)
            .build()

    /** 塗りのパス */
    private fun ImageVector.Builder.filled(evenOdd: Boolean = false, draw: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            pathBuilder = draw,
        )
    }

    /** 線画のパス */
    private fun ImageVector.Builder.outlined(draw: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    // ---- 足跡（左右の足裏を少しずらして並べる。それぞれ足裏の楕円＋かかとの小さい楕円） ----

    private const val KAPPA = 0.5523f

    /** 楕円（4 本のベジェ曲線で近似） */
    private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        moveTo(cx, cy - ry)
        curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        close()
    }

    /**
     * 足跡の形と置き場所（24 の目盛りで）。アイコンと、記録画面の歩く足跡（ui/collect/WalkingFootprints）の両方で使うので、
     * ここを直せば両方が同じ形になる
     */
    internal object Foot {
        /** 左足・右足の真ん中の x */
        const val LEFT_X = 7.2f
        const val RIGHT_X = 16.8f

        /** 上の足跡（左足）と下の足跡（右足）の上端 */
        const val UPPER_TOP = 2f
        const val LOWER_TOP = 8.5f

        /** 足裏の楕円（上端からの中心の高さ・横と縦の半径） */
        const val SOLE_DY = 4.4f
        const val SOLE_RX = 3.1f
        const val SOLE_RY = 4.4f

        /** かかとの楕円 */
        const val HEEL_DY = 11.6f
        const val HEEL_RX = 2.3f
        const val HEEL_RY = 1.9f
    }

    /** 足跡 1 つ。[shrink] は線画のときに線の太さぶん内側に寄せる量 */
    private fun PathBuilder.footprint(cx: Float, top: Float, shrink: Float = 0f) {
        ellipse(cx, top + Foot.SOLE_DY, Foot.SOLE_RX - shrink, Foot.SOLE_RY - shrink) // 足裏
        ellipse(cx, top + Foot.HEEL_DY, Foot.HEEL_RX - shrink, Foot.HEEL_RY - shrink) // かかと
    }

    val Footprints: ImageVector by lazy {
        icon("Footprints") {
            filled { footprint(Foot.LEFT_X, Foot.UPPER_TOP) } // 左足（上）
            filled { footprint(Foot.RIGHT_X, Foot.LOWER_TOP) } // 右足（下）
        }
    }

    val FootprintsOutline: ImageVector by lazy {
        icon("FootprintsOutline") {
            val s = STROKE / 2
            outlined { footprint(Foot.LEFT_X, Foot.UPPER_TOP + s, s) }
            outlined { footprint(Foot.RIGHT_X, Foot.LOWER_TOP + s, s) }
        }
    }

    // （靴のアイコンは足跡に置き換えたので消した。戻すときは git の履歴 e96f95e から）

    // （鞄のアイコンはノートに置き換えたので消した。戻すときは git の履歴から）

    // ---- ノート（コレクション。左の端をリングで綴じた縦長のノート。表紙に背の縦線と、題名のような短い横線 2 本） ----

    /** 表紙（縦長・角の丸い四角）。[inset] は線画のときに内側に寄せる量 */
    private fun PathBuilder.notebookCover(inset: Float = 0f) {
        roundRect(NOTE_LEFT + inset, NOTE_TOP + inset, NOTE_WIDTH - 2 * inset, NOTE_HEIGHT - 2 * inset, 1.4f)
    }

    /**
     * 表紙の背の縦線（リングのすぐ内側）と、右上の題名のような短い横線 2 本。
     * 前は上に札（枕のある寝台に見えた）、次は横線 3 本（文書に見えた）だったので、手帳やノートのアイコンでよく使う
     * 「背の線＋題名の線」にした
     */
    private fun PathBuilder.notebookMarks() {
        // 表紙の枠の線の内側で止める（枠からはみ出さないように）
        moveTo(NOTE_SPINE_X, NOTE_TOP + STROKE)
        lineTo(NOTE_SPINE_X, NOTE_TOP + NOTE_HEIGHT - STROKE)
        NOTE_TITLE.forEachIndexed { i, y ->
            moveTo(NOTE_SPINE_X + 2.6f, y)
            lineTo(NOTE_LEFT + NOTE_WIDTH - (if (i == NOTE_TITLE.lastIndex) 4.2f else 2.6f), y)
        }
    }

    /** 背の縦線と題名の線を、塗りの表紙からくり抜く細い四角にしたもの */
    private fun PathBuilder.notebookMarkHoles() {
        roundRect(NOTE_SPINE_X - 0.5f, NOTE_TOP + 0.9f, 1f, NOTE_HEIGHT - 1.8f, 0.5f)
        NOTE_TITLE.forEachIndexed { i, y ->
            val left = NOTE_SPINE_X + 2f
            val right = NOTE_LEFT + NOTE_WIDTH - (if (i == NOTE_TITLE.lastIndex) 3.6f else 2f)
            roundRect(left, y - 0.6f, right - left, 1.2f, 0.6f)
        }
    }

    /** リングの内側に見える所（表紙をくり抜く。リングの輪の中に、下の紙が見える） */
    private fun PathBuilder.notebookHoles() {
        NOTE_RINGS.forEach { y ->
            roundRect(NOTE_LEFT, y - RING_HALF_H, RING_HALF_W, 2 * RING_HALF_H, RING_HALF_H)
        }
    }

    /**
     * リング（左の端をまたぐ小さな輪）。前は外側だけに出る横棒だったが、ノートが右へ動いている線に見えたので、
     * 端の内と外に半分ずつかかる輪にした
     */
    private fun PathBuilder.notebookRings() {
        NOTE_RINGS.forEach { y ->
            roundRect(NOTE_LEFT - RING_HALF_W, y - RING_HALF_H, 2 * RING_HALF_W, 2 * RING_HALF_H, RING_HALF_H)
        }
    }

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        curveTo(x + w, y, x + w, y, x + w, y + r)
        lineTo(x + w, y + h - r)
        curveTo(x + w, y + h, x + w, y + h, x + w - r, y + h)
        lineTo(x + r, y + h)
        curveTo(x, y + h, x, y + h, x, y + h - r)
        lineTo(x, y + r)
        curveTo(x, y, x, y, x + r, y)
        close()
    }


    val NotebookOutline: ImageVector by lazy {
        icon("NotebookOutline") {
            outlined { notebookCover(inset = STROKE / 2) }
            ring { notebookMarks() }
            ring { notebookRings() }
        }
    }

    /** リングの線（ほかの線画より細い。太いと小さな輪がつぶれる） */
    private fun ImageVector.Builder.ring(draw: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = RING_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    /** ノートの位置と大きさ（閉じたとき。開くと、リングの所（左の端）を中心に表紙がめくれる。ui/TabIcons.kt） */
    // 縦横の比は 1：1.2 ほど（細長いと、ノートに見えにくかった）
    internal const val NOTE_LEFT = 6f
    internal const val NOTE_TOP = 3f
    internal const val NOTE_WIDTH = 15f
    internal const val NOTE_HEIGHT = 18f

    /** リングの高さ（4 つ）と、輪の半分の幅・高さ、線の太さ */
    internal val NOTE_RINGS = floatArrayOf(6.3f, 10.1f, 13.9f, 17.7f)
    internal const val RING_HALF_W = 1.9f
    internal const val RING_HALF_H = 0.95f
    internal const val RING_STROKE = 1.2f

    /** 背の縦線の x と、題名の線の高さ（2 本） */
    private const val NOTE_SPINE_X = NOTE_LEFT + 3.4f
    private val NOTE_TITLE = floatArrayOf(7.6f, 10.6f)

    // ---- 積み木（下に 2 つ、上に 1 つ） ----

    private fun PathBuilder.block(x: Float, y: Float, s: Float, r: Float = 1.3f) {
        moveTo(x + r, y)
        lineTo(x + s - r, y)
        curveTo(x + s, y, x + s, y, x + s, y + r)
        lineTo(x + s, y + s - r)
        curveTo(x + s, y + s, x + s, y + s, x + s - r, y + s)
        lineTo(x + r, y + s)
        curveTo(x, y + s, x, y + s, x, y + s - r)
        lineTo(x, y + r)
        curveTo(x, y, x, y, x + r, y)
        close()
    }

    private const val BLOCK = 9.6f

    /** 三角の積み木（頂点が上、底辺が下）。[shrink] は線画のときに内側に寄せる量 */
    private fun PathBuilder.triangle(cx: Float, top: Float, w: Float, h: Float, r: Float = 1.2f, shrink: Float = 0f) {
        // 角を少し丸めた三角。shrink は角の丸みを増やすことで近似する
        val rr = r + shrink
        val halfW = w / 2 - shrink * 1.2f
        val t = top + shrink * 1.5f
        val b = top + h - shrink
        moveTo(cx - rr * 0.6f, t + rr)
        curveTo(cx - rr * 0.2f, t, cx + rr * 0.2f, t, cx + rr * 0.6f, t + rr)
        lineTo(cx + halfW - rr * 0.4f, b - rr * 1.4f)
        curveTo(cx + halfW + rr * 0.3f, b - rr * 0.3f, cx + halfW, b, cx + halfW - rr, b)
        lineTo(cx - halfW + rr, b)
        curveTo(cx - halfW, b, cx - halfW - rr * 0.3f, b - rr * 0.3f, cx - halfW + rr * 0.4f, b - rr * 1.4f)
        close()
    }


    val BlocksOutline: ImageVector by lazy {
        icon("BlocksOutline") {
            // 線が太いぶん、少し内側に
            val i = STROKE / 2
            outlined { block(1.5f + i, 12.4f + i, BLOCK - STROKE) }
            outlined { ellipse(17.7f, 17.2f, 4.8f - i, 4.8f - i) }
            outlined { triangle(12f, 1.8f, 11.4f, 9.8f, shrink = i) }
        }
    }

    // ---- トロフィー（みんな。カップ・左右の取っ手・柱・台） ----

    /** カップ（上が平らで、下が丸い） */
    private fun PathBuilder.trophyCup(inset: Float = 0f) {
        val l = 6.5f + inset
        val r = 17.5f - inset
        val t = 3f + inset
        moveTo(l, t)
        lineTo(r, t)
        lineTo(r, 8f)
        curveTo(r, 11.2f - inset * 0.5f, 15.1f, 13.6f - inset, 12f, 13.6f - inset)
        curveTo(8.9f, 13.6f - inset, l, 11.2f - inset * 0.5f, l, 8f)
        close()
    }

    /** 左右の取っ手（線） */
    private fun PathBuilder.trophyEars() {
        moveTo(6.6f, 5.2f); lineTo(4.6f, 5.2f)
        curveTo(3.7f, 5.2f, 3.2f, 5.8f, 3.3f, 6.7f)
        curveTo(3.6f, 8.7f, 5.1f, 10.1f, 7.3f, 10.5f)
        moveTo(17.4f, 5.2f); lineTo(19.4f, 5.2f)
        curveTo(20.3f, 5.2f, 20.8f, 5.8f, 20.7f, 6.7f)
        curveTo(20.4f, 8.7f, 18.9f, 10.1f, 16.7f, 10.5f)
    }

    /** 柱と台 */
    private fun PathBuilder.trophyStand() {
        // 柱
        moveTo(10.9f, 13.2f); lineTo(13.1f, 13.2f); lineTo(13.1f, 17.6f); lineTo(10.9f, 17.6f); close()
        // 台（角の丸い横長の四角）
        moveTo(8.6f, 17.4f); lineTo(15.4f, 17.4f)
        curveTo(16.1f, 17.4f, 16.6f, 17.9f, 16.6f, 18.6f)
        lineTo(16.6f, 20.4f)
        curveTo(16.6f, 21.1f, 16.1f, 21.6f, 15.4f, 21.6f)
        lineTo(8.6f, 21.6f)
        curveTo(7.9f, 21.6f, 7.4f, 21.1f, 7.4f, 20.4f)
        lineTo(7.4f, 18.6f)
        curveTo(7.4f, 17.9f, 7.9f, 17.4f, 8.6f, 17.4f)
        close()
    }


    // ---- 家（タブバーの左の、ホームへ戻るボタン。ui/TabBar.kt） ----

    /** 家の線画：三角の屋根と、四角い壁、まん中の戸。ほかのタブの線画と同じ太さ */
    val HomeOutline: ImageVector by lazy {
        icon("HomeOutline") {
            outlined {
                // 屋根（軒を壁より少し外へ出す）
                moveTo(3.2f, 11.2f)
                lineTo(12f, 3.8f)
                lineTo(20.8f, 11.2f)
                // 壁
                moveTo(5.8f, 9.4f)
                lineTo(5.8f, 20.2f)
                lineTo(18.2f, 20.2f)
                lineTo(18.2f, 9.4f)
                // 戸
                moveTo(10f, 20.2f)
                lineTo(10f, 14.6f)
                lineTo(14f, 14.6f)
                lineTo(14f, 20.2f)
            }
        }
    }

    val TrophyOutline: ImageVector by lazy {
        icon("TrophyOutline") {
            val i = STROKE / 2
            outlined { trophyCup(inset = i) }
            outlined { trophyEars() }
            // 柱は 1 本の線、台は線の太さぶん内側に
            outlined { moveTo(12f, 13.6f - i); lineTo(12f, 17.4f + i) }
            outlined {
                moveTo(8.6f + i, 17.4f + i); lineTo(15.4f - i, 17.4f + i)
                lineTo(15.4f - i, 21.6f - i); lineTo(8.6f + i, 21.6f - i); close()
            }
        }
    }

    // ---- 積む画面のモード（モードを選ぶ画面の積み木に載せる塗りのアイコン。ui/game/ModeSelect.kt） ----

    /** ストップウォッチ（3 分チャレンジ）：丸い文字盤に針をくり抜き、上に押すつまみ、右上に小さなボタン */
    val Stopwatch: ImageVector by lazy {
        icon("Stopwatch") {
            // 文字盤と、くり抜いた針（12 時から少し右へ。偶奇の塗りで穴にする）
            filled(evenOdd = true) {
                ellipse(12f, 13.6f, 8.2f, 8.2f)
                moveTo(11.1f, 8.4f)
                lineTo(12.9f, 8.4f)
                lineTo(12.9f, 13.1f)
                lineTo(15.6f, 15.8f)
                lineTo(14.3f, 17.1f)
                lineTo(11.1f, 13.9f)
                close()
            }
            // 上のつまみ（横長の頭と、文字盤へつながる軸）
            filled { roundRect(9.4f, 1.6f, 5.2f, 2.3f, 1f) }
            filled { roundRect(11.1f, 3.4f, 1.8f, 2.5f, 0.4f) }
            // 右上の小さなボタン（斜め）
            filled {
                moveTo(17.3f, 6.1f)
                lineTo(18.7f, 4.7f)
                lineTo(20.5f, 6.5f)
                lineTo(19.1f, 7.9f)
                close()
            }
        }
    }

    /** カレンダー（毎日 1 つずつ積む）：上に 2 つのリング、見出しの線と日のます目をくり抜く */
    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            filled(evenOdd = true) {
                roundRect(2.8f, 4.4f, 18.4f, 17f, 2.4f)
                // 見出しと日付のあいだの線
                roundRect(2.8f, 8.9f, 18.4f, 1.2f, 0f)
                // 日のます目（2 段 × 3 列）
                for (row in 0..1) {
                    for (col in 0..2) {
                        val l = 5.6f + col * 4.7f
                        val t = 12.3f + row * 4.5f
                        roundRect(l, t, 3.4f, 2.9f, 0.6f)
                    }
                }
            }
            // リング
            filled { roundRect(6.6f, 2.0f, 2.4f, 4.6f, 1.2f) }
            filled { roundRect(15.0f, 2.0f, 2.4f, 4.6f, 1.2f) }
        }
    }

    /**
     * 入れ替え（積む画面の通常モードの「チェンジ」）：上は右向き、下は左向きの矢印（トランプのリバースのように、向きが逆の 2 本）。
     * 積み木の上で読めるよう、ほかの線画より太い線で描く
     */
    val Swap: ImageVector by lazy {
        icon("Swap") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 上の矢印（左から右へ）
                moveTo(4f, 8f); lineTo(19f, 8f)
                moveTo(15f, 4f); lineTo(19f, 8f); lineTo(15f, 12f)
                // 下の矢印（右から左へ）
                moveTo(20f, 16f); lineTo(5f, 16f)
                moveTo(9f, 12f); lineTo(5f, 16f); lineTo(9f, 20f)
            }
        }
    }

    // ---- 動かすアイコン用の形（下のタブで、選ばれたときに部品ごとに動かす。ui/TabIcons.kt） ----

    private fun shape(build: PathBuilder.() -> Unit): Path = PathBuilder().apply(build).nodes.toPath()

    /** ノートの表紙（背の線・題名の線・リングの穴をくり抜く。偶奇の塗りで描く） */
    internal val notebookCoverPath: Path by lazy {
        shape { notebookCover(); notebookMarkHoles(); notebookHoles() }.apply { fillType = PathFillType.EvenOdd }
    }

    /** 積み木：下の四角と丸、上の三角 */
    internal val blockSquarePath: Path by lazy { shape { block(1.5f, 12.4f, BLOCK) } }
    internal val blockCirclePath: Path by lazy { shape { ellipse(17.7f, 17.2f, 4.8f, 4.8f) } }
    internal val blockTrianglePath: Path by lazy { shape { triangle(12f, 1.8f, 11.4f, 9.8f) } }

    /** トロフィー：カップ（塗り）、取っ手（線。太さは [STROKE_WIDTH]）、柱と台（塗り） */
    internal val trophyCupPath: Path by lazy { shape { trophyCup() } }
    internal val trophyEarsPath: Path by lazy { shape { trophyEars() } }
    internal val trophyStandPath: Path by lazy { shape { trophyStand() } }

    /** 線画の線の太さ（24 の目盛りで） */
    internal const val STROKE_WIDTH = STROKE
}
