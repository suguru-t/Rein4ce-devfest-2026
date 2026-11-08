package com.rein4ce.roadcollector.ui.game

import android.util.AtomicFile
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.ChallengeRecords
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.common.*
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

private val challengeFileLock = Any()

/** Only the navigation entry changes; ordinary StackScreen and its in-flight fixes stay independent. */
@Composable
fun StackModes(onGoInventory: () -> Unit, onGoGallery: () -> Unit) {
    var challenge by rememberSaveable { mutableStateOf(false) }
    val request = GameSession.startRequest
    LaunchedEffect(request) { if (request != null && !request.started) challenge = false }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PressTextButton(onClick = { challenge = false }) { Text(if (!challenge) "通常 ✓" else "通常") }
            PressTextButton(onClick = { challenge = true }) { Text(if (challenge) "3分チャレンジ ✓" else "3分チャレンジ") }
        }
        Box(Modifier.weight(1f)) {
            if (challenge) ChallengeScreen() else StackScreen(onGoInventory, onGoGallery)
        }
    }
}

class ChallengeViewModel : ViewModel() {
    var run by mutableStateOf<ChallengeRun?>(null); private set
    fun start() { run?.pause(); run = ChallengeRun(Random.nextLong()) }
    override fun onCleared() { run?.pause() }
}

@Composable
private fun ChallengeScreen(vm: ChallengeViewModel = viewModel()) {
    val run = vm.run
    var revision by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val directory = LocalContext.current.applicationContext.filesDir
    var ranking by remember { mutableStateOf<List<ChallengeScore>>(emptyList()) }
    var saveError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    // Cancellation on navigation, mode switch or backgrounding always pauses the same monotonic clock.
    LaunchedEffect(run, lifecycle) {
        if (run != null) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            run.resume()
            try {
                while (isActive && !run.terminal) withFrameNanos { run.tick(); revision++ }
            } finally { run.pause(); revision++ }
        }
    }
    @Suppress("UNUSED_VARIABLE") val frame = revision
    val score = run?.score
    LaunchedEffect(score, retry) {
        saveError = false
        try {
            ranking = withContext(Dispatchers.IO) {
                synchronized(challengeFileLock) {
                val file = AtomicFile(File(directory, "challenge_records.json"))
                val records = ChallengeRecords(
                    read = { try { file.openRead().bufferedReader().use { it.readText() } } catch (_: java.io.FileNotFoundException) { null } },
                    write = { text ->
                        val stream = file.startWrite()
                        try { stream.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
                        catch (e: Exception) { file.failWrite(stream); throw e }
                    },
                )
                if (score != null) records.add(score) else records.ranking(com.rein4ce.roadcollector.core.challenge.ChallengeRules.ID)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { saveError = true }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val controlLimit = maxHeight * .45f
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            val seconds = ((run?.remainingNs ?: 180_000_000_000L) + 999_999_999L) / 1_000_000_000L
            Text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60))
            if (run != null && !run.terminal) {
                Row(Modifier.fillMaxWidth()) {
                    Text("今：${run.current.name}　次：${run.next.name}")
                    Canvas(Modifier.size(32.dp)) { drawPart(run.next, Offset(size.width / 2, size.height / 2), size.width * .75f) }
                }
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                if (run != null && size.height > 0) {
                    // Whole-tower fit, stable during aiming/falling; do not introduce the opposed follow-top policy (#42/#70).
                    val scale = min(size.width / 5f, size.height * .78f / run.focusHeight.toFloat())
                    fun at(x: Double, y: Double) = Offset(size.width / 2 + x.toFloat() * scale, size.height * .78f - y.toFloat() * scale)
                    for (shape in run.shapes) when (shape) {
                        is StackWorld.RenderShape.Poly -> {
                            val path = Path()
                            shape.xs.indices.forEach { i -> val p = at(shape.xs[i], shape.ys[i]); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                            path.close(); drawPath(path, shape.part?.kind?.color() ?: Color.Gray)
                        }
                        is StackWorld.RenderShape.Disc -> drawCircle(Color.Gray, (shape.r * scale).toFloat(), at(shape.cx, shape.cy))
                    }
                    if (run.phase == ChallengeRun.Phase.AIMING) drawPart(run.current, at(run.x, run.spawnY), scale, Math.toRadians(run.angle).toFloat(), alpha = .5f)
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = controlLimit).verticalScroll(rememberScrollState())) {
                if (run == null) {
                    Text("3分間、決まった5種類の形を積みます。左右・回転で狙って「置く」。画面を離れると一時停止します。")
                    PressButton(onClick = { vm.start() }) { Text("3分チャレンジを始める") }
                } else when (run.phase) {
                    ChallengeRun.Phase.AIMING -> {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            PressOutlinedButton(onClick = { run.move(-.1); revision++ }) { Text("左") }
                            PressButton(onClick = { run.place(); revision++ }) { Text("置く") }
                            PressOutlinedButton(onClick = { run.move(.1); revision++ }) { Text("右") }
                        }
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            PressTextButton(onClick = { run.rotate(15.0); revision++ }) { Text("左回転 15°") }
                            PressTextButton(onClick = { run.rotate(-15.0); revision++ }) { Text("右回転 15°") }
                        }
                    }
                    ChallengeRun.Phase.FALLING -> Text("落下中…")
                    ChallengeRun.Phase.FINALIZING -> Text("時間です。塔が落ち着くのを待っています…")
                    ChallengeRun.Phase.FINISHED, ChallengeRun.Phase.UNRESOLVED -> {
                        if (score != null) {
                            Text(String.format(Locale.ROOT, "記録 %.2f m", score.height))
                            if (ranking.any { it.runId == score.runId }) Text("この端末で ${1 + ranking.count { it.height > score.height }} 位")
                        } else Text("塔が落ち着かなかったため、記録には残しません。")
                        PressButton(onClick = { vm.start() }, enabled = score == null || ranking.any { it.runId == score.runId }) { Text("もう一度") }
                    }
                }
                if (saveError) {
                    Text("記録を読み書きできませんでした。")
                    PressTextButton(onClick = { retry++ }) { Text("再試行") }
                }
                if (run == null || run.terminal) {
                    Text("端末内ランキング（手動・3分）")
                    ranking.take(10).forEach { record ->
                        Text(String.format(Locale.ROOT, "%d 位　%.2f m", 1 + ranking.count { it.height > record.height }, record.height))
                    }
                    if (ranking.isEmpty()) Text("まだ記録がありません")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
