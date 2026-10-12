package com.plantdex.app.data.achievements

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.ScientificName
import com.plantdex.app.data.model.CollectionEntry
import java.time.Instant
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

    /** 3~5월 봄(0), 6~8월 여름(1), 9~11월 가을(2), 12~2월 겨울(3) */
    fun seasonOf(month: Int): Int = when (month) {
        3, 4, 5 -> 0
        6, 7, 8 -> 1
        9, 10, 11 -> 2
        else -> 3
    }
}
