package com.plantdex.app.data.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelsTest {
    @Test
    fun `levels rise with species and report progress`() {
        assertEquals(1, Levels.of(0).level.number)
        assertEquals(1, Levels.of(2).level.number)
        val three = Levels.of(3)
        assertEquals(2, three.level.number)
        assertEquals(4, three.remaining)
        assertEquals(0f, three.fraction)
        val five = Levels.of(5)
        assertEquals(2, five.level.number)
        assertEquals(0.5f, five.fraction)
        assertEquals(10, Levels.of(130).level.number)
    }

    @Test
    fun `last level has no next`() {
        val top = Levels.of(1000)
        assertEquals(Levels.ALL.last(), top.level)
        assertNull(top.next)
        assertEquals(1f, top.fraction)
        assertEquals(0, top.remaining)
        assertEquals(1, Levels.of(-5).level.number)
    }

    @Test
    fun `thresholds are strictly increasing`() {
        assertEquals(Levels.ALL.map { it.requiredSpecies }, Levels.ALL.map { it.requiredSpecies }.distinct().sorted())
    }
}
