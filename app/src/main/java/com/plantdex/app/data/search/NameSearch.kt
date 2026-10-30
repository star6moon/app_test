package com.plantdex.app.data.search

/** 이름 목록 검색 결과 한 줄 */
data class NameHit(
    /** 학명 (속명 첫 글자 대문자) */
    val scientificName: String,
    val korean: String?,
    val english: String?,
)

/** 식물 이름 목록(학명 → [한국어, 영어])에서 글자를 찾습니다 (순수 Kotlin, [PlantNameBook] 이 씁니다). */
object NameSearch {

    /**
     * [query] 가 한국어·영어 이름이나 학명에 들어 있는 항목. 앞부분이 일치하는 것을 먼저, 그다음 짧은 이름순.
     * 두 글자 미만이면 빈 목록 (너무 많이 나오므로).
     */
    fun search(names: Map<String, Array<String>>, query: String, limit: Int = 30): List<NameHit> {
        val q = EntrySearch.normalize(query)
        if (q.length < 2) return emptyList()
        val hits = ArrayList<Pair<Int, NameHit>>()
        for ((key, pair) in names) {
            val korean = pair.getOrNull(0)?.takeIf { it.isNotBlank() }
            val english = pair.getOrNull(1)?.takeIf { it.isNotBlank() }
            val rank = rank(q, korean, english, key) ?: continue
            hits += rank to NameHit(capitalize(key), korean, english)
            if (hits.size > limit * 20) break // 너무 많으면 여기서 멈춥니다
        }
        return hits.sortedWith(compareBy({ it.first }, { it.second.korean?.length ?: 99 }, { it.second.scientificName }))
            .take(limit)
            .map { it.second }
    }

    /** 0 = 앞부분 일치, 1 = 단어 시작 일치, 2 = 포함. 없으면 null */
    private fun rank(q: String, korean: String?, english: String?, key: String): Int? {
        var best: Int? = null
        for (field in listOfNotNull(korean?.lowercase(), english?.lowercase(), key)) {
            val r = when {
                field.startsWith(q) -> 0
                field.contains(" $q") -> 1
                field.contains(q) -> 2
                else -> continue
            }
            if (best == null || r < best) best = r
        }
        return best
    }

    private fun capitalize(key: String): String = key.replaceFirstChar { it.uppercase() }
}
