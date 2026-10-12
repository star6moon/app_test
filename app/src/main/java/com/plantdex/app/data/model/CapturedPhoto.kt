package com.plantdex.app.data.model

import java.io.File

/** 촬영 직후 업로드 전 상태의 사진과 메타데이터. */
data class CapturedPhoto(
    /** 업로드용으로 리사이즈·회전 보정된 JPEG. 도감에 올라가는 대표 사진입니다. */
    val file: File,
    val capturedAt: Long,
    val location: CaptureLocation?,
    /** 식별 정확도를 높이려고 함께 찍은 다른 부위 사진 (업로드하지 않음) */
    val extraFiles: List<File> = emptyList(),
) {
    /** 식별에 보낼 모든 사진 (대표 사진이 먼저) */
    val allFiles: List<File> get() = listOf(file) + extraFiles
}
