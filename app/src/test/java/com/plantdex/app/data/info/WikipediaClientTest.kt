package com.plantdex.app.data.info

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikipediaClientTest {

    private val summaryJson = """
        {"type":"standard","title":"능소화","extract":"능소화는 능소화과의 덩굴식물이다.",
         "content_urls":{"mobile":{"page":"https://ko.m.wikipedia.org/wiki/%EB%8A%A5%EC%86%8C%ED%99%94"}},
         "thumbnail":{"source":"https://upload.wikimedia.org/x.jpg"}}
    """.trimIndent()

    @Test
    fun parseStandardPage() {
        val s = WikipediaClient.parse(summaryJson, "ko")!!
        assertEquals("능소화", s.title)
        assertEquals("https://upload.wikimedia.org/x.jpg", s.imageUrl)
        assertEquals("ko", s.language)
    }

    @Test
    fun disambiguationIsIgnored() {
        assertNull(WikipediaClient.parse("""{"type":"disambiguation","title":"x","extract":"y"}""", "ko"))
    }

    @Test
    fun fallsBackToScientificNameThenEnglish() = runBlocking {
        val server = MockWebServer()
        val paths = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path.orEmpty()
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val port = server.port
        val client = WikipediaClient(OkHttpClient(), baseHost = { "localhost:$port" }, scheme = "http")
        // 테스트 서버는 언어(호스트)를 구분하지 못하므로 요청 순서만 확인합니다: 이름 → 학명 → 영어 학명
        assertNull(client.summary(listOf("능소화"), "Campsis grandiflora", "ko"))
        assertEquals(3, paths.size)
        assert(paths[0].endsWith("summary/%EB%8A%A5%EC%86%8C%ED%99%94")) { paths[0] }
        assert(paths[1].endsWith("summary/Campsis_grandiflora"))
        server.shutdown()
    }
}
