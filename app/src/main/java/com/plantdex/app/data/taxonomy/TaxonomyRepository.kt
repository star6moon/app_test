package com.plantdex.app.data.taxonomy

import com.plantdex.app.data.names.PlantNameLocalizer
import com.plantdex.app.data.catalog.ScientificName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/** 분류 그래프의 한 칸 */
data class TaxonNode(
    val rank: TaxonRank,
    val scientificName: String,
    /** 한국어 이름 (계·문·강은 모를 때 null) */
    val korean: String?,
    /** true 면 한국어 이름이 아니라 학명 발음 */
    val isPronunciation: Boolean = false,
    /** 짧은 설명 (예: 쌍떡잎식물) */
    val hint: String? = null,
)

/**
 * 식물의 분류(계 → 문 → 강 → 목 → 과 → 속)를 찾아 한국어 이름을 붙입니다.
 * 종(맨 아래 칸)은 화면이 식물 이름으로 직접 붙입니다.
 */
class TaxonomyRepository(
    private val client: TaxonomyClient,
    private val localizer: PlantNameLocalizer,
) {
    /**
     * 기록에 저장된 과·속으로 먼저 보여주고, GBIF 분류가 오면 계부터 다시,
     * 앱에 없는 한국어 이름을 찾으면 한 번 더 내보냅니다.
     */
    fun lineage(scientificName: String, gbifId: String?, family: String?, genus: String?): Flow<List<TaxonNode>> = flow {
        val stored = storedTaxa(scientificName, family, genus)
        if (stored.isNotEmpty()) emit(stored.map(::quickNode))

        val taxa = client.classify(scientificName, gbifId)
            ?.filter { it.rank != TaxonRank.Species }
            ?.takeIf { found -> found.any { it.rank == TaxonRank.Family || it.rank == TaxonRank.Genus } }
            ?: return@flow
        val quick = taxa.map(::quickNode)
        emit(quick)

        val refined = coroutineScope {
            taxa.zip(quick).map { (taxon, node) ->
                async { if (needsLookUp(taxon, node)) lookUp(taxon) ?: node else node }
            }.awaitAll()
        }
        if (refined != quick) emit(refined)
    }

    /** GBIF 에 닿지 못해도 그릴 수 있는 칸 (과·속) */
    private fun storedTaxa(scientificName: String, family: String?, genus: String?): List<Taxon> = listOfNotNull(
        family?.trim()?.takeIf { it.isNotEmpty() }?.let { Taxon(TaxonRank.Family, it) },
        (genus?.trim()?.takeIf { it.isNotEmpty() } ?: ScientificName.genusKey(scientificName)?.replaceFirstChar { it.uppercase() })
            ?.let { Taxon(TaxonRank.Genus, it) },
    )

    private fun quickNode(taxon: Taxon): TaxonNode {
        val hint = TaxonKoreanNames.hint(taxon.scientificName)
        TaxonKoreanNames.known(taxon.scientificName)?.let { return TaxonNode(taxon.rank, taxon.scientificName, it, hint = hint) }
        val sound = TaxonKoreanNames.pronunciation(taxon.rank, taxon.scientificName)
        return TaxonNode(taxon.rank, taxon.scientificName, sound, isPronunciation = sound != null, hint = hint)
    }

    private fun needsLookUp(taxon: Taxon, node: TaxonNode) =
        taxon.gbifKey != null && (node.korean == null || node.isPronunciation)

    private suspend fun lookUp(taxon: Taxon): TaxonNode? {
        val found = localizer.localName(taxon.scientificName, taxon.gbifKey, Locale.KOREAN) ?: return null
        return TaxonNode(
            rank = taxon.rank,
            scientificName = taxon.scientificName,
            korean = TaxonKoreanNames.withSuffix(taxon.rank, found),
            hint = TaxonKoreanNames.hint(taxon.scientificName),
        )
    }
}
