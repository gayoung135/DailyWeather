package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.BuildConfig
import com.smartapp.dailyweather.data.remote.AirKoreaApi
import com.smartapp.dailyweather.data.remote.RetrofitClient

/**
 * UI에서 쉽게 쓰도록 가공한 모델
 */
data class AirQualityUi(
    val code: String,           // good / normal / bad / hazard
    val airQualityLabel: String // "좋음", "보통", "나쁨", "매우나쁨"
)


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
            Log.w("AirRepo", "no air items for sido=$sidoName → fallback=normal")
            return "normal"
        }

        // 🔥 가장 최신 시간 데이터 선택
        val latest = items.maxByOrNull { it.dataTime ?: "" }!!

        // 🔥 등급 우선순위: pm25Grade1h > pm25Grade > khaiGrade
        val gradeRaw = latest.pm25Grade1h?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
            ?: latest.pm25Grade?.trim()
            ?: latest.khaiGrade?.trim()

        Log.d("AirRepo", "sido=$sidoName, dataTime=${latest.dataTime}, raw=$gradeRaw")

        return when (gradeRaw) {
            "1" -> "good"
            "2" -> "normal"
            "3" -> "bad"
            "4" -> "hazard"
            else -> {
                Log.w("AirRepo", "unknown grade=$gradeRaw → fallback=normal")
                "normal"
            }
        }
    }

    /**
     *  UI에서 바로 쓰려면 이 함수 사용
     * AirQualityUi(code, label) 형태로 즉시 사용 가능
     */
    suspend fun getAirForSido(sidoName: String): AirQualityUi {
        val code = getAirQualityCodeForRegion(sidoName)

        val label = when (code) {
            "good" -> "좋음"
            "normal" -> "보통"
            "bad" -> "나쁨"
            "hazard" -> "매우 나쁨"
            else -> "정보 없음"
        }

        return AirQualityUi(code, label)
    }
}