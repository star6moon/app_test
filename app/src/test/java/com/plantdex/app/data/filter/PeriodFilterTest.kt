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

    /**
     * 앱에서 실제로 난 충돌 재현: `PeriodFilter.All` 을 먼저 쓰고 나서 presets 를 읽으면
     * 첫 항목이 null 이었습니다. 클래스 초기화 순서를 통제하려고 새 클래스 로더에서 확인합니다.
     */
    @Test
    fun presetsHaveNoNullWhenAllIsTouchedFirst() {
        val urls = System.getProperty("java.class.path").split(java.io.File.pathSeparator)
            .map { java.io.File(it).toURI().toURL() }.toTypedArray()
        java.net.URLClassLoader(urls, null).use { loader ->
            val all = loader.loadClass("com.plantdex.app.data.filter.PeriodFilter\$All")
            all.getField("INSTANCE").get(null)
            val iface = loader.loadClass("com.plantdex.app.data.filter.PeriodFilter")
            val companion = iface.getField("Companion").get(null)
            val presets = companion.javaClass.getMethod("getPresets").invoke(companion) as List<*>
            assertTrue("presets = $presets", presets.none { it == null })
        }
    }

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
