package com.plantdex.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.plantdex.app.R

/**
 * 제목용 명조체 (고운바탕, OFL). 앱 크기를 줄이려고 자주 쓰는 한글 2,350자와 라틴 문자만 남겼고,
 * 없는 글자는 기기 기본 글꼴로 보입니다. 원본과 라이선스: licenses/GowunBatang-OFL.txt
 */
val SerifFamily = FontFamily(
    Font(R.font.gowun_batang_regular, FontWeight.Normal),
    Font(R.font.gowun_batang_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.serif(weight: FontWeight = FontWeight.Bold) =
    copy(fontFamily = SerifFamily, fontWeight = weight, letterSpacing = 0.sp)

/** 제목은 명조체, 본문과 라벨은 읽기 쉬운 기기 기본 글꼴. */
internal val PlantDexTypography = Typography(
    displayLarge = Base.displayLarge.serif(),
    displayMedium = Base.displayMedium.serif(),
    displaySmall = Base.displaySmall.serif(),
    headlineLarge = Base.headlineLarge.serif(),
    headlineMedium = Base.headlineMedium.serif(),
    headlineSmall = Base.headlineSmall.serif(),
    titleLarge = Base.titleLarge.serif(),
    titleMedium = Base.titleMedium.serif(),
    titleSmall = Base.titleSmall.serif(),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)
