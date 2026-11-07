package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test

/**
 * Gemini への指示文の組み立てと、返事の読み取りのテスト。Gemini は呼ばない。
 */
class NamingPromptTest {

    private val jst = ZoneId.of("Asia/Tokyo")
    private fun jstMillis(y: Int, mo: Int, d: Int, h: Int) =
        ZonedDateTime.of(y, mo, d, h, 0, 0, 0, jst).toInstant().toEpochMilli()

    // 横長のジグザグ。端点を除いて 3 回曲がる
    private val zigzag = Part(
        id = "p1",
        points = listOf(Pt(-0.5f, 0f), Pt(-0.2f, 0.2f), Pt(0.1f, 0f), Pt(0.3f, 0.2f), Pt(0.5f, 0f)),
        kind = Kind.ZIGZAG,
        distanceM = 820,
        walkedAt = jstMillis(2026, 11, 6, 17),
    )

    @Test
    fun `指示文に形・距離・時間が言葉で入る`() {
        val prompt = buildNamingPrompt(zigzag)

        assertContains(prompt, "道の形：何度も折れ曲がった道（ジグザグ）。横に長い。3 回曲がった")
        assertContains(prompt, "歩いた距離：820 m")
        assertContains(prompt, "歩いた時間：晩秋の夕方")
        assertContains(prompt, "日本語で 10 文字以内")
        assertContains(prompt, "地名・人名・店名・実在の作品名は入れない")
        assertContains(prompt, NAMING_TONE)
    }

    @Test
    fun `日時が入っていなければ時間の行は渡さない`() {
        val prompt = buildNamingPrompt(zigzag.copy(walkedAt = 0L))

        assertFalse(prompt.contains("歩いた時間"))
    }

    @Test
    fun `指示文に生の数値（日時のミリ秒や座標）を渡さない`() {
        val prompt = buildNamingPrompt(zigzag)

        assertFalse(prompt.contains(zigzag.walkedAt.toString()))
        assertFalse(prompt.contains("0.5"))
        assertFalse(prompt.contains("-0.2"))
    }

    @Test
    fun `輪は全頂点を曲がり角として数え、一周したと伝える`() {
        val ring = Part(
            id = "r", kind = Kind.RING, closed = true, distanceM = 1500,
            points = listOf(Pt(0f, 0.5f), Pt(0.5f, 0f), Pt(0f, -0.5f), Pt(-0.5f, 0f)),
        )

        assertEquals("ぐるっと一周して戻ってきた道（輪）。縦横がほぼ同じ。4 回曲がった", describeShape(ring))
    }

    @Test
    fun `まっすぐな棒は曲がった回数を言わない`() {
        val bar = Part(id = "b", kind = Kind.BAR, points = listOf(Pt(0f, -0.5f), Pt(0f, 0.5f)))

        assertEquals("まっすぐな道（棒）。縦に長い", describeShape(bar))
    }

    @Test
    fun `距離は 1 km 未満なら m、以上なら km`() {
        assertEquals("820 m", describeDistance(820))
        assertEquals("1.0 km", describeDistance(1000))
        assertEquals("2.3 km", describeDistance(2345))
        assertEquals("不明", describeDistance(0))
    }

    @Test
    fun `時間帯と季節は日本時間で決める`() {
        // UTC では前日の 20 時でも、日本時間では 1/1 の朝 5 時
        assertEquals("冬の朝", describeWhen(jstMillis(2027, 1, 1, 5)))
        assertEquals("初夏の昼", describeWhen(jstMillis(2026, 6, 10, 12)))
        assertEquals("秋の夜", describeWhen(jstMillis(2026, 10, 3, 21)))
        assertEquals("早春の深夜", describeWhen(jstMillis(2026, 3, 3, 2)))
        assertNull(describeWhen(0L))
    }

    // --- 返事の読み取り ---

    @Test
    fun `返事の JSON から名前を読む`() {
        assertEquals("夕焼けの稲妻", parseNameResponse("""{"name":"夕焼けの稲妻"}"""))
        assertEquals("夕焼けの稲妻", parseNameResponse("""  {"name": "夕焼けの稲妻", "extra": 1} """))
    }

    @Test
    fun `JSON でない返事はそのまま名前とみなし、壊れた JSON や空は null`() {
        assertEquals("夕焼けの稲妻", parseNameResponse("夕焼けの稲妻"))
        assertNull(parseNameResponse("""{"name":"""))
        assertNull(parseNameResponse("""{"other":"x"}"""))
        assertNull(parseNameResponse(""))
        assertNull(parseNameResponse(null))
    }
}
