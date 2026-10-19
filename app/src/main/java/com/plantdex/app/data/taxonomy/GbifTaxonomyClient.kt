package com.plantdex.app.data.taxonomy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 분류 단계. 위(계)에서 아래(종)로 갈수록 더 가까운 친척입니다. */
enum class TaxonRank { Kingdom, Phylum, Class, Order, Family, Genus, Species }

/** 분류 단계 하나 (예: 과 = Rosaceae) */
data class Taxon(val rank: TaxonRank, val scientificName: String, val gbifKey: String? = null)

/** 학명으로 계·문·강·목·과·속·종을 찾습니다. */
interface TaxonomyClient {
    /** 찾지 못하거나 네트워크 오류면 null */
    suspend fun classify(scientificName: String, gbifId: String?): List<Taxon>?
}

/**
 * GBIF 분류 체계(Backbone)에서 찾습니다. API 키가 필요 없습니다.
 * - GBIF 번호가 있으면 https://api.gbif.org/v1/species/{key}
 * - 없으면 https://api.gbif.org/v1/species/match?name=학명
 * 옛 학명(이명)이어도 GBIF 가 현재 학명의 분류를 돌려줍니다.
 */
class GbifTaxonomyClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.gbif.org/".toHttpUrl(),
) : TaxonomyClient {

    private val cache = ConcurrentHashMap<String, List<Taxon>>()

    override suspend fun classify(scientificName: String, gbifId: String?): List<Taxon>? {
        val id = gbifId?.takeIf { it.isNotBlank() }
        val key = id ?: scientificName
        cache[key]?.let { return it }
        val taxa = withContext(Dispatchers.IO) {
            try {
                val url = if (id != null) {
                    baseUrl.newBuilder().addPathSegments("v1/species").addPathSegment(id).build()
                } else {
                    baseUrl.newBuilder()
                        .addPathSegments("v1/species/match")
                        .addQueryParameter("name", scientificName)
                        .addQueryParameter("kingdom", "Plantae")
                        .build()
                }
                get(url)?.let { json.decodeFromString<GbifClassification>(it) }?.toTaxa()
            } catch (e: IOException) {
                null
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        if (!taxa.isNullOrEmpty()) cache[key] = taxa
        return taxa?.takeIf { it.isNotEmpty() }
    }

    private fun get(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string()
        }
    }

    private companion object {
        const val USER_AGENT = "PlantDex/0.1 (Android; https://github.com/star6moon/app_test)"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** GBIF 응답 중 분류 부분 (species/{key} 와 species/match 가 같은 이름을 씁니다) */
@Serializable
internal data class GbifClassification(
    val matchType: String? = null,
    val kingdom: String? = null,
    val phylum: String? = null,
    @SerialName("class") val clazz: String? = null,
    val order: String? = null,
    val family: String? = null,
    val genus: String? = null,
    val species: String? = null,
    val kingdomKey: Long? = null,
    val phylumKey: Long? = null,
    val classKey: Long? = null,
    val orderKey: Long? = null,
    val familyKey: Long? = null,
    val genusKey: Long? = null,
    val speciesKey: Long? = null,
) {
    fun toTaxa(): List<Taxon> {
        if (matchType == "NONE") return emptyList()
        return listOf(
            Triple(TaxonRank.Kingdom, kingdom, kingdomKey),
            Triple(TaxonRank.Phylum, phylum, phylumKey),
            Triple(TaxonRank.Class, clazz, classKey),
            Triple(TaxonRank.Order, order, orderKey),
            Triple(TaxonRank.Family, family, familyKey),
            Triple(TaxonRank.Genus, genus, genusKey),
            Triple(TaxonRank.Species, species, speciesKey),
        ).mapNotNull { (rank, name, key) ->
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { Taxon(rank, it, key?.toString()) }
        }
    }
}
