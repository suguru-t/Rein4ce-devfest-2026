package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/** 回転のボタンを 1 回押したときに回す角度 [度] */
internal const val ROTATE_STEP_DEG = 15f

/**
 * 回転のボタンを押し続けたときに、次の [ROTATE_STEP_DEG] を回すまでの間隔 [ms]（#114）。
 * 1 秒に約 125°（半回転に約 1.4 秒）。15° ずつ回すので、離したときも 15° きざみのまま（そろえ直さない）
 */
internal const val ROTATE_REPEAT_MS = 120L

/**
 * ⟲ と ⟳。[sign] は、押したときに回す向き（⟲ が +、⟳ が −。積む画面の回し方と同じ）
 */
enum class RotateDirection(val symbol: String, private val side: String, val sign: Float) {
    LEFT("⟲", "左", 1f),
    RIGHT("⟳", "右", -1f),
    ;

    /** ボタンの字（「⟲ 15°」） */
    val text: String get() = "$symbol ${ROTATE_STEP_DEG.toInt()}°"

    /** 読み上げの説明（「左に 15° 回す」） */
    val label: String get() = "${side}に ${ROTATE_STEP_DEG.toInt()}° 回す"
}

/**
 * 回転のボタン 1 つ（#114）。押すと [ROTATE_STEP_DEG] 回し、押し続けると [ROTATE_REPEAT_MS] ごとに回し続ける（離したら止まる）。
 * 回り始めるまでの間は、端末の長押しの時間（前の ◀▶ と同じ）。回し続けている間は、15° ごとに軽く振動する
 * （設定の「振動」に従う）。押した瞬間の振動は、ほかのボタンと同じ押し心地（pressFeel）から鳴る。
 *
 * 続けて回すのを止める場面（離す・外へずらす・裏に回る・画面を離れる・次のゲーム）と、読み上げの長押しの扱い（1 回だけ回す）は、
 * 前の ◀▶ と同じ。
 * 見た目は、ほかの画面のボタン（`PopButton`）と同じ積み木（オレンジの面・下にのぞく側面・太い丸ゴシックの白い字）に、
 * 回る矢印のアイコン（決定 67。前は Material の枠だけのボタンだった）。押せないときは灰色
 *
 * @param onRotate 回す角度 [度]（+ が ⟲）。狙っている間だけ回る（落下中などは、呼ぶ側の VM が何もしない）
 * @param sessionKey 次のゲームで、長押しの状態ごと作り直す
 * @param text ボタンの字（既定は「⟲ 15°」など）。積む画面は時計回りの 1 つだけにしたので「回す」（[TURN_BUTTON_TEXT]。決定 67）
 * @param label 読み上げの説明（既定は「左に 15° 回す」など）
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
        val pressed by source.collectIsPressedAsState()
        val scope = rememberCoroutineScope()
        val repeater = remember(scope) { MoveRepeater(scope, intervalMs = ROTATE_REPEAT_MS) }
        DisposableEffect(repeater, active) {
            if (!active) repeater.stop()
            onDispose { repeater.stop() }
        }
        LaunchedEffect(source, repeater) {
            source.interactions.collect {
                if (it is PressInteraction.Release || it is PressInteraction.Cancel) repeater.stop()
            }
        }
        val step = direction.sign * ROTATE_STEP_DEG
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .pressFeel(source)
                // 回し続けている間に、指をボタンの外へずらしたら止める（◀▶ と同じ。#88 の A のレビュー）
                .pointerInput(repeater) {
                    awaitPointerEventScope {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                            val p = change.position
                            val outside = p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height
                            if (change.pressed && outside) repeater.stop()
                        }
                    }
                }
                .combinedClickable(
                    enabled = active,
                    role = Role.Button,
                    interactionSource = source,
                    indication = null,
                    onClickLabel = label,
                    onLongClickLabel = label,
                    onClick = { currentRotate(step) },
                    onLongClick = {
                        if (pressed) {
                            var first = true
                            repeater.start {
                                currentRotate(step)
                                // 回り始めの 1 回は、押したときの振動と重なるので鳴らさない
                                if (!first) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                first = false
                            }
                        } else {
                            // 読み上げの長押しは指を離す知らせが来ないので、1 回だけ回す
                            currentRotate(step)
                        }
                    },
                )
                .popBlock(face = if (active) Pop.OrangeDeep else Fieldbook.GridMajor, corner = 14.dp)
                .heightIn(min = 48.dp)
                .padding(contentPadding)
                .semantics { contentDescription = label },
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
internal const val TURN_BUTTON_LABEL = "時計回りに 15° 回す。押し続けると回り続ける"
