package com.plantdex.app.data.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoHashTest {

    @Test
    fun encodesKnownValue() {
        assertEquals("u4pruydqqvj", GeoHash.encode(57.64911, 10.40744, 11))
        assertEquals("wydm9q", GeoHash.encode(37.5665, 126.9780, 6))
    }

    @Test
    fun coverContainsEveryPointInBounds() {
        val bounds = GeoBounds(37.50, 126.90, 37.62, 127.08) // 서울 도심
        val prefixes = GeoHash.coveringPrefixes(bounds)
        assertTrue(prefixes.size in 1..9)
        for (lat in listOf(37.50, 37.55, 37.62)) {
            for (lng in listOf(126.90, 126.99, 127.08)) {
                val hash = GeoHash.encode(lat, lng)
                assertTrue("$lat,$lng → $hash not in $prefixes", prefixes.any { hash.startsWith(it) })
            }
        }
    }

    @Test
    fun smallAreaUsesLongPrefixes() {
        val bounds = GeoBounds(37.5660, 126.9775, 37.5670, 126.9785)
        assertTrue(GeoHash.coveringPrefixes(bounds).all { it.length >= 6 })
    }

    @Test
    fun antimeridianIsSplit() {
        val prefixes = GeoHash.coveringPrefixes(GeoBounds(-10.0, 170.0, 10.0, -170.0))
        assertTrue(prefixes.any { GeoHash.encode(0.0, 175.0).startsWith(it) })
        assertTrue(prefixes.any { GeoHash.encode(0.0, -175.0).startsWith(it) })
    }

    @Test
    fun wholeWorldFallsBackToSingleQuery() {
        assertEquals(listOf(""), GeoHash.coveringPrefixes(GeoBounds(-85.0, -180.0, 85.0, 180.0)))
    }
}
