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

    @Test
    fun `the other settings start with their defaults`() {
        val settings = AppSettings(MapStore())
        assertEquals(WeatherMotion.NORMAL, settings.weatherMotion.value)
        assertFalse(settings.keepScreenOn.value)
        assertTrue(settings.aiNaming.value)
        assertEquals("", settings.nickname.value)
    }

    @Test
    fun `the other settings are saved and survive a restart`() {
        val store = MapStore()
        AppSettings(store).apply {
            setWeatherMotion(WeatherMotion.STILL)
            setKeepScreenOn(true)
            setAiNaming(false)
            setNickname("みちつみ太郎")
        }
        val again = AppSettings(store)
        assertEquals(WeatherMotion.STILL, again.weatherMotion.value)
        assertTrue(again.keepScreenOn.value)
        assertFalse(again.aiNaming.value)
        assertEquals("みちつみ太郎", again.nickname.value)
    }

    @Test
    fun `the nickname is trimmed, kept on one line and cut to the limit`() {
        assertEquals("みちつみ 太郎", AppSettings.cleanNickname("  みちつみ\n  太郎 "))
        assertEquals("", AppSettings.cleanNickname("   "))
        val long = "あいうえおかきくけこさしすせそ"
        assertEquals(AppSettings.MAX_NICKNAME_LENGTH, AppSettings.cleanNickname(long).length)
        // 絵文字（2 つの char でできている字）を途中で切らない
        val emoji = "😀".repeat(20)
        assertEquals(AppSettings.MAX_NICKNAME_LENGTH, AppSettings.cleanNickname(emoji).let { it.codePointCount(0, it.length) })
    }

    @Test
    fun `the how-to is shown until it is closed once`() {
        val store = MapStore()
        assertFalse(AppSettings(store).howToSeen.value)
        AppSettings(store).setHowToSeen(true)
        assertTrue(AppSettings(store).howToSeen.value)
    }

    @Test
    fun `broken saved values fall back to the defaults`() {
        val store = MapStore().apply {
            map["weather_motion"] = "TURBO"
            map["keep_screen_on"] = "maybe"
            map["ai_naming"] = ""
        }
        val settings = AppSettings(store)
        assertEquals(WeatherMotion.NORMAL, settings.weatherMotion.value)
        assertFalse(settings.keepScreenOn.value)
        assertTrue(settings.aiNaming.value)
    }
}
