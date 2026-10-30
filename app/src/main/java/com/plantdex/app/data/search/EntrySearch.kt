package com.plantdex.app.data.search

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.filter.PeriodFilter
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.names.PlantDisplayName
import java.time.LocalDate
import java.time.ZoneId

/** 공개 여부 필터 */
enum class VisibilityFilter { All, PublicOnly, PrivateOnly }

/** 검색 결과를 좁히는 조건. 비어 있으면 모두 통과합니다. */
data class SearchFilters(
    val catalogId: String? = null,
    /** 과(科) 학명, 소문자 비교 */
    val family: String? = null,
    val period: PeriodFilter = PeriodFilter.All,
    val visibility: VisibilityFilter = VisibilityFilter.All,
    val withLocationOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = catalogId == null && family == null && period == PeriodFilter.All &&
            visibility == VisibilityFilter.All && !withLocationOnly

    val activeCount: Int
        get() = listOf(catalogId != null, family != null, period != PeriodFilter.All, visibility != VisibilityFilter.All, withLocationOnly).count { it }
}

/** 내 기록 검색 (순수 Kotlin). 이름·학명·영어 이름·메모·장소를 띄어쓰기와 대소문자 무시하고 찾습니다. */
object EntrySearch {

    /** [query] 의 단어가 모두 어딘가에 들어 있으면 일치 (예: "민들레 서울" → 이름과 장소). */
    fun matches(entry: CollectionEntry, name: PlantDisplayName, query: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = listOfNotNull(
            name.korean,
            name.english,
            entry.scientificName,
            entry.commonName,
            entry.memo,
            entry.location?.placeName,
            entry.family,
            entry.genus,
        ).joinToString(" ") { normalize(it) }
        return words.all { it in haystack }
    }

    fun passes(
        entry: CollectionEntry,
        filters: SearchFilters,
        catalog: PlantCatalog?,
        today: LocalDate,
        zone: ZoneId,
    ): Boolean {
        if (filters.catalogId != null) {
            val species = catalog?.match(entry.scientificName) ?: return false
            if (catalog.catalog(filters.catalogId)?.speciesIds?.contains(species.id) != true) return false
        }
        if (filters.family != null && entry.family?.trim()?.lowercase() != filters.family.lowercase()) return false
        if (!filters.period.matches(entry.capturedAt, today, zone)) return false
        when (filters.visibility) {
            VisibilityFilter.PublicOnly -> if (!entry.isPublic) return false
            VisibilityFilter.PrivateOnly -> if (entry.isPublic) return false
            VisibilityFilter.All -> Unit
        }
        if (filters.withLocationOnly && entry.location == null) return false
        return true
    }

    fun search(
        entries: List<CollectionEntry>,
        query: String,
        filters: SearchFilters,
        catalog: PlantCatalog?,
        nameOf: (CollectionEntry) -> PlantDisplayName,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CollectionEntry> =
        entries.filter { passes(it, filters, catalog, today, zone) && matches(it, nameOf(it), query) }

    /** 내 기록에 나온 과(科) 목록, 많이 기록한 순 (필터 칩용) */
    fun families(entries: List<CollectionEntry>): List<String> =
        entries.mapNotNull { it.family?.trim()?.takeIf { f -> f.isNotEmpty() } }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** 소문자 + 공백 정리 (한글은 그대로) */
    fun normalize(text: String): String = text.lowercase().trim().replace(Regex("\\s+"), " ")
}
