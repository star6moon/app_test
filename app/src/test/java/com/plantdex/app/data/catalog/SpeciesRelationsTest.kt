package com.plantdex.app.data.catalog

import com.plantdex.app.data.model.CollectionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesRelationsTest {
    private fun entry(id: String, scientificName: String, family: String?, capturedAt: Long) = CollectionEntry(
        id = id, ownerId = "me", ownerName = "나", scientificName = scientificName, commonName = null,
        commonNameLanguage = null, gbifId = null, family = family, genus = null, score = 0.9, photoUrl = "",
        capturedAt = capturedAt, location = null, memo = "", isPublic = true,
    )

    @Test
    fun `same family lists each other species once, newest first, excluding this one`() {
        val entries = listOf(
            entry("1", "Rosa rugosa", "Rosaceae", 10),
            entry("2", "Prunus serrulata", "rosaceae ", 30),
            entry("3", "Prunus serrulata", "Rosaceae", 20),
            entry("4", "Taraxacum officinale", "Asteraceae", 40),
            entry("5", "Spiraea prunifolia", "Rosaceae", 25),
        )
        val result = SpeciesRelations.sameFamily(entries, "Rosaceae", "rosa rugosa", null)
        assertEquals(listOf("2", "5"), result.map { it.id })
        assertTrue(SpeciesRelations.sameFamily(entries, null, "rosa rugosa", null).isEmpty())
    }
}
