package com.plantdex.app.data.achievements

/** 레벨 하나: 번호, 칭호, 이 레벨이 되는 데 필요한 서로 다른 식물 수 */
data class Level(val number: Int, val title: String, val emoji: String, val requiredSpecies: Int)

/** 지금 레벨과 다음 레벨까지 남은 진행 */
data class LevelProgress(val level: Level, val next: Level?, val species: Int) {
    /** 다음 레벨까지의 진행률 0~1 (마지막 레벨이면 1) */
    val fraction: Float
        get() = next?.let { ((species - level.requiredSpecies).toFloat() / (it.requiredSpecies - level.requiredSpecies)).coerceIn(0f, 1f) } ?: 1f

    /** 다음 레벨까지 더 찾아야 하는 식물 수 (마지막 레벨이면 0) */
    val remaining: Int get() = next?.let { (it.requiredSpecies - species).coerceAtLeast(0) } ?: 0
}

/**
 * 수집한 식물(서로 다른 종) 수에 따른 레벨. 앱에서 계산하므로 서버 저장이 없습니다.
 * 처음에는 몇 종만 찾아도 오르고, 갈수록 더 많이 찾아야 올라요.
 */
object Levels {
    val ALL: List<Level> = listOf(
        Level(1, "새싹", "🌱", 0),
        Level(2, "떡잎", "🌿", 3),
        Level(3, "풀꽃 친구", "🌼", 7),
        Level(4, "들꽃 탐험가", "🌾", 12),
        Level(5, "숲길 안내자", "🌳", 20),
        Level(6, "계절 관찰자", "🍂", 30),
        Level(7, "식물 수집가", "📚", 45),
        Level(8, "식물 박사", "🔬", 65),
        Level(9, "숲의 현자", "🦉", 90),
        Level(10, "식물 마스터", "🏆", 120),
        Level(11, "전설의 탐험가", "🌟", 160),
        Level(12, "살아있는 도감", "📖", 220),
    )

    fun of(speciesCount: Int): LevelProgress {
        val count = speciesCount.coerceAtLeast(0)
        val index = ALL.indexOfLast { count >= it.requiredSpecies }.coerceAtLeast(0)
        return LevelProgress(ALL[index], ALL.getOrNull(index + 1), count)
    }
}
