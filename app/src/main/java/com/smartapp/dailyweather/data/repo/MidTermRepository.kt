package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MidDaily(
    val date: String,        // yyyyMMdd
    val minTemp: Int?,       // 최저기온
    val maxTemp: Int?,       // 최고기온
    val rainProb: Int?,      // 강수확률
    val wf: String?          // 날씨예보
)

class MidTermRepository(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {
    private val api = RetrofitClient.weatherApi

    /**
     * 중기예보는 regId가 필요함 → 시/도 단위로 매핑해야 함
     * 기상청 공식 regId 리스트 기반 간략화 버전
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
     * 중기예보 전체 로드 (3~10일차 → UI는 보통 3~8일까지만 사용)
     */
    suspend fun getMidTermForecast(
        sidoName: String,
        baseTimeKst: ZonedDateTime
    ): List<MidDaily> = withContext(Dispatchers.IO) {

        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.KOREA)

        val tmFc = if (baseTimeKst.hour < 18)
            baseTimeKst.format(DateTimeFormatter.ofPattern("yyyyMMdd0600"))
        else
            baseTimeKst.format(DateTimeFormatter.ofPattern("yyyyMMdd1800"))

        val (regTa, regLand) = mapSidoToRegId(sidoName)

        Log.d("MidTermRepo", "중기예보 호출 tmFc=$tmFc regTa=$regTa regLand=$regLand")

        // ---- 1) 중기 기온
        val taRes = api.getMidTemperature(regTa, tmFc)
        val taJson = JSONObject(taRes.string())
        val taItems =
            taJson.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")

        // ---- 2) 중기 육상
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

        for (d in 3..10) {
            val date = baseTimeKst.plusDays(d.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            val min = ta.optString("taMin$d").toIntOrNull()
            val max = ta.optString("taMax$d").toIntOrNull()

            val wf = when (d) {
                3 -> land.optString("wf3Am")
                4 -> land.optString("wf4Am")
                5 -> land.optString("wf5Am")
                6 -> land.optString("wf6Am")
                7 -> land.optString("wf7Am")
                else -> land.optString("wf${d}")
            }

            val rain = when (d) {
                3 -> land.optString("rnSt3Am").toIntOrNull()
                4 -> land.optString("rnSt4Am").toIntOrNull()
                5 -> land.optString("rnSt5Am").toIntOrNull()
                6 -> land.optString("rnSt6Am").toIntOrNull()
                7 -> land.optString("rnSt7Am").toIntOrNull()
                else -> land.optString("rnSt${d}").toIntOrNull()
            }

            list.add(
                MidDaily(
                    date = date,
                    minTemp = min,
                    maxTemp = max,
                    rainProb = rain,
                    wf = wf
                )
            )
        }

        list
    }
}
