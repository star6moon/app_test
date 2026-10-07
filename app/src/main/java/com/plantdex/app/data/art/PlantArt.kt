package com.plantdex.app.data.art

import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.SpeciesKey
import kotlinx.serialization.Serializable

/**
 * 식물의 외형을 설명하는 그림 사양. [PlantArtRenderer] 가 이 값으로 그림을 그립니다.
 *
 * - form: flower(꽃잎이 둥글게 펼쳐진 꽃), daisy(국화형), cluster(작은 꽃 무리), funnel(나팔꽃형), cup(튤립·목련형), bell(종 모양),
 *   iris(붓꽃형), spike(이삭·꽃대), plume(억새·갈대), conifer(침엽수), tree(활엽수), leaf(잎이 특징인 식물),
 *   rosette(땅에 퍼진 잎), berries(열매), sprout(새싹·마디 줄기)
 * - color: 주색(꽃잎, 잎, 열매), accent: 보조색(꽃 중심, 무늬, 줄기 등)
 * - petals: 꽃잎 수, petal: 꽃잎 모양(round, pointed, notched, wavy, oblong, narrow)
 * - variant: 형태별 세부 모양 (예: cluster 의 ball/cone/spray, conifer 의 pine/narrow)
 */
@Serializable
data class PlantArt(
    val form: String,
    val color: String,
    val accent: String? = null,
    val petals: Int = 5,
    val petal: String = "round",
    val variant: String? = null,
    val spots: Boolean = false,
    val layers: Int = 1,
)

object PlantArts {

    /** 도감에 없는 식물은 과(family)로 형태를 정하고, 색은 학명으로 고정해서 고릅니다. */
    private val byFamily: Map<String, (String) -> PlantArt> = buildMap {
        fun put(vararg families: String, make: (String) -> PlantArt) = families.forEach { put(it, make) }
        put("asteraceae", "compositae") { PlantArt("daisy", it, "#F2C14E", petals = 18) }
        put("rosaceae") { PlantArt("flower", it, "#F2C14E", petals = 5, petal = "round") }
        put("poaceae", "gramineae", "cyperaceae", "juncaceae") { PlantArt("plume", "#C9B48A") }
        put("pinaceae", "cupressaceae", "taxaceae") { PlantArt("conifer", "#2F6B3A") }
        put("fagaceae", "betulaceae", "salicaceae", "ulmaceae", "moraceae", "juglandaceae", "lauraceae") {
            PlantArt("tree", "#4E8B3A")
        }
        put("liliaceae", "amaryllidaceae", "hemerocallidaceae") { PlantArt("flower", it, "#F2C14E", petals = 6, petal = "pointed") }
        put("iridaceae") { PlantArt("iris", it, "#F6E7A8") }
        put("orchidaceae") { PlantArt("flower", it, "#F6E7A8", petals = 5, petal = "pointed", spots = true) }
        put("lamiaceae", "labiatae", "plantaginaceae", "verbenaceae") { PlantArt("spike", it) }
        put("fabaceae", "leguminosae") { PlantArt("spike", it, variant = "whorl") }
        put("campanulaceae") { PlantArt("bell", it) }
        put("convolvulaceae", "solanaceae") { PlantArt("funnel", it, "#FFFFFF") }
        put("malvaceae") { PlantArt("flower", it, "#B0174F", petals = 5, petal = "wavy", variant = "stamen") }
        put("ericaceae") { PlantArt("flower", it, "#8B1A4E", petals = 5, spots = true, variant = "stamen") }
        put("ranunculaceae", "papaveraceae") { PlantArt("flower", it, "#F2C14E", petals = 5) }
        put("brassicaceae") { PlantArt("flower", it, "#F2C14E", petals = 4, petal = "oblong") }
        put("apiaceae", "umbelliferae") { PlantArt("rosette", "#5E9E44", "#FFFFFF", variant = "umbel") }
        put("hydrangeaceae", "caprifoliaceae", "viburnaceae") { PlantArt("cluster", it, petals = 4, variant = "ball") }
        put("sapindaceae", "aceraceae") { PlantArt("leaf", "#E0452B", variant = "maple") }
        put("araliaceae") { PlantArt("leaf", "#2E7D32", variant = "hand") }
        put("ginkgoaceae") { PlantArt("leaf", "#F2C230", variant = "fan") }
        put("oxalidaceae") { PlantArt("leaf", "#7CB342", "#F7D046", variant = "clover") }
        put("aquifoliaceae") { PlantArt("leaf", "#2E7D32", "#D32F2F", variant = "holly") }
        put("amaranthaceae", "chenopodiaceae") { PlantArt("sprout", "#6E9E3A", variant = "jointed") }
        put("cactaceae", "crassulaceae", "aizoaceae") { PlantArt("rosette", "#7FA35A") }
        put("typhaceae") { PlantArt("spike", "#7B4A26", variant = "sausage") }
        put("nymphaeaceae", "nelumbonaceae") { PlantArt("flower", it, "#F5C518", petals = 12, petal = "pointed", layers = 2) }
        put("theaceae") { PlantArt("flower", it, "#F7D046", petals = 5, layers = 2, variant = "stamen") }
    }

    private val flowerColors = listOf(
        "#F4A7C0", "#F7C948", "#B39DDB", "#6FA8DC", "#E57373", "#FFFFFF", "#F39C4A", "#CE93D8",
    )

    fun forPlant(scientificName: String, family: String?, catalog: PlantCatalog?): PlantArt {
        val species = catalog?.match(scientificName)
        species?.art?.let { return it }
        val key = SpeciesKey.of(scientificName, catalog)
        val color = flowerColors[Math.floorMod(key.hashCode(), flowerColors.size)]
        return family?.trim()?.lowercase()?.let { byFamily[it] }?.invoke(color)
            ?: PlantArt("flower", color, "#F2C14E", petals = if (Math.floorMod(key.hashCode() / 7, 2) == 0) 5 else 6)
    }
}
