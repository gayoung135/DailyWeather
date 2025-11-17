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
        // 현재 시간 (KST)
        val nowKst = timeProvider.nowKorea()

        // base_time 계산에 사용할 기준 시각:
        //    - targetTime이 미래면 아직 발표 안 된 base_time을 고를 수 있으니까
        //      현재(now) 기준으로 base_time을 잡도록 보정
        val baseTarget = if (targetTimeKst.isAfter(nowKst)) nowKst else targetTimeKst

        // 1) base_date, base_time 계산 (KMA 규칙에 맞게)
        val (baseDate, baseTime) = computeBaseDateTime(baseTarget)

        Log.d("WeatherRepo", "==== 기온 조회 시작 ====")
        Log.d("WeatherRepo", "사용자가 요청한 시간(KST): $targetTimeKst")
        Log.d("WeatherRepo", "baseTarget(KST): $baseTarget")
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

        if (items.isEmpty()) {
            Log.e("WeatherRepo", "NO_DATA 또는 응답 item=0 → TMP 예보 없음 (baseDate=$baseDate, baseTime=$baseTime)")
            throw IllegalStateException("해당 날짜의 TMP 예보를 찾을 수 없습니다.")
        }

        // 3) 타겟 날짜/시간 문자열 (fcstDate, fcstTime)
        val dateStr = targetTimeKst.toLocalDate()
            .format(DateTimeFormatter.BASIC_ISO_DATE)        // yyyyMMdd
        val fcstTimeStr = computeTargetFcstTime(targetTimeKst) // "HHmm" (예: "1700")

        Log.d("WeatherRepo", "찾으려는 예보 시간 fcstDate=$dateStr, fcstTime=$fcstTimeStr")

        // 4) 해당 날짜의 TMP 리스트만 모으기
        val tmpList = items.filter { it.category == "TMP" && it.fcstDate == dateStr }

        if (tmpList.isEmpty()) {
            Log.e("WeatherRepo", "해당 날짜에 TMP 예보가 없습니다. date=$dateStr (baseDate=$baseDate, baseTime=$baseTime)")
            throw IllegalStateException("해당 날짜의 TMP 예보를 찾을 수 없습니다.")
        }

        // 5) 정확히 같은 시간 먼저 찾기
        val exact = tmpList.firstOrNull { it.fcstTime == fcstTimeStr }

        val targetItem = if (exact != null) {
            Log.d(
                "WeatherRepo",
                "예보 TMP 찾음 → fcstTime=${exact.fcstTime}, fcstValue=${exact.fcstValue}"
            )
            exact
        } else {
            // 없으면, 같은 날짜 안에서 "가장 가까운 시간"을 골라서 사용
            val target = fcstTimeStr.toInt()
            val sortedTimes = tmpList
                .mapNotNull { it.fcstTime?.toIntOrNull() }
                .sorted()

            Log.w(
                "WeatherRepo",
                "[TMP] 이 날짜 TMP 제공 시간 목록 = $sortedTimes (요청=$fcstTimeStr)"
            )

            val nearestTime = sortedTimes.minByOrNull { kotlin.math.abs(it - target) }
                ?: run {
                    Log.e("WeatherRepo", "TMP 시간 목록 계산 실패 date=$dateStr")
                    throw IllegalStateException("해당 날짜의 TMP 예보를 찾을 수 없습니다.")
                }

            val nearestItem = tmpList.first { it.fcstTime?.toIntOrNull() == nearestTime }
            Log.w(
                "WeatherRepo",
                "[TMP] 정확한 시간($fcstTimeStr) 없음 → 가장 가까운 시간(${nearestItem.fcstTime}) 사용"
            )
            nearestItem
        }

        val value = targetItem.fcstValue?.toIntOrNull()
            ?: error("TMP fcstValue 파싱 실패: ${targetItem.fcstValue}")

        val rounded = value // 필요하면 반올림/보정 로직 추가
        Log.d("WeatherRepo", "반올림된 최종 기온 = ${rounded}℃")
        Log.d("WeatherRepo", "==== 기온 조회 완료 ====")

        return rounded
    }

    /**
     * POP(강수확률)도 base_time 계산을 동일하게 보정해주자
     */
    suspend fun getPopAt(
        grid: GridPoint,
        targetTimeKst: ZonedDateTime
    ): Int? {
        val nowKst = timeProvider.nowKorea()
        val baseTarget = if (targetTimeKst.isAfter(nowKst)) nowKst else targetTimeKst

        val (baseDate, baseTime) = computeBaseDateTime(baseTarget)

        Log.d("WeatherRepo", "==== POP(강수확률) 조회 시작 ====")
        Log.d("WeatherRepo", "사용자가 요청한 시간(KST): $targetTimeKst")
        Log.d("WeatherRepo", "baseTarget(KST): $baseTarget")
        Log.d("WeatherRepo", " [POP] API 요청 base_date=$baseDate, base_time=$baseTime")

        val res = api.getShortTermForecast(
            baseDate = baseDate,
            baseTime = baseTime,
            nx = grid.x,
            ny = grid.y,
            numOfRows = 1000
        )

        val items = res.response?.body?.items?.item.orEmpty()
        Log.d("WeatherRepo", " [POP] 응답 item 개수 = ${items.size}")

        if (items.isEmpty()) {
            Log.w("WeatherRepo", " [POP] NO_DATA 또는 item=0 → POP 예보 없음 (baseDate=$baseDate, baseTime=$baseTime)")
            return null
        }

        val dateStr = targetTimeKst.toLocalDate()
            .format(DateTimeFormatter.BASIC_ISO_DATE)
        val fcstTimeStr = computeTargetFcstTime(targetTimeKst)

        Log.d("WeatherRepo", " [POP] 찾으려는 예보 시간 fcstDate=$dateStr, fcstTime=$fcstTimeStr")

        val popList = items.filter { it.category == "POP" && it.fcstDate == dateStr }

        if (popList.isEmpty()) {
            Log.w("WeatherRepo", " [POP] 해당 날짜 POP 예보 없음 → null 반환 (date=$dateStr)")
            return null
        }

        val exact = popList.firstOrNull { it.fcstTime == fcstTimeStr }

        val targetItem = if (exact != null) {
            exact
        } else {
            val target = fcstTimeStr.toInt()
            val times = popList.mapNotNull { it.fcstTime?.toIntOrNull() }.sorted()

            Log.d("WeatherRepo", " [POP] 이 날짜 POP 제공 시간 목록 = $times (요청=$fcstTimeStr)")

            val nearest = times.minByOrNull { kotlin.math.abs(it - target) }
                ?: return null

            val nearestItem = popList.first { it.fcstTime?.toIntOrNull() == nearest }
            Log.w(
                "WeatherRepo",
                " [POP] 정확한 시간($fcstTimeStr) 없음 → 가장 가까운 시간(${nearestItem.fcstTime}) 사용"
            )
            nearestItem
        }

        val pop = targetItem.fcstValue?.toIntOrNull()
        Log.d("WeatherRepo", " [POP] 선택된 POP → fcstTime=${targetItem.fcstTime}, POP=$pop")
        Log.d("WeatherRepo", "==== POP(강수확률) 조회 완료 ====")

        return pop

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
}