package com.plantdex.app.data.names

import org.junit.Assert.assertEquals
import org.junit.Test

class LatinHangulTest {
    private fun t(name: String) = LatinHangul.transliterate(name)

    @Test
    fun `reads common binomials the way Korean field guides do`() {
        assertEquals("헬리안투스 안누우스", t("Helianthus annuus"))
        assertEquals("타락사쿰 오피키날레", t("Taraxacum officinale"))
        assertEquals("벨리스 페렌니스", t("Bellis perennis"))
        assertEquals("퀘르쿠스 아쿠티시마", t("Quercus acutissima"))
        assertEquals("프루누스 세룰라타", t("Prunus serrulata"))
        assertEquals("크리산테뭄 모리폴리움", t("Chrysanthemum morifolium"))
        assertEquals("히드랑게아 마크로필라", t("Hydrangea macrophylla"))
        assertEquals("유글란스 레기아", t("Juglans regia"))
        assertEquals("캄파눌라 풍크타타", t("Campanula punctata"))
    }

    @Test
    fun `drops authors, hybrid marks and ranks`() {
        assertEquals("헬리안투스 안누우스", t("Helianthus annuus L."))
        assertEquals("프루누스 예도엔시스", t("Prunus × yedoensis Matsum."))
        assertEquals("로사", t("Rosa"))
        assertEquals("타락사쿰", t("Taraxacum sect. Taraxacum"))
        assertEquals("로도덴드론 예도엔세 포우카넨세", t("Rhododendron yedoense var. poukhanense"))
        assertEquals("카프셀라 부르사파스토리스", t("Capsella bursa-pastoris (L.) Medik."))
    }
}
