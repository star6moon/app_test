package com.plantdex.app.data.achievements

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CollectionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AchievementsTest {
    private val catalog: PlantCatalog =
        File("src/main/assets/catalogs.json").inputStream().use { PlantCatalog.parse(it) }
    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, scientificName: String, capturedAt: Long, place: String? = null, likes: Int = 0) =
        CollectionEntry(
            id = id, ownerId = "me", ownerName = "나", scientificName = scientificName, commonName = null,
            commonNameLanguage = null, gbifId = null, family = null, genus = null, score = 0.9, photoUrl = "",
            capturedAt = capturedAt, location = CaptureLocation(37.0, 127.0, null, place), memo = "",
            isPublic = true, likeCount = likes,
        )

    @Test
    fun badgesFromRecords() {
        val entries = listOf(
            entry("1", "Taraxacum officinale", at(2026, 4, 1, 6), "서울"),
            entry("2", "Campsis grandiflora", at(2026, 7, 1, 22), "부산", likes = 6),
            entry("3", "Taraxacum officinale", at(2026, 10, 1), "제주", likes = 4),
            entry("4", "Camellia japonica", at(2026, 1, 5), "여수"),
        )
        val badges = Badges.compute(entries, catalog, zone).associateBy { it.id }
        assertTrue(badges.getValue("first_record").earned)
        assertFalse(badges.getValue("records_10").earned)
        assertEquals(3, badges.getValue("species_10").progress)
        assertTrue(badges.getValue("four_seasons").earned)
        assertTrue(badges.getValue("early_bird").earned)
        assertTrue(badges.getValue("night_owl").earned)
        assertEquals(4, badges.getValue("places_5").progress)
        assertTrue(badges.getValue("liked_10").earned)
        assertTrue(badges.keys.any { it.startsWith("catalog_") })
    }

    @Test
    fun missionsAreStableWithinAWeek() {
        val monday = WeeklyMissions.forWeek(LocalDate.of(2026, 10, 12), catalog, emptyList(), zone)
        val sunday = WeeklyMissions.forWeek(LocalDate.of(2026, 10, 18), catalog, emptyList(), zone)
        assertEquals(monday.map { it.title }, sunday.map { it.title })
        assertEquals(4, monday.size)
        assertTrue(monday.none { it.done })
    }

    @Test
    fun missionProgressCountsOnlyThisWeek() {
        val today = LocalDate.of(2026, 10, 14)
        val entries = listOf(
            entry("old", "Taraxacum officinale", at(2026, 9, 1)),
            entry("a", "Taraxacum officinale", at(2026, 10, 12)),
            entry("b", "Campsis grandiflora", at(2026, 10, 13)),
        )
        val missions = WeeklyMissions.forWeek(today, catalog, entries, zone).associateBy { it.id }
        assertEquals(2, missions.getValue("records").progress)
        // 민들레는 예전에 기록했으므로 능소화만 새로 발견
        assertEquals(1, missions.getValue("new_species").progress)
        assertTrue(missions.getValue("new_species").done)
    }
}
