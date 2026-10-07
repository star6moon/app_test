package com.plantdex.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterSummaryTest {
    private data class Rec(val key: String, val name: String, val at: Long)

    private fun summarize(vararg items: Rec) = ClusterSummary.of(items.toList(), { it.key }, { it.name }, { it.at })

    @Test
    fun `labels several species by the most recorded one`() {
        val s = summarize(
            Rec("dandelion", "서양민들레", 1),
            Rec("trumpet", "능소화", 2),
            Rec("trumpet", "능소화", 3),
            Rec("cherry", "벚나무", 4),
            Rec("clover", "토끼풀", 5),
        )
        assertEquals("능소화 등 4종", s.label)
        assertEquals(5, s.recordCount)
        assertEquals(listOf("trumpet", "clover", "cherry", "dandelion"), s.groups.map { it.key })
        assertEquals(3L, s.top.items.first().at) // 종 안에서는 최신순
    }

    @Test
    fun `single species shows record count`() {
        assertEquals("능소화 3건", summarize(Rec("t", "능소화", 1), Rec("t", "능소화", 2), Rec("t", "능소화", 3)).label)
    }

    @Test
    fun `ties go to the most recently recorded species`() {
        assertEquals("벚나무 등 2종", summarize(Rec("a", "개나리", 1), Rec("b", "벚나무", 9)).label)
    }
}
