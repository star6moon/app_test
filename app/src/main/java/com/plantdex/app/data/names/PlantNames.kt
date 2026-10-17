package com.plantdex.app.data.names

import com.plantdex.app.data.catalog.PlantCatalog

/**
 * 화면에 보여줄 식물 이름.
 * [korean] 은 한국어 이름, 없으면 학명의 한글 발음 ([isPronunciation] = true). [english] 는 영어 이름 (없으면 null).
 */
data class PlantDisplayName(val korean: String, val english: String?, val isPronunciation: Boolean)

/**
 * 한국어 이름을 고르는 순서:
 * 1. 기록·식별 결과에 이미 들어 있는 한국어 이름
 * 2. 앱 도감의 이름 (학명이 정확히 같을 때)
 * 3. 앱에 들어 있는 이름 목록 ([PlantNameBook], Wikidata 기준)
 * 4. 학명의 한글 발음 ([LatinHangul]) — 화면에서는 이어서 GBIF·Wikidata 를 한 번 더 찾아봅니다.
 */
class PlantNames(
    private val nameBook: PlantNameBook,
    private val catalog: () -> PlantCatalog?,
) {
    fun resolve(scientificName: String, knownName: String? = null, englishHint: String? = null): PlantDisplayName {
        val korean = knownName?.takeIf { it.isNotBlank() && NameScript.matches(it, "ko") }
            ?: catalog()?.exact(scientificName)?.name
            ?: nameBook.korean(scientificName)
        val english = nameBook.english(scientificName)
            ?: englishHint?.trim()?.takeIf {
                it.isNotEmpty() && !NameScript.matches(it, "ko") && !it.equals(scientificName.trim(), ignoreCase = true)
            }
        return if (korean != null) {
            PlantDisplayName(korean, english, isPronunciation = false)
        } else {
            PlantDisplayName(LatinHangul.transliterate(scientificName).ifBlank { scientificName }, english, isPronunciation = true)
        }
    }
}
