package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 「回す」を押している間に回る速さ [度/秒]（決定 71）。半回転に 1.5 秒。
 * 前は 15° ずつ回し、押し続けると 120 ms ごとに 15° 回していた（1 秒に約 125°。#114）。速さはそのくらいのまま、きざみをなくした
 */
internal const val ROTATE_SPEED_DEG_PER_S = 120f

/** 読み上げ（TalkBack）で 1 回押したときに回す角度 [度]。読み上げでは押し続けられないので、決まった量ずつ回す */
internal const val ROTATE_A11Y_STEP_DEG = 10f

/** 回している間に軽く振動する間隔 [度]（設定の「振動」に従う。どれだけ回ったかを指で分かるように） */
internal const val ROTATE_TICK_DEG = 45f

/** 1 コマで回す時間の上限 [秒]（コマが飛んだときに、一度に大きく回らないように） */
private const val MAX_FRAME_S = 0.05f

/**
 * ⟲ と ⟳。[sign] は、回す向き（⟲ が +、⟳ が −。積む画面の回し方と同じ）
 */
enum class RotateDirection(val symbol: String, private val side: String, val sign: Float) {
    LEFT("⟲", "左", 1f),
    RIGHT("⟳", "右", -1f),
    ;

    /** ボタンの字（「⟲」） */
    val text: String get() = symbol

    /** 読み上げの説明（「左に回す」） */
    val label: String get() = "${side}に回す"
}

/**
 * 押している間、コマの時刻から回す角度を決める（決定 71）。押し始めのコマ（[start]）では回さず、
 * そこから [degPerSecond] の速さで回す。角度にきざみは無い（離したところの向きのまま）
 */
internal class TurnClock(private val degPerSecond: Float = ROTATE_SPEED_DEG_PER_S) {
    private var last = 0L
    private var sinceTick = 0f

    /** 押し始めたコマ [ns] */
    fun start(frameNanos: Long) {
        last = frameNanos
        sinceTick = 0f
    }

    /** [frameNanos] のコマで回す角度 [度]（向きは付けない。前のコマからの時間ぶん。上限は [MAX_FRAME_S] ぶん） */
    fun advance(frameNanos: Long): Float {
        val dt = ((frameNanos - last) / 1e9f).coerceIn(0f, MAX_FRAME_S)
        last = frameNanos
        val degrees = degPerSecond * dt
        sinceTick += degrees
        return degrees
    }

    /** 前の振動から [ROTATE_TICK_DEG] 回ったか（回ったら数え直す） */
    fun tick(): Boolean {
        if (sinceTick < ROTATE_TICK_DEG) return false
        sinceTick -= ROTATE_TICK_DEG
        return true
    }
}

/**
 * 回転のボタン 1 つ。**押している間、なめらかに回り続け、離したら止まる**（決定 71。[ROTATE_SPEED_DEG_PER_S]）。
 * 角度にきざみは無く、離したところの向きのまま（前は 15° ずつ回り、押し続けると 15° ずつ回り続けた。#114）。
 * 軽く触って離せば、少しだけ回る。指をボタンの外へずらしたら止まる。裏に回ったときや画面を離れたときも止まる。
 * 回している間は [ROTATE_TICK_DEG] ごとに軽く振動する（設定の「振動」に従う）。押した瞬間の振動と音は、ほかのボタンと同じ押し心地（pressFeel）から鳴る。
 * 読み上げ（TalkBack）では押し続けられないので、1 回押すごとに [ROTATE_A11Y_STEP_DEG] 回す。
 * 見た目は、ほかの画面のボタン（`PopButton`）と同じ積み木（オレンジの面・下にのぞく側面・太い丸ゴシックの白い字）に、
 * 回る矢印のアイコン（決定 67）。押せないときは灰色
 *
 * @param onRotate 回す角度 [度]（+ が ⟲）。押している間、毎コマ呼ぶ。狙っている間だけ回る（落下中などは、呼ぶ側の VM が何もしない）
 * @param sessionKey 次のゲームで、押している状態ごと作り直す
 * @param text ボタンの字（既定は「⟲」など）。積む画面は時計回りの 1 つだけにしたので「回す」（[TURN_BUTTON_TEXT]。決定 67）
 * @param label 読み上げの説明（既定は「左に回す」など）
 */
@Composable
fun RotateButton(
    direction: RotateDirection,
    sessionKey: Any,
    onRotate: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    text: String = direction.text,
    label: String = direction.label,
) {
    key(sessionKey) {
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        val currentRotate by rememberUpdatedState(onRotate)
        val haptics = rememberHaptics()
        val source = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .pressFeel(source)
                // 押している間だけ、毎コマ回す。裏に回ったら（active が変わる）、押している途中でも止める
                .pointerInput(active) {
                    if (!active) return@pointerInput
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val press = PressInteraction.Press(down.position)
                            source.tryEmit(press)
                            val turning = launch {
                                val clock = TurnClock()
                                clock.start(withFrameNanos { it })
                                while (isActive) {
                                    withFrameNanos { now ->
                                        currentRotate(direction.sign * clock.advance(now))
                                        if (clock.tick()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                }
                            }
                            // 離した・外へずらした・ほかに取られたら止める
                            val up = waitForUpOrCancellation()
                            turning.cancel()
                            if (up != null) {
                                up.consume()
                                source.tryEmit(PressInteraction.Release(press))
                            } else {
                                source.tryEmit(PressInteraction.Cancel(press))
                            }
                        }
                    }
                }
                .popBlock(face = if (active) Pop.OrangeDeep else Fieldbook.GridMajor, corner = 14.dp)
                .heightIn(min = 48.dp)
                .padding(contentPadding)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    if (active) {
                        onClick(label) {
                            currentRotate(direction.sign * ROTATE_A11Y_STEP_DEG)
                            true
                        }
                    } else {
                        disabled()
                    }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                // 回る向きの矢印（Material の「再読み込み」は時計回り。⟲ は左右を返す）
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (active) Pop.Letter else Fieldbook.Muted,
                    modifier = Modifier.size(20.dp).graphicsLayer { scaleX = if (direction.sign > 0f) -1f else 1f },
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text,
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = if (active) Pop.Letter else Fieldbook.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 積む画面の「回す」のボタン（時計回りの 1 つだけ。決定 67）の字と読み上げ */
internal const val TURN_BUTTON_TEXT = "回す"
internal const val TURN_BUTTON_LABEL = "時計回りに回す。押している間、回り続ける"
