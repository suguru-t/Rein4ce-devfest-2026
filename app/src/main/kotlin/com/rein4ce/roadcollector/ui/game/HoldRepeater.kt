package com.rein4ce.roadcollector.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * 押し続けている間、同じことを繰り返す仕組み（回すボタン。ui/game/RotateButtons.kt）。
 * もとは ◀▶（左右に 5 cm ずつ動かすボタン。#88）のためのものだったが、◀▶ は決定 67 でやめた（ドラッグして指を離すと落ちる形にしたため）
 */

/** 長押しで続けて繰り返す間隔の既定 [ms] */
internal const val MOVE_REPEAT_MS = 80L

/**
 * 長押しの間、[MOVE_REPEAT_MS] ごとに動かし続ける。始めた瞬間に 1 回動かす。
 * 別の向きで始め直すと、前の分は止める
 *
 * @param intervalMs 続けて動かす間隔 [ms]（回転のボタンは [ROTATE_REPEAT_MS]。#114）
 */
internal class MoveRepeater(private val scope: CoroutineScope, private val intervalMs: Long = MOVE_REPEAT_MS) {
    private var job: Job? = null

    fun start(step: () -> Unit) {
        stop()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (isActive) {
                step()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
