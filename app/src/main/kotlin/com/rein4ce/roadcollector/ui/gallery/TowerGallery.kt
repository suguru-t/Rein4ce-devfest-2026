package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * C 担当：サーバ（Cloud Run）から「みんなのタワー」を取ってくる係。アプリ全体で 1 つ（[com.rein4ce.roadcollector.RoadCollectorApp] が持つ）。
 *
 * 画面の ViewModel ではなくアプリ全体で持つのは、**アプリを立ち上げたときに先読みしておく**ため。
 * 本番のサーバはしばらく使われないと止まり、次の 1 回目は起きるまで 5 秒ほどかかる（コールドスタート。
 * 2026-11-07 のログで 5.3〜5.8 秒。起きていれば 0.1 秒）。「みんな」を開いてから取りに行くと、その 5 秒を画面の前で待つ。
 * 起動時に裏で取り始めておけば（[prefetch]）、タブを押すころには届いていることが多く、サーバも起きている。
 *
 * 一覧は遊び方（[TowerMode]。通常・3 分チャレンジ・毎日 1 つずつ積む）ごとに別に持つ（決定 77）。画面が見ている遊び方は [mode]。
 * [state]・[refreshing]・[refreshFailed] は、いま見ている遊び方のもの。
 *
 * サーバが無いときはこの画面にエラーを出すだけで、他の画面には影響しない。
 *
 * @param listTowers 遊び方ごとの一覧を取る（テストでは差し替える）
 * @param getTower   タワー 1 基の中身を取る
 * @param scope      通信を走らせる scope。アプリと同じ寿命
 */
