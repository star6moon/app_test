package com.plantdex.app.data.names

import java.lang.Character.UnicodeScript

/**
 * 이름이 해당 언어의 문자로 쓰였는지 대략 판단합니다.
 *
 * Pl@ntNet 은 요청한 언어의 일반명이 없으면 영어 이름을 대신 줄 때가 있어서,
 * 한국어·일본어처럼 라틴 문자를 쓰지 않는 언어는 실제 문자를 확인합니다.
 * 라틴 문자 언어(영어, 프랑스어 등)는 구별할 수 없으므로 그대로 믿습니다.
 */
object NameScript {

    private val scriptsByLanguage: Map<String, Set<UnicodeScript>> = mapOf(
        "ko" to setOf(UnicodeScript.HANGUL),
        "ja" to setOf(UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HAN),
        "zh" to setOf(UnicodeScript.HAN),
        "ru" to setOf(UnicodeScript.CYRILLIC),
        "uk" to setOf(UnicodeScript.CYRILLIC),
        "be" to setOf(UnicodeScript.CYRILLIC),
        "bg" to setOf(UnicodeScript.CYRILLIC),
        "sr" to setOf(UnicodeScript.CYRILLIC, UnicodeScript.LATIN),
        "mk" to setOf(UnicodeScript.CYRILLIC),
        "el" to setOf(UnicodeScript.GREEK),
        "he" to setOf(UnicodeScript.HEBREW),
        "ar" to setOf(UnicodeScript.ARABIC),
        "fa" to setOf(UnicodeScript.ARABIC),
        "hi" to setOf(UnicodeScript.DEVANAGARI),
        "th" to setOf(UnicodeScript.THAI),
    )

    fun matches(name: String, language: String): Boolean {
        val scripts = scriptsByLanguage[language] ?: return true
        return name.codePoints().anyMatch { UnicodeScript.of(it) in scripts }
    }
}
