package com.plantdex.app.data.model

/** AI 가 제안한 식물 후보 한 건. */
data class PlantCandidate(
    val scientificName: String,
    val scientificNameWithAuthor: String,
    val commonNames: List<String>,
    val genus: String?,
    val family: String?,
    /** 0.0 ~ 1.0 */
    val score: Double,
    val gbifId: String?,
) {
    /** 화면에 보여줄 대표 이름. 일반명이 없으면 학명. */
    val displayName: String get() = commonNames.firstOrNull() ?: scientificName
}
