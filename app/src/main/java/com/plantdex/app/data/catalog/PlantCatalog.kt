package com.plantdex.app.data.catalog

import com.plantdex.app.data.model.CollectionEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

/** 도감에 올라 있는 식물 한 종. */
@Serializable
data class CatalogSpecies(
    val id: String,
    val name: String,
    val scientificName: String,
    /** AI 가 다른 학명(이명, 비슷한 종)으로 답해도 이 종으로 인정할 학명 */
    val synonyms: List<String> = emptyList(),
    /** true 면 같은 속(genus)의 다른 종도 이 종으로 인정 (원예종처럼 학명이 제각각인 경우) */
    val acceptGenus: Boolean = false,
    /** 아직 발견하지 못했을 때 보여줄 힌트 */
    val hint: String = "",
    /** 지도 마커와 도감에 쓰는 이 종의 아이콘 */
    val emoji: String = "",
)

/** 주제별 도감 (도시의 꽃, 바닷가 식물, 봄꽃 …). 같은 종이 여러 도감에 들어갈 수 있습니다. */
@Serializable
data class Catalog(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val speciesIds: List<String>,
)

@Serializable
private data class CatalogFile(
    val version: Int,
    val species: List<CatalogSpecies>,
    val catalogs: List<Catalog>,
)

/**
 * 도감 목록과 학명 매칭.
 *
 * 기록의 학명은 "속 + 종소명"(예: prunus yedoensis)으로 정규화해 비교합니다.
 * 저자명, 변종(var.)·품종(f.), 교잡 기호(×)는 무시합니다.
 */
class PlantCatalog(
    val species: List<CatalogSpecies>,
    val catalogs: List<Catalog>,
) {
    private val speciesById: Map<String, CatalogSpecies> = species.associateBy { it.id }

    private val byBinomial: Map<String, CatalogSpecies> = buildMap {
        for (s in species) {
            for (name in listOf(s.scientificName) + s.synonyms) {
                ScientificName.binomialKey(name)?.let { putIfAbsent(it, s) }
            }
        }
    }

    private val byGenus: Map<String, CatalogSpecies> = buildMap {
        for (s in species.filter { it.acceptGenus }) {
            ScientificName.genusKey(s.scientificName)?.let { putIfAbsent(it, s) }
        }
    }

    fun species(id: String): CatalogSpecies? = speciesById[id]

    fun catalog(id: String): Catalog? = catalogs.firstOrNull { it.id == id }

    /** 학명에 해당하는 도감 종. 정확히 일치하는 종이 우선이고, 없으면 속 단위로 인정하는 종을 찾습니다. */
    fun match(scientificName: String): CatalogSpecies? =
        ScientificName.binomialKey(scientificName)?.let { byBinomial[it] }
            ?: ScientificName.genusKey(scientificName)?.let { byGenus[it] }

    /** 이 종이 들어 있는 도감들 */
    fun catalogsOf(speciesId: String): List<Catalog> = catalogs.filter { speciesId in it.speciesIds }

    /** 내 기록으로 도감별 수집 현황을 계산합니다. */
    fun progress(entries: List<CollectionEntry>): CollectionProgress {
        val entriesBySpecies: Map<String, List<CollectionEntry>> = entries
            .mapNotNull { entry -> match(entry.scientificName)?.let { it.id to entry } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedByDescending { it.capturedAt } }

        val catalogProgress = catalogs.map { catalog ->
            CatalogProgress(
                catalog = catalog,
                items = catalog.speciesIds.mapNotNull { id ->
                    speciesById[id]?.let { SpeciesProgress(it, entriesBySpecies[id].orEmpty()) }
                },
            )
        }
        val catalogSpeciesIds = catalogs.flatMap { it.speciesIds }.toSet()
        val discovered = entries.map { ScientificName.binomialKey(it.scientificName) ?: it.scientificName }.toSet()
        val outside = entries
            .filter { match(it.scientificName) == null }
            .map { ScientificName.binomialKey(it.scientificName) ?: it.scientificName }
            .toSet()

        return CollectionProgress(
            catalogs = catalogProgress,
            totalSpecies = catalogSpeciesIds.size,
            collectedSpecies = catalogSpeciesIds.count { it in entriesBySpecies },
            recordCount = entries.size,
            discoveredSpecies = discovered.size,
            outsideCatalogSpecies = outside.size,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(input: InputStream): PlantCatalog {
            val file = json.decodeFromString<CatalogFile>(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
            return PlantCatalog(file.species, file.catalogs)
        }
    }
}

/** 도감 한 칸: 종과 그 종으로 인정된 내 기록들 (최신순). */
data class SpeciesProgress(
    val species: CatalogSpecies,
    val entries: List<CollectionEntry>,
) {
    val collected: Boolean get() = entries.isNotEmpty()
    val latest: CollectionEntry? get() = entries.firstOrNull()
}

data class CatalogProgress(
    val catalog: Catalog,
    val items: List<SpeciesProgress>,
) {
    val total: Int get() = items.size
    val collected: Int get() = items.count { it.collected }
    val isComplete: Boolean get() = total > 0 && collected == total
}

data class CollectionProgress(
    val catalogs: List<CatalogProgress>,
    /** 모든 도감에 올라 있는 종 수 (중복 제외) */
    val totalSpecies: Int,
    /** 그중 내가 등록한 종 수 */
    val collectedSpecies: Int,
    /** 내 기록 수 */
    val recordCount: Int,
    /** 내가 기록한 서로 다른 종 수 (도감 밖 포함) */
    val discoveredSpecies: Int,
    /** 어느 도감에도 없는 종 수 */
    val outsideCatalogSpecies: Int,
)

object ScientificName {
    private val hybridMarks = setOf("×", "x", "X")

    private fun words(name: String): List<String> =
        name.replace("×", " × ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in hybridMarks }

    /** "Prunus × yedoensis Matsum." → "prunus yedoensis". 종소명이 없으면 null. */
    fun binomialKey(name: String): String? {
        val w = words(name)
        if (w.size < 2) return null
        val epithet = w[1].lowercase()
        // 두 번째 단어가 종소명이 아니면(대문자 저자명, "sp." 등) 종 수준 이름이 아닙니다.
        if (!epithet.matches(Regex("[a-z][a-z-]+")) || w[1][0].isUpperCase()) return null
        return "${w[0].lowercase()} $epithet"
    }

    fun genusKey(name: String): String? = words(name).firstOrNull()?.lowercase()
}
