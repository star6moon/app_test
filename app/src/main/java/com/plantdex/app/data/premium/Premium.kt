package com.plantdex.app.data.premium

/** 나중에 유료로 바꿀 수 있는 기능들. 지금은 모두 열려 있습니다. */
enum class PremiumFeature {
    /** 식물·기록 검색 */
    Search,
    /** 검색 결과 필터 (도감·과·기간·공개 여부·위치) */
    Filters,
    /** 지도의 '1년 전 (1개월)' 기간 */
    PastYearPeriod,
}

/**
 * 유료 기능 잠금 한 곳. 결제를 붙일 때 [isUnlocked] 만 구독 상태를 보도록 바꾸면
 * 화면들은 그대로 잠기거나 열립니다. (화면은 [PremiumLockedBox] 로 잠긴 모습을 보여줍니다)
 */
object Premium {
    fun isUnlocked(@Suppress("UNUSED_PARAMETER") feature: PremiumFeature): Boolean = true
}
