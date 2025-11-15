package com.smartapp.dailyweather.data.repo

import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.remote.RetrofitClient
import com.smartapp.dailyweather.data.remote.dto.VilageFcstItem
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class WeatherRepository(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {
    private val api = RetrofitClient.weatherApi

    // 단기예보에서 targetTime 시각의 TMP(기온) 1시간 값 가져오기
    suspend fun getTempAt(
        grid: GridPoint,
        targetTimeKst: ZonedDateTime
    ): Int {
        // 1) base_date, base_time 계산 (KMA 규칙에 맞게)
        val (baseDate, baseTime) = computeBaseDateTime(targetTimeKst)

        // 2) API 호출
        val res = api.getShortTermForecast(
            baseDate = baseDate,
            baseTime = baseTime,
            nx = grid.x,
            ny = grid.y,
            numOfRows = 1000
        )

        val items = res.response?.body?.items?.item.orEmpty()

        // 3) 타겟 예보시각 문자열 (fcstDate, fcstTime)
        val dateStr = targetTimeKst.toLocalDate()
            .format(DateTimeFormatter.BASIC_ISO_DATE) // yyyyMMdd

        val fcstTimeStr = computeTargetFcstTime(targetTimeKst)

        // 4) TMP(기온) 항목 찾기
        val tmpItem = items.firstOrNull {
            it.category == "TMP" &&
                    it.fcstDate == dateStr &&
                    it.fcstTime == fcstTimeStr
        } ?: error("해당 시간의 TMP 예보를 찾을 수 없습니다.")

        // 5) 예보값을 온도로 변환 (반올림)
        val t = tmpItem.fcstValue.toDoubleOrNull()
            ?: error("TMP fcstValue 변환 실패: ${tmpItem.fcstValue}")

        return t.roundToInt()
    }

    private fun computeTargetFcstTime(target: ZonedDateTime): String {
        val minute = target.minute
        var hour = target.hour

        // 30분 기준 반올림
        if (minute >= 30) hour += 1

        // 24시 넘어가면 00:00 처리
        if (hour == 24) hour = 0

        return String.format("%02d00", hour)
    }

    /**
     * KMA 단기예보 base_time 규칙에 맞게
     * targetTime 기준으로 가장 최근 발표시각(base_date, base_time)을 선택
     *
     * 단기예보 base_time : 02, 05, 08, 11, 14, 17, 20, 23시
     */
    private fun computeBaseDateTime(target: ZonedDateTime): Pair<String, String> {
        val kst = target.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
        val baseHours = listOf(2, 5, 8, 11, 14, 17, 20, 23)

        val hour = kst.hour
        val baseHour = baseHours.lastOrNull { it <= hour } ?: 23

        val baseDate = if (baseHour == 23 && hour < 2) {
            // 자정~새벽 1시대라면 전날 23시 발표분 사용
            kst.minusDays(1).toLocalDate()
        } else {
            kst.toLocalDate()
        }

        val baseDateStr = baseDate.format(DateTimeFormatter.BASIC_ISO_DATE) // yyyyMMdd
        val baseTimeStr = String.format("%02d00", baseHour)                 // HH00

        return baseDateStr to baseTimeStr
    }
}