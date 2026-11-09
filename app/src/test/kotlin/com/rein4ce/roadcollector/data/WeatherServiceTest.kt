package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.WalkWeather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 天気の保存（parts_weather.json）と、天気を取ってくる係（WeatherService）のテスト。
 * 通信は本物の代わりに、聞かれた URL を記録して決まった返事を返す関数を渡す。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** テストの「今」：2026-11-07 12:00（世界時） */
    private val now = 1_794_052_800_000L

    /** [at] の [ageMs] 前に歩き終えたパーツ（ふつうは、テストの「今」の [ageMs] 前） */
    private fun walk(id: String, ageMs: Long, at: Long = now) = Part(
        id = id,
        points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)),
        kind = Kind.BAR,
        distanceM = 341,
        walkedAt = at - ageMs,
    )

    private val place = PartPlace(lat = 35.7501, lng = 139.8055, sizeM = 341.0)

    private val rainNow = """{"current":{"time":"2026-11-07T12:00","weather_code":63,"precipitation":2.5}}"""
    private val cloudyHour = """{"hourly":{"time":["2026-11-05T09:00"],"weather_code":[3],"precipitation":[0.0]}}"""

    private val asked = mutableListOf<String>()

    private fun answer(url: String): String? {
        asked += url
        return if ("current=" in url) rainNow else cloudyHour
    }

    private fun TestScope.repository() = PartRepository(File(tmp.root, "parts.json"), this) { _, _ -> }

    /** WeatherService を動かす場所。テストの時計で動かし、テストが終わったら止める（ずっと待ち続ける係なので） */
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun stop() = scopes.forEach { it.cancel() }

    private fun TestScope.service(
        repo: PartRepository,
        fetch: suspend (String) -> String? = { answer(it) },
        location: Pair<Double, Double>? = null,
        clock: Long = now,
    ): WeatherService {
        // backgroundScope は advanceUntilIdle() で進まないので、同じ時計の別のスコープを使う
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).also { scopes += it }
        return WeatherService(repo, scope, fetch = fetch, lastLocation = { location }, now = { clock })
    }

    @Test
    fun `a part just collected gets the weather now`() = runTest {
        // 今は 12:40。12:35 に歩き終えた（同じ 12 時台なので、今の天気）
        val clock = now + 40 * MIN
        val repo = repository()
        service(repo, clock = clock)
        repo.add(walk("w1", ageMs = 5 * MIN, at = clock), place)
        advanceUntilIdle()

        assertEquals(WalkWeather(63, 2.5), repo.weatherOf("w1"))
        assertTrue(asked.single().contains("current="))
        // 送った位置は 0.1 度に丸めてある
        assertTrue(asked.single().contains("latitude=35.8&longitude=139.8&"))
    }

    @Test
    fun `an older part gets the weather of the hour it was walked`() = runTest {
        val repo = repository()
        service(repo)
        repo.add(walk("w2", ageMs = 2 * DAY), place)
        advanceUntilIdle()

        assertEquals(WalkWeather(3, 0.0), repo.weatherOf("w2"))
        assertTrue(asked.single().contains("start_hour=2026-11-05T12:00"))
    }

    @Test
    fun `a long walk gets the weather of the hour it started, not when it ended`() = runTest {
        // 2 日前に歩き始めて、5 分前に歩き終えた（途中で天気が変わっていても、歩き始めたときの天気にする。決定 55）
        val repo = repository()
        service(repo)
        repo.add(walk("long", ageMs = 5 * 60_000L), place.copy(startedAt = now - 2 * DAY))
        advanceUntilIdle()

        assertEquals(WalkWeather(3, 0.0), repo.weatherOf("long"))
        assertTrue(asked.single().contains("start_hour=2026-11-05T12:00"))
    }

    @Test
    fun `a short walk that started in the previous hour gets the weather of the hour it started`() = runTest {
        // 今は 12:10。11:50 に歩き始めて、12:05 に歩き終えた（1 時間に満たない散歩でも、歩き始めの 11 時台の天気。#103 の C のレビュー）
        val clock = now + 10 * MIN
        val repo = repository()
        service(repo, clock = clock)
        repo.add(walk("short", ageMs = 5 * MIN, at = clock), place.copy(startedAt = clock - 20 * MIN))
        advanceUntilIdle()

        assertEquals(WalkWeather(3, 0.0), repo.weatherOf("short"))
        assertTrue(asked.single().contains("start_hour=2026-11-07T11:00"))
    }

    @Test
    fun `a short walk within the same hour gets the weather now`() = runTest {
        // 今は 12:40。12:05 に歩き始めて、12:35 に歩き終えた（同じ 12 時台なので、今の天気）
        val clock = now + 40 * MIN
        val repo = repository()
        service(repo, clock = clock)
        repo.add(walk("same", ageMs = 5 * MIN, at = clock), place.copy(startedAt = clock - 35 * MIN))
        advanceUntilIdle()

        assertEquals(WalkWeather(63, 2.5), repo.weatherOf("same"))
        assertTrue(asked.single().contains("current="))
    }

    @Test
    fun `parts older than 90 days are not asked`() = runTest {
        val repo = repository()
        service(repo)
        repo.add(walk("old", ageMs = 100 * DAY), place)
        advanceUntilIdle()

        assertNull(repo.weatherOf("old"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a failed request is not repeated while the app is open`() = runTest {
        val repo = repository()
        var calls = 0
        service(repo, fetch = { calls++; null })
        repo.add(walk("w3", ageMs = 60_000L), place)
        advanceUntilIdle()
        repo.add(walk("w4", ageMs = 60_000L), place) // 別のパーツが増えても、w3 は聞き直さない
        advanceUntilIdle()

        assertNull(repo.weatherOf("w3"))
        assertEquals(2, calls)
    }

    @Test
    fun `the weather is saved apart from the parts, survives a restart and goes with the part`() = runTest {
        // 今は 12:40。12:39 に歩き終えた（同じ時台なので、今の天気）
        val clock = now + 40 * MIN
        val repo = repository()
        service(repo, clock = clock)
        repo.add(walk("w5", ageMs = MIN, at = clock), place)
        advanceUntilIdle()
        assertFalse(File(tmp.root, "parts.json").readText().contains("weather"))
        assertTrue(File(tmp.root, "parts_weather.json").readText().contains("\"code\":63"))

        val again = repository()
        advanceUntilIdle()
        assertEquals(WalkWeather(63, 2.5), again.weatherOf("w5"))

        again.remove("w5")
        advanceUntilIdle()
        assertNull(again.weatherOf("w5"))
        assertFalse(File(tmp.root, "parts_weather.json").readText().contains("w5"))
    }

    @Test
    fun `samples have a weather of their own`() = runTest {
        val repo = repository()
        advanceUntilIdle()
        SampleParts.all.forEach { assertEquals(SampleParts.weather[it.id], repo.weatherOf(it.id)) }
    }

    @Test
    fun `the weather now uses the phone's last location, or else the latest walk`() = runTest {
        val repo = repository()
        val withPhone = service(repo, location = 36.0 to 140.0)
        advanceUntilIdle()
        withPhone.refreshCurrent()
        assertEquals(WalkWeather(63, 2.5), withPhone.current.value)
        assertTrue(asked.last().contains("latitude=36.0&longitude=140.0&"))

        // 位置が分からなければ、最後に歩いた場所で聞く。歩いたことも無ければ聞かない
        asked.clear()
        val noPhone = service(repo)
        noPhone.refreshCurrent()
        assertNull(noPhone.current.value)
        repo.add(walk("w6", ageMs = 200 * DAY), place) // 古いので、このパーツの天気は聞かない
        advanceUntilIdle()
        noPhone.refreshCurrent()
        assertEquals(WalkWeather(63, 2.5), noPhone.current.value)
        assertTrue(asked.single().contains("latitude=35.8&longitude=139.8&"))

        // 15 分以内なら聞き直さない
        noPhone.refreshCurrent()
        assertEquals(1, asked.size)
    }

    private companion object {
        const val MIN = 60 * 1000L
        const val DAY = 24 * 60 * MIN
    }
}
