package com.rein4ce.roadcollector.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 積む画面の、モードごとの自分の記録。スマホの中だけに保存する（サーバには送らない）。
 * モードを選ぶ画面（ui/game/ModeSelect.kt）が、各モードのカードに出す。
 *
 * いまは「通常」の最高の高さだけ。終わって塔が止まり、台の上にパーツが残ったとき（「サーバに送信」できる結果）だけ数える。
 * 通常は決定 62 で「全部のパーツから終わりなく出して、台から落ちたら終わり」に変わったので、5 本まで選んで積んでいたころの記録
 * （保存の名前 `normal_best_m`）とは混ぜず、別の名前（[KEY_NORMAL_BEST]）で数え直す。前の記録は読まない
 * 3 分チャレンジ（#78）は自分の記録を自分で持つので、ここには入れない。
 *
 * @param store 保存先。アプリでは SharedPreferences、テストではただの変数
 */
class StackRecords(private val store: Store) {

    /** 記録を読み書きする場所 */
    interface Store {
        /** 通常の最高の高さ [m]。まだ無ければ null */
        fun loadNormalBest(): Float?
        fun saveNormalBest(heightM: Float)
    }

    /** アプリからはこちら（stack_records という名前の SharedPreferences） */
    constructor(context: Context) : this(object : Store {
        private val prefs = context.getSharedPreferences("stack_records", Context.MODE_PRIVATE)
        override fun loadNormalBest(): Float? =
            if (prefs.contains(KEY_NORMAL_BEST)) prefs.getFloat(KEY_NORMAL_BEST, 0f) else null
        override fun saveNormalBest(heightM: Float) = prefs.edit().putFloat(KEY_NORMAL_BEST, heightM).apply()
    })

    private val _normalBest = MutableStateFlow(store.loadNormalBest())

    /** 通常の最高の高さ [m]（まだ無ければ null） */
    val normalBest: StateFlow<Float?> = _normalBest

    /**
     * 通常で積み切った高さを渡す。今までの最高より高ければ、覚えて true を返す。
     * 0 以下・数でない値は数えない（同じ結果を何度渡しても、2 回目からは何もしない）
     */
    fun submitNormal(heightM: Float): Boolean {
        if (heightM.isNaN() || heightM <= 0f) return false
        val best = _normalBest.value
        if (best != null && heightM <= best) return false
        store.saveNormalBest(heightM)
        _normalBest.value = heightM
        return true
    }

    private companion object {
        /** 通常（決定 62 の終わりなく積む形）の最高。前の「5 本まで」の記録は `normal_best_m` に残っていて、読まない */
        const val KEY_NORMAL_BEST = "endless_best_m"
    }
}
