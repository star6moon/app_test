package com.plantdex.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import com.plantdex.app.data.model.CollectionEntry

/**
 * 기록의 식물 이름을 보는 사람의 언어로 돌려줍니다.
 * 저장된 이름이 다른 언어면 우선 그 이름을 보여주고, 번역을 찾으면 바꿔 보여줍니다.
 */
@Composable
fun localizedName(entry: CollectionEntry?): String {
    val localizer = appContainer().plantNameLocalizer
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.toLanguageTag().substringBefore('-').lowercase()
    val name by produceState(entry?.displayName.orEmpty(), entry?.id, entry?.commonName, language) {
        value = entry?.displayName.orEmpty()
        if (entry == null || entry.commonNameLanguage == language) return@produceState
        localizer.localName(entry.scientificName, entry.gbifId, locale)?.let { value = it }
    }
    return name
}
