package com.plantdex.app.data.art

import com.plantdex.app.data.art.ArtColor.darken
import com.plantdex.app.data.art.ArtColor.lighten
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** [PlantArt] 사양을 100×100 좌표계의 도형 목록으로 바꿉니다. */
object PlantArtRenderer {

    private const val OUTLINE = 1.1f

    fun render(art: PlantArt): List<ArtOp> {
        val main = ArtColor.parse(art.color, 0xFFF4A7C0)
        val accent = art.accent?.let { ArtColor.parse(it, ArtColor.POLLEN) }
        val ops = mutableListOf<ArtOp>()
        ops += CircleOp(50f, 50f, 50f, background(art.form, main))
        val canvas = Canvas(ops)
        when (art.form) {
            "flower" -> canvas.flower(50f, 50f, 1f, art.petals, art.petal, main, accent, art.layers, art.spots, art.variant)
            "daisy" -> canvas.daisy(art.petals, main, accent, art.variant)
            "cluster" -> canvas.cluster(art.petals, art.petal, main, accent, art.variant)
            "funnel" -> canvas.funnel(main, accent)
            "cup" -> canvas.cup(main, accent, art.variant)
            "bell" -> canvas.bell(main, accent, art.variant)
            "iris" -> canvas.iris(main, accent)
            "spike" -> canvas.spike(main, accent, art.variant)
            "plume" -> canvas.plume(main)
            "conifer" -> canvas.conifer(main, accent, art.variant)
            "tree" -> canvas.tree(main, accent, art.variant)
            "leaf" -> canvas.leaf(main, accent, art.variant)
            "rosette" -> canvas.rosette(main, accent, art.variant)
            "berries" -> canvas.berries(main, accent, art.variant)
            "sprout" -> canvas.sprout(main, art.variant)
            else -> canvas.flower(50f, 50f, 1f, art.petals, art.petal, main, accent, art.layers, art.spots, art.variant)
        }
        return ops
    }

    /** 꽃은 꽃색을 옅게, 흰 꽃·잎·나무는 연한 초록을 배경으로 씁니다. */
    private fun background(form: String, main: Long): Long = when {
        form in setOf("conifer", "tree", "leaf", "rosette", "sprout", "plume") -> 0xFFE6F2DF
        ArtColor.luminance(main) > 0.85f -> 0xFFE3F0DC
        else -> lighten(main, 0.8f)
    }

    // ── 도형 그리기 도구 ─────────────────────────────────────────────

