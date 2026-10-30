package com.plantdex.app.data.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 다른 언어로 부르는 이름 (예: 日本語 セイヨウタンポポ) */
data class ForeignName(val languageLabel: String, val name: String)

/** 식물 정보 화면의 '알고 있나요?' 에 쓰는 사실들. 모르면 각 항목이 null / 빈 목록. */
data class SpeciesFacts(
    /** 전 세계 관찰 기록 수 (GBIF) */
    val worldOccurrences: Long?,
    /** 한국 관찰 기록 수 (GBIF) */
    val koreaOccurrences: Long?,
    /** 가장 많이 관찰되는 달 (1~12), 많은 순 최대 3개 */
    val topMonths: List<Int>,
    /** 다른 언어 이름 */
    val foreignNames: List<ForeignName>,
) {
    val isEmpty: Boolean get() = worldOccurrences == null && koreaOccurrences == null && topMonths.isEmpty() && foreignNames.isEmpty()
}

/**
 * GBIF 에서 식물에 얽힌 숫자와 이름을 가져옵니다 (API 키 불필요).
 * - https://api.gbif.org/v1/species/match?name=학명 → 분류군 번호
 * - /v1/occurrence/count?taxonKey=번호(&country=KR) → 관찰 기록 수
 * - /v1/occurrence/search?taxonKey=번호&facet=month&limit=0 → 달별 관찰 수
 * - /v1/species/번호/vernacularNames → 여러 언어 이름
 * 결과는 앱이 실행되는 동안 메모리에 캐시합니다.
 */
class GbifFactsClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.gbif.org/".toHttpUrl(),
) {
    private val cache = ConcurrentHashMap<String, SpeciesFacts>()

    /** 네트워크 오류면 null. */
    suspend fun facts(scientificName: String, gbifId: String? = null): SpeciesFacts? {
        val cacheKey = gbifId?.takeIf { it.isNotBlank() } ?: scientificName
        cache[cacheKey]?.let { return it }
        val facts = withContext(Dispatchers.IO) {
            val key = gbifId?.takeIf { it.isNotBlank() } ?: attempt { matchKey(scientificName) } ?: return@withContext null
            coroutineScope {
                val world = async { attempt { count(key, country = null) } }
                val korea = async { attempt { count(key, country = "KR") } }
                val months = async { attempt { topMonths(key) }.orEmpty() }
                val names = async { attempt { foreignNames(key) }.orEmpty() }
                SpeciesFacts(world.await(), korea.await(), months.await(), names.await())
            }
        } ?: return null
        if (!facts.isEmpty) cache[cacheKey] = facts
        return facts
    }

    private inline fun <T> attempt(block: () -> T?): T? = try {
        block()
    } catch (e: IOException) {
        null
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun matchKey(scientificName: String): String? {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/species/match")
            .addQueryParameter("name", scientificName)
            .addQueryParameter("kingdom", "Plantae")
            .build()
        val match = json.decodeFromString<Match>(get(url) ?: return null)
        return match.usageKey?.takeIf { match.matchType != "NONE" }?.toString()
    }

    private fun count(key: String, country: String?): Long? {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/occurrence/count")
            .addQueryParameter("taxonKey", key)
            .apply { if (country != null) addQueryParameter("country", country) }
            .build()
        return get(url)?.trim()?.toLongOrNull()
    }

    private fun topMonths(key: String): List<Int> {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/occurrence/search")
            .addQueryParameter("taxonKey", key)
            .addQueryParameter("limit", "0")
            .addQueryParameter("facet", "month")
            .addQueryParameter("facetLimit", "12")
            .build()
        val facets = json.decodeFromString<FacetSearch>(get(url) ?: return emptyList()).facets
        val months = facets.firstOrNull { it.field.equals("MONTH", ignoreCase = true) }?.counts.orEmpty()
        return months
            .mapNotNull { c -> c.name.toIntOrNull()?.takeIf { it in 1..12 }?.let { it to c.count } }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }

    private fun foreignNames(key: String): List<ForeignName> {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/species")
            .addPathSegment(key)
            .addPathSegment("vernacularNames")
            .addQueryParameter("limit", "300")
            .build()
        val names = json.decodeFromString<Page<Vernacular>>(get(url) ?: return emptyList()).results
        return LANGUAGES.mapNotNull { (code, label) ->
            val candidates = names.filter { it.language.equals(code, ignoreCase = true) && it.vernacularName.isNotBlank() }
            val pick = candidates.firstOrNull { it.preferred == true } ?: candidates.firstOrNull()
            pick?.let { ForeignName(label, it.vernacularName.trim()) }
        }
    }

    private fun get(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string()
        }
    }

    @Serializable
    private data class Match(val usageKey: Long? = null, val matchType: String? = null)

    @Serializable
    private data class FacetSearch(val facets: List<Facet> = emptyList())

    @Serializable
    private data class Facet(val field: String = "", val counts: List<FacetCount> = emptyList())

    @Serializable
    private data class FacetCount(val name: String = "", val count: Long = 0)

    @Serializable
    private data class Page<T>(val results: List<T> = emptyList())

    @Serializable
    private data class Vernacular(val vernacularName: String = "", val language: String? = null, val preferred: Boolean? = null)

    private companion object {
        const val USER_AGENT = "PlantDex/0.1 (Android; https://github.com/star6moon/app_test)"
        val json = Json { ignoreUnknownKeys = true }
        /** GBIF 언어 코드(ISO 639-3) → 화면 표시 */
        val LANGUAGES = listOf("eng" to "English", "jpn" to "日本語", "zho" to "中文", "deu" to "Deutsch", "fra" to "Français", "spa" to "Español")
    }
}
