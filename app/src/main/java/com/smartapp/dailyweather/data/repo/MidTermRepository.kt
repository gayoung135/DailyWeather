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
    val wf: String?          // 날씨예보(맑음/비/흐림 등)
)

class MidTermRepository(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {
    private val api = RetrofitClient.weatherApi

    /** 시/도 → 중기예보 regId 매핑 */
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

    /** 기상청 tmFc 기준시각 계산 공식 */
    private fun computeMidBaseTime(nowKst: ZonedDateTime): ZonedDateTime {
        val kst = nowKst.withZoneSameInstant(ZoneId.of("Asia/Seoul"))

        return when {
            kst.hour < 6 ->
                kst.minusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)

            kst.hour < 18 ->
                kst.withHour(6).withMinute(0).withSecond(0).withNano(0)

            else ->
                kst.withHour(18).withMinute(0).withSecond(0).withNano(0)
        }
    }

    /** 중기예보 3~10일차 데이터 */
    suspend fun getMidTermForecast(
        sidoName: String,
        nowKst: ZonedDateTime
    ): List<MidDaily> = withContext(Dispatchers.IO) {

        val baseTime = computeMidBaseTime(nowKst)
        val tmFc = baseTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.KOREA))

        // UI 기준 날짜 → "오늘"
        val today = nowKst.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate()

        val (regTa, regLand) = mapSidoToRegId(sidoName)

        Log.d("MidTermRepo", "중기예보 호출 tmFc=$tmFc regTa=$regTa regLand=$regLand")

        /** 중기 기온 호출 */
        val taRes = api.getMidTemperature(regTa, tmFc)
        val taJson = JSONObject(taRes.string())
        val taItem =
            taJson.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")
                ?.optJSONObject(0)

        /** 중기 육상(날씨 + POP) 호출 */
        val landRes = api.getMidLandForecast(regLand, tmFc)
        val landJson = JSONObject(landRes.string())
        val landItem =
            landJson.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")
                ?.optJSONObject(0)

        if (taItem == null || landItem == null) {
            Log.e("MidTermRepo", "중기예보 데이터 없음")
            return@withContext emptyList()
        }

        val list = mutableListOf<MidDaily>()

        /** 3~10일차 반복 */
        for (d in 3..10) {

            // 시간대에 따른 날짜 조정
            val kstHour = nowKst.withZoneSameInstant(ZoneId.of("Asia/Seoul")).hour

            val offsetFromToday =
                if (kstHour < 6) d - 2  // 새벽은 3일차가 내일
                else d - 1             // 3일차가 모레

            val targetDate = today.plusDays(offsetFromToday.toLong())
            val dateStr = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE)

            // 기온
            val min = taItem.optString("taMin$d", "").toIntOrNull()
            val max = taItem.optString("taMax$d", "").toIntOrNull()

            // 날씨/강수확률
            val wf: String?
            val rain: Int?

            if (d in 3..7) {
                val wfAm = landItem.optString("wf${d}Am", "")
                val wfPm = landItem.optString("wf${d}Pm", "")

                wf = when {
                    wfAm.isNotBlank() -> wfAm
                    wfPm.isNotBlank() -> wfPm
                    else -> null
                }

                val rainAm = landItem.optString("rnSt${d}Am", "").toIntOrNull()
                val rainPm = landItem.optString("rnSt${d}Pm", "").toIntOrNull()

                rain = listOfNotNull(rainAm, rainPm).maxOrNull()
            } else {
                wf = landItem.optString("wf$d", "").ifBlank { null }
                rain = landItem.optString("rnSt$d", "").toIntOrNull()
            }

            // 완전 빈 날은 스킵
            if (min == null && max == null && rain == null && wf.isNullOrBlank()) {
                Log.d("MidTermRepo", "중기 d=$d : 데이터 없어 스킵")
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
