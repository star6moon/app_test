package com.plantdex.app.data.rules

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.SpeciesKey
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CollectionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class NearbyRuleTest {
    private val catalog = File("src/main/assets/catalogs.json").inputStream().use { PlantCatalog.parse(it) }

    // 위도 0.0001도 ≈ 11.1m
    private val here = CaptureLocation(37.5665, 126.9780, 5f, null)

    private fun entry(id: String, scientificName: String, lat: Double?, lng: Double? = 126.9780) = CollectionEntry(
        id = id, ownerId = "me", ownerName = "나", scientificName = scientificName,
        commonName = null, commonNameLanguage = null, gbifId = null, family = null, genus = null,
        score = 0.9, photoUrl = "", capturedAt = 0,
        location = lat?.let { CaptureLocation(it, lng!!, 5f, null) }, memo = "", isPublic = true,
    )

    private fun find(name: String, entries: List<CollectionEntry>) = NearbyRule.findNearby(
        speciesKey = SpeciesKey.of(name, catalog),
        location = here,
        myEntries = entries,
        speciesKeyOf = { SpeciesKey.of(it.scientificName, catalog) },
    )

    @Test
    fun `distance is computed in meters`() {
        assertEquals(111.2, NearbyRule.distanceMeters(37.5665, 126.978, 37.5675, 126.978), 0.5)
        assertEquals(0.0, NearbyRule.distanceMeters(37.5, 127.0, 37.5, 127.0), 1e-9)
    }

    @Test
    fun `same species within the radius is blocked and the nearest record is reported`() {
        val result = find(
            "Taraxacum officinale",
            listOf(
                entry("far", "Taraxacum officinale", 37.5665 + 0.00008), // 약 8.9m
                entry("near", "Taraxacum officinale F.H.Wigg.", 37.5665 + 0.00003), // 약 3.3m
            ),
        )
        assertEquals("near", result?.entry?.id)
        assertEquals(3.3, result!!.distanceMeters, 0.1)
    }

    @Test
    fun `synonyms count as the same species`() {
        val result = find("Prunus serrulata", listOf(entry("cherry", "Prunus × yedoensis", 37.5665 + 0.00005)))
        assertEquals("cherry", result?.entry?.id)
    }

    @Test
    fun `other species, far records and records without location do not block`() {
        assertNull(find("Taraxacum officinale", listOf(entry("clover", "Trifolium repens", 37.5665))))
        assertNull(find("Taraxacum officinale", listOf(entry("far", "Taraxacum officinale", 37.5665 + 0.0001)))) // 약 11.1m
        assertNull(find("Taraxacum officinale", listOf(entry("nolocation", "Taraxacum officinale", null))))
    }
}
