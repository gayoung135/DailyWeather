package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.data.remote.AirKoreaApi
import com.smartapp.dailyweather.data.remote.RetrofitClient

/**
 * UI에서 쉽게 쓰도록 가공한 모델
 */
data class AirQualityUi(
    val code: String,           // good / normal / bad / hazard
    val airQualityLabel: String // "좋음", "보통", "나쁨", "매우 나쁨"
)

/**
 * 한 측정소의 PM2.5 정보 (1시간/24시간)
 */
data class Pm25Info(
    val stationName: String?,
    val code1h: String,   // pm25Grade1h 기준
    val code24h: String,  // pm25Grade(24시간 평균) 기준
    val value1h: Int?,
    val value24h: Int?
)

class AirQualityRepository(
    private val api: AirKoreaApi = RetrofitClient.airKoreaApi,
    private val serviceKey: String =
        "51a18c5b71f3d20ccce3d995000f326d16ea701d420c672724dc908bda60cced"
) {

    /**
     * 시도(sidoName) 단위 실시간 대기질 목록에서
     * - stationNameHint(예: "강남구")와 가장 잘 매칭되는 측정소를 고르고
     * - 그 측정소의 PM2.5 1시간/24시간 등급을 모두 반환
     *
     * stationNameHint가 null이면 시도 내에서 가장 최신 dataTime 기준으로만 선택.
     *
     * - 24시간 기준: pm25Grade
     * - 1시간 기준: pm25Grade1h
     */
    suspend fun getPm25ForRegion(
        sidoName: String,
        stationNameHint: String? = null
    ): Pm25Info {
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
            return Pm25Info(
                stationName = null,
                code1h = "normal",
                code24h = "normal",
                value1h = null,
                value24h = null
            )
        }

        // stationNameHint(시/군/구명)와 가장 잘 맞는 측정소만 우선 후보로
        val filtered = stationNameHint?.let { hint ->
            val normHint = normalizeKoreanName(hint)

            val candidates = items.filter { item ->
                val name = normalizeKoreanName(item.stationName ?: "")
                name.contains(normHint)
            }

            if (candidates.isNotEmpty()) {
                Log.d(
                    "AirRepo",
                    "sido=$sidoName, hint=$stationNameHint → matched stations=${candidates.map { it.stationName }}"
                )
                candidates
            } else {
                // 매칭되는 측정소가 없으면 전체 시도 목록에서 선택
                Log.w(
                    "AirRepo",
                    "sido=$sidoName, hint=$stationNameHint 에 맞는 station 없음 → 전체에서 선택"
                )
                items
            }
        } ?: items

        // 가장 최신 시간 데이터 선택
        val latest = filtered.maxByOrNull { it.dataTime ?: "" }
            ?: items.maxByOrNull { it.dataTime ?: "" }!!

        // 1시간 등급/농도
        val grade1hRaw = latest.pm25Grade1h?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }

        val value1h = latest.pm25Value?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
            ?.toIntOrNull()

        // 24시간 등급/농도 (가능하면 pm25Grade / pm25Value24 사용)
        val grade24Raw = latest.pm25Grade?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
            ?: grade1hRaw // 24시간 정보가 없으면 1시간 기준으로 fallback

        val value24h = latest.pm25Value24?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
            ?.toIntOrNull()
            ?: value1h

        val code1h = mapGradeToCode(grade1hRaw)
        val code24h = mapGradeToCode(grade24Raw)

        Log.d(
            "AirRepo",
            "sido=$sidoName, station=${latest.stationName}, dataTime=${latest.dataTime}, " +
                    "grade1h=$grade1hRaw(code=$code1h), grade24=$grade24Raw(code=$code24h), " +
                    "pm25(1h)=$value1h, pm25(24h)=$value24h"
        )

        return Pm25Info(
            stationName = latest.stationName,
            code1h = code1h,
            code24h = code24h,
            value1h = value1h,
            value24h = value24h
        )
    }

    /**
     * 코디 추천 화면 / 필수품 추천용:
     * - PM2.5 **24시간 평균 기준** 등급을 사용
     */
    suspend fun getAirForOutfit(
        sidoName: String,
        stationNameHint: String?
    ): AirQualityUi {
        val pm = getPm25ForRegion(sidoName, stationNameHint)
        val label = toLabel(pm.code24h)

        return AirQualityUi(
            code = pm.code24h,
            airQualityLabel = label
        )
    }

    /**
     * 상세날씨 상단 "현재 미세먼지"용:
     * - PM2.5 **1시간 기준** 등급을 사용
     */
    suspend fun getAirForCurrent(
        sidoName: String,
        stationNameHint: String?
    ): AirQualityUi {
        val pm = getPm25ForRegion(sidoName, stationNameHint)
        val label = toLabel(pm.code1h)

        return AirQualityUi(
            code = pm.code1h,
            airQualityLabel = label
        )
    }

    // ─────────────────────────────
    // 기존 함수들(호환용) - 내부적으로 새 로직 사용
    // ─────────────────────────────

    /**
     * (호환용) 시도별 코드만 필요할 때 사용
     * - 내부적으로 24시간 평균 기준 코드 사용
     */
    suspend fun getAirQualityCodeForRegion(sidoName: String): String {
        return getPm25ForRegion(sidoName).code24h
    }

    /**
     * (호환용) 시/도만 알고 있을 때 UI에서 바로 쓰기
     * - 24시간 평균 기준
     */
    suspend fun getAirForSido(sidoName: String): AirQualityUi {
        return getAirForOutfit(sidoName, stationNameHint = null)
    }

    // ─────────────────────────────
    // 헬퍼
    // ─────────────────────────────

    private fun mapGradeToCode(gradeRaw: String?): String {
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

    private fun toLabel(code: String): String =
        when (code) {
            "good" -> "좋음"
            "normal" -> "보통"
            "bad" -> "나쁨"
            "hazard" -> "매우 나쁨"
            else -> "정보 없음"
        }

    /**
     * "강남구", "강남구청", "강남" 등 이름 매칭을 느슨하게 하기 위한 정규화
     * - 공백 제거
     * - "시", "군", "구" 접미어 제거
     */
    private fun normalizeKoreanName(raw: String): String {
        val trimmed = raw.trim().replace(" ", "")
        return trimmed
            .removeSuffix("시")
            .removeSuffix("군")
            .removeSuffix("구")
    }
}