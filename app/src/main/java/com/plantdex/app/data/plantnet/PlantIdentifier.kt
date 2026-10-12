package com.plantdex.app.data.plantnet

import com.plantdex.app.data.model.PlantCandidate
import java.io.File

/** 사진으로 식물을 식별하는 서비스. 다른 AI 로 교체할 수 있도록 인터페이스로 분리합니다. */
interface PlantIdentifier {
    /**
     * 같은 식물을 찍은 사진(꽃, 잎 등 최대 3장)으로 식별합니다. 여러 장일수록 정확해집니다.
     * 점수가 높은 순으로 정렬된 후보 목록을 반환합니다. 식별 결과가 없으면 빈 목록.
     */
    suspend fun identify(images: List<File>): List<PlantCandidate>
}

class PlantIdentificationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
