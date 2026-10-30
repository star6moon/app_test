package com.plantdex.app.data.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinMeaningsTest {
    @Test
    fun `explains genus and epithet, following suffix changes`() {
        val m = LatinMeanings.explain("Dendranthema grandiflorum (Ramat.) Kitam.")
        assertEquals(listOf("Dendranthema", "grandiflorum"), m.map { it.word })
        assertTrue(m[0].meaning.contains("나무"))
        assertEquals("큰 꽃의", m[1].meaning)

        assertEquals("약방(officina)에서 쓰던, 약용의", LatinMeanings.explain("Taraxacum officinale").last().meaning)
        assertEquals("일본의", LatinMeanings.explain("Camellia japonica").last().meaning)
        assertEquals("일본의", LatinMeanings.explain("Fatsia japonica").last().meaning)
    }

    @Test
    fun `hybrids and unknown names`() {
        val m = LatinMeanings.explain("Prunus × yedoensis Matsum.")
        assertEquals(listOf("Prunus", "yedoensis"), m.map { it.word })
        assertTrue(LatinMeanings.explain("Zzyzx qqqq").isEmpty())
        assertTrue(LatinMeanings.explain("").isEmpty())
    }
}
