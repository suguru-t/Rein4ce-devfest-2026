package com.rein4ce.roadcollector.ui.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * D 担当：積む画面の「ワールド座標（m、y 上向き、台の上面が y=0）→ キャンバス（px）」の変換。
 * パーツや台を描く位置も、指で動かした量を m に直すのも、同じこの変換を使う（縮めても、指の動きとゴーストがずれない）。
 *
 * - 横：x=0（台の真ん中）がキャンバスの真ん中
 * - 縦：台の上面（y=0）がキャンバスの上から [PLATFORM_ANCHOR] の位置（今までと同じ。台は動かさない）
 * - 倍率：[zoom] × (キャンバスの幅 ÷ [DEFAULT_WORLD_WIDTH_M])。zoom = 1 が今までの大きさで、塔が入りきらないときだけ小さくする
 */
data class StackViewport(val canvasWidth: Float, val canvasHeight: Float, val zoom: Float = 1f) {

    /** 1 m が何 px か */
    val scale: Float get() = canvasWidth / DEFAULT_WORLD_WIDTH_M * zoom

    /** ワールドの x=0 のキャンバス上の x [px] */
    val originX: Float get() = canvasWidth / 2f

    /** ワールドの y=0（台の上面）のキャンバス上の y [px] */
    val originY: Float get() = canvasHeight * PLATFORM_ANCHOR

    fun toScreenX(x: Float): Float = originX + x * scale
    fun toScreenY(y: Float): Float = originY - y * scale
    fun toWorldX(px: Float): Float = (px - originX) / scale
    fun toWorldY(py: Float): Float = (originY - py) / scale

    /** 指で横に動かした量 [px] を m に直す（描くときと同じ倍率を使う） */
    fun dragToMeters(dxPx: Float): Float = dxPx / scale

    companion object {
        /** zoom = 1 のとき、キャンバスの幅に入れるワールドの幅 [m]（今までの見え方） */
        const val DEFAULT_WORLD_WIDTH_M = 5f

        /** 台の上面を置く高さ（キャンバスの上からの割合）。今までと同じ 78% */
        const val PLATFORM_ANCHOR = 0.78f

        /**
         * 入れる範囲の外側に残す余白 [m]。パーツがキャンバスの縁に触れないように。
         * 横は小さくする：これから置くパーツの範囲は「どう回しても入る円」で見ていて、もともと広めなので、
         * 横も上下と同じだけ空けると、始めた時点から少し縮んでしまう
         */
        const val MARGIN_X_M = 0.05f
        const val MARGIN_Y_M = 0.15f

        /** これより小さくは縮めない（パーツが小さすぎて見分けられなくなるため） */
        const val MIN_ZOOM = 0.2f

        /**
         * [focus]（台・置いたパーツ・これから置くパーツが動ける範囲 [m]）が全部入る zoom。今までの大きさで入るなら 1。
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

/** ワールド座標の長方形 [m]（y 上向き） */
data class WorldRect(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    fun union(other: WorldRect): WorldRect =
        WorldRect(min(minX, other.minX), max(maxX, other.maxX), min(minY, other.minY), max(maxY, other.maxY))
}
