package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rein4ce.roadcollector.core.model.Tower
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
 * サーバが無いときはこの画面にエラーを出すだけで、他の画面には影響しない。
 *
 * @param listTowers 一覧を取る（テストでは差し替える）
 * @param getTower   タワー 1 基の中身を取る
 * @param scope      通信を走らせる scope。アプリと同じ寿命
 */
class TowerGallery(
    private val listTowers: suspend () -> List<TowerSummary> = { ApiClient.listTowers() },
    private val getTower: suspend (String) -> Tower = { ApiClient.getTower(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    sealed class State {
        data object Loading : State()
        data class Error(val message: String) : State()
        data class Loaded(val towers: List<TowerSummary>) : State()
    }

    var state: State by mutableStateOf(State.Loading)
        private set

    /**
     * 絵を描くためのタワーの中身（id → Tower）。一覧の JSON には置き方が入っていないので、カードが見えたときに 1 基ずつ取る。
     * タワーは保存後に変わらないので、一度取れたら再読み込みでも捨てない
     */
    val sketches = mutableStateMapOf<String, Tower>()
    private val fetching = mutableSetOf<String>()

    /**
     * 取れなかったタワーの id。スクロールで何度もカードが見えるたびに取り直すと、壊れた 1 基のせいで
     * 10 秒待ちの通信を繰り返すことになるので、次の「再読み込み」まで取り直さない
     */
    private val failed = mutableSetOf<String>()

    /** 一覧を取っている最中か（表示中の一覧は残したまま）。「再読み込み」を押せなくするのに使う */
    var refreshing: Boolean by mutableStateOf(false)
        private set

    /** 一覧が出たまま取り直しに失敗したか。一覧は残すが、黙って成功したように見せない */
    var refreshFailed: Boolean by mutableStateOf(false)
        private set

    /** 取っている最中に [refresh] が呼ばれた。今の分が取れたら、もう一度だけ取り直す */
    private var refreshAgain = false

    /**
     * アプリを立ち上げたときに呼ぶ（AppNav）。まだ一度も取れていなくて、取っている途中でもなければ取り始める。
     * 画面の回転などで何度呼ばれても、取るのは 1 回だけ。取れなかったあとは勝手に取り直さず、「みんな」を開いたときか「再読み込み」で取る
     */
    fun prefetch() {
        if (state is State.Loading && !refreshing) refresh()
    }

    /** カードが見えたときに呼ぶ。取れなかったら、次の「再読み込み」のあとにもう一度取る */
    fun loadSketch(id: String) {
        if (id in sketches || id in failed || !fetching.add(id)) return
        scope.launch {
            runCatching { getTower(id) }
                .onSuccess { sketches[id] = it }
                .onFailure { failed.add(id) }
            fetching.remove(id)
        }
    }

    /**
     * 一覧を取り直す。画面を開くたび（タブを切り替えて戻ったときも）と、「再読み込み」で呼ぶ。
     * アプリ全体で持っていて開くたびに取り直さないと、送信したばかりのタワーが出ない。
     *
     * - 既に一覧が出ているときは消さずに裏で取り直し、取れなかったら今の一覧を残す（通信が悪いときに一覧が消えないように）
     * - 取っている最中にまた呼ばれたら、**今の分は止めない**（起動時の先読みを「みんな」を開いたときに止めると、
     *   サーバが起きるまでの待ちを最初からやり直すことになる）。今の分が取れたらそれを出し、そのあと 1 回だけ取り直す
     *   （先に走り出した古い結果が、送信したあとの一覧として残らないように）。取れなかったときは取り直さない（続けて待たせない）
     */
    fun refresh() {
        if (refreshing) {
            refreshAgain = true
            return
        }
        failed.clear()
        val shown = state as? State.Loaded
        if (shown == null) state = State.Loading
        refreshing = true
        refreshFailed = false
        refreshAgain = false
        scope.launch {
            var succeeded = false
            try {
                val towers = listTowers()
                // 高さの順位表。同じ高さなら新しいほうを上に
                state = State.Loaded(towers.sortedWith(compareByDescending<TowerSummary> { t -> t.height }.thenByDescending { t -> t.createdAt }))
                succeeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shown == null) state = State.Error(e.message ?: e.toString()) else refreshFailed = true
            } finally {
                refreshing = false
                if (succeeded && refreshAgain && isActive) refresh()
                refreshAgain = false
            }
        }
    }
}
