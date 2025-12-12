package com.smartapp.dailyweather.core

import com.smartapp.dailyweather.data.model.LatLng
import kotlin.math.*

/**
 * 기상청 격자 <-> 위경도 변환 유틸
 * (공식 예제에 나오는 LCC DFS 좌표계)
 */
object KmaGridConverter {

    private const val RE = 6371.00877    // 지구 반경(km)
    private const val GRID = 5.0         // 격자 간격(km)
    private const val SLAT1 = 30.0       // 표준위도1(degree)
    private const val SLAT2 = 60.0       // 표준위도2(degree)
    private const val OLON = 126.0       // 기준점 경도(degree)
    private const val OLAT = 38.0        // 기준점 위도(degree)
    private const val XO = 43.0          // 기준점 X좌표(GRID)
    private const val YO = 136.0         // 기준점 Y좌표(GRID)

    private const val DEGRAD = Math.PI / 180.0
    private const val RADDEG = 180.0 / Math.PI

    /**
     * 기상청 격자 → 위경도
     */
    fun toLatLng(x: Int, y: Int): LatLng {
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD

        var sn = tan(Math.PI * 0.25 + slat2 * 0.5) / tan(Math.PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)

        var sf = tan(Math.PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn

        var ro = tan(Math.PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        val xn = x - XO
        val yn = ro - y + YO

        val ra = sqrt(xn * xn + yn * yn)
        var alat = (re * sf / ra).pow(1.0 / sn)
        alat = 2.0 * atan(alat) - Math.PI * 0.5

        var theta = atan2(xn, yn)
        val alon = theta / sn + olon

        val lat = alat * RADDEG
        val lon = alon * RADDEG

        return LatLng(latitude = lat, longitude = lon)
    }
}