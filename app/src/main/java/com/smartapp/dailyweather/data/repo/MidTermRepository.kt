package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MidDaily(
    val date: String,        // yyyyMMdd
    val minTemp: Int?,       // 최저기온
    val maxTemp: Int?,       // 최고기온
    val rainProb: Int?,      // 강수확률(%)
    val wf: String?          // 날씨예보(맑음/구름많음 등)
)

class MidTermRepository(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {
    private val api = RetrofitClient.weatherApi

    /**
     * 중기예보는 regId가 필요함 → 시/도 단위로 매핑
     */
    fun mapSidoToRegId(sido: String): Pair<String, String> {
        val upper = sido.replace(" ", "").trim()

        return when {
            upper.contains("서울") || upper.contains("인천") || upper.contains("경기") ->
                "11B10101" to "11B00000"

            upper.contains("강원") && upper.contains("영서") -> "11D10101" to "11D10000"
            upper.contains("강원") && upper.contains("영동") -> "11D20501" to "11D20000"

            upper.contains("충북") -> "11C10301" to "11C10000"
            upper.contains("충남") || upper.contains("대전") || upper.contains("세종") ->
                "11C20401" to "11C20000"

            upper.contains("전북") -> "11F10201" to "11F10000"
            upper.contains("광주") || upper.contains("전남") -> "11F20401" to "11F20000"

            upper.contains("대구") || upper.contains("경북") -> "11H10701" to "11H10000"
            upper.contains("부산") || upper.contains("울산") || upper.contains("경남") ->
                "11H20201" to "11H20000"

            upper.contains("제주") -> "11G00201" to "11G00000"
            else -> "11B10101" to "11B00000"
        }
    }

    /**
     * KMA 중기예보 기준 tmFc 계산
     * - 00~05시 59분  → 전날 18시
     * - 06~17시 59분 → 오늘 06시
     * - 18시~23시 59분 → 오늘 18시
     */
    private fun computeMidBaseTime(nowKst: ZonedDateTime): ZonedDateTime {
        val kst = nowKst.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
        return when {
            kst.hour < 6 -> kst.minusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)
            kst.hour < 18 -> kst.withHour(6).withMinute(0).withSecond(0).withNano(0)
            else -> kst.withHour(18).withMinute(0).withSecond(0).withNano(0)
        }
    }

    /**
     * 중기예보 전체 로드 (3~10일차)
     * UI에서는 필요에 따라 앞의 며칠만 사용해도 됨.
     */
    suspend fun getMidTermForecast(
        sidoName: String,
        nowKst: ZonedDateTime
    ): List<MidDaily> = withContext(Dispatchers.IO) {

        // 1) tmFc(발표시각) 계산
        val baseTime = computeMidBaseTime(nowKst)
        val tmFc = baseTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.KOREA))
        val baseDate = baseTime.toLocalDate()   // tmFc 날짜

        val (regTa, regLand) = mapSidoToRegId(sidoName)

        Log.d("MidTermRepo", "중기예보 호출 tmFc=$tmFc regTa=$regTa regLand=$regLand")

        // ---- 2) 중기 기온
        val taRes = api.getMidTemperature(regTa, tmFc)
        val taJson = JSONObject(taRes.string())
        val taItems =
            taJson.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")

        // ---- 3) 중기 육상(날씨 + 강수확률)
        val landRes = api.getMidLandForecast(regLand, tmFc)
        val landJson = JSONObject(landRes.string())
        val landItems =
            landJson.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")

        if (taItems == null || taItems.length() == 0 ||
            landItems == null || landItems.length() == 0
        ) {
            Log.e("MidTermRepo", "중기예보 데이터 없음")
            return@withContext emptyList()
        }

        val ta = taItems.getJSONObject(0)
        val land = landItems.getJSONObject(0)

        val list = mutableListOf<MidDaily>()

        // 3~10일차
        for (d in 3..10) {

            // tmFc 기준 날짜에서 D+? 로 변환
            // API 스펙상 3일차는 D+2, 4일차는 D+3 ... 로 쓰는 경우가 많아서
            // 일반적으로 D + (d-1) 로 맞춰 사용
            val targetDate = baseDate.plusDays((d - 1).toLong())
            val dateStr = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE)

            // ---- 기온
            val min = ta.optString("taMin$d", "").toIntOrNull()
            val max = ta.optString("taMax$d", "").toIntOrNull()

            // ---- 날씨(wf) & 강수확률(rnSt)
            val wf: String?
            val rain: Int?

            if (d in 3..7) {
                // 3~7일차 : 오전/오후 값 따로 있음
                val wfAm = land.optString("wf${d}Am", "")
                val wfPm = land.optString("wf${d}Pm", "")
                wf = when {
                    wfAm.isNotBlank() -> wfAm
                    wfPm.isNotBlank() -> wfPm
                    else -> null
                }

                val rainAm = land.optString("rnSt${d}Am", "").toIntOrNull()
                val rainPm = land.optString("rnSt${d}Pm", "").toIntOrNull()
                rain = listOfNotNull(rainAm, rainPm).maxOrNull()
            } else {
                // 8~10일차 : 하루 통합 값만 있음
                val wfDay = land.optString("wf$d", "")
                wf = if (wfDay.isNotBlank()) wfDay else null

                rain = land.optString("rnSt$d", "").toIntOrNull()
            }

            // 완전히 비어 있는 날은 리스트에 넣지 않음
            if (min == null && max == null && rain == null && wf.isNullOrBlank()) {
                Log.d("MidTermRepo", "d=$d (date=$dateStr) : 유효 데이터 없음 → 스킵")
                continue
            }

            list.add(
                MidDaily(
                    date = dateStr,
                    minTemp = min,
                    maxTemp = max,
                    rainProb = rain,
                    wf = wf
                )
            )
        }

        Log.d("MidTermRepo", "중기예보 일수 = ${list.size}")
        list
    }
}
