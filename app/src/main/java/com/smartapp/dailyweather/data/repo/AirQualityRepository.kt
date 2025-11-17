package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.BuildConfig
import com.smartapp.dailyweather.data.remote.AirKoreaApi
import com.smartapp.dailyweather.data.remote.RetrofitClient

class AirQualityRepository(
    private val api: AirKoreaApi = RetrofitClient.airKoreaApi,
    private val serviceKey: String =
        "51a18c5b71f3d20ccce3d995000f326d16ea701d420c672724dc908bda60cced"
) {

    /**
     * 시도별 실시간 미세먼지 등급을 조회해서
     * good / normal / bad / hazard 중 하나로 반환
     *
     * - pm25Grade1h(1시간 등급) 우선 사용
     * - 없으면 pm25Grade, 그것도 없으면 khaiGrade 사용
     */
    suspend fun getAirQualityCodeForRegion(sidoName: String): String {
        val res = api.getCityRealtime(
            serviceKey = serviceKey,
            sidoName = sidoName,
            returnType = "json",
            numOfRows = 100,
            pageNo = 1,
            ver = "1.5"
        )

        val items = res.response?.body?.items.orEmpty()
        if (items.isEmpty()) {
            Log.w("AirRepo", "no air items for sido=$sidoName, fallback=normal")
            return "normal"
        }

        // 가장 최근 시각 한 건 선택
        val latest = items.maxByOrNull { it.dataTime ?: "" }!!

        val gradeStr = latest.pm25Grade1h?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
            ?: latest.pm25Grade?.trim()
            ?: latest.khaiGrade?.trim()

        Log.d(
            "AirRepo",
            "sido=$sidoName, dataTime=${latest.dataTime}, gradeRaw=$gradeStr"
        )

        // AirKorea grade → 우리 코드
        return when (gradeStr) {
            "1" -> "good"    // 좋음
            "2" -> "normal"  // 보통
            "3" -> "bad"     // 나쁨 → 기본 마스크
            "4" -> "hazard"  // 매우나쁨 → KF94
            else -> {
                Log.w("AirRepo", "unknown grade=$gradeStr, fallback=normal")
                "normal"
            }
        }
    }
}