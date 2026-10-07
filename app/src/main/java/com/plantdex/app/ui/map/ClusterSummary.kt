package com.plantdex.app.ui.map

/** 묶음 안에서 같은 종끼리 모은 것. [items] 는 최신순입니다. */
data class SpeciesGroup<T>(
    val key: String,
    val name: String,
    val items: List<T>,
)

/** 묶음(클러스터) 요약: 기록이 많은 종부터 정렬합니다. */
data class ClusterSummary<T>(
    val groups: List<SpeciesGroup<T>>,
) {
    val recordCount: Int get() = groups.sumOf { it.items.size }
    val top: SpeciesGroup<T> get() = groups.first()

    /** "능소화 등 4종" (여러 종) 또는 "능소화 4건" (한 종) */
    val label: String
        get() = if (groups.size == 1) "${top.name} ${recordCount}건" else "${top.name} 등 ${groups.size}종"

    companion object {
        /**
         * 종별로 묶어 기록 수가 많은 순으로 정렬합니다.
         * 수가 같으면 더 최근에 기록된 종, 그다음 이름 순입니다.
         */
        fun <T> of(
            items: Collection<T>,
            speciesKey: (T) -> String,
            name: (T) -> String,
            capturedAt: (T) -> Long,
        ): ClusterSummary<T> {
            require(items.isNotEmpty()) { "빈 묶음은 요약할 수 없습니다" }
            val groups = items.groupBy(speciesKey)
                .map { (key, list) ->
                    val sorted = list.sortedByDescending(capturedAt)
                    SpeciesGroup(key, name(sorted.first()), sorted)
                }
                .sortedWith(
                    compareByDescending<SpeciesGroup<T>> { it.items.size }
                        .thenByDescending { capturedAt(it.items.first()) }
                        .thenBy { it.name },
                )
            return ClusterSummary(groups)
        }
    }
}
