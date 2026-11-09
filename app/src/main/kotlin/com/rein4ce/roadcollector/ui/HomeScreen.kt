package com.rein4ce.roadcollector.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.ui.collect.TitleBlocks
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.PopTag
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.settings.SettingsGearButton

/**
 * ホーム（開いたときに最初に出す画面。決定 52）。アプリのアイコン（積み木の塔）と題字「ミチツミ」、一言の下に、
 * 4 つの画面へ行くブロックのボタンを縦に並べる。
 * 並びは下のタブと同じ（使う順：歩いて記録 → コレクション → 積む → みんな）、色は題字の積み木と同じ並び。
 * 押すと、下のタブを押したときと同じように、その画面へ移る（タブバーはホームでは出さず、4 つの画面に入ると下から出てくる）。
 * 4 つの画面で、タブバーの左の家のボタンか端末の「戻る」を押すと、ここへ戻る。右上に設定の歯車。
 *
 * 開いたときのタイトル（TitleIntro.kt）は、ここの題字へ飛んで収まる。そのために題字の場所を知らせ、収まるまでは隠しておく
 * （飛んでくる題字と、ここの題字が二重に見えないように）。
 * 小さい画面や文字を大きくしている人の画面で入りきらないときは、画面ごと上下に動かせる
 */
@Composable
internal fun HomeScreen(onGo: (Dest) -> Unit) {
    val recording by TrackRecorder.isRecording.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(WindowInsets.systemBars.asPaddingValues())) {
        val viewport = maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 入るときは画面のまん中に。入らないときは上から並べて、動かして見る
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewport)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // アプリのアイコンの積み木の塔。起動画面と同じく、題字のすぐ上に
                AppIconTower(height = ICON_HEIGHT)
                Spacer(Modifier.height(4.dp))
                // 題字は押すと動く（TitleBlocks.kt）。題字はロゴなので、文字の大きさの設定では大きくしない
                TitleBlocks(
                    scale = TITLE_SCALE,
                    modifier = Modifier
                        // タイトルが終わったら、もう知らせない（余計な更新をしない）
                        .onGloballyPositioned { if (!TitleIntroState.done) TitleIntroState.anchor = it.boundsInRoot() }
                        .graphicsLayer { alpha = if (TitleIntroState.done) 1f else 0f },
                )
                Spacer(Modifier.height(16.dp))
                Text(TAGLINE, color = Fieldbook.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(36.dp))
                Column(
                    modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    for (dest in Dest.all) {
                        HomeButton(
                            dest = dest,
                            // 記録中なら、記録のボタンに「記録中」の札（ホームへ戻っても、歩いている途中だと分かるように）
                            badge = if (dest == Dest.Collect && recording) "記録中" else null,
                            onClick = { onGo(dest) },
                        )
                    }
                }
            }
        }
        // 設定の歯車は右上（前は記録画面の見出しの右）。上下に動かしても、いつも右上に見えている
        SettingsGearButton(modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
    }
}

/**
 * ホームのボタン 1 つ：左にタブと同じアイコン（塗りの形）、まん中に画面の名前と一言、右に「›」。
 * 押すとほかのボタンと同じ押し心地で縮む（ui/common/PressFeel）。読み上げでは、名前と一言を続けて読む
 */
@Composable
private fun HomeButton(dest: Dest, badge: String?, onClick: () -> Unit) {
    val look = lookOf(dest)
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .popBlock(face = look.face, corner = 18.dp, depth = 5.dp)
            .heightIn(min = 72.dp)
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        TabIcon(dest = dest, selected = true, animate = false, tint = look.text, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dest.label,
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = look.text,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    PopTag(badge, face = Pop.Navy)
                }
            }
            Text(
                look.note,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = look.text,
                style = noteStyle,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = look.text,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * アプリのアイコンの積み木の塔（res/drawable/ic_launcher_foreground。起動画面でも題字の上に出している）。
 * アイコンの絵は 108×108 の中の x 27〜81・y 22〜90 に描いてあるので、塔の所だけの場所を取る（まわりの余白は取らない）。
 * 飾りなので読み上げない（題字が「ミチツミ」と読まれる）
 */
@Composable
private fun AppIconTower(height: Dp, modifier: Modifier = Modifier) {
    val unit = height / ICON_ART_HEIGHT
    Box(modifier = modifier.size(width = unit * ICON_ART_WIDTH, height = height), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            // 絵の全体（108×108）を置き、塔のまん中（x 54・y 56）を場所のまん中に合わせる
            modifier = Modifier.requiredSize(unit * ICON_VIEWPORT).offset(y = unit * (ICON_VIEWPORT / 2 - ICON_ART_CENTER_Y)),
        )
    }
}

/** アイコンの絵の大きさ（108×108）と、その中の塔の幅・高さ・まん中の高さ（ic_launcher_foreground の座標） */
private const val ICON_VIEWPORT = 108f
private const val ICON_ART_WIDTH = 54f
private const val ICON_ART_HEIGHT = 68f
private const val ICON_ART_CENTER_Y = 56f

/** ホームに出す塔の高さ */
private val ICON_HEIGHT = 104.dp

/** ボタンの面の色・字の色・一言 */
private class HomeLook(val face: Color, val text: Color, val note: String)

/**
 * 画面ごとの見た目。面の色は題字の積み木と同じ並び（ミ：オレンジ、チ：青緑、ツ：黄、ミ：紺）。
 * 白い字を載せる面は、白い字と 4.5:1 を超える濃い色（Pop.kt の決まり）。黄色の面には墨の字
 */
private fun lookOf(dest: Dest): HomeLook = when (dest) {
    Dest.Collect -> HomeLook(Pop.OrangeDeep, Pop.Letter, "歩いた道を記録する")
    Dest.Inventory -> HomeLook(Pop.TealDeep, Pop.Letter, "集めたパーツを見る")
    Dest.Stack -> HomeLook(Pop.Yellow, Pop.InkOnYellow, "パーツを積み上げる")
    Dest.Gallery -> HomeLook(Pop.Navy, Pop.Letter, "みんなのタワーを見る")
}

/** ホームの題字の大きさ（記録画面の見出しの題字の何倍か） */
private const val TITLE_SCALE = 1.5f
