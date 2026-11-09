package com.rein4ce.roadcollector.ui.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * D 担当：積む画面の「ワールド座標（m、y 上向き、台の上面が y=0）→ キャンバス（px）」の変換。
 * パーツや台を描く位置も、指で動かした量を m に直すのも、同じこの変換を使う（カメラを動かしても、指の動きとゴーストがずれない）。
 *
 * - 横：x=0（台の真ん中）がキャンバスの真ん中
 * - 縦：ワールドの y = [cameraY] が、キャンバスの上から [PLATFORM_ANCHOR] の位置。cameraY = 0 なら台の上面がそこに来る（今までと同じ）
 * - 倍率：[zoom] × (キャンバスの幅 ÷ [DEFAULT_WORLD_WIDTH_M])。zoom = 1 が今までの大きさ
 *
 * カメラの動かし方は 2 つ（[CameraTarget]）：狙っている間と落下中は大きさを変えずに上へ動かし（[follow]）、
 * 置き切ったら塔全体と台が入るように引く（[overview]）
 */
data class StackViewport(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val zoom: Float = 1f,
    val cameraY: Float = 0f,
) {

    /** 1 m が何 px か */
    val scale: Float get() = canvasWidth / DEFAULT_WORLD_WIDTH_M * zoom

    /** ワールドの x=0 のキャンバス上の x [px] */
    val originX: Float get() = canvasWidth / 2f

    /** ワールドの y = [cameraY] のキャンバス上の y [px]（cameraY = 0 なら台の上面） */
    val anchorY: Float get() = canvasHeight * PLATFORM_ANCHOR

    fun toScreenX(x: Float): Float = originX + x * scale
    fun toScreenY(y: Float): Float = anchorY - (y - cameraY) * scale
    fun toWorldX(px: Float): Float = (px - originX) / scale
    fun toWorldY(py: Float): Float = cameraY + (anchorY - py) / scale

    /** 指で横に動かした量 [px] を m に直す（描くときと同じ倍率を使う） */
    fun dragToMeters(dxPx: Float): Float = dxPx / scale

    companion object {
        /** zoom = 1 のとき、キャンバスの幅に入れるワールドの幅 [m]（今までの見え方） */
        const val DEFAULT_WORLD_WIDTH_M = 5f

        /** 台の上面を置く高さ（キャンバスの上からの割合）。今までと同じ 78% */
        const val PLATFORM_ANCHOR = 0.78f

        /**
         * 入れる範囲の外側に残す余白 [m]。パーツがキャンバスの縁に触れないように。
         * 横は小さくする：台の幅いっぱいに積んだ塔が、余白のためだけに縮まないように
         */
        const val MARGIN_X_M = 0.05f
        const val MARGIN_Y_M = 0.15f

        /** これより小さくは縮めない（パーツが小さすぎて見分けられなくなるため） */
        const val MIN_ZOOM = 0.2f

        /**
         * 狙っている間と落下中のカメラ。大きさは今までのまま（zoom = 1）で、[top]（次のパーツが出てくる範囲のいちばん上 [m]）が
         * キャンバスに入るところまでカメラを上げる。入るなら上げない（台の上面はいつもの位置）。
         * 塔が高いと台はキャンバスの下へ外れるが、狙うときに見たいのは、塔のてっぺんと次のパーツなので、それでよい
         * （小さい画面でも、次のパーツの上が切れない）
         */
        fun follow(canvasWidth: Float, canvasHeight: Float, top: Float): StackViewport {
            val viewport = StackViewport(canvasWidth, canvasHeight)
            if (canvasWidth <= 0f || canvasHeight <= 0f) return viewport
            // cameraY = 0 のとき、台の上面より上に見える高さ [m]
            val visibleAbove = viewport.anchorY / viewport.scale
            return viewport.copy(cameraY = max(0f, top + MARGIN_Y_M - visibleAbove))
        }

        /**
         * 置き切ったときのカメラ。台の位置（[PLATFORM_ANCHOR]）はそのままで、[focus]（台と残ったパーツ [m]）が全部入るように引く。
         * 今までの大きさで入るなら引かない
         */
        fun overview(canvasWidth: Float, canvasHeight: Float, focus: WorldRect): StackViewport =
            StackViewport(canvasWidth, canvasHeight, zoom = zoomToFit(canvasWidth, canvasHeight, focus))

        /**
         * [focus]（[m]）が全部入る zoom。今までの大きさで入るなら 1。
         * 縦横同じ倍率で縮める。台の位置（[PLATFORM_ANCHOR]）と横の真ん中（x=0）は動かさないので、
         * 台より上・台より下・左右の遠いほう、のうち一番きついところに合わせる
         */
        fun zoomToFit(canvasWidth: Float, canvasHeight: Float, focus: WorldRect): Float {
            if (canvasWidth <= 0f || canvasHeight <= 0f) return 1f
            val base = canvasWidth / DEFAULT_WORLD_WIDTH_M
            val halfWidth = max(abs(focus.minX), abs(focus.maxX)) + MARGIN_X_M
            val above = max(focus.maxY, 0f) + MARGIN_Y_M
            val below = max(-focus.minY, 0f) + MARGIN_Y_M
            val fitX = (canvasWidth / 2f) / (halfWidth * base)
            val fitAbove = canvasHeight * PLATFORM_ANCHOR / (above * base)
            val fitBelow = canvasHeight * (1f - PLATFORM_ANCHOR) / (below * base)
            return min(1f, min(fitX, min(fitAbove, fitBelow))).coerceAtLeast(MIN_ZOOM)
        }
    }
}

/**
 * 積む画面のカメラの狙い。VM がパーツの止まったときだけ決め（[StackViewModel.camera]）、画面がキャンバスの大きさに合わせて
 * [StackViewport] にする
 */
sealed interface CameraTarget {
    /**
     * 狙っている間と落下中：大きさは変えず、次のパーツが出てくる範囲のいちばん上 [top] [m] が入るところまでカメラを上げる
     * （[StackViewport.follow]）
     */
    data class Follow(val top: Float) : CameraTarget

    /** 置き切ったあと：台と、台の上に残ったパーツ [focus] が全部入るように引く（[StackViewport.overview]） */
    data class Overview(val focus: WorldRect) : CameraTarget
}

/** ワールド座標の長方形 [m]（y 上向き） */
data class WorldRect(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    fun union(other: WorldRect): WorldRect =
        WorldRect(min(minX, other.minX), max(maxX, other.maxX), min(minY, other.minY), max(maxY, other.maxY))
}
