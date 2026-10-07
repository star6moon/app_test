package com.plantdex.app.data.plantnet

import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.names.PlantNameLocalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Locale

class LocalizingPlantIdentifierTest {

    private fun candidate(names: List<String>, language: String?) = PlantCandidate(
        scientificName = "Taraxacum officinale",
        scientificNameWithAuthor = "Taraxacum officinale F.H.Wigg.",
        commonNames = names,
        genus = "Taraxacum",
        family = "Asteraceae",
        score = 0.9,
        gbifId = "5394",
        namesLanguage = language,
    )

    private class FakeLocalizer(private val name: String?) : PlantNameLocalizer {
        var calls = 0
        override suspend fun localName(scientificName: String, gbifId: String?, locale: Locale): String? {
            calls++
            return name
        }
    }

    private fun identify(result: PlantCandidate, localizer: FakeLocalizer, locale: Locale = Locale.KOREAN) =
        LocalizingPlantIdentifier(
            delegate = object : PlantIdentifier {
                override suspend fun identify(image: File) = listOf(result)
            },
            localizer = localizer,
            locale = { locale },
        )

    @Test
    fun `keeps plantnet name already in user language`() = runTest {
        val localizer = FakeLocalizer("다른이름")
        val result = identify(candidate(listOf("Dandelion", "서양민들레"), "ko"), localizer).identify(File("x"))

        assertEquals("서양민들레", result[0].displayName)
        assertEquals("ko", result[0].namesLanguage)
        assertEquals(0, localizer.calls)
    }

    @Test
    fun `looks up name when plantnet returned another language`() = runTest {
        val result = identify(candidate(listOf("Dandelion"), "en"), FakeLocalizer("서양민들레")).identify(File("x"))

        assertEquals(listOf("서양민들레", "Dandelion"), result[0].commonNames)
        assertEquals("ko", result[0].namesLanguage)
    }

    @Test
    fun `keeps english name when nothing is found`() = runTest {
        val result = identify(candidate(listOf("Dandelion"), "en"), FakeLocalizer(null)).identify(File("x"))

        assertEquals("Dandelion", result[0].displayName)
        assertEquals("en", result[0].namesLanguage)
    }

    @Test
    fun `name language is unknown when plantnet silently answered in another script`() = runTest {
        val result = identify(candidate(listOf("Dandelion"), "ko"), FakeLocalizer(null)).identify(File("x"))

        assertEquals("Dandelion", result[0].displayName)
        assertNull(result[0].namesLanguage)
    }
}
