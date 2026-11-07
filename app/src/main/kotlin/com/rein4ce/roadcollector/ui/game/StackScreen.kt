package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 * D 担当：積み上げゲーム画面。
 *
 * ワールド座標（m、y 上向き、台の上面が y=0）をスクリーンに写す：
 *   横 5 m が画面幅になる倍率。台は画面の下から 22% の位置。タワーが高くなるとカメラが上にずれる。
 */
@Composable
fun StackScreen(
    onGoInventory: () -> Unit,
    onGoGallery: () -> Unit,
    vm: StackViewModel = viewModel(),
) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val inventory by repository.parts.collectAsStateWithLifecycle()
    val isLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    val startRequest = GameSession.startRequest

    LaunchedEffect(inventory, isLoaded, startRequest) {
        // partsとisLoadedの収集順にかかわらず、開始境界ではRepositoryの現在値を使う。
        vm.updateInventory(repository.parts.value, repository.isLoaded.value, startRequest)
    }

    // ゲームループ
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) vm.tick((now - last) / 1_000_000_000.0)
                last = now
            }
        }
    }

    if (vm.phase == StackViewModel.Phase.Idle) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 読み込み後は、VM が「始める」か「startMessage を出す」のどちらかになる。
            // パーツがあって案内もないのは、上の LaunchedEffect が動く前の一瞬だけ
            if (!isLoaded || (inventory.isNotEmpty() && vm.startMessage == null)) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("パーツを読み込み中…")
            } else {
                Text(vm.startMessage ?: "コレクションにパーツがありません")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGoInventory) { Text("コレクションへ") }
            }
        }
        return
    }

    vm.startMessage?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissStartMessage,
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissStartMessage()
                    onGoInventory()
                }) { Text("コレクションへ") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissStartMessage) { Text("ゲームに戻る") }
            },
        )
    }

    // 地と字はテーマの色（ダークモードでは、あたたかみのある黒に明るい字）
    val ink = MaterialTheme.colorScheme.onBackground
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StackCanvas(vm = vm, modifier = Modifier.fillMaxSize())

        // 上部のスコア
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("高さ %.2f m".format(vm.height), style = MaterialTheme.typography.headlineSmall, color = ink)
            Column(horizontalAlignment = Alignment.End) {
                Text("残り ${vm.remaining} 個", style = MaterialTheme.typography.bodyMedium, color = ink)
                if (vm.fallenCount > 0) {
                    Text("落下 ${vm.fallenCount} 個", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD64545))
                }
            }
        }

        // 下部の操作
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (vm.phase) {
                StackViewModel.Phase.Placing -> {
                    Text(
                        "${vm.current?.displayName ?: ""} ・ 横にドラッグして位置を決める",
                        style = MaterialTheme.typography.bodySmall,
                        color = ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.rotate(+15f) }) { Text("⟲ 15°") }
                        Button(onClick = { vm.place() }) { Text("置く") }
                        OutlinedButton(onClick = { vm.rotate(-15f) }) { Text("⟳ 15°") }
                    }
                    TextButton(onClick = { vm.restart(repository.parts.value) }) { Text("やり直す") }
                }
                StackViewModel.Phase.Settling -> {
                    Text("落下中…", style = MaterialTheme.typography.bodyMedium, color = ink)
                    Spacer(Modifier.height(48.dp))
                }
                StackViewModel.Phase.Finished -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("完成！ 高さ %.2f m".format(vm.height), style = MaterialTheme.typography.titleLarge)
                            Text(
                                "残ったパーツ ${vm.sessionParts.size - vm.fallenCount} / ${vm.sessionParts.size}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.restart(repository.parts.value) }) { Text("もう一度") }
                                // 送信済みなら押せない（押すたびに同じタワーが別の id で保存され、「みんな」に重複して並ぶため）
                                OutlinedButton(onClick = { vm.upload() }, enabled = !vm.uploading && !vm.uploaded) {
                                    when {
                                        vm.uploading -> CircularProgressIndicator(modifier = Modifier.height(16.dp))
                                        vm.uploaded -> Text("送信済み")
                                        else -> Text("サーバに送信")
                                    }
                                }
                                TextButton(onClick = onGoGallery) { Text("みんな") }
                            }
                            vm.uploadMessage?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                StackViewModel.Phase.Idle -> Unit
            }
        }
    }
}

@Composable
private fun StackCanvas(vm: StackViewModel, modifier: Modifier = Modifier) {
    // 落下ラインと台の色。ダークモード（地が暗い）では、暗い地の上でも見える明るさにする
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val guide = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.13f)
    val platform = if (dark) Color(0xFF7A7FA6) else Color(0xFF4A4E69)
    Canvas(
        modifier = modifier.pointerInput(vm) {
            detectDragGestures { change, drag ->
                change.consume()
                val scalePx = size.width / WORLD_WIDTH_M
                vm.dragBy(drag.x / scalePx)
            }
        },
    ) {
        val scalePx = size.width / WORLD_WIDTH_M
        // タワーが 1.2 m を超えたらカメラを上げる
        val cameraY = max(0f, vm.height - 1.2f)
        val platformScreenY = size.height * 0.78f
        fun sx(x: Double) = size.width / 2 + x.toFloat() * scalePx
        fun sy(y: Double) = platformScreenY - (y.toFloat() - cameraY) * scalePx

        // 落下ライン
        drawLine(
            color = guide,
            start = Offset(0f, sy(-1.5)),
            end = Offset(size.width, sy(-1.5)),
            strokeWidth = 2f,
        )

        // 物理ワールドの図形
        for (s in vm.shapes) {
            val color = s.part?.kind?.color() ?: platform
            when (s) {
                is StackWorld.RenderShape.Poly -> {
                    val path = Path()
                    for (i in s.xs.indices) {
                        val px = sx(s.xs[i])
                        val py = sy(s.ys[i])
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, color)
                }
                is StackWorld.RenderShape.Disc -> {
                    drawCircle(color, radius = s.r.toFloat() * scalePx, center = Offset(sx(s.cx), sy(s.cy)))
                }
            }
        }

        // ゴースト（これから置くパーツ）
        val current = vm.current
        if (vm.phase == StackViewModel.Phase.Placing && current != null) {
            drawPart(
                part = current,
                center = Offset(sx(vm.ghostX.toDouble()), sy(vm.spawnY().toDouble())),
                scalePx = scalePx,
                angleRad = Math.toRadians(vm.ghostAngleDeg.toDouble()).toFloat(),
                alpha = 0.45f,
            )
        }
    }
}

/** 画面幅に収めるワールドの横幅 [m] */
private const val WORLD_WIDTH_M = 5f
