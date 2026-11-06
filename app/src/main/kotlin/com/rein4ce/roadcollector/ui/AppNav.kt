package com.rein4ce.roadcollector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.rein4ce.roadcollector.ui.gallery.GalleryScreen
import com.rein4ce.roadcollector.ui.game.StackScreen
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.InventoryScreen

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
    // 靴・鞄・積み木は自作のベクター（AppIcons）。「みんな」だけ material-icons の標準セット
    data object Collect : Dest("collect", "採集", AppIcons.Shoe, AppIcons.ShoeOutline)
    data object Inventory : Dest("inventory", "手持ち", AppIcons.Bag, AppIcons.BagOutline)
    data object Stack : Dest("stack", "積む", AppIcons.Blocks, AppIcons.BlocksOutline)
    data object Gallery : Dest("gallery", "みんな", Icons.Filled.Share, Icons.Outlined.Share)

    companion object {
        val all = listOf(Collect, Inventory, Stack, Gallery)
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    fun go(dest: Dest) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
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

/**
 * 下のタブバー。Instagram のタブのように、文字なし・アイコンだけ。
 * 選択中は「塗り」、それ以外は「線画」のアイコンで、大きさと色は同じ（墨色）。
 * タップしたタブは、ひと呼吸だけ縮んで戻る。背景は野帳の紙色、上に細い罫線。
 * 文字が無いぶん、読み上げ（TalkBack）にはタブ名と選択状態を渡す。
 */
@Composable
private fun TabBar(current: NavDestination?, onGo: (Dest) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Fieldbook.Paper)) {
        HorizontalDivider(color = Fieldbook.GridMajor, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(50.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dest.all.forEach { dest ->
                val isSelected = current?.hierarchy?.any { it.route == dest.route } == true
                // 選ばれた瞬間にひと呼吸縮んで、バネで戻る
                val scale = remember { Animatable(1f) }
                LaunchedEffect(isSelected) {
                    if (isSelected) {
                        scale.snapTo(0.8f)
                        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
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
