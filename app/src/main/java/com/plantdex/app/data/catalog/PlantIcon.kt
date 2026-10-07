package com.plantdex.app.data.catalog

/**
 * 식물 종마다 정해지는 아이콘: 이모지 + 배경색(색상환 각도).
 * 같은 이모지를 쓰는 종도 배경색으로 구분됩니다.
 */
data class PlantIcon(
    val emoji: String,
    /** 0 ~ 360 */
    val hue: Float,
)

object PlantIcons {

    /** 도감에 없는 식물은 과(family)로 이모지를 고릅니다. */
    private val byFamily: Map<String, String> = mapOf(
        "rosaceae" to "🌸", "asteraceae" to "🌼", "compositae" to "🌼", "poaceae" to "🌾", "gramineae" to "🌾",
        "cyperaceae" to "🌾", "pinaceae" to "🌲", "cupressaceae" to "🌲", "taxaceae" to "🌲", "fagaceae" to "🌰",
        "betulaceae" to "🌳", "salicaceae" to "🌳", "ulmaceae" to "🌳", "moraceae" to "🌳", "oleaceae" to "🌳",
        "liliaceae" to "🌷", "amaryllidaceae" to "🌼", "orchidaceae" to "💜", "iridaceae" to "💜", "violaceae" to "💜",
        "cactaceae" to "🌵", "crassulaceae" to "🌵", "lamiaceae" to "🌿", "labiatae" to "🌿", "apiaceae" to "🌿",
        "araceae" to "🌿", "asparagaceae" to "🌿", "plantaginaceae" to "🌿", "euphorbiaceae" to "🌿",
        "fabaceae" to "🍀", "leguminosae" to "🍀", "oxalidaceae" to "☘️", "sapindaceae" to "🍁", "aceraceae" to "🍁",
        "ginkgoaceae" to "🍂", "arecaceae" to "🌴", "palmae" to "🌴", "malvaceae" to "🌺", "theaceae" to "🌺",
        "convolvulaceae" to "🌺", "begoniaceae" to "🌺", "ericaceae" to "🌸", "caryophyllaceae" to "🌸",
        "polygonaceae" to "🌸", "geraniaceae" to "🌸", "ranunculaceae" to "🌼", "brassicaceae" to "🌱",
        "amaranthaceae" to "🌱", "nymphaeaceae" to "💮", "nelumbonaceae" to "💮", "magnoliaceae" to "💮",
        "hydrangeaceae" to "💐", "campanulaceae" to "🔔", "solanaceae" to "🍅", "cucurbitaceae" to "🎃",
        "rutaceae" to "🍊", "vitaceae" to "🍇", "juglandaceae" to "🌰", "aquifoliaceae" to "🎄", "musaceae" to "🍌",
    )

    private val fallback = listOf("🌸", "🌼", "🌺", "🌷", "🌿", "🌱", "🍀", "💮")

    fun forPlant(scientificName: String, family: String?, catalog: PlantCatalog?): PlantIcon {
        val species = catalog?.match(scientificName)
        val hash = speciesKey(scientificName, catalog).hashCode()
        val emoji = species?.emoji?.takeIf { it.isNotBlank() }
            ?: family?.let { byFamily[it.trim().lowercase()] }
            ?: fallback[Math.floorMod(hash, fallback.size)]
        return PlantIcon(emoji, Math.floorMod(hash * 31 + 7, 360).toFloat())
    }

    /**
     * 같은 종을 하나로 셀 때 쓰는 키. 이명으로 기록돼도 같은 종이 되도록 도감 id 를 우선 사용합니다.
     * 아이콘 색도 이 키로 정하므로 같은 종은 늘 같은 색입니다.
     */
    fun speciesKey(scientificName: String, catalog: PlantCatalog?): String =
        catalog?.match(scientificName)?.id ?: ScientificName.binomialKey(scientificName) ?: scientificName.lowercase()
}
