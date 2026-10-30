package com.plantdex.app.data.catalog

import com.plantdex.app.data.model.CollectionEntry

/** 식물 정보 화면에서 쓰는 '내 기록 사이의 관계' 계산 (순수 Kotlin). */
object SpeciesRelations {

    /** 내 기록 중 같은 과(科)의 다른 식물을 한 종씩, 최근에 기록한 순으로. */
    fun sameFamily(
        myEntries: List<CollectionEntry>,
        family: String?,
        excludeKey: String,
        catalog: PlantCatalog?,
    ): List<CollectionEntry> {
        val target = family?.trim()?.takeIf { it.isNotEmpty() }?.lowercase() ?: return emptyList()
        return myEntries
            .filter { it.family?.trim()?.lowercase() == target }
            .sortedByDescending { it.capturedAt }
            .distinctBy { SpeciesKey.of(it.scientificName, catalog) }
            .filter { SpeciesKey.of(it.scientificName, catalog) != excludeKey }
    }
}