    private class Path(private val transform: (Float, Float) -> Pair<Float, Float>) {
        val commands = mutableListOf<PathCmd>()
        fun moveTo(x: Float, y: Float) = transform(x, y).let { commands += PathCmd.MoveTo(it.first, it.second) }
        fun lineTo(x: Float, y: Float) = transform(x, y).let { commands += PathCmd.LineTo(it.first, it.second) }
        fun quadTo(x1: Float, y1: Float, x: Float, y: Float) {
            val a = transform(x1, y1)
            val b = transform(x, y)
            commands += PathCmd.QuadTo(a.first, a.second, b.first, b.second)
        }
        fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
            val a = transform(x1, y1)
            val b = transform(x2, y2)
            val c = transform(x, y)
            commands += PathCmd.CubicTo(a.first, a.second, b.first, b.second, c.first, c.second)
        }
        fun close() {
            commands += PathCmd.Close
        }
    }

    /** (cx, cy) 로 옮기고 angle 도(0 = 위쪽, 시계방향) 만큼 돌린 뒤 scale 배 합니다. */
    private fun place(cx: Float, cy: Float, angle: Float = 0f, scale: Float = 1f): (Float, Float) -> Pair<Float, Float> {
        val rad = angle * PI.toFloat() / 180f
        val c = cos(rad)
        val s = sin(rad)
        return { x, y -> (cx + (x * c - y * s) * scale) to (cy + (x * s + y * c) * scale) }
    }

    private val identity: (Float, Float) -> Pair<Float, Float> = { x, y -> x to y }

    private class Canvas(val ops: MutableList<ArtOp>) {

        fun path(
            fill: Long?,
            stroke: Long? = null,
            width: Float = OUTLINE,
            transform: (Float, Float) -> Pair<Float, Float> = identity,
            build: Path.() -> Unit,
        ) {
            val p = Path(transform).apply(build)
            ops += PathOp(p.commands, fill, stroke, if (stroke != null) width else 0f)
        }

        fun circle(x: Float, y: Float, r: Float, fill: Long?, stroke: Long? = null, width: Float = OUTLINE) {
            ops += CircleOp(x, y, r, fill, stroke, if (stroke != null) width else 0f)
        }

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Long, width: Float) =
            path(null, color, width) { moveTo(x1, y1); lineTo(x2, y2) }

        /** 타원 (rx, ry) 을 angle 만큼 돌려서 */
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float, fill: Long?, stroke: Long? = null) {
            val k = 0.5523f
            path(fill, stroke, transform = place(cx, cy, angle)) {
                moveTo(0f, -ry)
                cubicTo(rx * k, -ry, rx, -ry * k, rx, 0f)
                cubicTo(rx, ry * k, rx * k, ry, 0f, ry)
                cubicTo(-rx * k, ry, -rx, ry * k, -rx, 0f)
                cubicTo(-rx, -ry * k, -rx * k, -ry, 0f, -ry)
                close()
            }
        }

        /**
         * 꽃잎 하나: 원점에서 위쪽(-y)으로 뻗고, r0 에서 시작해 len 에서 끝나며 최대 반폭 w.
         */
        fun petal(shape: String, r0: Float, len: Float, w: Float, transform: (Float, Float) -> Pair<Float, Float>, fill: Long, stroke: Long?) {
            val d = len - r0
            path(fill, stroke, transform = transform) {
                moveTo(0f, -r0)
                when (shape) {
                    "pointed", "narrow" -> {
                        cubicTo(w * 1.25f, -r0 - d * 0.3f, w * 0.55f, -len + d * 0.18f, 0f, -len)
                        cubicTo(-w * 0.55f, -len + d * 0.18f, -w * 1.25f, -r0 - d * 0.3f, 0f, -r0)
                    }
                    "notched" -> {
                        cubicTo(w * 1.3f, -r0 - d * 0.2f, w * 1.1f, -len - d * 0.04f, w * 0.38f, -len)
                        lineTo(0f, -len + d * 0.14f)
                        lineTo(-w * 0.38f, -len)
                        cubicTo(-w * 1.1f, -len - d * 0.04f, -w * 1.3f, -r0 - d * 0.2f, 0f, -r0)
                    }
                    "wavy" -> {
                        cubicTo(w * 1.2f, -r0 - d * 0.15f, w * 1.15f, -len + d * 0.45f, w * 0.95f, -len + d * 0.25f)
                        quadTo(w * 0.85f, -len - d * 0.06f, w * 0.32f, -len + d * 0.03f)
                        quadTo(0f, -len - d * 0.12f, -w * 0.32f, -len + d * 0.03f)
                        quadTo(-w * 0.85f, -len - d * 0.06f, -w * 0.95f, -len + d * 0.25f)
                        cubicTo(-w * 1.15f, -len + d * 0.45f, -w * 1.2f, -r0 - d * 0.15f, 0f, -r0)
                    }
                    else -> { // round, oblong
                        cubicTo(w * 1.3f, -r0 - d * 0.15f, w * 1.0f, -len - d * 0.12f, 0f, -len)
                        cubicTo(-w * 1.0f, -len - d * 0.12f, -w * 1.3f, -r0 - d * 0.15f, 0f, -r0)
                    }
                }
                close()
            }
        }

        fun petalWidth(shape: String, n: Int, len: Float): Float {
            val arc = PI.toFloat() * len / n
            return when (shape) {
                "narrow" -> (arc * 0.28f).coerceIn(2.2f, 6f)
                "oblong" -> (arc * 0.42f).coerceIn(3f, 11f)
                else -> (arc * 0.6f).coerceIn(4f, 17f)
            }
        }

        fun leafBlade(x: Float, y: Float, angle: Float, len: Float, w: Float, color: Long = ArtColor.LEAF) =
            petal("pointed", 0f, len, w, place(x, y, angle), color, darken(color, 0.25f))

        // ── 형태별 그림 ──────────────────────────────────────────────

        fun flower(
            cx: Float, cy: Float, scale: Float, n: Int, shape: String, main: Long, accent: Long?,
            layers: Int, spots: Boolean, variant: String?,
        ) {
            val count = n.coerceIn(3, 24)
            val len = 40f
            val w = petalWidth(shape, count, len)
            val outline = darken(main, 0.28f)
            for (layer in 0 until layers.coerceIn(1, 3)) {
                val s = 1f - layer * 0.3f
                val color = darken(main, layer * 0.1f)
                val rotate = layer * 180f / count
                for (i in 0 until count) {
                    petal(shape, 2f, len * s, w * (1f - layer * 0.12f), place(cx, cy, rotate + i * 360f / count, scale), color, outline)
                }
            }
            if (spots) {
                val dot = darken(accent ?: main, 0.45f)
                for (i in 0 until count) {
                    val t = place(cx, cy, i * 360f / count, scale)
                    listOf(0f to -len * 0.42f, -w * 0.3f to -len * 0.58f, w * 0.3f to -len * 0.6f).forEach { (x, y) ->
                        val p = t(x, y)
                        circle(p.first, p.second, 1.4f * scale, dot)
                    }
                }
            }
            val center = accent ?: ArtColor.POLLEN
            when (variant) {
                "face" -> {
                    circle(cx, cy, 15f * scale, center)
                    circle(cx, cy, 4f * scale, ArtColor.POLLEN, darken(ArtColor.POLLEN, 0.3f))
                }
                "corona" -> {
                    circle(cx, cy, 14f * scale, center, darken(center, 0.25f), 1.4f)
                    circle(cx, cy, 8f * scale, darken(center, 0.15f))
                }
                "stamen" -> {
                    circle(cx, cy, 6f * scale, center)
                    for (a in listOf(-34f, -17f, 0f, 17f, 34f)) {
                        val t = place(cx, cy, a, scale)
                        val tip = t(0f, -20f)
                        val base = t(0f, 0f)
                        line(base.first, base.second, tip.first, tip.second, darken(center, 0.2f), 1.1f * scale)
                        circle(tip.first, tip.second, 1.8f * scale, ArtColor.POLLEN, darken(ArtColor.POLLEN, 0.35f), 0.6f)
                    }
                }
                else -> circle(cx, cy, 6.5f * scale, center, darken(center, 0.3f))
            }
        }

        fun daisy(n: Int, main: Long, accent: Long?, variant: String?) {
            val count = n.coerceIn(8, 32)
            val big = variant == "big"
            val outline = darken(main, 0.28f)
            val inner = if (big) 14f else 6f
            val w = if (count >= 16) 3.4f else 4.8f
            if (count >= 16) {
                val half = count / 2
                for (i in 0 until half) {
                    petal("oblong", inner, 43f, w, place(50f, 50f, i * 360f / half + 180f / half), darken(main, 0.08f), outline)
                }
                for (i in 0 until half) petal("oblong", inner, 40f, w, place(50f, 50f, i * 360f / half), main, outline)
            } else {
                for (i in 0 until count) petal("oblong", inner, 42f, w, place(50f, 50f, i * 360f / count), main, outline)
            }
            val center = accent ?: ArtColor.POLLEN
            val r = if (big) 17f else 10f
            circle(50f, 50f, r, center, darken(center, 0.3f))
            for (i in 0 until 10) {
                val p = place(50f, 50f, i * 36f)(0f, -r * 0.55f)
                circle(p.first, p.second, if (big) 1.6f else 1.1f, darken(center, 0.25f))
            }
        }

        /** 작은 꽃(floret)들이 모인 꽃차례: ball(수국), cone(라일락), spray(조팝나무처럼 흩어진 무리) */
        fun cluster(n: Int, shape: String, main: Long, accent: Long?, variant: String?) {
            val florets: List<Triple<Float, Float, Float>> = when (variant) {
                "cone" -> {
                    leafBlade(50f, 90f, -40f, 30f, 8f)
                    leafBlade(50f, 90f, 40f, 30f, 8f)
                    line(50f, 92f, 50f, 70f, darken(ArtColor.LEAF, 0.1f), 2.4f)
                    listOf(
                        Triple(50f, 16f, 6f),
                        Triple(44f, 26f, 7f), Triple(56f, 26f, 7f),
                        Triple(38f, 38f, 7.5f), Triple(50f, 36f, 7.5f), Triple(62f, 38f, 7.5f),
                        Triple(34f, 51f, 8f), Triple(46f, 49f, 8f), Triple(58f, 49f, 8f), Triple(67f, 52f, 7.5f),
                        Triple(40f, 63f, 8f), Triple(53f, 62f, 8f), Triple(64f, 65f, 7f),
                    )
                }
                "spray" -> {
                    path(null, darken(ArtColor.LEAF, 0.15f), 2.2f) {
                        moveTo(22f, 88f); cubicTo(36f, 70f, 50f, 54f, 76f, 34f)
                    }
                    leafBlade(36f, 72f, 60f, 16f, 5f)
                    leafBlade(56f, 52f, -30f, 14f, 4.5f)
                    listOf(
                        Triple(30f, 66f, 6.5f), Triple(42f, 58f, 7f), Triple(30f, 48f, 6.5f), Triple(48f, 42f, 7f),
                        Triple(62f, 46f, 6.5f), Triple(58f, 30f, 7f), Triple(72f, 30f, 6.5f), Triple(44f, 28f, 6f),
                        Triple(70f, 18f, 5.5f),
                    )
                }
                else -> { // ball
                    leafBlade(50f, 76f, -55f, 26f, 9f)
                    leafBlade(50f, 76f, 55f, 26f, 9f)
                    listOf(
                        Triple(50f, 30f, 9f), Triple(36f, 36f, 9f), Triple(64f, 36f, 9f),
                        Triple(28f, 50f, 8.5f), Triple(72f, 50f, 8.5f), Triple(43f, 49f, 9.5f), Triple(57f, 49f, 9.5f),
                        Triple(35f, 63f, 8.5f), Triple(50f, 62f, 9f), Triple(65f, 63f, 8.5f),
                    )
                }
            }
            val outline = darken(main, 0.28f)
            val count = n.coerceIn(3, 12)
            florets.forEachIndexed { index, (x, y, size) ->
                val s = size / 40f * 1.9f
                val color = if (index % 3 == 1) lighten(main, 0.12f) else main
                val w = petalWidth(shape, count, 40f)
                for (i in 0 until count) {
                    petal(shape, 2f, 40f, w, place(x, y, i * 360f / count + index * 17f, s), color, outline)
                }
                circle(x, y, 1.6f * s * 2.2f, accent ?: lighten(ArtColor.POLLEN, 0.2f))
            }
        }

        /** 나팔꽃형을 정면에서 본 모양 */
        fun funnel(main: Long, accent: Long?) {
            val outline = darken(main, 0.25f)
            for (i in 0 until 5) petal("round", 0f, 43f, 24f, place(50f, 50f, i * 72f), main, outline)
            circle(50f, 50f, 15f, accent ?: lighten(main, 0.55f))
            for (i in 0 until 5) {
                val tip = place(50f, 50f, i * 72f)(0f, -38f)
                line(50f, 50f, tip.first, tip.second, lighten(main, 0.4f), 2.2f)
            }
            circle(50f, 50f, 3.5f, lighten(ArtColor.POLLEN, 0.3f))
        }

        /** 옆에서 본 튤립(cup) 또는 활짝 핀 목련(open) */
        fun cup(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.28f)
            if (variant == "open") {
                val petalOutline = if (ArtColor.luminance(main) > 0.85f) 0xFFB8A9A0 else outline
                line(50f, 96f, 50f, 76f, darken(ArtColor.TRUNK, 0.1f), 3.4f)
                for (a in listOf(-62f, 62f)) petal("round", 0f, 40f, 12f, place(50f, 76f, a), darken(main, 0.04f), petalOutline)
                for (a in listOf(-30f, 0f, 30f)) {
                    petal("round", 0f, 52f, 13f, place(50f, 76f, a), main, petalOutline)
                    if (accent != null) petal("pointed", 0f, 20f, 8f, place(50f, 76f, a), accent, null)
                }
                return
            }
            path(null, darken(ArtColor.LEAF, 0.1f), 3.5f) { moveTo(50f, 95f); lineTo(50f, 62f) }
            path(ArtColor.LEAF, darken(ArtColor.LEAF, 0.25f)) {
                moveTo(50f, 93f); cubicTo(30f, 86f, 25f, 64f, 32f, 52f); cubicTo(40f, 66f, 46f, 80f, 50f, 93f); close()
            }
            path(ArtColor.LEAF, darken(ArtColor.LEAF, 0.25f)) {
                moveTo(50f, 93f); cubicTo(66f, 86f, 72f, 70f, 68f, 60f); cubicTo(60f, 72f, 54f, 82f, 50f, 93f); close()
            }
            path(main, outline) {
                moveTo(30f, 30f)
                cubicTo(27f, 56f, 37f, 68f, 50f, 68f)
                cubicTo(63f, 68f, 73f, 56f, 70f, 30f)
                lineTo(62f, 42f); lineTo(57f, 22f); lineTo(50f, 38f); lineTo(43f, 22f); lineTo(38f, 42f)
                close()
            }
            path(darken(main, 0.1f), outline) {
                moveTo(43f, 22f); cubicTo(41f, 40f, 45f, 58f, 50f, 66f); cubicTo(55f, 58f, 59f, 40f, 57f, 22f)
                lineTo(50f, 38f); close()
            }
            if (accent != null) {
                path(accent, null) {
                    moveTo(40f, 64f); cubicTo(44f, 67f, 56f, 67f, 60f, 64f); cubicTo(56f, 60f, 44f, 60f, 40f, 64f); close()
                }
            }
        }

        private fun cubicPoint(p0: Pair<Float, Float>, p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>, t: Float): Pair<Float, Float> {
            val u = 1 - t
            fun c(a: Float, b: Float, cc: Float, d: Float) = u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * cc + t * t * t * d
            return c(p0.first, p1.first, p2.first, p3.first) to c(p0.second, p1.second, p2.second, p3.second)
        }

        /** 줄기에 매달린 종 모양 꽃: 기본(은방울꽃), tube(둥굴레), heart(금낭화), single(할미꽃) */
        fun bell(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.3f).let { if (ArtColor.luminance(main) > 0.85f) 0xFF9E9E9E else it }
            val stem = darken(ArtColor.LEAF, 0.15f)
            if (variant == "single") {
                path(null, stem, 2.6f) { moveTo(44f, 95f); cubicTo(40f, 60f, 50f, 30f, 68f, 26f) }
                leafBlade(44f, 92f, -50f, 22f, 5f)
                leafBlade(44f, 92f, 45f, 20f, 5f)
                drawBell(68f, 30f, 2.4f, main, outline, null)
                accent?.let { a -> listOf(-4f, 0f, 4f).forEach { circle(68f + it * 2.2f, 30f + 10f * 2.4f + 1f, 1.8f, a) } }
                return
            }
            if (variant != "heart") {
                path(ArtColor.LEAF, darken(ArtColor.LEAF, 0.25f)) {
                    moveTo(30f, 93f); cubicTo(12f, 70f, 16f, 38f, 30f, 22f); cubicTo(40f, 44f, 42f, 72f, 30f, 93f); close()
                }
            }
            val p0 = 42f to 93f
            val p1 = 44f to 50f
            val p2 = 56f to 24f
            val p3 = 84f to 24f
            path(null, stem, 2.4f) { moveTo(p0.first, p0.second); cubicTo(p1.first, p1.second, p2.first, p2.second, p3.first, p3.second) }
            val positions = listOf(0.42f, 0.6f, 0.76f, 0.9f)
            positions.forEachIndexed { i, t ->
                val (x, y) = cubicPoint(p0, p1, p2, p3, t)
                val scale = 1.15f - i * 0.12f
                line(x, y, x, y + 4f, stem, 1.2f)
                drawBell(x, y + 4f, scale, main, outline, variant, accent)
            }
        }

        private fun drawBell(x: Float, y: Float, s: Float, main: Long, outline: Long, variant: String?, accent: Long? = null) {
            when (variant) {
                "tube" -> {
                    path(main, outline, transform = place(x, y, 0f, s)) {
                        moveTo(-3.5f, 0f); lineTo(3.5f, 0f); lineTo(4.5f, 14f); lineTo(-4.5f, 14f); close()
                    }
                    path(accent ?: 0xFF9CCC65, outline, transform = place(x, y, 0f, s)) {
                        moveTo(-4.5f, 14f); lineTo(4.5f, 14f); lineTo(4.8f, 17f); lineTo(-4.8f, 17f); close()
                    }
                }
                "heart" -> {
                    path(main, outline, transform = place(x, y, 0f, s)) {
                        moveTo(0f, 13f)
                        cubicTo(-10f, 6f, -9f, -3f, -3.5f, 0f)
                        cubicTo(-1.5f, 1f, 1.5f, 1f, 3.5f, 0f)
                        cubicTo(9f, -3f, 10f, 6f, 0f, 13f)
                        close()
                    }
                    val tip = place(x, y, 0f, s)(0f, 14.5f)
                    circle(tip.first, tip.second, 2.4f * s, accent ?: ArtColor.WHITE, outline, 0.7f)
                }
                else -> path(main, outline, transform = place(x, y, 0f, s)) {
                    moveTo(0f, 0f)
                    cubicTo(7f, 0f, 7f, 7f, 8f, 10f)
                    quadTo(5f, 8f, 2.7f, 10.5f)
                    quadTo(0f, 8f, -2.7f, 10.5f)
                    quadTo(-5f, 8f, -8f, 10f)
                    cubicTo(-7f, 7f, -7f, 0f, 0f, 0f)
                    close()
                }
            }
        }

        fun iris(main: Long, accent: Long?) {
            val outline = darken(main, 0.3f)
            for (a in listOf(180f, 60f, 300f)) {
                petal("round", 3f, 42f, 15f, place(50f, 50f, a), main, outline)
                petal("pointed", 8f, 26f, 4f, place(50f, 50f, a), accent ?: ArtColor.POLLEN, null)
            }
            for (a in listOf(0f, 120f, 240f)) petal("pointed", 2f, 31f, 8f, place(50f, 50f, a), lighten(main, 0.2f), outline)
            circle(50f, 50f, 5f, lighten(main, 0.35f), outline)
        }

        fun spike(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.3f).let { if (ArtColor.luminance(main) > 0.85f) 0xFF9E9E9E else it }
            val stem = darken(ArtColor.LEAF, 0.12f)
            when (variant) {
                "sausage" -> {
                    leafBlade(50f, 95f, -18f, 70f, 4.5f)
                    leafBlade(50f, 95f, 22f, 62f, 4.5f)
                    line(50f, 95f, 50f, 10f, stem, 2.2f)
                    path(main, darken(main, 0.3f)) {
                        moveTo(44f, 34f); quadTo(44f, 26f, 50f, 26f); quadTo(56f, 26f, 56f, 34f)
                        lineTo(56f, 60f); quadTo(56f, 68f, 50f, 68f); quadTo(44f, 68f, 44f, 60f); close()
                    }
                    return
                }
                "catkin" -> {
                    path(null, ArtColor.TRUNK, 3f) { moveTo(22f, 90f); cubicTo(38f, 66f, 56f, 42f, 78f, 14f) }
                    listOf(Triple(36f, 64f, -60f), Triple(56f, 44f, 35f), Triple(68f, 24f, -50f)).forEach { (x, y, a) ->
                        val off = place(x, y, a)(0f, -7f)
                        ellipse(off.first, off.second, 5.5f, 9f, a, main, outline)
                        accent?.let { ac -> circle(off.first, off.second + 2f, 1.6f, ac) }
                    }
                    return
                }
                "curved" -> {
                    leafBlade(50f, 94f, -45f, 26f, 6f)
                    leafBlade(50f, 94f, 50f, 24f, 6f)
                    val p0 = 50f to 94f
                    val p1 = 50f to 60f
                    val p2 = 52f to 28f
                    val p3 = 76f to 22f
                    path(null, stem, 2.2f) { moveTo(p0.first, p0.second); cubicTo(p1.first, p1.second, p2.first, p2.second, p3.first, p3.second) }
                    for (k in 0 until 11) {
                        val t = 0.45f + k * 0.055f
                        val (x, y) = cubicPoint(p0, p1, p2, p3, t)
                        circle(x + if (k % 2 == 0) -2.5f else 2.5f, y, 4.2f - k * 0.22f, main, outline, 0.9f)
                    }
                    return
                }
                "spadix" -> {
                    leafBlade(46f, 95f, -12f, 78f, 5f)
                    leafBlade(52f, 95f, 14f, 72f, 5f)
                    ellipse(60f, 46f, 3.6f, 11f, 22f, main, darken(main, 0.3f))
                    return
                }
            }
            leafBlade(50f, 95f, -38f, 36f, 6f)
            leafBlade(50f, 95f, 40f, 32f, 6f)
            line(50f, 95f, 50f, 16f, stem, 2.4f)
            when (variant) {
                "whorl" -> listOf(70f, 52f, 34f).forEach { y ->
                    leafBlade(50f, y + 3f, -70f, 10f, 4f)
                    leafBlade(50f, y + 3f, 70f, 10f, 4f)
                    listOf(-7f, -2.5f, 2.5f, 7f).forEach { dx -> ellipse(50f + dx, y - 3f, 2.6f, 5f, dx * 4f, main, outline) }
                }
                "hood" -> listOf(Triple(44f, 66f, 1f), Triple(56f, 50f, 0.95f), Triple(45f, 34f, 0.85f), Triple(52f, 20f, 0.7f)).forEach { (x, y, s) ->
                    path(main, outline, transform = place(x, y, 0f, s)) {
                        moveTo(-7f, 5f); cubicTo(-8f, -9f, 8f, -12f, 7f, 3f); quadTo(1f, 0f, -7f, 5f); close()
                    }
                }
                "pods" -> {
                    for (k in 0 until 6) {
                        val y = 76f - k * 8f
                        val side = if (k % 2 == 0) -1f else 1f
                        line(50f, y + 3f, 50f + side * 7f, y, stem, 1f)
                        path(0xFF8BC34A, darken(0xFF8BC34A, 0.3f), 0.8f, place(50f + side * 9f, y, side * 20f)) {
                            moveTo(0f, 3.5f); lineTo(-4f, -3f); quadTo(0f, -1f, 4f, -3f); close()
                        }
                    }
                    listOf(-3f to 18f, 3f to 18f, 0f to 14f, -2f to 22f, 2f to 22f).forEach { (dx, y) -> circle(50f + dx, y, 2f, main, outline, 0.6f) }
                }
                else -> for (k in 0 until 11) {
                    val y = 70f - k * 5f
                    circle(50f + if (k % 2 == 0) -3f else 3f, y, 4.3f - k * 0.22f, main, outline, 0.9f)
                }
            }
            accent?.let { if (variant == null) circle(50f, 16f, 2f, it) }
        }

        fun plume(main: Long) {
            val blade = darken(ArtColor.LEAF, 0.05f)
            path(null, blade, 2.6f) { moveTo(48f, 95f); cubicTo(40f, 76f, 30f, 62f, 18f, 54f) }
            path(null, blade, 2.6f) { moveTo(50f, 95f); cubicTo(46f, 74f, 40f, 56f, 32f, 40f) }
            path(null, blade, 2.6f) { moveTo(52f, 95f); cubicTo(58f, 76f, 70f, 64f, 82f, 60f) }
            val p0 = 50f to 95f
            val p1 = 50f to 62f
            val p2 = 54f to 36f
            val p3 = 68f to 14f
            path(null, darken(main, 0.35f), 1.6f) { moveTo(p0.first, p0.second); cubicTo(p1.first, p1.second, p2.first, p2.second, p3.first, p3.second) }
            for (k in 0 until 12) {
                val t = 0.42f + k * 0.05f
                val (x, y) = cubicPoint(p0, p1, p2, p3, t)
                val len = 10f - k * 0.45f
                line(x, y, x - len, y - len * 0.5f, main, 2.4f)
                line(x, y, x + len * 0.8f, y - len * 0.7f, lighten(main, 0.15f), 2.4f)
            }
        }

        fun conifer(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.3f)
            when (variant) {
                "pine" -> {
                    val trunk = accent ?: ArtColor.TRUNK
                    path(trunk, darken(trunk, 0.3f)) {
                        moveTo(46f, 95f); cubicTo(44f, 76f, 50f, 64f, 47f, 46f); lineTo(53f, 46f)
                        cubicTo(56f, 62f, 52f, 76f, 55f, 95f); close()
                    }
                    path(null, trunk, 2.4f) { moveTo(49f, 60f); quadTo(40f, 54f, 32f, 48f) }
                    path(null, trunk, 2.4f) { moveTo(51f, 52f); quadTo(60f, 44f, 66f, 38f) }
                    ellipse(32f, 44f, 17f, 8f, -6f, main, outline)
                    ellipse(66f, 34f, 17f, 8f, 8f, main, outline)
                    ellipse(48f, 22f, 14f, 7.5f, 0f, darken(main, 0.05f), outline)
                }
                "narrow" -> {
                    path(accent ?: ArtColor.TRUNK, null) { moveTo(47f, 95f); lineTo(53f, 95f); lineTo(52f, 78f); lineTo(48f, 78f); close() }
                    // (밑변 y, 반폭, 꼭대기 y): 좁고 높은 원뿔
                    listOf(Triple(82f, 16f, 30f), Triple(64f, 13f, 17f), Triple(46f, 10f, 7f)).forEachIndexed { i, (base, half, apex) ->
                        path(darken(main, i * 0.06f), outline) { moveTo(50f, apex); lineTo(50f + half, base); lineTo(50f - half, base); close() }
                    }
                }
                else -> {
                    path(ArtColor.TRUNK, null) { moveTo(46f, 95f); lineTo(54f, 95f); lineTo(53f, 76f); lineTo(47f, 76f); close() }
                    listOf(Triple(78f, 30f, 0f), Triple(60f, 24f, 0.05f), Triple(42f, 18f, 0.1f)).forEach { (base, half, d) ->
                        path(darken(main, d), outline) { moveTo(50f, base - 32f); lineTo(50f + half, base); lineTo(50f - half, base); close() }
                    }
                    if (variant == "berry" && accent != null) {
                        listOf(40f to 70f, 60f to 54f, 44f to 46f, 58f to 72f).forEach { (x, y) -> circle(x, y, 2.8f, accent, darken(accent, 0.3f)) }
                    }
                }
            }
        }

        fun tree(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.3f)
            val dy = if (variant == "shrub") 14f else 0f
            path(ArtColor.TRUNK, darken(ArtColor.TRUNK, 0.3f)) {
                moveTo(45f, 95f); lineTo(47f, 60f + dy); lineTo(53f, 60f + dy); lineTo(55f, 95f); close()
            }
            val blobs = if (variant == "shrub") {
                listOf(Triple(50f, 54f, 20f), Triple(30f, 62f, 15f), Triple(70f, 62f, 15f), Triple(50f, 68f, 16f))
            } else {
                listOf(Triple(50f, 34f, 22f), Triple(30f, 46f, 15f), Triple(70f, 46f, 15f), Triple(50f, 54f, 17f), Triple(38f, 30f, 12f), Triple(62f, 30f, 12f))
            }
            blobs.forEach { (x, y, r) -> circle(x, y, r + OUTLINE, outline) }
            blobs.forEachIndexed { i, (x, y, r) -> circle(x, y, r, if (i % 2 == 0) main else lighten(main, 0.08f)) }
            accent?.let { a ->
                val fruits = if (variant == "shrub") listOf(38f to 56f, 60f to 60f, 50f to 48f) else listOf(40f to 32f, 62f to 40f, 48f to 50f, 66f to 26f, 32f to 48f)
                fruits.forEach { (x, y) -> circle(x, y, 2.8f, a, darken(a, 0.3f)) }
            }
        }

        fun leaf(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.3f)
            val stem = darken(ArtColor.LEAF, 0.15f)
            when (variant) {
                "fan" -> {
                    line(50f, 94f, 50f, 64f, darken(main, 0.25f), 2.4f)
                    path(main, outline) {
                        moveTo(50f, 66f)
                        cubicTo(38f, 58f, 20f, 42f, 16f, 28f)
                        cubicTo(28f, 16f, 42f, 14f, 47f, 16f)
                        lineTo(50f, 30f); lineTo(53f, 16f)
                        cubicTo(58f, 14f, 72f, 16f, 84f, 28f)
                        cubicTo(80f, 42f, 62f, 58f, 50f, 66f)
                        close()
                    }
                    listOf(-38f, -24f, -10f, 10f, 24f, 38f).forEach { a ->
                        val tip = place(50f, 66f, a)(0f, -40f)
                        line(50f, 66f, tip.first, tip.second, darken(main, 0.12f), 0.8f)
                    }
                }
                "maple" -> {
                    line(50f, 94f, 50f, 60f, darken(main, 0.25f), 2.2f)
                    // 다섯 갈래 손바닥 모양 잎: (각도, 반지름)을 시계 방향으로 이어 톱니 가장자리를 만듭니다.
                    val edge = listOf(
                        0f to 40f, 9f to 27f, 17f to 29f, 28f to 14f,
                        41f to 30f, 55f to 37f, 65f to 27f, 84f to 14f,
                        99f to 24f, 112f to 25f, 125f to 14f, 160f to 6f,
                        -160f to 6f, -125f to 14f, -112f to 25f, -99f to 24f,
                        -84f to 14f, -65f to 27f, -55f to 37f, -41f to 30f,
                        -28f to 14f, -17f to 29f, -9f to 27f,
                    )
                    path(main, outline) {
                        edge.forEachIndexed { i, (a, r) ->
                            val p = place(50f, 58f, a)(0f, -r)
                            if (i == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second)
                        }
                        close()
                    }
                    listOf(0f to 40f, -55f to 37f, 55f to 37f, -112f to 25f, 112f to 25f).forEach { (a, r) ->
                        val tip = place(50f, 58f, a)(0f, -r * 0.85f)
                        line(50f, 58f, tip.first, tip.second, darken(main, 0.18f), 0.9f)
                    }
                }
                "hand" -> {
                    line(50f, 95f, 50f, 64f, stem, 2.6f)
                    for (a in listOf(-80f, -54f, -27f, 0f, 27f, 54f, 80f)) petal("pointed", 0f, 36f, 7.5f, place(50f, 64f, a), main, outline)
                    circle(50f, 64f, 9f, main)
                    for (a in listOf(-80f, -54f, -27f, 0f, 27f, 54f, 80f)) {
                        val tip = place(50f, 64f, a)(0f, -30f)
                        line(50f, 64f, tip.first, tip.second, lighten(main, 0.25f), 0.9f)
                    }
                }
                "holly" -> {
                    for ((a, len) in listOf(-38f to 38f, 40f to 34f)) {
                        path(main, outline, transform = place(50f, 66f, a)) {
                            moveTo(0f, 0f)
                            val pts = listOf(
                                9f to -6f, 7f to -12f, 11f to -18f, 7f to -24f, 9f to -30f, 4f to -33f, 0f to -len,
                                -4f to -33f, -9f to -30f, -7f to -24f, -11f to -18f, -7f to -12f, -9f to -6f,
                            )
                            pts.forEach { (x, y) -> lineTo(x, y) }
                            close()
                        }
                        val tip = place(50f, 66f, a)(0f, -len * 0.9f)
                        line(50f, 66f, tip.first, tip.second, lighten(main, 0.2f), 0.9f)
                    }
                    val berry = accent ?: 0xFFD32F2F
                    listOf(50f to 68f, 57f to 64f, 44f to 63f).forEach { (x, y) ->
                        circle(x, y, 4.6f, berry, darken(berry, 0.3f))
                        circle(x - 1.4f, y - 1.6f, 1f, lighten(berry, 0.6f))
                    }
                }
                else -> { // clover
                    path(null, stem, 2.4f) { moveTo(50f, 94f); cubicTo(50f, 78f, 48f, 66f, 50f, 52f) }
                    for (a in listOf(0f, 120f, 240f)) {
                        path(main, outline, transform = place(50f, 44f, a)) {
                            moveTo(0f, 0f)
                            cubicTo(-14f, -5f, -17f, -23f, -7f, -26f)
                            cubicTo(-2.5f, -27f, 0f, -24f, 0f, -21f)
                            cubicTo(0f, -24f, 2.5f, -27f, 7f, -26f)
                            cubicTo(17f, -23f, 14f, -5f, 0f, 0f)
                            close()
                        }
                        path(null, lighten(main, 0.45f), 1.4f, place(50f, 44f, a)) {
                            moveTo(-5f, -15f); quadTo(0f, -10f, 5f, -15f)
                        }
                    }
                    accent?.let { flower(76f, 22f, 0.3f, 5, "round", it, ArtColor.POLLEN, 1, false, null) }
                }
            }
        }

        fun rosette(main: Long, accent: Long?, variant: String?) {
            val outline = darken(main, 0.28f)
            for (i in 0 until 7) petal("round", 2f, 36f, 9f, place(50f, 58f, i * 360f / 7 + 10f), if (i % 2 == 0) main else lighten(main, 0.08f), outline)
            for (i in 0 until 7) {
                val tip = place(50f, 58f, i * 360f / 7 + 10f)(0f, -28f)
                line(50f, 58f, tip.first, tip.second, lighten(main, 0.35f), 0.9f)
            }
            when (variant) {
                "spike" -> {
                    line(50f, 58f, 50f, 12f, darken(main, 0.2f), 2.2f)
                    ellipse(50f, 22f, 3.4f, 11f, 0f, accent ?: lighten(main, 0.3f), darken(accent ?: main, 0.3f))
                }
                "umbel" -> {
                    val c = accent ?: ArtColor.WHITE
                    for (i in 0 until 9) {
                        val p = place(50f, 56f, i * 40f)(0f, -8f)
                        circle(p.first, p.second, 3f, c, 0xFF9E9E9E, 0.6f)
                    }
                    circle(50f, 56f, 3.4f, c, 0xFF9E9E9E, 0.6f)
                }
                else -> accent?.let { circle(50f, 58f, 6f, it, darken(it, 0.3f)) }
            }
        }

        fun berries(main: Long, accent: Long?, variant: String?) {
            val leafColor = accent ?: ArtColor.LEAF
            if (variant == "ball") {
                val twig = darken(leafColor, 0.2f)
                for (a in listOf(-150f, -110f, -70f, -30f, 10f, 50f, 90f, 130f)) {
                    val mid = place(50f, 52f, a)(0f, -14f)
                    val tip = place(50f, 52f, a)(0f, -26f)
                    line(50f, 52f, mid.first, mid.second, twig, 2.2f)
                    line(mid.first, mid.second, tip.first, tip.second, twig, 2f)
                    petal("oblong", 0f, 12f, 3.6f, place(tip.first, tip.second, a - 20f), leafColor, darken(leafColor, 0.25f))
                    petal("oblong", 0f, 12f, 3.6f, place(tip.first, tip.second, a + 20f), leafColor, darken(leafColor, 0.25f))
                }
                listOf(50f to 52f, 44f to 46f, 57f to 47f, 46f to 58f, 56f to 58f).forEach { (x, y) ->
                    circle(x, y, 3.6f, main, darken(main, 0.3f))
                }
                return
            }
            path(null, darken(ArtColor.TRUNK, 0.05f), 2.6f) { moveTo(24f, 90f); cubicTo(36f, 72f, 48f, 54f, 58f, 34f) }
            leafBlade(34f, 76f, -55f, 22f, 6.5f, leafColor)
            leafBlade(44f, 60f, 60f, 22f, 6.5f, leafColor)
            leafBlade(52f, 46f, -40f, 18f, 5.5f, leafColor)
            listOf(0f to 0f, 8f to 3f, -6f to 5f, 3f to 10f, -2f to -7f, 9f to -5f, -9f to -3f, 12f to 9f).forEach { (dx, dy) ->
                val x = 62f + dx
                val y = 30f + dy
                circle(x, y, 4.3f, main, darken(main, 0.3f))
                circle(x - 1.3f, y - 1.5f, 1f, lighten(main, 0.6f))
            }
        }

        fun sprout(main: Long, variant: String?) {
            val outline = darken(main, 0.3f)
            if (variant == "jointed") {
                for ((x, segments, tilt) in listOf(Triple(36f, 4, -14f), Triple(50f, 6, 0f), Triple(64f, 4, 14f))) {
                    for (k in 0 until segments) {
                        val p = place(x, 92f, tilt)(0f, -6f - k * 11f)
                        ellipse(p.first, p.second, 4.6f, 6.4f, tilt, lighten(main, (k % 2) * 0.08f), outline)
                    }
                }
                return
            }
            path(null, darken(main, 0.15f), 2.6f) { moveTo(50f, 94f); cubicTo(50f, 78f, 50f, 66f, 50f, 56f) }
            petal("round", 0f, 26f, 11f, place(50f, 56f, -55f), main, outline)
            petal("round", 0f, 26f, 11f, place(50f, 56f, 55f), lighten(main, 0.08f), outline)
        }
    }
}
