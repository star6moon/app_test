package com.plantdex.app.data.names

/**
 * 학명(라틴어)을 한글 발음으로 적습니다. 한국어 이름이 없는 식물에 씁니다.
 * 예: Helianthus annuus → 헬리안투스 안누우스, Quercus acutissima → 퀘르쿠스 아쿠티시마
 *
 * 국내 식물 학명 읽기에서 흔히 쓰는 방식(고전 라틴어 발음)을 단순화한 규칙입니다.
 * - c·k·q·ch → ㅋ, ph → ㅍ, th → ㅌ, x → ㄱ받침+ㅅ, v → ㅂ, z → ㅈ, j·y(모음 앞) → 반모음
 * - 모음은 하나씩 읽음 (Paeonia → 파에오니아), 겹자음은 하나로 (l·m·n 은 받침+첫소리)
 * - 모음 사이나 자음 뒤의 l 은 앞 음절에 ㄹ받침을 더함 (헬리안투스, 유글란스)
 * - 받침은 ㅁ·ㄴ·ㄹ (n 은 ㄱ·ㅋ 앞에서 ㅇ), 그 밖의 자음은 'ㅡ' 를 붙여 따로 적음 (스, 르, 트)
 * - 저자명은 빼고 속명·종소명(·변종명)만 적습니다.
 */
object LatinHangul {

    fun transliterate(scientificName: String): String =
        nameWords(scientificName).joinToString(" ") { word(it) }.trim()

    /** 저자명·괄호를 뺀 속명, 종소명, (var./subsp./f. 다음의) 변종명 */
    private fun nameWords(name: String): List<String> {
        val words = name.replace("×", " ").split(Regex("\\s+")).filter { it.isNotBlank() && it != "x" && it != "X" }
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf(words[0])
        var i = 1
        while (i < words.size && result.size < 3) {
            val w = words[i]
            when {
                w in RANKS -> {
                    val next = words.getOrNull(i + 1)
                    if (next != null && next.first().isLowerCase() && result.size >= 2) result += next
                    i += 2
                }
                w.first().isLowerCase() && w.all { it.isLetter() || it == '-' } -> {
                    result += w
                    i++
                }
                else -> break // 저자명 (대문자, 마침표, 괄호)
            }
        }
        return result
    }

    private val RANKS = setOf("var.", "subsp.", "ssp.", "f.", "sect.", "subg.", "cv.")

    // 첫소리(초성) 번호
    private const val G = 0; private const val N = 2; private const val D = 3; private const val R = 5
    private const val M = 6; private const val B = 7; private const val S = 9; private const val O = 11
    private const val J = 12; private const val K = 15; private const val T = 16; private const val P = 17
    private const val H = 18

    // 가운뎃소리(중성) 번호
    private const val A = 0; private const val E = 5; private const val I = 20; private const val OO = 8
    private const val U = 13; private const val EU = 18

    // 받침(종성) 번호
    private const val CODA_G = 1; private const val CODA_N = 4; private const val CODA_L = 8
    private const val CODA_M = 16; private const val CODA_NG = 21

    private sealed interface Tok
    private data class V(val vowel: Int) : Tok
    private data class C(val onset: Int, val coda: Int? = null, val isL: Boolean = false, val isN: Boolean = false) : Tok
    private data class Glide(val w: Boolean) : Tok // w = true 이면 'w', 아니면 'y'

    private val VOWELS = mapOf('a' to A, 'e' to E, 'i' to I, 'o' to OO, 'u' to U)

