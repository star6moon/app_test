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

    private fun entry(
        id: String,
        scientificName: String,
        capturedAt: Long,
        place: String? = null,
        likes: Int = 0,
        family: String? = null,
        memo: String = "",
        owner: String = "me",
        commonName: String? = null,
    ) = CollectionEntry(
        id = id, ownerId = owner, ownerName = if (owner == "me") "나" else owner, scientificName = scientificName, commonName = commonName,
        commonNameLanguage = null, gbifId = null, family = family, genus = null, score = 0.9, photoUrl = "",
        capturedAt = capturedAt, location = CaptureLocation(37.0, 127.0, null, place), memo = memo,
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
        assertEquals(5, monday.size) // 친구 기록이 없으면 친구 미션은 없습니다
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

    @Test
    fun newBadgesFromHabitsAndRevisits() {
        val entries = listOf(
            // 같은 민들레를 사계절 + 1년 뒤
            entry("1", "Taraxacum officinale", at(2025, 4, 1), "서울숲", family = "Asteraceae"),
            entry("2", "Taraxacum officinale", at(2025, 7, 1), "서울숲", family = "Asteraceae", memo = "씨앗"),
            entry("3", "Taraxacum officinale", at(2025, 10, 1), "서울숲", family = "Asteraceae"),
            entry("4", "Taraxacum officinale", at(2026, 1, 1), "서울숲", family = "Asteraceae"),
            entry("5", "Taraxacum officinale", at(2026, 4, 2), "서울숲", family = "Asteraceae"),
            // 7일 연속 (4월 10일~16일), 과 2개
            entry("6", "Rosa rugosa", at(2026, 4, 10), family = "Rosaceae"),
            entry("7", "Rosa rugosa", at(2026, 4, 11), family = "Rosaceae"),
            entry("8", "Rosa rugosa", at(2026, 4, 12), family = "Rosaceae"),
            entry("9", "Rosa rugosa", at(2026, 4, 13), family = "Rosaceae"),
            entry("10", "Rosa rugosa", at(2026, 4, 14), family = "Rosaceae"),
            entry("11", "Rosa rugosa", at(2026, 4, 15), family = "Rosaceae"),
            entry("12", "Rosa rugosa", at(2026, 4, 16), family = "Rosaceae"),
        )
        val badges = Badges.compute(entries, catalog, zone).associateBy { it.id }
        assertTrue(badges.getValue("species_four_seasons").earned)
        assertTrue(badges.getValue("rediscover").earned)
        assertTrue(badges.getValue("streak_7").earned)
        assertEquals(7, badges.getValue("streak_7").progress)
        assertEquals(2, badges.getValue("families_10").progress)
        assertEquals(1, badges.getValue("memo_10").progress)
        assertEquals(1, badges.getValue("place_10_species").progress)
    }

    @Test
    fun longestStreakSkipsGaps() {
        val days = listOf(1, 2, 3, 5, 6, 7, 8, 20).map { LocalDate.of(2026, 5, it) }.toSet()
        assertEquals(4, Badges.longestStreak(days))
        assertEquals(0, Badges.longestStreak(emptySet()))
    }

    @Test
    fun friendMissionTargetsASpeciesIHaveNotRecorded() {
        val today = LocalDate.of(2026, 10, 14)
        val mine = listOf(entry("old", "Taraxacum officinale", at(2026, 9, 1)))
        val friends = listOf(
            entry("f1", "Taraxacum officinale", at(2026, 10, 10), owner = "민수"),
            entry("f2", "Campsis grandiflora", at(2026, 10, 11), owner = "민수", commonName = "능소화"),
        )
        val missions = WeeklyMissions.forWeek(today, catalog, mine, zone, friendsEntries = friends)
        val friend = missions.single { it.id.startsWith("friend_") }
        assertEquals("friend_campsis_grandiflora", friend.id)
        assertTrue(friend.title.contains("민수") && friend.title.contains("능소화"))
        assertFalse(friend.done)

        val followed = WeeklyMissions.forWeek(today, catalog, mine + entry("new", "Campsis grandiflora", at(2026, 10, 13)), zone, friendsEntries = friends)
        assertTrue(followed.single { it.id.startsWith("friend_") }.done)
    }
}
