package com.plantdex.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlantIconsTest {
    private val catalog = File("src/main/assets/catalogs.json").inputStream().use { PlantCatalog.parse(it) }

    @Test
    fun `every catalog species has an emoji`() {
        for (s in catalog.species) assertTrue(s.id, s.emoji.isNotBlank())
    }

    @Test
    fun `catalog species use their own emoji and synonyms share the icon`() {
        val cherry = PlantIcons.forPlant("Prunus × yedoensis", "Rosaceae", catalog)
        assertEquals("🌸", cherry.emoji)
        assertEquals(cherry, PlantIcons.forPlant("Prunus serrulata Lindl.", null, catalog))
        assertEquals("🎺", PlantIcons.forPlant("Campsis grandiflora", null, catalog).emoji)
    }

    @Test
    fun `other plants fall back to family emoji then a stable default`() {
        assertEquals("🌵", PlantIcons.forPlant("Opuntia ficus-indica", "Cactaceae", catalog).emoji)
        val unknown = PlantIcons.forPlant("Foo bar", null, catalog)
        assertEquals(unknown, PlantIcons.forPlant("Foo bar L.", null, catalog))
        assertTrue(unknown.hue in 0f..360f)
    }

    @Test
    fun `different species get different colors`() {
        assertNotEquals(
            PlantIcons.forPlant("Taraxacum officinale", null, catalog).hue,
            PlantIcons.forPlant("Campsis grandiflora", null, catalog).hue,
        )
    }
}
