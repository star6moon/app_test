package com.plantdex.app.data.geo

import kotlin.math.floor
import kotlin.math.pow

/** 지도 화면에 보이는 영역 (도 단위). 날짜변경선을 넘으면 [west] > [east] 입니다. */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/**
 * 지오해시: 위도·경도를 문자열 하나로 바꿔, 앞부분이 같으면 가까운 곳이 되게 합니다.
 * 서버(functions/src/geo.ts)가 기록마다 10자리 지오해시를 저장하고, 앱은 지도 영역을 덮는
 * 몇 개의 접두사로 범위 검색해 화면에 보이는 곳의 기록만 불러옵니다.
 */
object GeoHash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(latitude: Double, longitude: Double, precision: Int = 10): String {
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0
        val hash = StringBuilder()
        var bit = 0
        var ch = 0
        var even = true
        while (hash.length < precision) {
            if (even) {
                val mid = (lngMin + lngMax) / 2
                if (longitude >= mid) { ch = (ch shl 1) or 1; lngMin = mid } else { ch = ch shl 1; lngMax = mid }
            } else {
                val mid = (latMin + latMax) / 2
                if (latitude >= mid) { ch = (ch shl 1) or 1; latMin = mid } else { ch = ch shl 1; latMax = mid }
            }
            even = !even
            if (++bit == 5) {
                hash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return hash.toString()
    }

    /** [precision] 자리 지오해시 칸 하나의 (위도 높이, 경도 너비) */
    fun cellSize(precision: Int): Pair<Double, Double> {
        val bits = 5 * precision
        val lngBits = (bits + 1) / 2
        val latBits = bits / 2
        return 180.0 / 2.0.pow(latBits) to 360.0 / 2.0.pow(lngBits)
    }

    /**
     * 영역을 덮는 지오해시 접두사들. 칸이 [maxCells] 개 이하가 되는 가장 긴(정밀한) 자릿수를 고릅니다.
     * 세계 전체처럼 너무 넓으면 빈 문자열 하나(= 전체 검색)를 돌려줍니다.
     */
    fun coveringPrefixes(bounds: GeoBounds, maxCells: Int = 9): List<String> {
        val boxes = if (bounds.west <= bounds.east) {
            listOf(bounds)
        } else {
            listOf(bounds.copy(east = 180.0), bounds.copy(west = -180.0))
        }
        for (precision in 9 downTo 1) {
            val count = boxes.sumOf { cellCount(it, precision) }
            if (count <= maxCells) return boxes.flatMap { cells(it, precision) }.distinct()
        }
        return listOf("")
    }

    private data class IndexRange(val latFrom: Int, val latTo: Int, val lngFrom: Int, val lngTo: Int)

    private fun indices(b: GeoBounds, precision: Int): IndexRange {
        val (h, w) = cellSize(precision)
        val latMax = (180.0 / h).toInt() - 1
        val lngMax = (360.0 / w).toInt() - 1
        fun lat(v: Double) = floor((v.coerceIn(-90.0, 90.0) + 90.0) / h).toInt().coerceIn(0, latMax)
        fun lng(v: Double) = floor((v.coerceIn(-180.0, 180.0) + 180.0) / w).toInt().coerceIn(0, lngMax)
        return IndexRange(lat(b.south), lat(b.north), lng(b.west), lng(b.east))
    }

    private fun cellCount(b: GeoBounds, precision: Int): Long {
        val r = indices(b, precision)
        return (r.latTo - r.latFrom + 1).toLong() * (r.lngTo - r.lngFrom + 1).toLong()
    }

    private fun cells(b: GeoBounds, precision: Int): List<String> {
        val (h, w) = cellSize(precision)
        val r = indices(b, precision)
        return buildList {
            for (i in r.latFrom..r.latTo) {
                for (j in r.lngFrom..r.lngTo) {
                    add(encode(-90.0 + (i + 0.5) * h, -180.0 + (j + 0.5) * w, precision))
                }
            }
        }
    }
}
