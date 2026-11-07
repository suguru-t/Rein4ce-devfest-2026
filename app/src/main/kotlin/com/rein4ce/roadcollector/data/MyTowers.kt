package com.rein4ce.roadcollector.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * C 担当：このスマホから「サーバに送信」したタワーの id。スマホの中だけに保存する。
 *
 * サーバには利用者を見分ける仕組み（ログインや端末の id）が無く、作者名はただのニックネームなので、
 * 同じ名前の人が 2 人いても区別できない。「みんな」で自分のタワーに「あなた」の札を出すために、
 * 送信できた id を端末に残しておく（端末を替えたり、アプリを消したりすると印は消える）。
 *
 * @param store 保存先。アプリでは SharedPreferences、テストではただの集合
 */
class MyTowers(private val store: Store) {

    /** id の集まりをまるごと読み書きする場所 */
    interface Store {
        fun load(): Set<String>
        fun save(ids: Set<String>)
    }

    /** アプリからはこちら（my_towers という名前の SharedPreferences） */
    constructor(context: Context) : this(object : Store {
        private val prefs = context.getSharedPreferences("my_towers", Context.MODE_PRIVATE)
        override fun load(): Set<String> = prefs.getStringSet(KEY_IDS, null).orEmpty().toSet()
        override fun save(ids: Set<String>) = prefs.edit().putStringSet(KEY_IDS, ids).apply()
    })

    private val _ids = MutableStateFlow(store.load())

    /** 送信したタワーの id。「みんな」がこれを見て「あなた」の札を出す */
    val ids: StateFlow<Set<String>> = _ids

    /** 送信できたら呼ぶ（ApiClient.onPosted から）。空の id は無視する */
    fun add(id: String) {
        if (id.isBlank() || id in _ids.value) return
        val next = _ids.value + id
        store.save(next)
        _ids.value = next
    }

    private companion object {
        const val KEY_IDS = "ids"
    }
}
