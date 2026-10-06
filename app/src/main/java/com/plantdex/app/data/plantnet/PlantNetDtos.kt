package com.plantdex.app.data.plantnet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pl@ntNet `/v2/identify/{project}` 응답 중 앱에서 쓰는 필드만 정의합니다.
 * https://my.plantnet.org/doc/openapi
 */
@Serializable
internal data class IdentifyResponse(
    val bestMatch: String? = null,
    val results: List<ResultDto> = emptyList(),
    val remainingIdentificationRequests: Int? = null,
)

@Serializable
internal data class ResultDto(
    val score: Double,
    val species: SpeciesDto,
    val gbif: ExternalIdDto? = null,
)

@Serializable
internal data class SpeciesDto(
    val scientificNameWithoutAuthor: String,
    val scientificNameAuthorship: String? = null,
    val scientificName: String? = null,
    val genus: TaxonDto? = null,
    val family: TaxonDto? = null,
    val commonNames: List<String> = emptyList(),
)

@Serializable
internal data class TaxonDto(
    val scientificNameWithoutAuthor: String? = null,
    val scientificName: String? = null,
)

@Serializable
internal data class ExternalIdDto(
    val id: String? = null,
)

@Serializable
internal data class ErrorResponse(
    val statusCode: Int? = null,
    val error: String? = null,
    @SerialName("message") val message: String? = null,
)
