package com.plantdex.app.data.model

/** 도감에 기록된 식물 한 건 (Firestore `entries/{id}` 문서). */
data class CollectionEntry(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val scientificName: String,
    val commonName: String?,
    /** [commonName] 의 언어 (ISO 639-1). 보는 사람의 언어와 다르면 화면에서 다시 번역합니다. */
    val commonNameLanguage: String?,
    val gbifId: String?,
    val family: String?,
    val genus: String?,
    /** AI 식별 신뢰도 0.0 ~ 1.0 */
    val score: Double,
    val photoUrl: String,
    /** 촬영 시각 (epoch millis) */
    val capturedAt: Long,
    val location: CaptureLocation?,
    val memo: String,
    val isPublic: Boolean,
    /** 좋아요 수 */
    val likeCount: Int = 0,
) {
    val displayName: String get() = commonName?.takeIf { it.isNotBlank() } ?: scientificName
}

/** 촬영 위치. */
data class CaptureLocation(
    val latitude: Double,
    val longitude: Double,
    /** 정확도 반경 (미터) */
    val accuracyMeters: Float?,
    /** 역지오코딩으로 얻은 사람이 읽을 수 있는 지명 (예: "서울특별시 종로구") */
    val placeName: String?,
)

data class UserProfile(
    val uid: String,
    val displayName: String,
)
