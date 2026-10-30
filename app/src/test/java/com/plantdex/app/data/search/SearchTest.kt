package com.plantdex.app.data.search

import com.plantdex.app.data.filter.PeriodFilter
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.names.PlantDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SearchTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 14)

    private fun entry(id: String, scientificName: String, family: String? = null, place: String? = null, memo: String = "", isPublic: Boolean = true, location: Boolean = true) =
        CollectionEntry(
            id = id, ownerId = "me", ownerName = "나", scientificName = scientificName, commonName = null,
            commonNameLanguage = null, gbifId = null, family = family, genus = null, score = 0.9, photoUrl = "",
            capturedAt = LocalDate.of(2026, 10, 10).atStartOfDay(zone).toInstant().toEpochMilli(),
            location = if (location) CaptureLocation(37.0, 127.0, null, place) else null, memo = memo, isPublic = isPublic,
        )

    private val names = mapOf(
        "Taraxacum officinale" to PlantDisplayName("서양민들레", "Dandelion", false),
        "Rosa rugosa" to PlantDisplayName("해당화", "Rugosa Rose", false),
    )

    @Test
    fun `matches every word across name, scientific name, memo and place`() {
        val e = entry("1", "Taraxacum officinale", place = "서울숲", memo = "노란 꽃")
        val n = names.getValue(e.scientificName)
        assertTrue(EntrySearch.matches(e, n, "민들레"))
        assertTrue(EntrySearch.matches(e, n, "dande"))
        assertTrue(EntrySearch.matches(e, n, "TARAXACUM"))
        assertTrue(EntrySearch.matches(e, n, "민들레 서울숲"))
        assertTrue(EntrySearch.matches(e, n, "노란"))
        assertFalse(EntrySearch.matches(e, n, "장미"))
        assertTrue(EntrySearch.matches(e, n, "   "))
    }

    @Test
    fun `filters narrow by family, visibility, location and period`() {
        val entries = listOf(
            entry("a", "Taraxacum officinale", family = "Asteraceae"),
            entry("b", "Rosa rugosa", family = "Rosaceae", isPublic = false),
            entry("c", "Rosa rugosa", family = "Rosaceae", location = false),
        )
        fun run(filters: SearchFilters, query: String = "") =
            EntrySearch.search(entries, query, filters, null, { names.getValue(it.scientificName) }, today, zone).map { it.id }

        assertEquals(listOf("b", "c"), run(SearchFilters(family = "rosaceae")))
        assertEquals(listOf("a", "c"), run(SearchFilters(visibility = VisibilityFilter.PublicOnly)))
        assertEquals(listOf("b"), run(SearchFilters(visibility = VisibilityFilter.PrivateOnly)))
        assertEquals(listOf("a", "b"), run(SearchFilters(withLocationOnly = true)))
        assertEquals(listOf("b", "c"), run(SearchFilters(), "해당화"))
        assertEquals(emptyList<String>(), run(SearchFilters(period = PeriodFilter.Today)))
        assertEquals(listOf("Rosaceae", "Asteraceae"), EntrySearch.families(entries))
        assertEquals(2, SearchFilters(family = "x", withLocationOnly = true).activeCount)
        assertTrue(SearchFilters().isEmpty)
    }

    @Test
    fun `name search ranks prefix matches first and needs two characters`() {
        val book = mapOf(
            "taraxacum officinale" to arrayOf("서양민들레", "Dandelion"),
            "taraxacum platycarpum" to arrayOf("민들레", ""),
            "rosa rugosa" to arrayOf("해당화", "Rugosa rose"),
            "chrysanthemum morifolium" to arrayOf("국화", "Florist's daisy"),
        )
        assertEquals(listOf("Taraxacum platycarpum", "Taraxacum officinale"), NameSearch.search(book, "민들레").map { it.scientificName })
        assertEquals(listOf("Rosa rugosa"), NameSearch.search(book, "ROSA").map { it.scientificName })
        assertEquals(listOf("Rosa rugosa"), NameSearch.search(book, "rose").map { it.scientificName })
        assertTrue(NameSearch.search(book, "국").isEmpty())
        assertEquals(1, NameSearch.search(book, "taraxacum", limit = 1).size)
    }
}
