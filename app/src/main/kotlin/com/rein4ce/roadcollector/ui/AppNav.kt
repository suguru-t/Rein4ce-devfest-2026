package com.rein4ce.roadcollector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.ui.collect.CollectScreen
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.common.ProvideAppHaptics
import com.rein4ce.roadcollector.ui.gallery.GalleryScreen
import com.rein4ce.roadcollector.ui.game.StackScreen
import com.rein4ce.roadcollector.ui.inventory.InventoryScreen
import com.rein4ce.roadcollector.ui.inventory.fieldbookGrid
import com.rein4ce.roadcollector.ui.settings.HowToDialog
import com.rein4ce.roadcollector.ui.weather.BackdropScreen
import com.rein4ce.roadcollector.ui.weather.WeatherLayer

/**
 * 4 画面のタブ。担当分けに合わせて 1 画面 = 1 パッケージ。
 *
 *   collect   … A：GPS 採集（ui/collect）
 *   inventory … B：手持ちパーツ（ui/inventory）
 *   stack     … D：積み上げゲーム（ui/game）
 *   gallery   … C：サーバ連携（ui/gallery, net/）
 *
 * @property iconOutline 選ばれていないときに出す「線画」のアイコン（選ばれているときは、TabIcon が塗りの形を描いて動かす）
 */
sealed class Dest(val route: String, val label: String, val iconOutline: ImageVector) {
    // 足跡・ノート・積み木・トロフィーは自作のベクター（AppIcons）。選ばれた瞬間の動きは ui/TabIcons.kt
    // 画面の名前は「記録」（「記録開始」のボタンや設定の「記録」とそろえる。前は「採集」。route とコードの名前はそのまま）
    data object Collect : Dest("collect", "記録", AppIcons.FootprintsOutline)
    data object Inventory : Dest("inventory", "コレクション", AppIcons.NotebookOutline)
    data object Stack : Dest("stack", "積む", AppIcons.BlocksOutline)
    // 「みんな」は、高さを競うランキングなのでトロフィー（前は共有の印）
    data object Gallery : Dest("gallery", "みんな", AppIcons.TrophyOutline)

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
    // 中の LocalHapticFeedback を設定の「振動」に従うものにする（Compose の部品が自分で鳴らす振動にも効くように）
    ProvideAppHaptics {
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

            // 開いたときに使い方を出す。「次回から表示しない」にチェックを入れて閉じるまで、開くたびに出す
            // （チェックなしで閉じたら、その回だけ閉じる。画面の回転やダークへの切り替えでは出し直さない）。
            // 設定の「使い方」ではいつでも見られる。起動画面の下で先に出しておくので、門をくぐると使い方が出ている
            val settings = RoadCollectorApp.from(context).settings
            val howToSeen by settings.howToSeen.collectAsStateWithLifecycle()
            var howToClosed by rememberSaveable { mutableStateOf(false) }
            // 開いたときのタイトル（TitleIntro）が終わってから出す（タイトルの上に使い方の窓が重ならないように）
            if (!howToSeen && !howToClosed && TitleIntroState.done) {
                HowToDialog(onClose = { dontShowAgain ->
                    if (dontShowAgain) settings.setHowToSeen(true)
                    howToClosed = true
                })
            }
        }
    }
}
