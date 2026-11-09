package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rein4ce.roadcollector.ui.common.pressFeel

/**
 * 回転のボタンを押している間に回す速さ [度/秒]（決定 67）。半回転に 2 秒。
 * 15° きざみをやめて、押している間はなめらかに回り、離した角度で止まる
 */
internal const val ROTATE_SPEED_DEG_S = 90f

/** 1 コマで回す時間の上限 [秒]。裏から戻ったときなどに、長いコマで大きく回ってしまわないように */
internal const val ROTATE_MAX_FRAME_S = 0.05f

/**
 * 読み上げ（TalkBack）で押したときに 1 回で回す角度 [度]。読み上げの「押す」は指を離す知らせが来ないので、
 * 押している間だけ回すことができない。そのときだけ、決まった角度を回す
 */
internal const val ROTATE_A11Y_STEP_DEG = 15f

/** ボタンの記号（⟲⟳）の大きさ */
private val ROTATE_SYMBOL_SIZE = 30.sp

/**
 * ⟲ と ⟳。[sign] は、押したときに回す向き（⟲ が +、⟳ が −。積む画面の回し方と同じ）
 */
enum class RotateDirection(val symbol: String, private val side: String, val sign: Float) {
    LEFT("⟲", "左", 1f),
    RIGHT("⟳", "右", -1f),
    ;

    /** ボタンの字（「⟲」。角度は書かない） */
    val text: String get() = symbol

    /** 読み上げの説明（「左に回す」） */
    val label: String get() = "${side}に回す"
}

/**
 * 押している間に、コマごとに回す角度を出す（前のコマからの時間 × [ROTATE_SPEED_DEG_S]）。
 * 押し始めのコマは 0（前のコマが無い）。長すぎるコマは [ROTATE_MAX_FRAME_S] で切る
 */
internal class RotateSpin(private val sign: Float, private val speedDegS: Float = ROTATE_SPEED_DEG_S) {
    private var lastNanos: Long? = null

    fun step(frameNanos: Long): Float {
        val last = lastNanos
        lastNanos = frameNanos
        if (last == null) return 0f
        val dt = ((frameNanos - last) / 1e9f).coerceIn(0f, ROTATE_MAX_FRAME_S)
        return sign * speedDegS * dt
    }
}

/**
 * 回転のボタン 1 つ（決定 67）。押している間、[ROTATE_SPEED_DEG_S] でなめらかに回り続け、指を離した角度で止まる。
 * 指をボタンの外へずらしたとき・アプリが裏に回ったとき・画面を離れたとき・次のゲーム（[sessionKey] が変わる）でも止まる。
 * 押した瞬間の振動と音は、ほかのボタンと同じ押し心地（pressFeel）から鳴る。
 *
 * 読み上げで押したときは、[ROTATE_A11Y_STEP_DEG] だけ回す。
 * 見た目は今までの回転のボタン（PressOutlinedButton）と同じ枠・字・押し心地
 *
 * @param onRotate 回す角度 [度]（+ が ⟲）。コマごとに小さな角度で呼ぶ。狙っている間だけ回る（落下中などは、呼ぶ側の VM が何もしない）
 * @param sessionKey 次のゲームで、押している状態ごと作り直す
 */
@Composable
fun RotateButton(
    direction: RotateDirection,
    sessionKey: Any,
    onRotate: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    key(sessionKey) {
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        val currentRotate by rememberUpdatedState(onRotate)
        val source = remember { MutableInteractionSource() }
        var holding by remember { mutableStateOf(false) }
        // 押している間、コマごとに回す（離す・裏に回る・画面を離れると、この繰り返しも止まる）
        LaunchedEffect(holding, active) {
            if (!holding || !active) return@LaunchedEffect
            val spin = RotateSpin(direction.sign)
            while (true) {
                val degrees = withFrameNanos { spin.step(it) }
                if (degrees != 0f) currentRotate(degrees)
            }
        }
        val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Surface(
            shape = ButtonDefaults.outlinedShape,
            border = ButtonDefaults.outlinedButtonBorder(active),
            color = Color.Transparent,
            contentColor = ink,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .pressFeel(source)
                .indication(source, LocalIndication.current)
                .pointerInput(active) {
                    if (!active) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        source.tryEmit(press)
                        holding = true
                        var released = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    released = true
                                    break
                                }
                                // 指をボタンの外へずらしたら止める（◀▶ があったころと同じ。#88 の A のレビュー）
                                if (change.isOutOfBounds(size, extendedTouchPadding)) break
                            }
                        } finally {
                            holding = false
                            source.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        }
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = direction.label
                    if (active) {
                        onClick(label = direction.label) {
                            currentRotate(direction.sign * ROTATE_A11Y_STEP_DEG)
                            true
                        }
                    } else {
                        disabled()
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MinHeight)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                // 記号だけになったので、ボタンの字（labelLarge）より大きく描く（⟲⟳ は字の中で小さく描かれる記号のため）
                Text(
                    direction.text,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = ROTATE_SYMBOL_SIZE, lineHeight = TextUnit.Unspecified),
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}
