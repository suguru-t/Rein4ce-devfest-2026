package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * 積む画面（通常・3 分チャレンジ）のキャンバスの操作（決定 67）：指を置いて横に動かすと狙う形が動き、**指を離すと落ちる**。
 * 「置く」のボタンと ◀▶ は無い（押すボタンを減らして、画面の操作をドラッグとチェンジ・回すだけにする）。
 * 動かさずに軽く触って離しても、その場所で落ちる。
 *
 * - [onDrag] は、横に動いた量 [px]（右が +）。キャンバスの変換（StackViewport）で m に直すのは呼ぶ側
 * - [onRelease] は、指を離したとき。落とすかどうか（狙っている間だけ・自動で落ちるときは落とさない等）は呼ぶ側が決める
 * - 2 本目の指は見ない（最初の指だけ）。指が取り消された（画面の端からのジェスチャーなど）ときは落とさない
 *
 * @param key これが変わったら、ジェスチャーを受け付け直す（ゲームの作り直しなど）
 */
internal fun Modifier.aimByDrag(key: Any?, onDrag: (dxPx: Float) -> Unit, onRelease: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val id = down.id
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: return@awaitEachGesture
                if (!change.pressed) {
                    // ほかに取られた（consumed）ときは落とさない
                    if (!change.isConsumed) {
                        change.consume()
                        onRelease()
                    }
                    return@awaitEachGesture
                }
                val dx = change.positionChange().x
                if (dx != 0f) {
                    change.consume()
                    onDrag(dx)
                }
            }
        }
    }

/**
 * ドラッグの量（m）を、決まった幅（[stepM]）ずつの動きに直す。3 分チャレンジは、前の ◀▶ と同じ 10 cm きざみで動かすので、
 * ドラッグにしても、狙える位置は変わらない（決まり（記録の ID）を変えずに済む）。端数は次のドラッグに持ち越す
 */
internal class DragSteps(private val stepM: Double) {
    private var pending = 0.0

    /** [meters] 動いたぶんを足し、今動かすきざみの数（右が +、左が −）を返す */
    fun add(meters: Double): Int {
        pending += meters
        val steps = (pending / stepM).toInt() // 0 の方へ切り捨て（左右どちらも、1 きざみに届いてから動く）
        pending -= steps * stepM
        return steps
    }

    /** 指を離したときなどに、持ち越しの端数を捨てる */
    fun reset() {
        pending = 0.0
    }

    /** いま持ち越している端数 [m]（テスト用） */
    internal val remainder: Double get() = pending
}
