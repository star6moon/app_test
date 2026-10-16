package com.plantdex.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

private fun TextStyle.emphasized() = copy(fontWeight = FontWeight.SemiBold)

/** 기기 기본 글꼴. 제목만 조금 굵게 해서 카드·구역 제목이 눈에 띄게 합니다. */
internal val PlantDexTypography = Base.copy(
    displayLarge = Base.displayLarge.emphasized(),
    displayMedium = Base.displayMedium.emphasized(),
    displaySmall = Base.displaySmall.emphasized(),
    headlineLarge = Base.headlineLarge.emphasized(),
    headlineMedium = Base.headlineMedium.emphasized(),
    headlineSmall = Base.headlineSmall.emphasized(),
    titleLarge = Base.titleLarge.emphasized(),
    titleMedium = Base.titleMedium.emphasized(),
)
