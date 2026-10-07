package com.plantdex.app.data.model

/** AI 가 제안한 식물 후보 한 건. */
data class PlantCandidate(
    val scientificName: String,
    val scientificNameWithAuthor: String,
    /** 일반명 목록. 첫 번째가 대표 이름입니다. */
    val commonNames: List<String>,
    val genus: String?,
    val family: String?,
    /** 0.0 ~ 1.0 */
    val score: Double,
    val gbifId: String?,
    /** [commonNames] 첫 번째 이름의 언어 (ISO 639-1, 예: "ko"). 모르면 null. */
    val namesLanguage: String? = null,
) {
    /** 화면에 보여줄 대표 이름. 일반명이 없으면 학명. */
    val displayName: String get() = commonNames.firstOrNull() ?: scientificName
}
