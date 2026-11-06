package com.rein4ce.roadcollector.ui.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

/** 名前を付けるダイアログの文字数の数え方（10 文字まで） */
class NameLengthTest {

    @Test
    fun `japanese characters count one each`() {
        assertEquals(6, nameLength("黄昏の折り目"))
        assertEquals(MAX_NAME_LENGTH, nameLength("初夏を刺す直進の小道"))
    }

    @Test
    fun `spaces around the name are not counted`() {
        // 保存のときに落とすので、数えると 10 文字ちょうどの名前が保存できなくなる
        assertEquals(6, nameLength("  冬夜のループ "))
        assertEquals(0, nameLength("   "))
    }

    @Test
    fun `an emoji counts as one character`() {
        // 🚶 は UTF-16 では 2 つ分だが、見た目は 1 文字
        assertEquals(3, nameLength("🚶の道"))
    }
}
