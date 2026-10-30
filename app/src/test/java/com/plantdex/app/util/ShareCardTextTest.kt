package com.plantdex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareCardTextTest {
    @Test
    fun `builds lines and caption`() {
        val text = ShareCardText.of("서양민들레", "Dandelion", "Taraxacum officinale", "2026.4.3", " 서울숲 ", "  노란 꽃이 가득  ")
        assertEquals("2026.4.3 · 서울숲", text.whenWhere)
        assertEquals("노란 꽃이 가득", text.memo)
        assertEquals(
            "🌿 서양민들레 · Dandelion\nTaraxacum officinale\n2026.4.3 · 서울숲\n노란 꽃이 가득\nhttps://x/y.jpg\n#PlantDex",
            text.caption("https://x/y.jpg"),
        )
    }

    @Test
    fun `drops a duplicate english name and shortens long memos`() {
        val text = ShareCardText.of("Rosa", "rosa", "Rosa rugosa", "2026.5.1", null, "a".repeat(100))
        assertNull(text.english)
        assertEquals("2026.5.1", text.whenWhere)
        assertEquals(ShareCardText.MAX_MEMO, text.memo!!.length)
        assertEquals("🌿 Rosa\nRosa rugosa\n2026.5.1\n${text.memo}\n#PlantDex", text.caption(null))
    }
}
