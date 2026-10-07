package com.plantdex.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterSummaryTest {
    private data class Rec(val key: String, val name: String, val at: Long, val likes: Int = 0)

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

    @Test
    fun `panel order puts the most liked species and records first`() {
        val s = summarize(
            Rec("trumpet", "능소화", 1, likes = 1),
            Rec("trumpet", "능소화", 2, likes = 0),
            Rec("trumpet", "능소화", 3, likes = 0),
            Rec("cherry", "벚나무", 4, likes = 2),
            Rec("cherry", "벚나무", 5, likes = 5),
            Rec("clover", "토끼풀", 6, likes = 0),
        )
        val groups = s.groupsByLikes({ it.likes }, { it.at })
        assertEquals(listOf("cherry", "trumpet", "clover"), groups.map { it.key })
        assertEquals(listOf(5, 2), groups[0].items.map { it.likes })
        // 좋아요가 같으면 최근 기록이 먼저
        assertEquals(listOf(1L, 3L, 2L), groups[1].items.map { it.at })
        // 마커 이름은 여전히 기록 수 기준
        assertEquals("능소화 등 3종", s.label)
    }
}
