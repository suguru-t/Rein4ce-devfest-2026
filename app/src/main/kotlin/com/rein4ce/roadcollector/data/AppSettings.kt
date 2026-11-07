package com.rein4ce.roadcollector.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 画面の明るさ（ライト・ダーク）の決め方 */
enum class ThemeMode(val label: String) {
    /** 端末の設定（ダークテーマ）に合わせる。初めはこれ */
    SYSTEM("端末に合わせる"),
    LIGHT("ライト"),
    DARK("ダーク"),
    ;

    /** この決め方で、今ダークにするか。[systemDark] は端末がダークテーマか */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/**
 * アプリの設定（いまは画面の明るさだけ）。スマホの中だけに保存する。
 * 採集画面の右上の歯車から変える。
 *
 * @param store 保存先。アプリでは SharedPreferences、テストではただの Map
 */
class AppSettings(private val store: Store) {

    /** 文字列を 1 つずつ保存する場所 */
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String)
    }

    /** アプリからはこちら（settings という名前の SharedPreferences） */
    constructor(context: Context) : this(object : Store {
        private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    })

    private val _themeMode = MutableStateFlow(readThemeMode())

    /** 画面の明るさ。変えると、すぐにアプリ全体の色が変わる */
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        store.put(KEY_THEME, mode.name)
        _themeMode.value = mode
    }

    /** 保存した値が読めない（前の版の値など）ときは「端末に合わせる」 */
    private fun readThemeMode(): ThemeMode =
        store.get(KEY_THEME)?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
