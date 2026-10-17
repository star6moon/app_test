package com.plantdex.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.names.NameScript
import com.plantdex.app.data.names.PlantDisplayName
import java.util.Locale

/**
 * 식물의 한국어 이름(없으면 학명의 한글 발음)과 영어 이름.
 * 앱 안의 목록에서 바로 고르고, 한글 발음밖에 없으면 GBIF·Wikidata 에서 한국어 이름을 한 번 더 찾아봅니다.
 */
@Composable
fun plantName(scientificName: String, knownName: String?, englishHint: String? = null, gbifId: String? = null): PlantDisplayName {
    val container = appContainer()
    val initial = container.plantNames.resolve(scientificName, knownName, englishHint)
    val name by produceState(initial, scientificName, knownName, englishHint) {
        value = container.plantNames.resolve(scientificName, knownName, englishHint)
        if (!value.isPronunciation) return@produceState
        val found = container.plantNameLocalizer.localName(scientificName, gbifId, Locale.KOREAN)
        if (found != null && NameScript.matches(found, "ko")) value = value.copy(korean = found, isPronunciation = false)
    }
    return name
}

/** 기록의 식물 이름 */
@Composable
fun plantName(entry: CollectionEntry): PlantDisplayName = plantName(
    scientificName = entry.scientificName,
    knownName = entry.commonName?.takeIf { entry.commonNameLanguage == null || entry.commonNameLanguage == "ko" },
    englishHint = entry.commonName?.takeIf { entry.commonNameLanguage == "en" },
    gbifId = entry.gbifId,
)

/** 식별 후보의 식물 이름 (Pl@ntNet 이 준 영어 이름을 영어 줄에 씁니다) */
@Composable
fun plantName(candidate: PlantCandidate): PlantDisplayName = plantName(
    scientificName = candidate.scientificName,
    knownName = candidate.commonNames.firstOrNull { NameScript.matches(it, "ko") },
    englishHint = candidate.commonNames.firstOrNull { !NameScript.matches(it, "ko") },
    gbifId = candidate.gbifId,
)

/** 기록의 식물 이름 (한국어 줄만) */
@Composable
fun localizedName(entry: CollectionEntry?): String = if (entry == null) "" else plantName(entry).korean

/** 한국어 이름 바로 아래에 작게 보여주는 영어 이름. 없으면 아무것도 그리지 않습니다. */
@Composable
fun EnglishName(
    name: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    if (name.isNullOrBlank()) return
    Text(
        name,
        modifier = modifier,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
