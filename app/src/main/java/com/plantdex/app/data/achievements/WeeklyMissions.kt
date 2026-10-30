package com.plantdex.app.data.achievements

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.ScientificName
import com.plantdex.app.data.model.CollectionEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 주간 미션 하나 */
data class Mission(
    val id: String,
    val emoji: String,
    val title: String,
    val progress: Int,
    val goal: Int,
) {
    val done: Boolean get() = progress >= goal
    val shownProgress: Int get() = progress.coerceAtMost(goal)
}

/**
 * 이번 주(월~일) 미션. 같은 주에는 모든 사용자가 같은 미션을 받습니다 (주 시작일로 정해짐).
 * 계절 도감의 식물 찾기가 들어 있어 계절마다 미션이 달라집니다.
 */
object WeeklyMissions {
    private val SEASON_CATALOGS = listOf("spring", "summer", "autumn", "winter")
    private val PLACE_CATALOGS = listOf("city", "seaside", "mountain", "wetland")

    fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun habitMission(seed: Long, thisWeek: List<CollectionEntry>, zone: ZoneId): Mission {
        fun hour(e: CollectionEntry) = Instant.ofEpochMilli(e.capturedAt).atZone(zone).hour
        fun weekend(e: CollectionEntry) = Instant.ofEpochMilli(e.capturedAt).atZone(zone).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }
        return when (Math.floorMod(seed / 7, 4L).toInt()) {
            0 -> Mission("habit_memo", "✍️", "메모와 함께 기록 2개 남기기", thisWeek.count { it.memo.isNotBlank() }, 2)
            1 -> Mission("habit_morning", "🌅", "오전 9시 전에 기록 1개 남기기", thisWeek.count { hour(it) < 9 }, 1)
            2 -> Mission("habit_weekend", "⛺", "주말에 기록 2개 남기기", thisWeek.count { weekend(it) }, 2)
            else -> Mission("habit_public", "🤝", "공개 기록 2개 남기기", thisWeek.count { it.isPublic }, 2)
        }
    }

    private fun friendMission(
        seed: Long,
        entries: List<CollectionEntry>,
        friendsEntries: List<CollectionEntry>,
        thisWeek: List<CollectionEntry>,
        weekFrom: Long,
    ): Mission? {
        fun key(e: CollectionEntry) = ScientificName.binomialKey(e.scientificName) ?: e.scientificName.lowercase()
        // 이번 주 전에 내가 기록한 적 없는 종만 (이번 주에 따라 찾은 것은 달성으로 세야 하므로)
        val mineBefore = entries.filter { it.capturedAt < weekFrom }.map(::key).toSet()
        val candidates = friendsEntries
            .filter { it.capturedAt >= weekFrom - FRIEND_LOOKBACK_MILLIS && key(it) !in mineBefore }
            .sortedByDescending { it.capturedAt }
            .distinctBy { key(it) }
        if (candidates.isEmpty()) return null
        val target = candidates[Math.floorMod(seed * 13 + 5, candidates.size.toLong()).toInt()]
        val name = target.commonName?.takeIf { it.isNotBlank() } ?: target.scientificName
        val found = thisWeek.any { key(it) == key(target) }
        return Mission("friend_${key(target).replace(' ', '_')}", "👣", "${target.ownerName}님이 찾은 $name 따라 찾기", if (found) 1 else 0, 1)
    }

    /** 친구 기록을 얼마나 거슬러 볼지 (2주) */
    private const val FRIEND_LOOKBACK_MILLIS = 14 * 24 * 60 * 60 * 1000L

    fun forWeek(
        today: LocalDate,
        catalog: PlantCatalog,
        entries: List<CollectionEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
        /** 팔로우하는 사람들의 최근 공개 기록 (없으면 친구 미션을 내지 않습니다) */
        friendsEntries: List<CollectionEntry> = emptyList(),
    ): List<Mission> {
        val start = weekStart(today)
        val seed = start.toEpochDay()
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val thisWeek = entries.filter { it.capturedAt in from until until }

        val missions = mutableListOf<Mission>()

        // 1. 기록 개수
        val recordGoal = 3 + Math.floorMod(seed, 3L).toInt()
        missions += Mission("records", "📸", "이번 주 기록 ${recordGoal}개 남기기", thisWeek.size, recordGoal)

        // 2. 장소 도감에서 2종
        val places = PLACE_CATALOGS.mapNotNull { catalog.catalog(it) }
        if (places.isNotEmpty()) {
            val target = places[Math.floorMod(seed, places.size.toLong()).toInt()]
            val found = thisWeek.mapNotNull { catalog.match(it.scientificName)?.id }.filter { it in target.speciesIds }.toSet().size
            missions += Mission("catalog_${target.id}", target.emoji, "${target.title} 식물 2종 기록하기", found, 2)
        }

        // 3. 이번 계절 식물 하나 찾기
        val season = catalog.catalog(SEASON_CATALOGS[Badges.seasonOf(today.monthValue)])
        val seasonal = season?.speciesIds?.mapNotNull { catalog.species(it) }.orEmpty()
        if (seasonal.isNotEmpty()) {
            val target = seasonal[Math.floorMod(seed * 7 + 3, seasonal.size.toLong()).toInt()]
            val found = thisWeek.any { catalog.match(it.scientificName)?.id == target.id }
            missions += Mission("species_${target.id}", season!!.emoji, "${target.name} 찾아 기록하기", if (found) 1 else 0, 1)
        }

        // 4. 처음 보는 식물 (이번 주에 처음 기록한 종)
        val firstSeen = entries
            .groupBy { ScientificName.binomialKey(it.scientificName) ?: it.scientificName.lowercase() }
            .values
            .count { list -> list.minOf { it.capturedAt } in from until until }
        missions += Mission("new_species", "✨", "처음 보는 식물 1종 발견하기", firstSeen, 1)

        // 5. 관찰 습관 (주마다 하나씩 돌아가며)
        missions += habitMission(seed, thisWeek, zone)

        // 6. 친구가 찾은 식물 따라 찾기 (내가 아직 기록하지 않은 종 중 친구가 최근에 기록한 것)
        friendMission(seed, entries, friendsEntries, thisWeek, from)?.let { missions += it }

        return missions
    }
}
