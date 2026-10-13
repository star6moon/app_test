package com.plantdex.app.ui.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln

/** 지도 확대 수준 계산 (순수 계산이라 JVM 단위 테스트로 확인합니다). */
object MapZoom {
    /** 확대 0 에서 적도 기준 화면 1dp 가 나타내는 거리 (m). 구글 지도 타일 256dp 기준 */
    private const val METERS_PER_DP_AT_ZOOM_0 = 156_543.03392

    /**
     * 위도 [latitude] 를 가운데에 두고, 화면 폭 [widthDp] 안에 반경 [radiusMeters] 가 다 보이는 확대 수준.
     * 지도 확대 범위(2~21)를 벗어나지 않게 자릅니다.
     */
    fun forRadius(latitude: Double, radiusMeters: Double, widthDp: Int): Float {
        val metersPerDp = 2 * radiusMeters / widthDp.coerceAtLeast(1)
        val scale = METERS_PER_DP_AT_ZOOM_0 * cos(latitude * PI / 180) / metersPerDp
        return (ln(scale) / ln(2.0)).toFloat().coerceIn(2f, 21f)
    }
}