class TowerGallery(
    private val listTowers: suspend (TowerMode) -> List<TowerSummary> = { ApiClient.listTowers(it) },
    private val getTower: suspend (String) -> Tower = { ApiClient.getTower(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    sealed class State {
        data object Loading : State()
        data class Error(val message: String) : State()
        data class Loaded(val towers: List<TowerSummary>) : State()
    }

    /**
     * いま「みんな」で見ている遊び方。上の切り替えで変わる。3 分チャレンジや毎日 1 つずつ積むの「ランキング」から開くときは、
     * 先にその遊び方にしてから「みんな」へ行く
     */
    var mode: TowerMode by mutableStateOf(TowerMode.NORMAL)

    /** 次に「みんな」を開いたとき、ホームではなく [mode] のランキングから見せるか（[open]） */
    private var openRequested = false

    /**
     * 3 分チャレンジ・毎日 1 つずつ積むの「ランキング」から呼ぶ（決定 77）。次に「みんな」を開いたとき、ホーム（どのランキングを見に行くか）を
     * 飛ばして、[mode] のランキングから見せる
     */
    fun open(mode: TowerMode) {
        this.mode = mode
        openRequested = true
    }

    /** [open] で頼まれていたら true を返し、頼みを消す（「みんな」の画面を作るときに 1 回だけ見る） */
    fun consumeOpenRequest(): Boolean = openRequested.also { openRequested = false }

    /** 遊び方 1 つぶんの一覧と、取り直しのようす */
    private inner class ModeList(val mode: TowerMode) {
        var state: State by mutableStateOf(State.Loading)
        var refreshing: Boolean by mutableStateOf(false)
        var refreshFailed: Boolean by mutableStateOf(false)

        /** 取っている最中に [refresh] が呼ばれた。今の分が取れたら、もう一度だけ取り直す */
        var refreshAgain = false
    }

    private val lists: Map<TowerMode, ModeList> = TowerMode.entries.associateWith { ModeList(it) }

    /** いま見ている遊び方の一覧 */
    val state: State get() = lists.getValue(mode).state

    /** [mode] の遊び方の一覧（遊び方を切り替えずに見る） */
    fun stateOf(mode: TowerMode): State = lists.getValue(mode).state

    /**
     * 絵を描くためのタワーの中身（id → Tower）。一覧の JSON には置き方が入っていないので、カードが見えたときに 1 基ずつ取る。
     * 通常と 3 分チャレンジのタワーは保存後に変わらないので、一度取れたら再読み込みでも捨てない。
     * 毎日 1 つずつ積むの塔は送るたびに同じ id のまま新しくなるので、その一覧を取り直したら、出ている絵はそのまま新しい形を取り直す
     * （前は捨てていたが、絵を取りに行くのはカードが見えたときだけなので、一覧が変わらないと取りに行かず、点線の枠のままになっていた。決定 88）
     */
    val sketches = mutableStateMapOf<String, Tower>()
    private val fetching = mutableSetOf<String>()

    /** 取っている最中に、毎日の塔が新しくなったかもしれないと分かった id。今の分が取れたら、もう一度取る */
    private val stale = mutableSetOf<String>()

    /**
     * 取れなかったタワーの id。スクロールで何度もカードが見えるたびに取り直すと、壊れた 1 基のせいで
     * 10 秒待ちの通信を繰り返すことになるので、次の「再読み込み」まで取り直さない
     */
    private val failed = mutableSetOf<String>()

    /** いま見ている遊び方の一覧を取っている最中か（表示中の一覧は残したまま）。「再読み込み」を押せなくするのに使う */
    val refreshing: Boolean get() = lists.getValue(mode).refreshing

    /** いま見ている遊び方の一覧が出たまま、取り直しに失敗したか。一覧は残すが、黙って成功したように見せない */
    val refreshFailed: Boolean get() = lists.getValue(mode).refreshFailed

    /**
     * アプリを立ち上げたときに呼ぶ（AppNav）。まだ一度も取れていなくて、取っている途中でもなければ取り始める。
     * 画面の回転などで何度呼ばれても、取るのは 1 回だけ。取れなかったあとは勝手に取り直さず、「みんな」を開いたときか「再読み込み」で取る。
     * 先に読むのは通常だけ（いちばん見られる。ほかの遊び方は切り替えたときに取る）
     */
    fun prefetch() {
        val normal = lists.getValue(TowerMode.NORMAL)
        if (normal.state is State.Loading && !normal.refreshing) refresh(TowerMode.NORMAL)
    }

    /** カードが見えたときに呼ぶ。取れなかったら、次の「再読み込み」のあとにもう一度取る */
    fun loadSketch(id: String) {
        if (id in sketches || id in failed) return
        fetchSketch(id)
    }

    private fun fetchSketch(id: String) {
        if (!fetching.add(id)) return
        scope.launch {
            runCatching { getTower(id) }
                .onSuccess { sketches[id] = it }
                .onFailure { failed.add(id) }
            fetching.remove(id)
            // 取っている間に一覧が取り直され、塔が新しくなっていたかもしれない：もう一度取る
            if (stale.remove(id)) fetchSketch(id)
        }
    }

    /** 毎日 1 つずつ積むの塔が新しくなったかもしれない：出ている絵（か取っている最中の絵）は、そのまま新しい形を取り直す */
    private fun refetchSketch(id: String) {
        when {
            id in fetching -> stale.add(id)
            id in sketches -> fetchSketch(id)
        }
    }

    /**
     * [mode] の遊び方の一覧を取り直す。画面を開くたび（タブを切り替えて戻ったときも）、遊び方を切り替えたとき、「再読み込み」で呼ぶ。
     * アプリ全体で持っていて開くたびに取り直さないと、送信したばかりのタワーが出ない。
     *
     * - 既に一覧が出ているときは消さずに裏で取り直し、取れなかったら今の一覧を残す（通信が悪いときに一覧が消えないように）
     * - 取っている最中にまた呼ばれたら、**今の分は止めない**（起動時の先読みを「みんな」を開いたときに止めると、
     *   サーバが起きるまでの待ちを最初からやり直すことになる）。今の分が取れたらそれを出し、そのあと 1 回だけ取り直す
     *   （先に走り出した古い結果が、送信したあとの一覧として残らないように）。取れなかったときは取り直さない（続けて待たせない）
     */
    fun refresh(mode: TowerMode = this.mode) {
        val list = lists.getValue(mode)
        if (list.refreshing) {
            list.refreshAgain = true
            return
        }
        failed.clear()
        val shown = list.state as? State.Loaded
        if (shown == null) list.state = State.Loading
        list.refreshing = true
        list.refreshFailed = false
        list.refreshAgain = false
        scope.launch {
            var succeeded = false
            try {
                val towers = listTowers(mode)
                // 毎日 1 つずつ積むの塔は同じ id のまま新しくなるので、前に取った絵は取り直す（取れるまでは前の絵を出しておく）
                if (mode == TowerMode.DAILY) towers.forEach { refetchSketch(it.id) }
                // 高さの順位表。同じ高さなら新しいほうを上に
                list.state = State.Loaded(towers.sortedWith(compareByDescending<TowerSummary> { t -> t.height }.thenByDescending { t -> t.createdAt }))
                succeeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shown == null) list.state = State.Error(e.message ?: e.toString()) else list.refreshFailed = true
            } finally {
                list.refreshing = false
                if (succeeded && list.refreshAgain && isActive) refresh(mode)
                list.refreshAgain = false
            }
        }
    }
}
