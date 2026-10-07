package com.plantdex.app.data.art

import java.util.Locale

/** 테스트·미리보기용: 그림 도형을 SVG 로 변환합니다. */
object ArtSvg {
    private fun color(c: Long) = String.format(Locale.US, "#%06X", c and 0xFFFFFF)
    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    fun toSvg(ops: List<ArtOp>, size: Int): String = buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$size\" height=\"$size\" viewBox=\"0 0 100 100\">")
        append("<clipPath id=\"c\"><circle cx=\"50\" cy=\"50\" r=\"50\"/></clipPath><g clip-path=\"url(#c)\">")
        for (op in ops) {
            when (op) {
                is CircleOp -> append(
                    "<circle cx=\"${f(op.cx)}\" cy=\"${f(op.cy)}\" r=\"${f(op.r)}\" fill=\"${op.fill?.let(::color) ?: "none"}\"" +
                        (op.stroke?.let { " stroke=\"${color(it)}\" stroke-width=\"${f(op.strokeWidth)}\"" } ?: "") + "/>",
                )
                is PathOp -> {
                    val d = op.commands.joinToString(" ") {
                        when (it) {
                            is PathCmd.MoveTo -> "M${f(it.x)},${f(it.y)}"
                            is PathCmd.LineTo -> "L${f(it.x)},${f(it.y)}"
                            is PathCmd.QuadTo -> "Q${f(it.x1)},${f(it.y1)} ${f(it.x)},${f(it.y)}"
                            is PathCmd.CubicTo -> "C${f(it.x1)},${f(it.y1)} ${f(it.x2)},${f(it.y2)} ${f(it.x)},${f(it.y)}"
                            PathCmd.Close -> "Z"
                        }
                    }
                    append(
                        "<path d=\"$d\" fill=\"${op.fill?.let(::color) ?: "none"}\"" +
                            (op.stroke?.let { " stroke=\"${color(it)}\" stroke-width=\"${f(op.strokeWidth)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"" } ?: "") +
                            "/>",
                    )
                }
            }
        }
        append("</g></svg>")
    }
}
