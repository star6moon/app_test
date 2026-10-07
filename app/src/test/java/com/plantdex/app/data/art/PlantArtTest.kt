package com.plantdex.app.data.art

import com.plantdex.app.data.catalog.PlantCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlantArtTest {
    private val catalog = File("src/main/assets/catalogs.json").inputStream().use { PlantCatalog.parse(it) }

    private val forms = setOf(
        "flower", "daisy", "cluster", "funnel", "cup", "bell", "iris", "spike", "plume",
        "conifer", "tree", "leaf", "rosette", "berries", "sprout",
    )

    @Test
    fun `every catalog species has a valid drawing`() {
        for (s in catalog.species) {
            val art = assertNotNullReturn(s.id, s.art)
            assertTrue("${s.id}: unknown form ${art.form}", art.form in forms)
            assertTrue("${s.id}: bad color", art.color.matches(Regex("#[0-9A-Fa-f]{6}")))
            art.accent?.let { assertTrue("${s.id}: bad accent", it.matches(Regex("#[0-9A-Fa-f]{6}"))) }
        }
    }

    @Test
    fun `drawings stay inside the canvas`() {
        for (s in catalog.species) {
            val ops = PlantArtRenderer.render(s.art!!)
            assertTrue(s.id, ops.size > 3)
            for (op in ops) {
                val points = when (op) {
                    is CircleOp -> listOf(op.cx to op.cy)
                    is PathOp -> op.commands.mapNotNull {
                        when (it) {
                            is PathCmd.MoveTo -> it.x to it.y
                            is PathCmd.LineTo -> it.x to it.y
                            is PathCmd.QuadTo -> it.x to it.y
                            is PathCmd.CubicTo -> it.x to it.y
                            PathCmd.Close -> null
                        }
                    }
                }
                points.forEach { (x, y) -> assertTrue("${s.id}: ($x, $y)", x in -2f..102f && y in -2f..102f) }
            }
        }
    }

    @Test
    fun `synonyms share the drawing and other plants get one by family`() {
        assertEquals(
            PlantArts.forPlant("Prunus × yedoensis", null, catalog),
            PlantArts.forPlant("Prunus serrulata Lindl.", "Rosaceae", catalog),
        )
        assertEquals("conifer", PlantArts.forPlant("Abies koreana", "Pinaceae", catalog).form)
        assertEquals("daisy", PlantArts.forPlant("Bellis perennis", "Asteraceae", catalog).form)
        val unknown = PlantArts.forPlant("Foo bar", null, catalog)
        assertEquals(unknown, PlantArts.forPlant("Foo bar L.", null, catalog))
        PlantArtRenderer.render(unknown)
    }

    /** 미리보기: build/plant-art-gallery.html 에 모든 그림을 모아 둡니다. */
    @Test
    fun `write gallery`() {
        val html = buildString {
            append("<html><head><meta charset=\"utf-8\"><style>body{font-family:sans-serif;margin:8px;background:#fafafa}")
            append(".g{display:grid;grid-template-columns:repeat(10,1fr);gap:6px}.c{text-align:center;font-size:11px}</style></head><body><div class=\"g\">")
            for (s in catalog.species) {
                append("<div class=\"c\">${ArtSvg.toSvg(PlantArtRenderer.render(s.art!!), 84)}<br>${s.name}</div>")
            }
            append("</div><p>small</p><div style=\"display:flex;flex-wrap:wrap;gap:4px\">")
            for (s in catalog.species) append(ArtSvg.toSvg(PlantArtRenderer.render(s.art!!), 28))
            append("</div></body></html>")
        }
        File("build").mkdirs()
        File("build/plant-art-gallery.html").writeText(html)
    }

    private fun <T : Any> assertNotNullReturn(message: String, value: T?): T {
        assertNotNull(message, value)
        return value!!
    }
}