    private fun tokens(raw: String): List<Tok> {
        val s = raw.lowercase().filter { it in 'a'..'z' }
        val out = mutableListOf<Tok>()
        var i = 0
        fun isVowel(c: Char?) = c != null && (c in VOWELS || c == 'y')
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)
            val rest = s.substring(i)
            when {
                rest.startsWith("sch") -> { out += C(S); out += C(K); i += 3 }
                rest.startsWith("ch") || rest.startsWith("ck") -> { out += C(K); i += 2 }
                rest.startsWith("ph") -> { out += C(P); i += 2 }
                rest.startsWith("th") -> { out += C(T); i += 2 }
                rest.startsWith("rh") -> { out += C(R); i += 2 }
                rest.startsWith("qu") -> { out += C(K); out += Glide(w = true); i += 2 }
                c in VOWELS -> { out += V(VOWELS.getValue(c)); i++ }
                c == 'y' || c == 'j' -> {
                    val glide = isVowel(next) && next != 'y' && (i == 0 || isVowel(s.getOrNull(i - 1)) || c == 'j')
                    out += if (glide) Glide(w = false) else V(I)
                    i++
                }
                c == 'w' -> { out += Glide(w = true); i++ }
                c == 'h' -> {
                    // 단어 처음이나 모음 사이의 h 만 소리 냅니다.
                    if (i == 0 || isVowel(s.getOrNull(i - 1)) && isVowel(next)) out += C(H)
                    i++
                }
                c == next && c !in "lmn" -> i++ // 겹자음은 하나로 (ss, rr, tt …)
                else -> {
                    when (c) {
                        'x' -> { out += C(K, coda = CODA_G); out += C(S) }
                        'c', 'k', 'q' -> out += C(K)
                        'g' -> out += C(G)
                        's' -> out += C(S)
                        't' -> out += C(T)
                        'd' -> out += C(D)
                        'b', 'v' -> out += C(B)
                        'p', 'f' -> out += C(P)
                        'z' -> out += C(J)
                        'l' -> out += C(R, coda = CODA_L, isL = true)
                        'r' -> out += C(R)
                        'm' -> out += C(M, coda = CODA_M)
                        'n' -> out += C(N, coda = CODA_N, isN = true)
                    }
                    i++
                }
            }
        }
        return out
    }

    private fun glideVowel(w: Boolean, vowel: Int): Int = if (w) {
        when (vowel) { A -> 9; E -> 15; I -> 16; OO -> 14; else -> U }
    } else {
        when (vowel) { A -> 2; E -> 7; OO -> 12; U -> 17; else -> I }
    }

    private fun word(raw: String): String {
        val toks = tokens(raw)
        val syllables = mutableListOf<IntArray>() // [첫소리, 가운뎃소리, 받침]
        var prevWasVowel = false
        var i = 0
        while (i < toks.size) {
            when (val t = toks[i]) {
                is V -> { syllables += intArrayOf(O, t.vowel, 0); prevWasVowel = true; i++ }
                is Glide -> {
                    val v = toks.getOrNull(i + 1) as? V
                    if (v != null) {
                        syllables += intArrayOf(O, glideVowel(t.w, v.vowel), 0)
                        i += 2
                    } else {
                        syllables += intArrayOf(O, if (t.w) U else I, 0)
                        i++
                    }
                    prevWasVowel = true
                }
                is C -> {
                    val next = toks.getOrNull(i + 1)
                    val afterGlide = (toks.getOrNull(i + 2) as? V)?.takeIf { next is Glide }
                    when {
                        next is V || afterGlide != null -> {
                            // 모음 사이나 'ㅡ' 음절 뒤의 l 은 앞 음절에 ㄹ받침을 더합니다 (헬리안투스, 유글란스).
                            val last = syllables.lastOrNull()
                            if (t.isL && last != null && last[2] == 0 && (prevWasVowel || last[1] == EU)) {
                                last[2] = CODA_L
                            }
                            if (afterGlide != null) {
                                syllables += intArrayOf(t.onset, glideVowel((next as Glide).w, afterGlide.vowel), 0)
                                i += 3
                            } else {
                                syllables += intArrayOf(t.onset, (next as V).vowel, 0)
                                i += 2
                            }
                            prevWasVowel = true
                        }
                        else -> {
                            val following = next as? C
                            val coda = when {
                                t.isN && following != null && following.onset in setOf(G, K) -> CODA_NG
                                else -> t.coda
                            }
                            if (coda != null && prevWasVowel && syllables.isNotEmpty() && syllables.last()[2] == 0) {
                                syllables.last()[2] = coda
                            } else {
                                syllables += intArrayOf(t.onset, EU, 0)
                            }
                            prevWasVowel = false
                            i++
                        }
                    }
                }
            }
        }
        return syllables.joinToString("") { (onset, vowel, coda) ->
            (0xAC00 + (onset * 21 + vowel) * 28 + coda).toChar().toString()
        }
    }
}
