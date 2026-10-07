package com.plantdex.app.data.names

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class GbifWikidataNameLocalizerTest {

    private lateinit var server: MockWebServer
    private val routes = mutableMapOf<String, MockResponse>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val key = when {
                    url.encodedPath == "/v1/species/match" -> "match"
                    url.encodedPath.endsWith("/vernacularNames") -> "gbif:" + url.pathSegments[2]
                    url.queryParameter("action") == "query" -> "search:" + url.queryParameter("srsearch")
                    url.queryParameter("action") == "wbgetentities" ->
                        "entity:" + url.queryParameter("ids") + ":" + url.queryParameter("languages")
                    else -> "unknown"
                }
                return routes[key] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun localizer() = GbifWikidataNameLocalizer(OkHttpClient(), server.url("/"), server.url("/"))

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun `uses preferred gbif vernacular name in user language`() = runTest {
        routes["gbif:5394"] = json(
            """
            {"results":[
              {"vernacularName":"Dandelion","language":"eng","preferred":true},
              {"vernacularName":"민들레","language":"kor"},
              {"vernacularName":"서양민들레","language":"kor","preferred":true}
            ]}
            """,
        )

        assertEquals("서양민들레", localizer().localName("Taraxacum officinale", "5394", Locale.KOREAN))
    }

    @Test
    fun `falls back to wikidata label`() = runTest {
        routes["gbif:5394"] = json("""{"results":[{"vernacularName":"Dandelion","language":"eng"}]}""")
        routes["search:haswbstatement:P846=5394"] = json("""{"query":{"search":[{"title":"Q131219"}]}}""")
        routes["entity:Q131219:ko"] = json(
            """{"entities":{"Q131219":{"labels":{"ko":{"language":"ko","value":"서양민들레"}}}}}""",
        )

        assertEquals("서양민들레", localizer().localName("Taraxacum officinale", "5394", Locale.KOREAN))
    }

    @Test
    fun `ignores labels that are just the scientific name`() = runTest {
        routes["search:haswbstatement:P846=5394"] = json("""{"query":{"search":[{"title":"Q1"}]}}""")
        routes["entity:Q1:ko"] = json(
            """{"entities":{"Q1":{"labels":{"ko":{"language":"ko","value":"Taraxacum officinale"}}}}}""",
        )

        assertNull(localizer().localName("Taraxacum officinale", "5394", Locale.KOREAN))
    }

    @Test
    fun `matches gbif key by scientific name when id is missing`() = runTest {
        routes["match"] = json("""{"usageKey":5394,"matchType":"EXACT"}""")
        routes["gbif:5394"] = json("""{"results":[{"vernacularName":"서양민들레","language":"kor"}]}""")

        assertEquals("서양민들레", localizer().localName("Taraxacum officinale", null, Locale.KOREAN))
    }

    @Test
    fun `caches results`() = runTest {
        routes["gbif:5394"] = json("""{"results":[{"vernacularName":"서양민들레","language":"kor"}]}""")
        val localizer = localizer()

        localizer.localName("Taraxacum officinale", "5394", Locale.KOREAN)
        val before = server.requestCount
        assertEquals("서양민들레", localizer.localName("Taraxacum officinale", "5394", Locale.KOREAN))
        assertEquals(before, server.requestCount)
    }

    @Test
    fun `server errors return null and are not cached`() = runTest {
        routes["gbif:5394"] = MockResponse().setResponseCode(503)
        routes["search:haswbstatement:P846=5394"] = MockResponse().setResponseCode(503)
        val localizer = localizer()

        assertNull(localizer.localName("Taraxacum officinale", "5394", Locale.KOREAN))

        routes["gbif:5394"] = json("""{"results":[{"vernacularName":"서양민들레","language":"kor"}]}""")
        assertEquals("서양민들레", localizer.localName("Taraxacum officinale", "5394", Locale.KOREAN))
    }
}
