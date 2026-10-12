package com.plantdex.app.data.filter

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 촬영 시기 필터. (지역·종·도감 등 다른 필터는 추후 유료 기능으로 남겨 둡니다.)
 */
sealed interface PeriodFilter {
    val label: String

    data object All : PeriodFilter { override val label = "전체" }
    data object ThisWeek : PeriodFilter { override val label = "이번 주" }
    data object ThisMonth : PeriodFilter { override val label = "이번 달" }
    data object ThisYear : PeriodFilter { override val label = "올해" }

    /** 직접 고른 기간. 두 날짜 모두 포함합니다. */
    data class Custom(val start: LocalDate, val end: LocalDate) : PeriodFilter {
        override val label: String get() = "${start.monthValue}.${start.dayOfMonth} ~ ${end.monthValue}.${end.dayOfMonth}"
    }

    /** 이 필터가 허용하는 촬영 시각 범위 [시작, 끝) (epoch millis). 전체면 null. */
    fun range(today: LocalDate, zone: ZoneId): LongRange? {
        val (from, untilExclusive) = when (this) {
            All -> return null
            ThisWeek -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
            ThisMonth -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            ThisYear -> today.withDayOfYear(1).let { it to it.plusYears(1) }
            is Custom -> minOf(start, end) to maxOf(start, end).plusDays(1)
        }
        return from.atStartOfDay(zone).toInstant().toEpochMilli() until
            untilExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun matches(capturedAt: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        range(today, zone)?.contains(capturedAt) ?: true

    companion object {
        val presets: List<PeriodFilter> = listOf(All, ThisWeek, ThisMonth, ThisYear)

        /** DatePicker 가 주는 UTC 자정 millis → 날짜 */
        fun utcMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
    }
}
