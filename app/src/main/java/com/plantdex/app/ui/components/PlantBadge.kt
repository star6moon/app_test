package com.plantdex.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plantdex.app.data.art.PlantArt
import com.plantdex.app.data.art.PlantArts

/** 동그란 식물 그림 아이콘 (지도 마커, 카드, 목록에 사용) */
@Composable
fun PlantBadge(art: PlantArt, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    PlantArtImage(
        art = art,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.95f), CircleShape),
    )
}

/** 학명·과로 식물 그림 사양을 고릅니다 (도감 종이면 도감 그림, 아니면 과에 맞춰 생성). */
@Composable
fun rememberPlantArt(scientificName: String, family: String?): PlantArt {
    val catalogRepository = appContainer().catalogRepository
    return remember(scientificName, family) {
        PlantArts.forPlant(scientificName, family, runCatching { catalogRepository.catalog }.getOrNull())
    }
}
