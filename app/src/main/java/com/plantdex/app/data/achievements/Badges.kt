package com.plantdex.app.data.achievements

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.ScientificName
import com.plantdex.app.data.model.CollectionEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 배지 하나. [progress]/[goal] 로 얼마나 남았는지 보여줍니다. */
data class Badge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val progress: Int,
    val goal: Int,
) {
    val earned: Boolean get() = progress >= goal
}

/**
 * 내 기록으로 배지를 계산합니다 (서버 저장 없이 앱에서 계산).
 * 화면과 무관한 순수 계산이라 JVM 단위 테스트로 확인합니다.
 */
object Badges {

    fun compute(entries: List<CollectionEntry>, catalog: PlantCatalog, zone: ZoneId = ZoneId.systemDefault()): List<Badge> {
        val species = entries.map { ScientificName.binomialKey(it.scientificName) ?: it.scientificName.lowercase() }.toSet().size
        val hours = entries.map { Instant.ofEpochMilli(it.capturedAt).atZone(zone).hour }
        val seasons = entries.map { seasonOf(Instant.ofEpochMilli(it.capturedAt).atZone(zone).monthValue) }.toSet().size
        val places = entries.mapNotNull { it.location?.placeName?.takeIf { p -> p.isNotBlank() } }.toSet().size
        val likes = entries.sumOf { it.likeCount }
        val publicCount = entries.count { it.isPublic }
        val progress = catalog.progress(entries)

        // 기록을 종·날짜별로 묶어 보는 배지들
        val bySpecies = entries.groupBy { speciesKey(it) }
        val speciesAllSeasons = bySpecies.values.maxOfOrNull { list ->
            list.map { seasonOf(Instant.ofEpochMilli(it.capturedAt).atZone(zone).monthValue) }.toSet().size
        } ?: 0
        val speciesPerPlace = entries
            .filter { !it.location?.placeName.isNullOrBlank() }
            .groupBy { it.location!!.placeName!! }
            .values.maxOfOrNull { list -> list.map { speciesKey(it) }.toSet().size } ?: 0
        val rediscovered = bySpecies.values.count { list ->
            val first = list.minOf { it.capturedAt }
            val last = list.maxOf { it.capturedAt }
            last - first >= REDISCOVER_DAYS * DAY_MILLIS
        }
        val days = entries.map { Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() }.toSet()
        val streak = longestStreak(days)
        val families = entries.mapNotNull { it.family?.takeIf { f -> f.isNotBlank() }?.lowercase() }.toSet().size
        val weekend = entries.count {
            Instant.ofEpochMilli(it.capturedAt).atZone(zone).dayOfWeek.let { d -> d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY }
        }
        val withMemo = entries.count { it.memo.isNotBlank() }

        val base = listOf(
            Badge("first_record", "🌱", "첫 발견", "첫 기록을 남겨요", entries.size, 1),
            Badge("records_10", "📒", "꾸준한 기록가", "기록 10개", entries.size, 10),
            Badge("records_50", "📚", "식물 일기장", "기록 50개", entries.size, 50),
            Badge("records_100", "🏛️", "살아있는 도감", "기록 100개", entries.size, 100),
            Badge("species_10", "🔍", "식물 탐정", "서로 다른 식물 10종", species, 10),
            Badge("species_30", "🧭", "식물 탐험가", "서로 다른 식물 30종", species, 30),
            Badge("species_100", "🌳", "식물 박사", "서로 다른 식물 100종", species, 100),
            Badge("four_seasons", "🍂", "사계절 관찰자", "봄·여름·가을·겨울에 모두 기록", seasons, 4),
            Badge("early_bird", "🌅", "아침 산책", "오전 7시 전에 기록", hours.count { it < 7 }, 1),
            Badge("night_owl", "🌙", "밤의 식물", "밤 9시 이후에 기록", hours.count { it >= 21 }, 1),
            Badge("places_5", "🗺️", "여행하는 식물학자", "서로 다른 지역 5곳에서 기록", places, 5),
            Badge("liked_10", "❤️", "인기 기록", "받은 좋아요 10개", likes, 10),
            Badge("sharer", "🤝", "나눔", "공개 기록 10개", publicCount, 10),
            Badge("species_four_seasons", "🌳", "한 그루의 사계", "같은 식물을 봄·여름·가을·겨울에 모두 기록", speciesAllSeasons, 4),
            Badge("place_10_species", "🏞️", "동네 식물 지도", "한 장소에서 서로 다른 식물 10종", speciesPerPlace, 10),
            Badge("rediscover", "🔁", "다시 만난 친구", "같은 식물을 1년 뒤에 다시 기록", rediscovered, 1),
            Badge("streak_7", "🔥", "일주일 연속", "7일 연속으로 기록", streak, 7),
            Badge("families_10", "🧬", "가계도 수집가", "서로 다른 과(科) 10개", families, 10),
            Badge("weekend_10", "⛺", "주말 산책러", "주말에 기록 10개", weekend, 10),
            Badge("memo_10", "✍️", "관찰 일기", "메모와 함께 기록 10개", withMemo, 10),
        )
        val catalogBadges = progress.catalogs.map { c ->
            Badge(
                id = "catalog_${c.catalog.id}",
                emoji = c.catalog.emoji,
                title = "${c.catalog.title} 완성",
                description = "${c.catalog.title} 도감의 식물을 모두 찾아요",
                progress = c.collected,
                goal = c.total.coerceAtLeast(1),
            )
        }
        return base + catalogBadges
    }

    /** 같은 종을 하나로 세는 키 (속 + 종소명) */
    private fun speciesKey(entry: CollectionEntry) =
        ScientificName.binomialKey(entry.scientificName) ?: entry.scientificName.lowercase()

    /** 하루도 빠짐없이 이어진 날짜의 최대 길이 */
    fun longestStreak(days: Set<LocalDate>): Int {
        var best = 0
        for (day in days) {
            if (day.minusDays(1) in days) continue // 연속 구간의 첫날만 셉니다
            var length = 1
            while (day.plusDays(length.toLong()) in days) length++
            best = maxOf(best, length)
        }
        return best
    }

    /** 이 날짜 이상 지나 다시 기록하면 '다시 만난 친구' (약 11개월, 해마다 조금 이른 개화도 인정) */
    const val REDISCOVER_DAYS = 330L
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** 3~5월 봄(0), 6~8월 여름(1), 9~11월 가을(2), 12~2월 겨울(3) */
    fun seasonOf(month: Int): Int = when (month) {
        3, 4, 5 -> 0
        6, 7, 8 -> 1
        9, 10, 11 -> 2
        else -> 3
    }
}
