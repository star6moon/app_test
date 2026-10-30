package com.plantdex.app.data.info

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

class GbifFactsClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val body = when {
                    url.encodedPath == "/v1/species/match" -> """{"usageKey":5394,"matchType":"EXACT"}"""
                    url.encodedPath == "/v1/occurrence/count" && url.queryParameter("country") == "KR" -> "1234"
                    url.encodedPath == "/v1/occurrence/count" -> "987654"
                    url.encodedPath == "/v1/occurrence/search" ->
                        """{"facets":[{"field":"MONTH","counts":[{"name":"4","count":50},{"name":"5","count":80},{"name":"6","count":20},{"name":"13","count":9}]}]}"""
                    url.encodedPath.endsWith("/vernacularNames") ->
                        """{"results":[
                          {"vernacularName":"Common Dandelion","language":"eng"},
                          {"vernacularName":"Dandelion","language":"eng","preferred":true},
                          {"vernacularName":"セイヨウタンポポ","language":"jpn"},
                          {"vernacularName":"서양민들레","language":"kor"}
                        ]}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `collects counts, months and foreign names`() = runTest {
        val facts = GbifFactsClient(OkHttpClient(), server.url("/")).facts("Taraxacum officinale")!!
        assertEquals(987654L, facts.worldOccurrences)
        assertEquals(1234L, facts.koreaOccurrences)
        assertEquals(listOf(5, 4, 6), facts.topMonths)
        assertEquals(listOf(ForeignName("English", "Dandelion"), ForeignName("日本語", "セイヨウタンポポ")), facts.foreignNames)
    }

    @Test
    fun `unknown species gives null`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(200).setBody("""{"matchType":"NONE"}""")
        }
        assertNull(GbifFactsClient(OkHttpClient(), server.url("/")).facts("Nothing here"))
    }
}
