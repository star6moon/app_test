package com.plantdex.app.data.taxonomy

import com.plantdex.app.data.names.PlantNameLocalizer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TaxonomyRepositoryTest {

    private val chrysanthemum = """
        {"usageKey":3117424,"matchType":"EXACT",
         "kingdom":"Plantae","phylum":"Tracheophyta","order":"Asterales","family":"Asteraceae",
         "genus":"Chrysanthemum","species":"Chrysanthemum morifolium","class":"Magnoliopsida",
         "kingdomKey":6,"phylumKey":7707728,"classKey":220,"orderKey":414,"familyKey":3065,
         "genusKey":3117399,"speciesKey":3117424}
    """

    private class FakeClient(private val taxa: List<Taxon>?) : TaxonomyClient {
        override suspend fun classify(scientificName: String, gbifId: String?) = taxa
    }

    private class FakeLocalizer(private val names: Map<String, String>) : PlantNameLocalizer {
        val asked = mutableListOf<String>()
        override suspend fun localName(scientificName: String, gbifId: String?, locale: Locale): String? {
            asked += scientificName
            return names[scientificName]
        }
    }

    @Test
    fun `reads every rank from gbif`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(chrysanthemum))
        server.start()
        val taxa = GbifTaxonomyClient(OkHttpClient(), server.url("/")).classify("Chrysanthemum morifolium", null)!!
        val request = server.takeRequest()
        server.shutdown()

        assertEquals("/v1/species/match", request.requestUrl!!.encodedPath)
        assertEquals(TaxonRank.entries, taxa.map { it.rank })
        assertEquals("Magnoliopsida", taxa[2].scientificName)
        assertEquals("3065", taxa[4].gbifKey)
    }

    @Test
    fun `uses the gbif id when there is one`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(chrysanthemum))
        server.start()
        GbifTaxonomyClient(OkHttpClient(), server.url("/")).classify("Chrysanthemum morifolium", "3117424")
        val request = server.takeRequest()
        server.shutdown()

        assertEquals("/v1/species/3117424", request.requestUrl!!.encodedPath)
    }

    @Test
    fun `no match gives null`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"matchType":"NONE"}"""))
        server.start()
        val taxa = GbifTaxonomyClient(OkHttpClient(), server.url("/")).classify("Nothing here", null)
        server.shutdown()

        assertNull(taxa)
    }

    @Test
    fun `shows stored ranks first then the full lineage in korean`() = runTest {
        val taxa = listOf(
            Taxon(TaxonRank.Kingdom, "Plantae", "6"),
            Taxon(TaxonRank.Phylum, "Tracheophyta", "7707728"),
            Taxon(TaxonRank.Class, "Magnoliopsida", "220"),
            Taxon(TaxonRank.Order, "Asterales", "414"),
            Taxon(TaxonRank.Family, "Asteraceae", "3065"),
            Taxon(TaxonRank.Genus, "Chrysanthemum", "3117399"),
            Taxon(TaxonRank.Species, "Chrysanthemum morifolium", "3117424"),
        )
        val localizer = FakeLocalizer(mapOf("Chrysanthemum" to "국화"))
        val steps = TaxonomyRepository(FakeClient(taxa), localizer)
            .lineage("Chrysanthemum morifolium", null, "Asteraceae", null)
            .toList()

        assertEquals(listOf("국화과", "크리산테뭄속"), steps[0].map { it.korean })
        assertEquals(
            listOf("식물계", "관다발식물문", "목련강", "국화목", "국화과", "국화속"),
            steps.last().map { it.korean },
        )
        assertEquals("쌍떡잎식물", steps.last()[2].hint)
        // 앱에 이름이 있는 단계는 다시 찾지 않습니다.
        assertEquals(listOf("Chrysanthemum"), localizer.asked)
    }

    @Test
    fun `keeps the stored ranks when gbif cannot be reached`() = runTest {
        val steps = TaxonomyRepository(FakeClient(null), FakeLocalizer(emptyMap()))
            .lineage("Rosa rugosa", null, "Rosaceae", "Rosa")
            .toList()

        assertEquals(1, steps.size)
        assertEquals(listOf("장미과", "로사속"), steps[0].map { it.korean })
        assertEquals(true, steps[0][1].isPronunciation)
    }

    @Test
    fun `pronounces unknown ranks with a suffix`() {
        assertEquals("피크람니아목", TaxonKoreanNames.pronunciation(TaxonRank.Order, "Picramniales"))
        assertEquals("민들레속", TaxonKoreanNames.withSuffix(TaxonRank.Genus, "민들레"))
        assertEquals("장미과", TaxonKoreanNames.withSuffix(TaxonRank.Family, "장미과 (식물)"))
        assertNull(TaxonKoreanNames.pronunciation(TaxonRank.Class, "Magnoliopsida"))
    }
}
