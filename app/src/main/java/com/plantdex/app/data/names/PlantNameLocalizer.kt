package com.plantdex.app.data.names

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** 학명을 사용자 언어의 일반명(예: Taraxacum officinale → 서양민들레)으로 바꿉니다. */
interface PlantNameLocalizer {
    /** 찾지 못하면 null. 네트워크 오류도 null 로 처리합니다. */
    suspend fun localName(scientificName: String, gbifId: String?, locale: Locale): String?
}

/**
 * GBIF 일반명 → Wikidata 라벨 순서로 찾습니다. 두 서비스 모두 API 키가 필요 없습니다.
 * - GBIF: https://api.gbif.org/v1/species/{key}/vernacularNames
 * - Wikidata: GBIF taxon ID(P846)로 항목을 찾아 해당 언어 라벨을 사용
 *
 * 결과(찾지 못한 경우 포함)는 앱이 실행되는 동안 메모리에 캐시합니다.
 */
class GbifWikidataNameLocalizer(
    private val client: OkHttpClient,
    private val gbifBaseUrl: HttpUrl = "https://api.gbif.org/".toHttpUrl(),
    private val wikidataBaseUrl: HttpUrl = "https://www.wikidata.org/".toHttpUrl(),
) : PlantNameLocalizer {

    private val cache = ConcurrentHashMap<String, CachedName>()

    override suspend fun localName(scientificName: String, gbifId: String?, locale: Locale): String? {
        val language = locale.toLanguageTag().substringBefore('-').lowercase()
        val key = "$language|$scientificName"
        cache[key]?.let { return it.name }

        var failed = false
        fun <T> attempt(block: () -> T?): T? = try {
            block()
        } catch (e: IOException) {
            failed = true
            null
        } catch (e: SerializationException) {
            failed = true
            null
        } catch (e: IllegalArgumentException) {
            failed = true
            null
        }

        val name = withContext(Dispatchers.IO) {
            val taxonKey = gbifId?.takeIf { it.isNotBlank() }
                ?: attempt { matchGbifKey(scientificName) }
                ?: return@withContext null
            attempt { gbifVernacularName(taxonKey, locale, language, scientificName) }
                ?: attempt { wikidataLabel(taxonKey, language, scientificName) }
        }
        // 네트워크 오류로 못 찾은 경우는 캐시하지 않아 다음에 다시 시도합니다.
        if (name != null || !failed) cache[key] = CachedName(name)
        return name
    }

    private fun matchGbifKey(scientificName: String): String? {
        val url = gbifBaseUrl.newBuilder()
            .addPathSegments("v1/species/match")
            .addQueryParameter("name", scientificName)
            .addQueryParameter("rank", "SPECIES")
            .build()
        val match = json.decodeFromString<GbifMatch>(get(url) ?: return null)
        return match.usageKey?.takeIf { match.matchType != "NONE" }?.toString()
    }

    private fun gbifVernacularName(taxonKey: String, locale: Locale, language: String, scientificName: String): String? {
        val url = gbifBaseUrl.newBuilder()
            .addPathSegments("v1/species")
            .addPathSegment(taxonKey)
            .addPathSegment("vernacularNames")
            .addQueryParameter("limit", "1000")
            .build()
        val iso3 = runCatching { locale.isO3Language }.getOrDefault("")
        val names = json.decodeFromString<GbifPage<GbifVernacularName>>(get(url) ?: return null).results
            .filter { it.language.equals(iso3, ignoreCase = true) || it.language.equals(language, ignoreCase = true) }
            .filter { isUsable(it.vernacularName, language, scientificName) }
        return (names.firstOrNull { it.preferred == true } ?: names.firstOrNull())?.vernacularName?.trim()
    }

    private fun wikidataLabel(taxonKey: String, language: String, scientificName: String): String? {
        val searchUrl = wikidataBaseUrl.newBuilder()
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", "haswbstatement:P846=$taxonKey")
            .addQueryParameter("srlimit", "1")
            .addQueryParameter("format", "json")
            .build()
        val itemId = json.decodeFromString<WikidataSearch>(get(searchUrl) ?: return null)
            .query?.search?.firstOrNull()?.title ?: return null

        val entityUrl = wikidataBaseUrl.newBuilder()
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "wbgetentities")
            .addQueryParameter("ids", itemId)
            .addQueryParameter("props", "labels")
            .addQueryParameter("languages", language)
            .addQueryParameter("format", "json")
            .build()
        val label = json.decodeFromString<WikidataEntities>(get(entityUrl) ?: return null)
            .entities[itemId]?.labels?.get(language)
            ?.takeIf { it.language == language }
            ?.value
        return label?.trim()?.takeIf { isUsable(it, language, scientificName) }
    }

    /** 학명을 그대로 라벨로 쓰는 경우가 많아 걸러냅니다. */
    private fun isUsable(name: String, language: String, scientificName: String): Boolean =
        name.isNotBlank() &&
            !name.trim().equals(scientificName, ignoreCase = true) &&
            NameScript.matches(name, language)

    /** 응답 본문. 404 면 null, 그 밖의 실패는 예외. */
    private fun get(url: HttpUrl): String? {
        val request = Request.Builder()
            .url(url)
            // Wikimedia API 정책상 앱을 식별할 수 있는 User-Agent 가 필요합니다.
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string()
        }
    }

    private class CachedName(val name: String?)

    private companion object {
        const val USER_AGENT = "PlantDex/0.1 (Android; https://github.com/star6moon/app_test)"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class GbifMatch(val usageKey: Long? = null, val matchType: String? = null)

@Serializable
private data class GbifPage<T>(val results: List<T> = emptyList())

@Serializable
private data class GbifVernacularName(
    val vernacularName: String = "",
    val language: String? = null,
    val preferred: Boolean? = null,
)

@Serializable
private data class WikidataSearch(val query: Query? = null) {
    @Serializable
    data class Query(val search: List<Hit> = emptyList())

    @Serializable
    data class Hit(val title: String)
}

@Serializable
private data class WikidataEntities(val entities: Map<String, Entity> = emptyMap()) {
    @Serializable
    data class Entity(val labels: Map<String, Label> = emptyMap())

    @Serializable
    data class Label(val language: String, val value: String)
}
