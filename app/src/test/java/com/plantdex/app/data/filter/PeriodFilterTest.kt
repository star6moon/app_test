package com.plantdex.app.data.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PeriodFilterTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 14) // 수요일

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun allHasNoRange() = assertNull(PeriodFilter.All.range(today, zone))

    @Test
    fun weekStartsOnMonday() {
        val f = PeriodFilter.ThisWeek
        assertTrue(f.matches(at(2026, 10, 12, 0), today, zone))
        assertTrue(f.matches(at(2026, 10, 18, 23), today, zone))
        assertFalse(f.matches(at(2026, 10, 11, 23), today, zone))
        assertFalse(f.matches(at(2026, 10, 19, 0), today, zone))
    }

    @Test
    fun monthAndYear() {
        assertTrue(PeriodFilter.ThisMonth.matches(at(2026, 10, 1, 0), today, zone))
        assertFalse(PeriodFilter.ThisMonth.matches(at(2026, 9, 30, 23), today, zone))
        assertTrue(PeriodFilter.ThisYear.matches(at(2026, 1, 1, 0), today, zone))
        assertFalse(PeriodFilter.ThisYear.matches(at(2025, 12, 31, 23), today, zone))
    }

    @Test
    fun customIncludesBothEnds() {
        val f = PeriodFilter.Custom(LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 1))
        assertTrue(f.matches(at(2026, 5, 1, 0), today, zone))
        assertTrue(f.matches(at(2026, 5, 3, 23), today, zone))
        assertFalse(f.matches(at(2026, 5, 4, 0), today, zone))
    }
}
