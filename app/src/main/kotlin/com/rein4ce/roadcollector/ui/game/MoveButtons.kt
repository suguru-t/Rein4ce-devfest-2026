package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Coordinates remain in meters; callers use the same move callback as dragging. */
internal const val MOVE_STEP_M = .05f

internal class MoveRepeater(private val scope: CoroutineScope) {
    private var job: Job? = null
    fun start(step: () -> Unit) {
        stop()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (isActive) { step(); delay(80) }
        }
    }
    fun stop() { job?.cancel(); job = null }
}

/** Reusable by other game modes without coupling their rules to StackViewModel. */
@Composable
fun MoveButtons(enabled: Boolean, sessionKey: Any, onMove: (Float) -> Unit) {
    key(sessionKey) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoveButton("◀", "左へ5cm", -MOVE_STEP_M, enabled, sessionKey, onMove)
            MoveButton("▶", "右へ5cm", MOVE_STEP_M, enabled, sessionKey, onMove)
        }
    }
}

@Composable
private fun MoveButton(symbol: String, label: String, step: Float, enabled: Boolean, sessionKey: Any, onMove: (Float) -> Unit) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = enabled && state.isAtLeast(Lifecycle.State.RESUMED)
    val currentMove by rememberUpdatedState(onMove)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val repeater = remember(scope, sessionKey) { MoveRepeater(scope) }
    DisposableEffect(repeater, active) {
        if (!active) repeater.stop()
        onDispose { repeater.stop() }
    }
    LaunchedEffect(source, repeater) {
        source.interactions.collect {
            if (it is PressInteraction.Release || it is PressInteraction.Cancel) repeater.stop()
        }
    }
    val color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 1f else .38f)
    Surface(
        shape = ButtonDefaults.outlinedShape,
        border = ButtonDefaults.outlinedButtonBorder(active),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = color,
        modifier = Modifier.pressFeel(source).combinedClickable(
            enabled = active,
            role = Role.Button,
            interactionSource = source,
            indication = LocalIndication.current,
            onClickLabel = label,
            onLongClickLabel = "${label}ずつ連続移動",
            onClick = { currentMove(step) },
            onLongClick = {
                // Accessibility's long-click action has no physical release event: perform one step.
                if (pressed) repeater.start { currentMove(step) } else currentMove(step)
            },
        ),
    ) {
        Box(Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).padding(12.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Text(symbol)
        }
    }
}
