package com.rein4ce.roadcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** アプリの設定（画面の明るさ） */
class AppSettingsTest {

    /** SharedPreferences の代わり */
    private class MapStore : AppSettings.Store {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
    }

    @Test
    fun `first it follows the phone`() {
        assertEquals(ThemeMode.SYSTEM, AppSettings(MapStore()).themeMode.value)
    }

    @Test
    fun `the chosen mode is saved and survives a restart`() {
        val store = MapStore()
        AppSettings(store).setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, AppSettings(store).themeMode.value)
    }

    @Test
    fun `an unknown saved value falls back to following the phone`() {
        val store = MapStore().apply { map["theme_mode"] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, AppSettings(store).themeMode.value)
    }

    @Test
    fun `each mode decides dark or light`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }
}
