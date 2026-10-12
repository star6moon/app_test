package com.plantdex.app.data.info

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** 위키백과 문서 요약 */
data class WikiSummary(
    val title: String,
    val extract: String,
    /** 브라우저로 열 문서 주소 */
    val pageUrl: String?,
    val imageUrl: String?,
    /** 문서 언어 (ko, en …) */
    val language: String,
)

/**
 * 위키백과 REST API 로 식물 설명을 가져옵니다 (API 키 불필요).
 * 사용자 언어 문서를 이름 → 학명 순으로 찾고, 없으면 영어 문서를 학명으로 찾습니다.
 * 순수 JVM 코드라 단위 테스트가 가능합니다.
 */
class WikipediaClient(
    private val client: OkHttpClient,
    private val baseHost: (language: String) -> String = { "$it.wikipedia.org" },
    private val scheme: String = "https",
) {
    private val cache = ConcurrentHashMap<String, WikiSummary>()

    suspend fun summary(names: List<String>, scientificName: String, language: String): WikiSummary? {
        val lang = language.lowercase().ifBlank { "en" }
        val attempts = buildList {
            names.filter { it.isNotBlank() && !it.equals(scientificName, ignoreCase = true) }.forEach { add(lang to it) }
            add(lang to scientificName)
            if (lang != "en") add("en" to scientificName)
        }.distinct()
        val cacheKey = "$lang|$scientificName"
        cache[cacheKey]?.let { return it }
        for ((l, title) in attempts) {
            val found = fetch(l, title) ?: continue
            cache[cacheKey] = found
            return found
        }
        return null
    }

    private suspend fun fetch(language: String, title: String): WikiSummary? = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme(scheme)
            .host(baseHost(language).substringBefore(':'))
            .apply { baseHost(language).substringAfter(':', "").toIntOrNull()?.let { port(it) } }
            .addPathSegments("api/rest_v1/page/summary")
            .addPathSegment(title.trim().replace(' ', '_'))
            .build()
        val request = Request.Builder()
            .url(url)
            // 위키미디어 정책: 앱을 알 수 있는 User-Agent 를 보냅니다.
            .header("User-Agent", "PlantDex/1.0 (Android; plant identification app)")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parse(response.body?.string().orEmpty(), language)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 응답을 해석합니다. 동음이의 문서나 내용이 없는 문서는 null. */
        fun parse(body: String, language: String): WikiSummary? {
            val dto = runCatching { json.decodeFromString<SummaryDto>(body) }.getOrNull() ?: return null
            if (dto.type == "disambiguation" || dto.extract.isBlank()) return null
            return WikiSummary(
                title = dto.title,
                extract = dto.extract,
                pageUrl = dto.contentUrls?.mobile?.page ?: dto.contentUrls?.desktop?.page,
                imageUrl = dto.thumbnail?.source,
                language = language,
            )
        }
    }
}

@Serializable
private data class SummaryDto(
    val title: String = "",
    val extract: String = "",
    val type: String = "",
    @SerialName("content_urls") val contentUrls: ContentUrls? = null,
    val thumbnail: Thumbnail? = null,
)

@Serializable
private data class ContentUrls(val mobile: PageUrl? = null, val desktop: PageUrl? = null)

@Serializable
private data class PageUrl(val page: String? = null)

@Serializable
private data class Thumbnail(val source: String? = null)
