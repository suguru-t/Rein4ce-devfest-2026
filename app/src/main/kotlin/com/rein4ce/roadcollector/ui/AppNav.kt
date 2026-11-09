package com.rein4ce.roadcollector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.ui.collect.CollectScreen
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.common.ProvideAppHaptics
import com.rein4ce.roadcollector.ui.common.zoomInFrom
import com.rein4ce.roadcollector.ui.common.zoomOrigin
import com.rein4ce.roadcollector.ui.gallery.GalleryScreen
import com.rein4ce.roadcollector.ui.game.StackTab
import com.rein4ce.roadcollector.ui.inventory.InventoryScreen
import com.rein4ce.roadcollector.ui.inventory.fieldbookGrid
import com.rein4ce.roadcollector.ui.settings.HowToDialog
import com.rein4ce.roadcollector.ui.weather.BackdropScreen
import com.rein4ce.roadcollector.ui.weather.WeatherLayer

/**
 * 4 画面のタブ。担当分けに合わせて 1 画面 = 1 パッケージ。
 * 開くと、まずホーム（ui/HomeScreen.kt）。そこからボタンで 4 画面に入り、4 画面の間は下のタブで行き来する。タブバーの左の家のボタンか端末の「戻る」で、ホームへ戻る（決定 52）。
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

/** ホームの route。ホームはタブではないので [Dest] には入れない（ui/HomeScreen.kt） */
internal const val HOME_ROUTE = "home"

/**
 * @param collectRequests アプリが生きているまま、記録中の通知を押して戻ってきた回数（MainActivity.onNewIntent）。
 *   増えたら、記録中なら記録画面へ移す
 */
