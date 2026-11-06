package com.rein4ce.roadcollector.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rein4ce.roadcollector.ui.collect.CollectScreen
import com.rein4ce.roadcollector.ui.gallery.GalleryScreen
import com.rein4ce.roadcollector.ui.game.StackScreen
import com.rein4ce.roadcollector.ui.inventory.InventoryScreen

/**
 * 4 画面のタブ。担当分けに合わせて 1 画面 = 1 パッケージ。
 *
 *   collect   … A：GPS 採集（ui/collect）
 *   inventory … B：手持ちパーツ（ui/inventory）
 *   stack     … D：積み上げゲーム（ui/game）
 *   gallery   … C：サーバ連携（ui/gallery, net/）
 */
sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    // アイコンは material-icons の標準セット（追加依存なし）。好みで material-icons-extended に替えてよい
    data object Collect : Dest("collect", "採集", Icons.Filled.Place)
    data object Inventory : Dest("inventory", "手持ち", Icons.AutoMirrored.Filled.List)
    data object Stack : Dest("stack", "積む", Icons.Filled.Build)
    data object Gallery : Dest("gallery", "みんな", Icons.Filled.Share)

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
        bottomBar = {
            NavigationBar {
                Dest.all.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { go(dest) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
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
