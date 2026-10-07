package com.plantdex.app.data.plantnet

import com.plantdex.app.data.model.PlantCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Pl@ntNet Identification API 클라이언트.
 *
 * 순수 JVM 코드(OkHttp + kotlinx.serialization)만 사용하므로 로컬 단위 테스트가 가능합니다.
 */
class PlantNetIdentifier(
    private val apiKey: String,
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    /** 일반명(commonNames)을 받을 언어 (ISO 639-1). 호출할 때마다 읽으므로 기기 언어 변경을 따라갑니다. */
    private val language: () -> String = { FALLBACK_LANGUAGE },
    private val project: String = "all",
    private val maxResults: Int = 5,
) : PlantIdentifier {

    override suspend fun identify(image: File): List<PlantCandidate> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw PlantIdentificationException(
                "Pl@ntNet API 키가 설정되지 않았습니다. local.properties 에 PLANTNET_API_KEY 를 추가하세요.",
            )
        }

        val lang = language().ifBlank { FALLBACK_LANGUAGE }
        try {
            try {
                request(image, lang)
            } catch (e: UnsupportedLanguageException) {
                // Pl@ntNet 이 지원하지 않는 언어입니다. 영어 이름으로 다시 요청하고,
                // 사용자 언어 이름은 LocalizingPlantIdentifier 가 따로 찾아 채웁니다.
                request(image, FALLBACK_LANGUAGE)
            }
        } catch (e: IOException) {
            throw PlantIdentificationException("네트워크 연결을 확인해 주세요.", e)
        } catch (e: SerializationException) {
            throw PlantIdentificationException("식별 결과를 해석하지 못했습니다.", e)
        }
    }

    private fun request(image: File, lang: String): List<PlantCandidate> {
        val url = baseUrl.newBuilder()
            .addPathSegments("v2/identify")
            .addPathSegment(project)
            .addQueryParameter("api-key", apiKey)
            .addQueryParameter("lang", lang)
            .addQueryParameter("nb-results", maxResults.toString())
            .addQueryParameter("include-related-images", "false")
            .build()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("images", image.name, image.asRequestBody(JPEG))
            .addFormDataPart("organs", "auto")
            .build()

        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return when {
                response.isSuccessful -> parseCandidates(text, lang)
                // Pl@ntNet 은 어떤 종과도 일치하지 않으면 404 "Species not found" 를 돌려줍니다.
                response.code == 404 -> emptyList()
                response.code == 401 || response.code == 403 ->
                    throw PlantIdentificationException("Pl@ntNet API 키가 올바르지 않습니다.")
                response.code == 429 ->
                    throw PlantIdentificationException("오늘 사용할 수 있는 식별 횟수를 모두 사용했습니다.")
                // 지원하지 않는 lang 값은 400 검증 오류로 돌아옵니다.
                response.code == 400 && lang != FALLBACK_LANGUAGE &&
                    errorMessage(text).contains("lang", ignoreCase = true) ->
                    throw UnsupportedLanguageException()
                else -> throw PlantIdentificationException(
                    "식물 식별에 실패했습니다 (${response.code}): ${errorMessage(text)}",
                )
            }
        }
    }

    private class UnsupportedLanguageException : Exception()

    private fun errorMessage(body: String): String =
        runCatching { json.decodeFromString<ErrorResponse>(body).message }.getOrNull()
            ?: body.take(200)

    companion object {
        const val DEFAULT_BASE_URL = "https://my-api.plantnet.org/"
        const val FALLBACK_LANGUAGE = "en"
        private val JPEG = "image/jpeg".toMediaType()

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseCandidates(body: String, lang: String): List<PlantCandidate> =
            json.decodeFromString<IdentifyResponse>(body).results
                .map { it.toCandidate(lang) }
                .sortedByDescending { it.score }

        private fun ResultDto.toCandidate(lang: String) = PlantCandidate(
            scientificName = species.scientificNameWithoutAuthor,
            scientificNameWithAuthor = species.scientificName ?: species.scientificNameWithoutAuthor,
            commonNames = species.commonNames.filter { it.isNotBlank() },
            genus = species.genus?.scientificNameWithoutAuthor,
            family = species.family?.scientificNameWithoutAuthor,
            score = score,
            gbifId = gbif?.id,
            namesLanguage = lang,
        )
    }
}
