package com.plantdex.app.data.model

import java.io.File

/** 촬영 직후 업로드 전 상태의 사진과 메타데이터. */
data class CapturedPhoto(
    /** 업로드용으로 리사이즈·회전 보정된 JPEG */
    val file: File,
    val capturedAt: Long,
    val location: CaptureLocation?,
)
