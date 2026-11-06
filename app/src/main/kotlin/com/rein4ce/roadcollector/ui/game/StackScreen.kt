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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.theme.Cream
import com.rein4ce.roadcollector.ui.theme.Ink
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
    val parts: List<Part> = GameSession.selected.ifEmpty { inventory.take(GameSession.MAX_PARTS) }

    // 選択が変わったとき、または未開始のときだけ開始（タブを行き来してもゲームは続く）
    LaunchedEffect(parts) {
        if (parts.isNotEmpty() && (vm.phase == StackViewModel.Phase.Idle || vm.sessionParts != parts)) {
            vm.start(parts)
        }
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

    if (parts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("コレクションにパーツがありません")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGoInventory) { Text("コレクションへ") }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        StackCanvas(vm = vm, modifier = Modifier.fillMaxSize())

        // 上部のスコア
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("高さ %.2f m".format(vm.height), style = MaterialTheme.typography.headlineSmall, color = Ink)
            Column(horizontalAlignment = Alignment.End) {
                Text("残り ${vm.remaining} 個", style = MaterialTheme.typography.bodyMedium, color = Ink)
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
                        color = Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.rotate(+15f) }) { Text("⟲ 15°") }
                        Button(onClick = { vm.place() }) { Text("置く") }
                        OutlinedButton(onClick = { vm.rotate(-15f) }) { Text("⟳ 15°") }
                    }
                    TextButton(onClick = { vm.restart() }) { Text("やり直す") }
                }
                StackViewModel.Phase.Settling -> {
                    Text("落下中…", style = MaterialTheme.typography.bodyMedium, color = Ink)
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
                                Button(onClick = { vm.restart() }) { Text("もう一度") }
                                OutlinedButton(onClick = { vm.upload() }, enabled = !vm.uploading) {
                                    if (vm.uploading) CircularProgressIndicator(modifier = Modifier.height(16.dp)) else Text("サーバに送信")
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
            color = Color(0x22000000),
            start = Offset(0f, sy(-1.5)),
            end = Offset(size.width, sy(-1.5)),
            strokeWidth = 2f,
        )

        // 物理ワールドの図形
        for (s in vm.shapes) {
            val color = s.part?.kind?.color() ?: Color(0xFF4A4E69)
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
