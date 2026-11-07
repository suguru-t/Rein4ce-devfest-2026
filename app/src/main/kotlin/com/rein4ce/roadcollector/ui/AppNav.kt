package com.rein4ce.roadcollector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rein4ce.roadcollector.ui.collect.CollectScreen
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.ui.gallery.GalleryScreen
import com.rein4ce.roadcollector.ui.game.StackScreen
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.InventoryScreen
import com.rein4ce.roadcollector.ui.inventory.fieldbookGrid
import com.rein4ce.roadcollector.ui.weather.BackdropScreen
import com.rein4ce.roadcollector.ui.weather.WeatherLayer
import kotlin.math.roundToInt

/**
 * 4 画面のタブ。担当分けに合わせて 1 画面 = 1 パッケージ。
 *
 *   collect   … A：GPS 採集（ui/collect）
 *   inventory … B：手持ちパーツ（ui/inventory）
 *   stack     … D：積み上げゲーム（ui/game）
 *   gallery   … C：サーバ連携（ui/gallery, net/）
 *
 * @property icon        選択中に出す「塗り」のアイコン
 * @property iconOutline それ以外のときに出す「線画」のアイコン
 */
sealed class Dest(val route: String, val label: String, val icon: ImageVector, val iconOutline: ImageVector) {
    // 足跡・鞄・積み木は自作のベクター（AppIcons）。「みんな」だけ material-icons の標準セット
    data object Collect : Dest("collect", "採集", AppIcons.Footprints, AppIcons.FootprintsOutline)
    data object Inventory : Dest("inventory", "コレクション", AppIcons.Bag, AppIcons.BagOutline)
    data object Stack : Dest("stack", "積む", AppIcons.Blocks, AppIcons.BlocksOutline)
    data object Gallery : Dest("gallery", "みんな", Icons.Filled.Share, Icons.Outlined.Share)

    companion object {
        // 使うときに作る。すぐ作ると、`Dest.all` より先に `Dest.Collect` などを使ったとき（背景の天気の判定など）、
        // Collect を作っている途中でこの一覧を作ることになり、一覧に null が入って落ちる
        val all: List<Dest> by lazy { listOf(Collect, Inventory, Stack, Gallery) }
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    // 「みんなのタワー」の一覧を、画面が出た時点で裏で取り始める（C）。本番のサーバはしばらく使われないと止まっていて
    // 起きるまで 5 秒ほどかかるので、「みんな」を押してから取りに行くとそこで待つ。先に取り始めておけば、
    // 押すころには届いていることが多い。失敗しても何も出さない（「みんな」を開いたときに取り直す）
    val context = LocalContext.current
    LaunchedEffect(Unit) { RoadCollectorApp.from(context).gallery.prefetch() }

    fun go(dest: Dest) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 地はアプリ全体で方眼紙。その上に、見ている画面に合わせた天気（雨・雪など）を降らせる（B：ui/weather）。
    // 画面の部品はさらにその上。タブバーは透明なので、浮いたピルの下に方眼と雨が透けて見える
    Box(modifier = Modifier.fillMaxSize().fieldbookGrid()) {
        val backdrop = when (currentDestination?.route) {
            Dest.Collect.route -> BackdropScreen.COLLECT
            Dest.Inventory.route -> BackdropScreen.COLLECTION
            Dest.Gallery.route -> BackdropScreen.GALLERY
            else -> BackdropScreen.OTHER
        }
        WeatherLayer(backdrop, modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { TabBar(currentDestination, onGo = ::go) },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Dest.Collect.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Dest.Collect.route) { CollectScreen(onGoInventory = { go(Dest.Inventory) }) }
                composable(Dest.Inventory.route) { InventoryScreen(onGoStack = { go(Dest.Stack) }) }
                composable(Dest.Stack.route) { StackScreen(onGoInventory = { go(Dest.Inventory) }, onGoGallery = { go(Dest.Gallery) }) }
                composable(Dest.Gallery.route) { GalleryScreen() }
            }
        }
    }
}

/**
 * 下のタブバー。地は透明で、4 つのアイコンを 1 つの丸いピル（錠剤型）で囲んで浮かせる。
 * Instagram のタブのように文字なし・アイコンだけで、選択中は「塗り」、それ以外は「線画」（大きさと色は同じ墨色）。
 * 選択中の後ろには薄い丸、タップしたタブはひと呼吸だけ縮んで戻る。
 * 文字が無いぶん、読み上げ（TalkBack）にはタブ名と選択状態を渡す。
 */
@Composable
private fun TabBar(current: NavDestination?, onGo: (Dest) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(elevation = 6.dp, shape = PillShape, ambientColor = Fieldbook.Ink, spotColor = Fieldbook.Ink)
                // 紙色を少し透かして、下の方眼が見えるように
                .background(Fieldbook.Paper.copy(alpha = 0.88f), PillShape)
                .border(1.dp, Fieldbook.GridMajor, PillShape)
                .clip(PillShape),
        ) {
            // 選択中のタブの後ろの薄い楕円（横長。ピルの形と相似）。1 つだけ置いて、タブを切り替えると横にすべって動く
            val tabWidth = maxWidth / Dest.all.size
            val selectedIndex = Dest.all.indexOfFirst { dest -> current?.hierarchy?.any { it.route == dest.route } == true }
            if (selectedIndex >= 0) {
                val position by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                    label = "tabIndicator",
                )
                Box(
                    modifier = Modifier
                        .offset {
                            val x = tabWidth.toPx() * position + (tabWidth - IndicatorWidth).toPx() / 2
                            IntOffset(x.roundToInt(), ((56.dp - IndicatorHeight) / 2).roundToPx())
                        }
                        .size(width = IndicatorWidth, height = IndicatorHeight)
                        .background(Fieldbook.Ink.copy(alpha = 0.08f), CircleShape),
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dest.all.forEach { dest ->
                    val isSelected = current?.hierarchy?.any { it.route == dest.route } == true
                    // 押したタブは少し大きいまま（1.18 倍）。選ばれた瞬間はひと呼吸縮んでから、バネで弾んで大きくなる
                    val scale = remember { Animatable(if (isSelected) SelectedScale else 1f) }
                    LaunchedEffect(isSelected) {
                        if (isSelected) {
                            scale.snapTo(0.85f)
                            scale.animateTo(SelectedScale, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        } else {
                            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = dest.label
                            }
                            // 波紋は出さない（Instagram と同じく、アイコンの弾みだけ）
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onGo(dest) },
                    ) {
                        Icon(
                            imageVector = if (isSelected) dest.icon else dest.iconOutline,
                            contentDescription = null,
                            tint = Fieldbook.Ink,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                },
                        )
                    }
                }
            }
        }
    }
}

/** ピルの形（高さ 56dp の半分の角丸） */
private val PillShape = RoundedCornerShape(28.dp)

/** 選択中のタブの後ろの楕円の大きさ */
private val IndicatorWidth = 60.dp
private val IndicatorHeight = 40.dp

/** 押したタブのアイコンの大きさ（元の何倍か） */
private const val SelectedScale = 1.18f
