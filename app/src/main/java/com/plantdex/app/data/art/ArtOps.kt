package com.plantdex.app.data.art

/**
 * 식물 그림을 이루는 도형들. 100×100 좌표계(가운데 50,50)에서 정의하며,
 * 화면에서는 크기에 맞춰 늘려 그립니다. Android 에 의존하지 않아 단위 테스트와 SVG 미리보기가 가능합니다.
 */
sealed interface ArtOp

data class CircleOp(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val fill: Long?,
    val stroke: Long? = null,
    val strokeWidth: Float = 0f,
) : ArtOp

data class PathOp(
    val commands: List<PathCmd>,
    val fill: Long?,
    val stroke: Long? = null,
    val strokeWidth: Float = 0f,
) : ArtOp

sealed interface PathCmd {
    data class MoveTo(val x: Float, val y: Float) : PathCmd
    data class LineTo(val x: Float, val y: Float) : PathCmd
    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : PathCmd
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathCmd
    data object Close : PathCmd
}

/** ARGB 색 (0xAARRGGBB) 계산 */
internal object ArtColor {
    const val WHITE = 0xFFFFFFFF
    const val LEAF = 0xFF5B9B45
    const val TRUNK = 0xFF8D5A2B
    const val POLLEN = 0xFFF2C14E

    fun parse(hex: String?, fallback: Long): Long {
        val clean = hex?.trim()?.removePrefix("#") ?: return fallback
        if (clean.length != 6) return fallback
        return clean.toLongOrNull(16)?.let { 0xFF000000 or it } ?: fallback
    }

    private fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()

    fun mix(a: Long, b: Long, t: Float): Long {
        fun m(shift: Int) = (channel(a, shift) + (channel(b, shift) - channel(a, shift)) * t).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000 or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    fun lighten(c: Long, t: Float) = mix(c, WHITE, t)
    fun darken(c: Long, t: Float) = mix(c, 0xFF000000, t)

    /** 0(검정) ~ 1(흰색) */
    fun luminance(c: Long): Float =
        (0.2126f * channel(c, 16) + 0.7152f * channel(c, 8) + 0.0722f * channel(c, 0)) / 255f
}
