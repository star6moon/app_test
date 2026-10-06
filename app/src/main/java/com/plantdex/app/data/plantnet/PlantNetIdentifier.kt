package com.plantdex.app.data.plantnet

import com.plantdex.app.data.model.PlantCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /** 일반명(commonNames) 언어. Pl@ntNet 이 지원하지 않는 언어면 영어로 바꾸세요. */
    private val language: String = "en",
    private val project: String = "all",
    private val maxResults: Int = 5,
) : PlantIdentifier {

    override suspend fun identify(image: File): List<PlantCandidate> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw PlantIdentificationException(
                "Pl@ntNet API 키가 설정되지 않았습니다. local.properties 에 PLANTNET_API_KEY 를 추가하세요.",
            )
        }

        val url = baseUrl.newBuilder()
            .addPathSegments("v2/identify")
            .addPathSegment(project)
            .addQueryParameter("api-key", apiKey)
            .addQueryParameter("lang", language)
            .addQueryParameter("nb-results", maxResults.toString())
            .addQueryParameter("include-related-images", "false")
            .build()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("images", image.name, image.asRequestBody(JPEG))
            .addFormDataPart("organs", "auto")
            .build()

        val request = Request.Builder().url(url).post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parseCandidates(text)
                    // Pl@ntNet 은 어떤 종과도 일치하지 않으면 404 "Species not found" 를 돌려줍니다.
                    response.code == 404 -> emptyList()
                    response.code == 401 || response.code == 403 ->
                        throw PlantIdentificationException("Pl@ntNet API 키가 올바르지 않습니다.")
                    response.code == 429 ->
                        throw PlantIdentificationException("오늘 사용할 수 있는 식별 횟수를 모두 사용했습니다.")
                    else -> throw PlantIdentificationException(
                        "식물 식별에 실패했습니다 (${response.code}): ${errorMessage(text)}",
                    )
                }
            }
        } catch (e: IOException) {
            throw PlantIdentificationException("네트워크 연결을 확인해 주세요.", e)
        }
    }

    private fun errorMessage(body: String): String =
        runCatching { json.decodeFromString<ErrorResponse>(body).message }.getOrNull()
            ?: body.take(200)

    companion object {
        const val DEFAULT_BASE_URL = "https://my-api.plantnet.org/"
        private val JPEG = "image/jpeg".toMediaType()

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseCandidates(body: String): List<PlantCandidate> =
            json.decodeFromString<IdentifyResponse>(body).results
                .map { it.toCandidate() }
                .sortedByDescending { it.score }

        private fun ResultDto.toCandidate() = PlantCandidate(
            scientificName = species.scientificNameWithoutAuthor,
            scientificNameWithAuthor = species.scientificName ?: species.scientificNameWithoutAuthor,
            commonNames = species.commonNames.filter { it.isNotBlank() },
            genus = species.genus?.scientificNameWithoutAuthor,
            family = species.family?.scientificNameWithoutAuthor,
            score = score,
            gbifId = gbif?.id,
        )
    }
}
