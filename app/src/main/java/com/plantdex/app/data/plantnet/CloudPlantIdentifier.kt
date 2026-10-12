package com.plantdex.app.data.plantnet

import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.plantdex.app.data.model.PlantCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File

/**
 * 서버 함수(identifyPlant)를 거쳐 Pl@ntNet 으로 식별합니다.
 * API 키는 서버에만 있고, 서버가 사용자별 하루 한도와 App Check 를 확인합니다.
 */
class CloudPlantIdentifier(
    private val functions: FirebaseFunctions,
    private val language: () -> String,
) : PlantIdentifier {

    override suspend fun identify(images: List<File>): List<PlantCandidate> {
        require(images.isNotEmpty()) { "사진이 필요합니다" }
        val encoded = withContext(Dispatchers.IO) {
            images.take(3).map { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        }
        val result = try {
            functions.getHttpsCallable("identifyPlant")
                .call(mapOf("images" to encoded, "lang" to language()))
                .await()
                .getData() as? Map<*, *>
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            throw PlantIdentificationException(e.toMessage(), e)
        } catch (e: Exception) {
            throw PlantIdentificationException("네트워크 연결을 확인해 주세요.", e)
        } ?: throw PlantIdentificationException("식별 결과를 받지 못했습니다.")

        val status = (result["status"] as? Number)?.toInt() ?: 0
        val body = result["body"] as? String ?: ""
        val lang = result["lang"] as? String ?: "en"
        return when (status) {
            200 -> try {
                PlantNetIdentifier.parseCandidates(body, lang)
            } catch (e: SerializationException) {
                throw PlantIdentificationException("식별 결과를 해석하지 못했습니다.", e)
            }
            404 -> emptyList()
            429 -> throw PlantIdentificationException("오늘 앱 전체의 식별 가능 횟수를 모두 사용했어요. 내일 다시 시도해 주세요.")
            else -> throw PlantIdentificationException("식물 식별에 실패했습니다 ($status)")
        }
    }

    private fun FirebaseFunctionsException.toMessage(): String = when (code) {
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "오늘 식별할 수 있는 횟수를 모두 사용했어요. 내일 다시 시도해 주세요."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "다시 로그인해 주세요."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "앱 인증(App Check)에 실패했어요. 최신 버전의 앱인지 확인해 주세요."
        FirebaseFunctionsException.Code.NOT_FOUND -> "서버 기능이 아직 배포되지 않았어요. (firebase deploy --only functions)"
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "네트워크 연결을 확인해 주세요."
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "사진을 보내지 못했어요. 다시 찍어 주세요."
        else -> "식물 식별에 실패했습니다: ${message.orEmpty()}"
    }
}
