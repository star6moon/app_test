package com.plantdex.app.data.names

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameScriptTest {
    @Test
    fun `korean requires hangul`() {
        assertTrue(NameScript.matches("서양민들레", "ko"))
        assertFalse(NameScript.matches("Common dandelion", "ko"))
    }

    @Test
    fun `japanese accepts kana and kanji`() {
        assertTrue(NameScript.matches("セイヨウタンポポ", "ja"))
        assertTrue(NameScript.matches("西洋蒲公英", "ja"))
        assertFalse(NameScript.matches("Dandelion", "ja"))
    }

    @Test
    fun `latin script languages accept any name`() {
        assertTrue(NameScript.matches("Pissenlit", "fr"))
        assertTrue(NameScript.matches("Common dandelion", "en"))
    }
}
