package com.smartapp.dailyweather.data.remote.dto

data class AirCityResponse(
    val response: AirCityResponseWrapper?
)

data class AirCityResponseWrapper(
    val body: AirCityResponseBody?
)

data class AirCityResponseBody(
    val items: List<AirCityItem> = emptyList()
)

data class AirCityItem(
    val dataTime: String?,      // "2025-11-16 00:00"
    val stationName: String?,
    val sidoName: String?,

    val pm10Value: String?,     // 초미세먼지 같이 사용 가능
    val pm25Value: String?,     // 1시간 PM2.5 농도
    val pm25Value24: String?,   // 24시간 평균 PM2.5 농도

    val pm25Grade1h: String?,   // 1~4 (1:좋음, 2:보통, 3:나쁨, 4:매우나쁨)
    val pm25Grade: String?,     // 24시간 등급
    val khaiGrade: String?      // 통합대기환경지수 등급
)