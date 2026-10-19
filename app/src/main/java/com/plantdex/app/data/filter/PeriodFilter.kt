package com.plantdex.app.data.filter

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 촬영 시기 필터. (지역·종·도감 등 다른 필터는 추후 유료 기능으로 남겨 둡니다.)
 */
sealed interface PeriodFilter {
    val label: String

    data object All : PeriodFilter { override val label = "전체" }
    /** 오늘 하루 */
    data object Today : PeriodFilter { override val label = "오늘" }

    /** 최근 1개월 (한 달 전 같은 날부터 오늘까지) */
    data object LastMonth : PeriodFilter { override val label = "최근 1개월" }

    /** 최근 3개월 */
    data object Last3Months : PeriodFilter { override val label = "최근 3개월" }

    /** 1년 전의 최근 1개월 (작년 이맘때) */
    data object YearAgoMonth : PeriodFilter { override val label = "1년 전 (1개월)" }

    /** 직접 고른 기간. 두 날짜 모두 포함합니다. 올해가 아니면 연도도 적습니다. */
    data class Custom(val start: LocalDate, val end: LocalDate) : PeriodFilter {
        override val label: String get() {
            val from = minOf(start, end)
            val to = maxOf(start, end)
            val thisYear = LocalDate.now().year
            fun text(d: LocalDate) =
                if (from.year == thisYear && to.year == thisYear) "${d.monthValue}.${d.dayOfMonth}"
                else "${d.year % 100}.${d.monthValue}.${d.dayOfMonth}"
            return "${text(from)} ~ ${text(to)}"
        }
    }

    /** 이 필터가 허용하는 촬영 시각 범위 [시작, 끝) (epoch millis). 전체면 null. */
    fun range(today: LocalDate, zone: ZoneId): LongRange? {
        val (from, untilExclusive) = when (this) {
            All -> return null
            Today -> today to today.plusDays(1)
            LastMonth -> today.minusMonths(1) to today.plusDays(1)
            Last3Months -> today.minusMonths(3) to today.plusDays(1)
            YearAgoMonth -> today.minusYears(1).let { it.minusMonths(1) to it.plusDays(1) }
            is Custom -> minOf(start, end) to maxOf(start, end).plusDays(1)
        }
        return from.atStartOfDay(zone).toInstant().toEpochMilli() until
            untilExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun matches(capturedAt: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        range(today, zone)?.contains(capturedAt) ?: true

    companion object {
        /**
         * 미리 정한 기간들. 매번 새로 만듭니다: 인터페이스의 companion 안에 값으로 저장하면,
         * `PeriodFilter.All` 을 먼저 쓸 때 All 이 아직 만들어지는 중이라 목록에 null 이 들어갑니다.
         */
        /** 지도에서 바로 고르는 기간 */
        val recentPresets: List<PeriodFilter> get() = listOf(Today, LastMonth, Last3Months)

        /** 지도의 '기타' 메뉴에 넣는 기간 (직접 선택과 함께, 추후 유료 기능 후보) */
        val extraPresets: List<PeriodFilter> get() = listOf(YearAgoMonth)

        /** DatePicker 가 주는 UTC 자정 millis → 날짜 */
        fun utcMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
    }
}
