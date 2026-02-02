package com.company.camon.util

import kotlin.math.*

object GeoConverter {
    private const val RAD = PI / 180.0
    private const val EARTH_RADIUS = 6378137.0
    private const val K0 = 0.9996
    private const val LON0 = 127.0 * RAD
    private const val LAT0 = 38.0 * RAD
    private const val X0 = 200000.0
    private const val Y0 = 600000.0

    fun katechToWgs84(mapx: String, mapy: String): Pair<Double, Double>? {
        return try {
            // 💡 1. 기본적인 숫자 변환 및 비정상 데이터 필터링
            val x = mapx.toDoubleOrNull() ?: return null
            val y = mapy.toDoubleOrNull() ?: return null

            // 네이버 KATECH 좌표는 보통 6자리~7자리 숫자입니다. 너무 작으면 처리 안 함
            if (x < 10000.0 || y < 10000.0) return null

            val e = 0.0818191908426
            val e2 = e * e
            val e4 = e2 * e2
            val e6 = e4 * e2
            val ep2 = e2 / (1 - e2)

            val m0 = calculateM(LAT0, e2, e4, e6)
            val m = m0 + (y - Y0) / K0
            val mu = m / (EARTH_RADIUS * (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256))

            val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
            val j1 = 3 * e1 / 2 - 27 * e1 * e1 * e1 / 32
            val j2 = 21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32
            val j3 = 151 * e1 * e1 * e1 / 96
            val j4 = 1097 * e1 * e1 * e1 * e1 / 512

            val fp = mu + j1 * sin(2 * mu) + j2 * sin(4 * mu) + j3 * sin(6 * mu) + j4 * sin(8 * mu)

            val n1 = EARTH_RADIUS / sqrt(1 - e2 * sin(fp) * sin(fp))
            val r1 = EARTH_RADIUS * (1 - e2) / (1 - e2 * sin(fp) * sin(fp)).pow(1.5)
            val d = (x - X0) / (n1 * K0)

            val latRad = fp - (n1 * tan(fp) / r1) * (d * d / 2 - (5 + 3 * tan(fp).pow(2) + 10 * ep2 * cos(fp).pow(2) - 4 * ep2 * ep2 * cos(fp).pow(4) - 9 * ep2 * tan(fp).pow(2) * cos(fp).pow(2)) * d.pow(4) / 24 + (61 + 90 * tan(fp).pow(2) + 298 * ep2 * cos(fp).pow(2) + 45 * tan(fp).pow(4) - 252 * ep2 * ep2 * cos(fp).pow(2) - 3 * ep2 * ep2 * cos(fp).pow(4)) * d.pow(6) / 720)
            val lonRad = LON0 + (d - (1 + 2 * tan(fp).pow(2) + ep2 * cos(fp).pow(2)) * d.pow(3) / 6 + (5 - 2 * ep2 * cos(fp).pow(2) + 28 * tan(fp).pow(2) - 3 * ep2 * ep2 * cos(fp).pow(4) + 8 * ep2 * tan(fp).pow(2) * cos(fp).pow(2) + 24 * tan(fp).pow(4)) * d.pow(5) / 120) / cos(fp)

            val finalLat = latRad / RAD
            val finalLon = lonRad / RAD

            // 💡 2. 최종 결과물이 한국 위경도 범위를 벗어나면 에러로 간주 (필터링)
            if (finalLat.isNaN() || finalLon.isNaN() || finalLat < 30.0 || finalLat > 45.0 || finalLon < 120.0 || finalLon > 135.0) {
                return null
            }

            Pair(finalLat, finalLon)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateM(lat: Double, e2: Double, e4: Double, e6: Double): Double {
        return EARTH_RADIUS * ((1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat - (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * lat) + (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * lat) - (35 * e6 / 3072) * sin(6 * lat))
    }
}