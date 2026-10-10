package com.plantdex.app.data.catalog

import com.plantdex.app.data.model.CollectionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlantCatalogTest {

    /** 실제 앱에 들어가는 도감 파일 (Gradle 단위 테스트는 모듈 폴더에서 실행됩니다). */
    private val catalog: PlantCatalog =
        File("src/main/assets/catalogs.json").inputStream().use { PlantCatalog.parse(it) }

    private fun entry(id: String, scientificName: String, capturedAt: Long = 0) = CollectionEntry(
        id = id,
        ownerId = "me",
        ownerName = "나",
        scientificName = scientificName,
        commonName = null,
        commonNameLanguage = null,
        gbifId = null,
        family = null,
        genus = null,
        score = 0.9,
        photoUrl = "",
        capturedAt = capturedAt,
        location = null,
        memo = "",
        isPublic = true,
    )

    // ── 도감 데이터 검증 ──────────────────────────────

    @Test
    fun `every catalog references existing species without duplicates`() {
        assertTrue(catalog.catalogs.isNotEmpty())
        for (c in catalog.catalogs) {
            assertEquals("${c.id} has duplicates", c.speciesIds.size, c.speciesIds.toSet().size)
            for (id in c.speciesIds) assertNotNull("${c.id} -> $id", catalog.species(id))
        }
    }

    @Test
    fun `species ids are unique and every species is in a catalog`() {
        assertEquals(catalog.species.size, catalog.species.map { it.id }.toSet().size)
        val used = catalog.catalogs.flatMap { it.speciesIds }.toSet()
        for (s in catalog.species) assertTrue("${s.id} is unused", s.id in used)
    }

    @Test
    fun `no two species claim the same scientific name`() {
        val owners = mutableMapOf<String, String>()
        for (s in catalog.species) {
            for (name in listOf(s.scientificName) + s.synonyms) {
                val key = ScientificName.binomialKey(name) ?: continue
                val previous = owners.put(key, s.id)
                assertTrue("$key is claimed by $previous and ${s.id}", previous == null || previous == s.id)
            }
        }
    }

    @Test
    fun `no two genus-wide species share a genus`() {
        val genera = catalog.species.filter { it.acceptGenus }.map { ScientificName.genusKey(it.scientificName) }
        assertEquals(genera.size, genera.toSet().size)
    }

    @Test
    fun `every species name is a valid binomial`() {
        for (s in catalog.species) {
            assertNotNull(s.scientificName, ScientificName.binomialKey(s.scientificName))
            assertTrue(s.id, s.name.isNotBlank() && s.hint.isNotBlank())
        }
    }

    // ── 학명 정규화 ──────────────────────────────────

    @Test
    fun `binomial key ignores authors, hybrids and infraspecific ranks`() {
        assertEquals("prunus yedoensis", ScientificName.binomialKey("Prunus × yedoensis Matsum."))
        assertEquals("petunia atkinsiana", ScientificName.binomialKey("Petunia x atkinsiana"))
        assertEquals("rhododendron yedoense", ScientificName.binomialKey("Rhododendron yedoense var. poukhanense"))
        assertEquals("capsella bursa-pastoris", ScientificName.binomialKey("Capsella bursa-pastoris (L.) Medik."))
        assertNull(ScientificName.binomialKey("Taraxacum"))
        assertNull(ScientificName.binomialKey("Rosa L."))
    }

    // ── 매칭 ─────────────────────────────────────────

    @Test
    fun `matches scientific names, synonyms and genus-wide species`() {
        assertEquals("cherry", catalog.match("Prunus × yedoensis")?.id)
        assertEquals("cherry", catalog.match("Prunus serrulata Lindl.")?.id)
        assertEquals("plum", catalog.match("Prunus mume")?.id)
        assertEquals("abelia", catalog.match("Abelia × grandiflora (Rovelli ex André) Rehder")?.id)
        assertEquals("abelia", catalog.match("Linnaea × grandiflora")?.id)
        assertEquals("korean_azalea", catalog.match("Rhododendron yedoense f. poukhanense")?.id)
        // 원예종: 같은 속이면 인정
        assertEquals("forsythia", catalog.match("Forsythia ovata")?.id)
        // 정확히 일치하는 종이 속 단위 매칭보다 우선
        assertEquals("pansy", catalog.match("Viola × wittrockiana")?.id)
        assertEquals("violet", catalog.match("Viola grypoceras")?.id)
        assertEquals("rugosa", catalog.match("Rosa rugosa")?.id)
        assertEquals("rose", catalog.match("Rosa banksiae")?.id)
        // 같은 속이어도 속 단위 인정이 아니면 매칭하지 않음
        assertNull(catalog.match("Prunus persica"))
        assertNull(catalog.match("Quercus acutissima"))
    }

    // ── 수집 현황 ────────────────────────────────────

    @Test
    fun `progress counts each species once and shares it across catalogs`() {
        val progress = catalog.progress(
            listOf(
                entry("1", "Taraxacum officinale", capturedAt = 1),
                entry("2", "Taraxacum officinale F.H.Wigg.", capturedAt = 5),
                entry("3", "Camellia japonica"),
                entry("4", "Quercus acutissima"), // 도감 밖
            ),
        )

        assertEquals(4, progress.recordCount)
        assertEquals(3, progress.discoveredSpecies)
        assertEquals(1, progress.outsideCatalogSpecies)
        assertEquals(2, progress.collectedSpecies)
        assertEquals(catalog.species.size, progress.totalSpecies)

        val city = progress.catalogs.first { it.catalog.id == "city" }
        assertEquals(1, city.collected)
        assertEquals(city.catalog.speciesIds.size, city.total)
        val dandelion = city.items.first { it.species.id == "dandelion_west" }
        assertEquals("2", dandelion.latest?.id) // 최신 기록이 대표 사진

        // 동백나무는 바닷가·봄·겨울 도감 모두에서 발견 처리
        for (id in listOf("seaside", "spring", "winter")) {
            assertTrue(id, progress.catalogs.first { it.catalog.id == id }.items.first { it.species.id == "camellia" }.collected)
        }
        assertFalse(progress.catalogs.first { it.catalog.id == "mountain" }.isComplete)
    }
}
