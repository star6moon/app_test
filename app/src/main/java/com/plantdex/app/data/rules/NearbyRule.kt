package com.plantdex.app.data.rules

import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CollectionEntry
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 같은 종을 이미 등록한 가까운 기록과 그 거리 */
data class NearbyRecord(
    val entry: CollectionEntry,
    val distanceMeters: Double,
)

/**
 * 같은 계정은 같은 종을 기존 기록에서 [MIN_DISTANCE_METERS] 이상 떨어진 곳에서만 다시 등록할 수 있습니다.
 * (한 자리에서 같은 식물을 반복 등록해 도감·지도를 채우는 것을 막습니다.)
 */
object NearbyRule {
    const val MIN_DISTANCE_METERS = 100.0

    /** [location] 에서 반경 안에 있는 같은 종의 내 기록 중 가장 가까운 것. 없으면 null. */
    fun findNearby(
        speciesKey: String,
        location: CaptureLocation,
        myEntries: List<CollectionEntry>,
        speciesKeyOf: (CollectionEntry) -> String,
        minDistanceMeters: Double = MIN_DISTANCE_METERS,
    ): NearbyRecord? = myEntries.asSequence()
        .filter { it.location != null && speciesKeyOf(it) == speciesKey }
        .map { entry ->
            val other = entry.location!!
            NearbyRecord(entry, distanceMeters(location.latitude, location.longitude, other.latitude, other.longitude))
        }
        .filter { it.distanceMeters < minDistanceMeters }
        .minByOrNull { it.distanceMeters }

    /** 두 좌표 사이의 지표면 거리 (하버사인 공식, 미터) */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
