package com.plantdex.app.util

/** 공유 카드에 적는 글줄 (그림과 공유 글 둘 다 이것을 씁니다). 순수 Kotlin 이라 테스트할 수 있습니다. */
data class ShareCardText(
    val korean: String,
    val english: String?,
    val scientificName: String,
    /** "2026.4.3 · 서울숲" 처럼 날짜와 장소를 한 줄로 */
    val whenWhere: String,
    val memo: String?,
) {
    /** 공유 글 (이미지와 함께 보내는 텍스트) */
    fun caption(photoUrl: String?): String = buildString {
        append("🌿 ").append(korean)
        english?.let { append(" · ").append(it) }
        appendLine()
        appendLine(scientificName)
        appendLine(whenWhere)
        memo?.let { appendLine(it) }
        photoUrl?.let { appendLine(it) }
        append("#PlantDex")
    }

    companion object {
        const val MAX_MEMO = 80

        fun of(
            korean: String,
            english: String?,
            scientificName: String,
            date: String,
            placeName: String?,
            memo: String,
        ): ShareCardText = ShareCardText(
            korean = korean.trim().ifBlank { scientificName },
            english = english?.trim()?.takeIf { it.isNotEmpty() && !it.equals(korean.trim(), ignoreCase = true) },
            scientificName = scientificName.trim(),
            whenWhere = listOfNotNull(date, placeName?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · "),
            memo = memo.trim().takeIf { it.isNotEmpty() }?.let { if (it.length > MAX_MEMO) it.take(MAX_MEMO - 1) + "…" else it },
        )
    }
}
