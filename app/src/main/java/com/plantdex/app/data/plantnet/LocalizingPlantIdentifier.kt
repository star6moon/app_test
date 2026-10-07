package com.plantdex.app.data.plantnet

import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.names.NameScript
import com.plantdex.app.data.names.PlantNameLocalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.Locale

/**
 * 식별 결과의 대표 이름을 사용자 언어로 맞춥니다.
 *
 * 1. Pl@ntNet 이 준 일반명 중 사용자 언어로 쓰인 이름이 있으면 그 이름을 맨 앞으로
 * 2. 없으면 [PlantNameLocalizer] (GBIF → Wikidata)로 찾아 맨 앞에 추가
 * 3. 그래도 없으면 Pl@ntNet 이름(보통 영어)을 그대로 사용
 */
class LocalizingPlantIdentifier(
    private val delegate: PlantIdentifier,
    private val localizer: PlantNameLocalizer,
    private val locale: () -> Locale,
) : PlantIdentifier {

    override suspend fun identify(image: File): List<PlantCandidate> {
        val candidates = delegate.identify(image)
        val target = locale()
        val language = target.toLanguageTag().substringBefore('-').lowercase()
        return coroutineScope {
            candidates.map { candidate -> async { localize(candidate, target, language) } }.awaitAll()
        }
    }

    private suspend fun localize(candidate: PlantCandidate, locale: Locale, language: String): PlantCandidate {
        val requestedInTarget = candidate.namesLanguage == language
        val native = if (requestedInTarget) {
            candidate.commonNames.firstOrNull { NameScript.matches(it, language) }
        } else {
            null
        }
        if (native != null) {
            return candidate.copy(
                commonNames = listOf(native) + (candidate.commonNames - native),
                namesLanguage = language,
            )
        }

        val found = localizer.localName(candidate.scientificName, candidate.gbifId, locale)
            // 사용자 언어로 요청했는데 다른 문자의 이름이 왔다면 그 이름의 언어는 알 수 없습니다.
            ?: return candidate.copy(namesLanguage = if (requestedInTarget) null else candidate.namesLanguage)
        return candidate.copy(
            commonNames = listOf(found) + (candidate.commonNames - found),
            namesLanguage = language,
        )
    }
}
