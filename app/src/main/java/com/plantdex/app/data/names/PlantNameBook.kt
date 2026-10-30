package com.plantdex.app.data.names

import android.content.Context
import android.util.JsonReader
import com.plantdex.app.data.catalog.ScientificName
import com.plantdex.app.data.search.NameHit
import com.plantdex.app.data.search.NameSearch

/**
 * 앱에 들어 있는 식물 이름 목록 (assets/plant_names.json).
 * Pl@ntNet 이 알아보는 종의 한국어 이름(Wikidata)과 영어 이름을 학명으로 찾습니다.
 * 목록은 GitHub Actions 의 Deploy → names 로 새로 만듭니다 (tools/plant_names/build.py).
 */
class PlantNameBook(private val context: Context) {

    /** 키: "속명 종소명" 소문자 → [한국어, 영어] (없으면 빈 문자열) */
    private val names: Map<String, Array<String>> by lazy { load() }

    /** 처음 찾을 때 멈칫하지 않도록 앱이 켜질 때 미리 읽어 둡니다. */
    fun preload() {
        names.size
    }

    fun korean(scientificName: String): String? = entry(scientificName)?.get(0)?.takeIf { it.isNotBlank() }

    fun english(scientificName: String): String? = entry(scientificName)?.get(1)?.takeIf { it.isNotBlank() }

    /** 이름이나 학명에 [query] 가 들어 있는 식물 (검색 화면용). */
    fun search(query: String, limit: Int = 30): List<NameHit> = NameSearch.search(names, query, limit)

    private fun entry(scientificName: String): Array<String>? =
        ScientificName.binomialKey(scientificName)?.let { names[it] }

    private fun load(): Map<String, Array<String>> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { input ->
            val result = HashMap<String, Array<String>>(100_000)
            JsonReader(input).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "names") {
                        reader.skipValue()
                        continue
                    }
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        reader.beginArray()
                        val korean = if (reader.hasNext()) reader.nextString() else ""
                        val english = if (reader.hasNext()) reader.nextString() else ""
                        while (reader.hasNext()) reader.skipValue()
                        reader.endArray()
                        result[key] = arrayOf(korean, english)
                    }
                    reader.endObject()
                }
                reader.endObject()
            }
            result
        }
    }.getOrDefault(emptyMap())

    companion object {
        const val ASSET = "plant_names.json"
    }
}
