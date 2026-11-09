package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rein4ce.roadcollector.ui.common.pressFeel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** ◀▶ を 1 回押したときに動かす量 [m]。座標は m のまま、ドラッグと同じ動かし方（StackViewModel.dragBy）に渡す */
internal const val MOVE_STEP_M = 0.05f

/** 長押しで続けて動かす間隔 [ms] */
internal const val MOVE_REPEAT_MS = 80L

/** ◀ と ▶ */
enum class MoveDirection(val symbol: String, val label: String, val stepM: Float) {
    LEFT("◀", "左へ 5cm", -MOVE_STEP_M),
    RIGHT("▶", "右へ 5cm", MOVE_STEP_M),
}

/**
 * 長押しの間、[MOVE_REPEAT_MS] ごとに動かし続ける。始めた瞬間に 1 回動かす。
 * 別の向きで始め直すと、前の分は止める
 */
internal class MoveRepeater(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start(step: () -> Unit) {
        stop()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (isActive) {
                step()
                delay(MOVE_REPEAT_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * ◀▶ のボタン 1 つ。押すと [MOVE_STEP_M] 動かし、長押しすると [MOVE_REPEAT_MS] ごとに続けて動かす。
 * 動かし方は呼ぶ側の [onMove]（積む画面では、ドラッグと同じ StackViewModel.dragBy。範囲の端で止まる）。
 *
 * - 置く前（狙っている間）だけ出す前提。落下中・完成後は、呼ぶ側が出さず、VM も動かさない
 * - 続けて動かすのは、指を離す・キャンセル・アプリが裏に回る・画面を離れる・次のゲーム（[sessionKey] が変わる）で止める
 * - 読み上げの長押し（指を離す知らせが来ない）は 1 回だけ動かす（押したまま動き続けないように）
 * - 見た目は回転のボタン（PressOutlinedButton）と同じ枠と高さ。押し心地も同じ（pressFeel）
 */
@Composable
fun MoveButton(
    direction: MoveDirection,
    sessionKey: Any,
    onMove: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 次のゲームでは、長押しの状態ごと作り直す
    key(sessionKey) {
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        val currentMove by rememberUpdatedState(onMove)
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scope = rememberCoroutineScope()
        val repeater = remember(scope) { MoveRepeater(scope) }
        DisposableEffect(repeater, active) {
            if (!active) repeater.stop()
            onDispose { repeater.stop() }
        }
        LaunchedEffect(source, repeater) {
            source.interactions.collect {
                if (it is PressInteraction.Release || it is PressInteraction.Cancel) repeater.stop()
            }
        }
        val ink = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 1f else 0.38f)
        Surface(
            shape = ButtonDefaults.outlinedShape,
            border = ButtonDefaults.outlinedButtonBorder(active),
            color = Color.Transparent,
            contentColor = ink,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .pressFeel(source)
                .combinedClickable(
                    enabled = active,
                    role = Role.Button,
                    interactionSource = source,
                    indication = LocalIndication.current,
                    onClickLabel = direction.label,
                    onLongClickLabel = "${direction.label}ずつ続けて動かす",
                    onClick = { currentMove(direction.stepM) },
                    onLongClick = {
                        // 読み上げの長押しは指を離す知らせが来ないので、1 回だけ動かす
                        if (pressed) repeater.start { currentMove(direction.stepM) } else currentMove(direction.stepM)
                    },
                ),
        ) {
            Box(
                Modifier
                    .size(width = MOVE_BUTTON_WIDTH, height = ButtonDefaults.MinHeight)
                    .semantics { contentDescription = direction.label },
                contentAlignment = Alignment.Center,
            ) {
                Text(direction.symbol)
            }
        }
    }
}

/** ◀▶ のボタンの幅。回転・置くのボタンと 1 行に並べるので、指で押せる最小の幅にする */
private val MOVE_BUTTON_WIDTH = 48.dp
