package com.smartapp.dailyweather.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 시간 관리 도우미
// 1. 현재 시각 제공 함수 <nowInstant(), nowKorea()>
// 2. 포맷 통일(타임존+패턴) <ZoneId.of("Asia/Seoul"), patternFormat()>
// 3. 날짜 로직(당일 여부) <isToday>
interface TimeProvider {

    // 1. 현재 시간 제공
    // UTC 기준 현재 시각 - 전 세계 공통 기준 시간(서버 저장용)
    fun nowInstant(): Instant

    // Asia/Seoul 기준 현재 시각 (화면 표시용)
    fun nowKorea(): ZonedDateTime

    // 2. 표시용 포맷 통일
    fun patternFormat(dt: ZonedDateTime): String

    // 3. 당일인지 판단
    fun isToday(lastUpdated: Instant): Boolean
}

class SystemTimeProvider(
    // 타임존
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),   // 한국 시간대
    private val locale: Locale = Locale.KOREA             // 한국 로케일(요일, 숫자 형식 등)
) : TimeProvider {

    // 2. 표시용 포맷 통일 -> 날짜를 보기 좋게 출력하기 위한 형식 지정
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale)

    // 1. 현재 시각 제공 (UTC 기준)
    override fun nowInstant(): Instant = Instant.now()

    // Asia/Seoul 기준 현재 시각 (화면 표시용)
    override fun nowKorea(): ZonedDateTime = ZonedDateTime.now(zone)

    // 2. 포맷 통일된 문자열로 변환
    override fun patternFormat(dt: ZonedDateTime): String = dt.format(timeFormatter)

    // 3. 당일인지 판단
    override fun isToday(lastUpdated: Instant): Boolean {
        // 저장된 시간을 서울 시간대 기준으로 바꾸기
        val last = lastUpdated.atZone(zone)
        val now = nowKorea()

        // 날짜 부분만 비교 (시간, 분, 초는 제외)
        return last.toLocalDate().isEqual(now.toLocalDate())
    }
}