@Composable
fun AppNav(collectRequests: Int = 0) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    // ホームにいるか（開いた直後の、まだ画面が決まっていない一瞬もホームとみなす）
    val atHome = currentDestination == null || currentDestination.route == HOME_ROUTE

    // 「みんなのタワー」の一覧を、画面が出た時点で裏で取り始める（C）。本番のサーバはしばらく使われないと止まっていて
    // 起きるまで 5 秒ほどかかるので、「みんな」を押してから取りに行くとそこで待つ。先に取り始めておけば、
    // 押すころには届いていることが多い。失敗しても何も出さない（「みんな」を開いたときに取り直す）
    val context = LocalContext.current
    LaunchedEffect(Unit) { RoadCollectorApp.from(context).gallery.prefetch() }

    // 下のタブバーの家のボタンでホームへ戻るときだけ、ホームをそのボタンの所から大きくして出す（決定 78。ui/common/ZoomFrom.kt）。
    // 値は大きくなり始める点（画面の中の割合）。端末の「戻る」やタブの行き来では null にして、ふだんどおりうすく入れ替える。
    // 切り替えの動きは切り替わった時点で決まるので、次に移るときまで残しておいてよい
    var homeZoomFrom by remember { mutableStateOf<TransformOrigin?>(null) }
    // 画面の切り替えの入れ物（NavHost）の、アプリの画面の中での位置と大きさ（家のボタンの位置を割合にするため）
    val navArea = remember { arrayOf(Rect.Zero) }

    // 4 つの画面へ移る（ホームのボタンと、下のタブバーから）。いつもホームの上に 1 つだけ積む。
    // さっきまで見ていた画面は、状態を残して下ろす（戻ってきたら続きから）
    fun go(dest: Dest) {
        homeZoomFrom = null
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 4 つの画面で端末の「戻る」を押したら、ホームへ戻る。タブを切り替えたときと同じく、画面の状態は残す
    // （記録画面のデモ散歩の再生や、コレクションのスクロールの位置が、ホームへ戻っただけで消えないように）
    fun backHome() {
        homeZoomFrom = null
        navController.popBackStack(HOME_ROUTE, inclusive = false, saveState = true)
    }

    // タブバーの家のボタンでホームへ戻る。戻り方は端末の「戻る」と同じで、ホームが [center]（家のボタンのまん中）から大きくなって出てくる
    fun zoomHome(center: Offset) {
        homeZoomFrom = zoomOrigin(center, navArea[0]) ?: HOME_ZOOM_FALLBACK
        navController.popBackStack(HOME_ROUTE, inclusive = false, saveState = true)
    }

    // 記録中に開いたとき（記録中の通知から開き直したときなど）は、ホームを飛ばして記録画面から始める（戻るとホーム）。
    // 開くたびに 1 回だけ見る（画面の作り直しでは見ない）。飛ぶときは、画面の切り替えとタブバーの出る動きを付けない
    var launched by rememberSaveable { mutableStateOf(false) }
    var instant by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (launched) return@LaunchedEffect
        launched = true
        if (TrackRecorder.isRecording.value) {
            instant = true
            go(Dest.Collect)
            // 切り替えの動きが決まる（次のコマ）まで待ってから、ふだんの動きに戻す
            withFrameNanos {}
            withFrameNanos {}
            instant = false
        }
    }
    // アプリが生きているまま記録中の通知を押したとき（ホームへ戻ってから裏に回していたときなど）も、記録画面へ移す。
    // こちらは見ていた画面から移るので、ふだんの切り替えの動きを付ける（#101 の A のレビュー）
    LaunchedEffect(collectRequests) {
        if (collectRequests > 0 && TrackRecorder.isRecording.value) go(Dest.Collect)
    }

    // タブバーがホームへ戻るとき（下へ隠れる間）も、さっきまでの画面を選んだままにしておく
    val lastTab = remember { arrayOfNulls<NavDestination>(1) }
    if (!atHome) lastTab[0] = currentDestination

    // 地はアプリ全体で方眼紙。その上に、見ている画面に合わせた天気（雨・雪など）を降らせる（B：ui/weather）。
    // 画面の部品はさらにその上。タブバーは透明なので、浮いたピルの下に方眼と雨が透けて見える
    // 中の LocalHapticFeedback を設定の「振動」に従うものにする（Compose の部品が自分で鳴らす振動にも効くように）
    ProvideAppHaptics {
        Box(modifier = Modifier.fillMaxSize().fieldbookGrid()) {
            val backdrop = when (currentDestination?.route) {
                null, HOME_ROUTE -> BackdropScreen.HOME
                Dest.Collect.route -> BackdropScreen.COLLECT
                Dest.Inventory.route -> BackdropScreen.COLLECTION
                Dest.Stack.route -> BackdropScreen.STACK
                Dest.Gallery.route -> BackdropScreen.GALLERY
                else -> BackdropScreen.OTHER
            }
            WeatherLayer(backdrop, modifier = Modifier.fillMaxSize())
            // 4 つの画面は、上は端末の時計などのバー、下はタブバー（と端末の下のバー）の分をあけて置く。ホームは自分であける
            val bars = WindowInsets.systemBars.asPaddingValues()
            val direction = LocalLayoutDirection.current
            val tabPadding = PaddingValues(
                start = bars.calculateStartPadding(direction),
                top = bars.calculateTopPadding(),
                end = bars.calculateEndPadding(direction),
                bottom = bars.calculateBottomPadding() + TabBarSpace,
            )
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { navArea[0] = it.boundsInRoot() },
                enterTransition = { if (instant) EnterTransition.None else fadeIn(tween(NAV_FADE_MS)) },
                exitTransition = { if (instant) ExitTransition.None else fadeOut(tween(NAV_FADE_MS)) },
                // ホームへ戻るとき（戻る先はいつもホーム）の、見ていた画面の消え方。家のボタンからなら、さっと消して
                // ホームが大きくなるのを見せる（戻るときは、消える画面がホームより上に描かれるため）
                popExitTransition = {
                    if (homeZoomFrom != null) fadeOut(tween(HOME_ZOOM_OUT_MS)) else fadeOut(tween(NAV_FADE_MS))
                },
            ) {
                composable(
                    HOME_ROUTE,
                    popEnterTransition = { homeZoomFrom?.let(::zoomInFrom) ?: fadeIn(tween(NAV_FADE_MS)) },
                ) { HomeScreen(onGo = ::go) }
                composable(Dest.Collect.route) {
                    TabPage(tabPadding, onBack = ::backHome) { CollectScreen(onGoInventory = { go(Dest.Inventory) }) }
                }
                composable(Dest.Inventory.route) {
                    TabPage(tabPadding, onBack = ::backHome) { InventoryScreen() }
                }
                composable(Dest.Stack.route) {
                    TabPage(tabPadding, onBack = ::backHome) {
                        // まずモードを選ぶ画面。選んだら、そのモードの画面（通常なら積む画面）になる（ui/game/ModeSelect.kt）
                        StackTab(onGoInventory = { go(Dest.Inventory) }, onGoGallery = { go(Dest.Gallery) })
                    }
                }
                composable(Dest.Gallery.route) {
                    TabPage(tabPadding, onBack = ::backHome) { GalleryScreen() }
                }
            }
            // 下のタブバー。ホームでは隠し、4 つの画面に入ると下から出てくる（ホームへ戻ると下へ隠れる）。
            // 左の丸い家のボタンは、端末の「戻る」と同じくホームへ戻る（見ていた画面は続きから）。
            // こちらは、ホームが家のボタンの所から大きくなって出てくる（決定 78）
            AnimatedVisibility(
                visible = !atHome,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (instant) EnterTransition.None else slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                TabBar(lastTab[0], onGo = ::go, onHome = ::zoomHome)
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

/**
 * 4 つの画面の入れ物：まわり（端末のバーとタブバー）をあけて置き、端末の「戻る」でホームへ戻る。
 * 画面の中にも「戻る」の使い道（「みんな」の一覧から戻る・コレクションの選ぶのをやめる など）があれば、そちらが先に効く
 * （「戻る」は後から置いたものが先に効くので、ここは画面より先に置く）
 */
@Composable
private fun TabPage(padding: PaddingValues, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
}

/** 画面を切り替えるときに、うすく入れ替わる時間 [ms]（Navigation の決まりの値と同じ） */
private const val NAV_FADE_MS = 700

/** 家のボタンで戻るとき、見ていた画面が消える時間 [ms] */
private const val HOME_ZOOM_OUT_MS = 150

/** 家のボタンの位置が分からないとき（置かれる前に押された など）に大きくなり始める点：画面の左下 */
private val HOME_ZOOM_FALLBACK = TransformOrigin(0.1f, 0.95f)
