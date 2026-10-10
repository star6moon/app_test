package com.plantdex.app.data.plantnet

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class PlantNetIdentifierTest {

    private lateinit var server: MockWebServer
    private lateinit var image: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        image = File.createTempFile("plant", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        image.delete()
    }

    private fun identifier(apiKey: String = "test-key", language: String = "ko") =
        PlantNetIdentifier(apiKey, OkHttpClient(), server.url("/"), language = { language })

    @Test
    fun `maps successful response to candidates sorted by score`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(SAMPLE_RESPONSE))

        val candidates = identifier().identify(image)

        assertEquals(2, candidates.size)
        val best = candidates[0]
        assertEquals("Taraxacum officinale", best.scientificName)
        assertEquals("Taraxacum officinale F.H.Wigg.", best.scientificNameWithAuthor)
        assertEquals("서양민들레", best.displayName)
        assertEquals("Taraxacum", best.genus)
        assertEquals("Asteraceae", best.family)
        assertEquals(0.91, best.score, 1e-9)
        assertEquals("5394", best.gbifId)
        assertEquals("ko", best.namesLanguage)

        // 일반명이 없으면 학명을 대표 이름으로 사용
        assertEquals("Hypochaeris radicata", candidates[1].displayName)
        assertNull(candidates[1].gbifId)
    }

    @Test
    fun `sends api key, language, image and organs`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(SAMPLE_RESPONSE))

        identifier().identify(image)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/identify/all", request.requestUrl!!.encodedPath)
        assertEquals("test-key", request.requestUrl!!.queryParameter("api-key"))
        assertEquals("ko", request.requestUrl!!.queryParameter("lang"))
        assertEquals("true", request.requestUrl!!.queryParameter("no-reject"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"images\""))
        assertTrue(body.contains("name=\"organs\""))
        assertTrue(body.contains("auto"))
    }

    @Test
    fun `species not found returns empty list`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"statusCode":404,"error":"Not Found","message":"Species not found"}"""),
        )

        assertTrue(identifier().identify(image).isEmpty())
    }

    @Test
    fun `unsupported language retries in english`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"statusCode":400,"error":"Bad Request","message":"\"lang\" must be one of [en, fr, es]"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(SAMPLE_RESPONSE))

        val candidates = identifier(language = "xx").identify(image)

        assertEquals("xx", server.takeRequest().requestUrl!!.queryParameter("lang"))
        assertEquals("en", server.takeRequest().requestUrl!!.queryParameter("lang"))
        assertEquals("en", candidates[0].namesLanguage)
    }

    @Test
    fun `retries without no-reject when the server does not accept it`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"statusCode":400,"error":"Bad Request","message":"\"no-reject\" is not allowed"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(SAMPLE_RESPONSE))

        val candidates = identifier().identify(image)

        assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("no-reject"))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("no-reject"))
        assertEquals(2, candidates.size)
    }

    @Test
    fun `other bad requests are not retried`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"statusCode":400,"error":"Bad Request","message":"Unsupported file type"}"""),
        )

        try {
            identifier().identify(image)
            fail("예외가 발생해야 합니다")
        } catch (e: PlantIdentificationException) {
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `missing api key fails without request`() = runTest {
        try {
            identifier(apiKey = "").identify(image)
            fail("예외가 발생해야 합니다")
        } catch (e: PlantIdentificationException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `server error becomes exception with message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"statusCode":400,"error":"Bad Request","message":"Unsupported file type"}"""),
        )

        try {
            identifier().identify(image)
            fail("예외가 발생해야 합니다")
        } catch (e: PlantIdentificationException) {
            assertTrue(e.message!!.contains("Unsupported file type"))
        }
    }

    @Test
    fun `malformed success response becomes identification exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"results":[{"species":{}}]}"""))

        try {
            identifier().identify(image)
            fail("예외가 발생해야 합니다")
        } catch (e: PlantIdentificationException) {
            assertTrue(e.message!!.contains("해석"))
        }
    }

    private companion object {
        val SAMPLE_RESPONSE = """
            {
              "query": {"project": "all", "images": ["abc"], "organs": ["auto"]},
              "language": "ko",
              "preferedReferential": "k-world-flora",
              "bestMatch": "Taraxacum officinale F.H.Wigg.",
              "results": [
                {
                  "score": 0.05,
                  "species": {
                    "scientificNameWithoutAuthor": "Hypochaeris radicata",
                    "scientificNameAuthorship": "L.",
                    "genus": {"scientificNameWithoutAuthor": "Hypochaeris", "scientificNameAuthorship": "", "scientificName": "Hypochaeris"},
                    "family": {"scientificNameWithoutAuthor": "Asteraceae", "scientificNameAuthorship": "", "scientificName": "Asteraceae"},
                    "commonNames": [],
                    "scientificName": "Hypochaeris radicata L."
                  }
                },
                {
                  "score": 0.91,
                  "species": {
                    "scientificNameWithoutAuthor": "Taraxacum officinale",
                    "scientificNameAuthorship": "F.H.Wigg.",
                    "genus": {"scientificNameWithoutAuthor": "Taraxacum", "scientificNameAuthorship": "", "scientificName": "Taraxacum"},
                    "family": {"scientificNameWithoutAuthor": "Asteraceae", "scientificNameAuthorship": "", "scientificName": "Asteraceae"},
                    "commonNames": ["서양민들레", "Common dandelion"],
                    "scientificName": "Taraxacum officinale F.H.Wigg."
                  },
                  "gbif": {"id": "5394"},
                  "powo": {"id": "208802-2"}
                }
              ],
              "version": "2025-01-17 (7.3)",
              "remainingIdentificationRequests": 497
            }
        """.trimIndent()
    }
}
