package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * 狙う所（キャンバス）の指の扱い（決定 67）。触れている間は横に動かした量を [onDrag]（px）で渡し、指を離したら [onRelease]（落とす）。
 * 「置く」と ◀▶ のボタンの代わり。ドラッグせずに触れて離しただけでも、その位置で落ちる。
 *
 * - 触れたときに狙っていなかった（[aimKey] が null。落下中・終わったあとなど）なら、その指では何もしない
 * - 触れたときと離したときで狙っているもの（[aimKey]）が変わっていたら、離しても落とさない
 *   （落下中に触れたまま次のパーツが出て、離したとたんに落ちる、とならないように）
 * - 2 本目の指は見ない。最初に触れた指だけで動かして落とす
 *
 * [aimKey]・[onDrag]・[onRelease] は、指を動かすたびにその時の値を読む（呼ぶ側が rememberUpdatedState で渡す前提）
 *
 * @param key 指の扱いを作り直す鍵（画面の VM など）
 */
internal fun Modifier.dragThenDrop(
    key: Any,
    aimKey: () -> Any?,
    onDrag: (dxPx: Float) -> Unit,
    onRelease: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val aimed = aimKey() ?: return@awaitEachGesture
        down.consume()
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) {
                change.consume()
                if (aimKey() == aimed) onRelease()
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
