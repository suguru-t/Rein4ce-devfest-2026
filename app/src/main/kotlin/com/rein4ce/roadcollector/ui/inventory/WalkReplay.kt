package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.PopButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * コレクションの拡大表示の「もう一度歩く」：パーツの道を、歩いた順になぞって見せる（止めるのは「立ち止まる」）。
 * 歩き始めに赤い旗、終わりに青い旗（記録画面の地図と同じ。ui/collect/StartGoalMarks.kt）を立て、
 * 足跡が向きを変えながらゴールの旗へ歩いていく。歩いたところまでの道を濃く、まだ歩いていない道をうすく出す。
 *
 * パーツに残っているのは角を残して簡単にした形と歩いた長さだけ（記録したときの点と時刻は残していない）なので、
 * 同じ速さで歩いたものとしてなぞる。どのパーツも [REPLAY_MS] で歩き終わるようにし、
 * 時速 [WALK_KMH] km で歩いたとしたら何倍の速さかを「約 ○ 倍速」と出す（[replaySpeed]）
 */

/** なぞっているところ。[progress] は道の長さの 0〜1、[steps] は足跡が進んだ歩数（[FieldbookPart] に渡す） */
@Immutable
data class WalkTraceFrame(val progress: Float, val steps: Float)

/** 再生の進み具合。拡大表示の窓を閉じると止まる（窓の rememberCoroutineScope で動かすため） */
@Stable
internal class WalkReplay(private val scope: CoroutineScope) {
    /** なぞっているところ。再生していなければ null（ふつうの図） */
    var frame by mutableStateOf<WalkTraceFrame?>(null)
        private set

    /** 再生しているか（歩き終わって、ゴールで少し止まっている間も含む） */
    var playing by mutableStateOf(false)
        private set

    private var job: Job? = null

    /** 歩き始めから再生する（再生中なら、初めからやり直す） */
    fun play() {
        job?.cancel()
        job = scope.launch {
            val me = coroutineContext[Job]
            playing = true
            try {
                // 画面を描き直す時刻で進める（端末で「動きを減らす」にしていても、押して見るものなので動かす。
                // Compose の動き（animateTo など）は、その設定で一瞬で終わってしまう）
                val start = withFrameMillis { it }
                while (true) {
                    val t = withFrameMillis { it } - start
                    val progress = (t / REPLAY_MS.toFloat()).coerceAtMost(1f)
                    frame = WalkTraceFrame(progress, steps = stepsAt(t.coerceAtMost(REPLAY_MS)))
                    if (progress >= 1f) break
                }
                // ゴールの旗の横で少し止まってから、ふつうの図に戻す
                delay(END_HOLD_MS)
            } finally {
                // やり直したときは、新しい再生を消さない
                if (job === me) {
                    frame = null
                    playing = false
                }
            }
        }
    }

    /** 止めて、ふつうの図に戻す */
    fun stop() {
        job?.cancel()
        job = null
        frame = null
        playing = false
    }
}

@Composable
internal fun rememberWalkReplay(): WalkReplay {
    val scope = rememberCoroutineScope()
    return remember(scope) { WalkReplay(scope) }
}

/**
 * 「もう一度歩く」ボタン（歩いている間は「立ち止まる」）。拡大表示の窓の「みんな」と同じブロックのボタン（青緑の面に白い字）。
 * 押すたびに字が入れ替わるので、読み上げでも今できることが分かる
 */
@Composable
internal fun ReplayButton(replay: WalkReplay, modifier: Modifier = Modifier) {
    PopButton(
        label = if (replay.playing) "立ち止まる" else "もう一度歩く",
        onClick = { if (replay.playing) replay.stop() else replay.play() },
        face = Pop.TealDeep,
        text = Pop.Letter,
        fontSize = 15.sp,
        modifier = modifier,
    )
}

/**
 * 時速 [WALK_KMH] km で歩いたとしたら、[REPLAY_MS] で歩き終わる再生は何倍の速さか。
 * 100 倍より速ければ 10 倍きざみに丸める。歩いた長さが分からなければ null
 */
internal fun replaySpeed(distanceM: Int): Int? {
    if (distanceM <= 0) return null
    val walkSeconds = distanceM / (WALK_KMH * 1000f / 3600f)
    val x = walkSeconds / (REPLAY_MS / 1000f)
    return if (x >= 100f) (x / 10f).roundToInt() * 10 else x.roundToInt().coerceAtLeast(1)
}

/** 再生を始めてから [tMs] たったときに、足跡が進んだ歩数。1 歩ずつ、踏み出して止まる（記録画面の足跡と同じ動き方） */
private fun stepsAt(tMs: Long): Float {
    val x = tMs / STEP_MS.toFloat()
    val n = floor(x)
    return n + FastOutSlowInEasing.transform(x - n)
}

/** どのパーツも、この時間で歩き終わる [ms] */
private const val REPLAY_MS = 6000L

/** 歩き終わってから、ゴールで止まっている時間 [ms] */
private const val END_HOLD_MS = 1200L

/** 足跡の 1 歩の時間 [ms] */
private const val STEP_MS = 350f

/** 何倍速かを出すときの、ふつうに歩く速さ [km/h] */
private const val WALK_KMH = 4.8f
