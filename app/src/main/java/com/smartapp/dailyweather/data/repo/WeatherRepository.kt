package com.smartapp.dailyweather.data.repo

import android.util.Log
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

    // 단기예보에서 targetTime 시각의 TMP(기온) 1시간 단위 값 가져오기
    suspend fun getTempAt(
        grid: GridPoint,
        targetTimeKst: ZonedDateTime
    ): Int {

        Log.d("WeatherRepo", "==== 기온 조회 시작 ====")
        Log.d("WeatherRepo", "사용자가 요청한 시간(KST): $targetTimeKst")

        // 1) base_date, base_time 계산
        val (baseDate, baseTime) = computeBaseDateTime(targetTimeKst)
        Log.d("WeatherRepo", "API 요청 base_date=$baseDate, base_time=$baseTime")

        // 2) API 호출
        val res = api.getShortTermForecast(
            baseDate = baseDate,
            baseTime = baseTime,
            nx = grid.x,
            ny = grid.y,
            numOfRows = 1000
        )

        val items = res.response?.body?.items?.item.orEmpty()
        Log.d("WeatherRepo", "응답 item 개수 = ${items.size}")

        // 3) 타겟 예보시각 (날짜+시간 반올림)
        val (fcstDateStr, fcstTimeStr) = computeTargetFcstDateTime(targetTimeKst)

        Log.d("WeatherRepo", "찾으려는 예보 시간 fcstDate=$fcstDateStr, fcstTime=$fcstTimeStr")

        // 이 날짜의 TMP 시간 목록
        val dateTmpTimes = items
            .filter { it.category == "TMP" && it.fcstDate == fcstDateStr }
            .map { it.fcstTime }

        Log.d("WeatherRepo", "이 날짜 TMP 제공 시간 목록 = $dateTmpTimes")

        if (dateTmpTimes.isEmpty()) {
            Log.e("WeatherRepo", "이 날짜($fcstDateStr)에 TMP 데이터가 하나도 없습니다.")
            return DEFAULT_TEMP  // TMP가 하나도 없을 경우 기본값 반환
        }

        // 4) TMP 항목 필터
        val tmpItem = items.firstOrNull {
            it.category == "TMP" &&
                    it.fcstDate == fcstDateStr &&
                    it.fcstTime == fcstTimeStr
        }

        if (tmpItem == null) {
            Log.e("WeatherRepo", "TMP를 찾지 못함! date=$fcstDateStr, time=$fcstTimeStr")
            return DEFAULT_TEMP  // 해당 시간 TMP 없을 경우 기본값 반환
        }

        Log.d("WeatherRepo", "선택된 TMP → fcstTime=${tmpItem.fcstTime}, fcstValue=${tmpItem.fcstValue}")

        // 5) 섭씨 변환
        val t = tmpItem.fcstValue.toDoubleOrNull()
            ?: error("TMP fcstValue 변환 실패: ${tmpItem.fcstValue}")

        val rounded = t.roundToInt()

        Log.d("WeatherRepo", "반올림된 최종 기온 = $rounded℃")
        Log.d("WeatherRepo", "==== 기온 조회 완료 ====")

        return rounded
    }

    // "HHmm" 문자열끼리 시(hour) 차이 절댓값
    private fun timeDiffHour(a: String, b: String): Int {
        val ah = a.substring(0, 2).toIntOrNull() ?: 0
        val bh = b.substring(0, 2).toIntOrNull() ?: 0
        return kotlin.math.abs(ah - bh)
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

    private fun computeTargetFcstDateTime(target: ZonedDateTime): Pair<String, String> {
        // KST 로 맞춰둠
        var kst = target.withZoneSameInstant(ZoneId.of("Asia/Seoul"))

        // 30분 이상이면 +1시간 (날짜는 ZonedDateTime이 알아서 넘어감)
        if (kst.minute >= 30) {
            kst = kst.plusHours(1)
        }

        val dateStr = kst.toLocalDate()
            .format(DateTimeFormatter.BASIC_ISO_DATE)   // yyyyMMdd

        val timeStr = String.format("%02d00", kst.hour) // HH00

        return dateStr to timeStr
    }

    companion object {
        const val DEFAULT_TEMP = 15  // TMP 누락 시 기본 기온
    }
}