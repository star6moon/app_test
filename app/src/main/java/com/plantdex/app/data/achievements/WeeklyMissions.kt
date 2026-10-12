package com.plantdex.app.data.achievements

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.ScientificName
import com.plantdex.app.data.model.CollectionEntry
import java.time.DayOfWeek
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

    fun forWeek(
        today: LocalDate,
        catalog: PlantCatalog,
        entries: List<CollectionEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
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

        return missions
    }
}
