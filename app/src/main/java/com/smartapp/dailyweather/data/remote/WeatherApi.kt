package com.smartapp.dailyweather.data.remote

import com.smartapp.dailyweather.data.remote.dto.VilageFcstResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * typ02: 단기예보 + 중기기온 + 중기육상
 */
interface WeatherApi {
    // 단기예보조회
    @GET("openApi/VilageFcstInfoService_2.0/getVilageFcst")
    suspend fun getShortTermForecast(
        @Query("base_date") baseDate: String, // yyyyMMdd
        @Query("base_time") baseTime: String, // HHmm (예: 0500)
        @Query("nx") nx: Int,
        @Query("ny") ny: Int,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 1000,
        @Query("dataType") dataType: String = "JSON"
    ): VilageFcstResponse

    // 중기기온조회
    @GET("openApi/MidFcstInfoService/getMidTa")
    suspend fun getMidTemperature(
        @Query("regId") regId: String, // 예: 11B10101
        @Query("tmFc") tmFc: String,   // yyyyMMdd0600/1800
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 10,
        @Query("dataType") dataType: String = "JSON"
    ): ResponseBody // 이후 DTO 클래스로 교체

    // 중기육상예보조회
    @GET("openApi/MidFcstInfoService/getMidLandFcst")
    suspend fun getMidLandForecast(
        @Query("regId") regId: String, // 예: 11B00000
        @Query("tmFc") tmFc: String,   // yyyyMMdd0600/1800
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 10,
        @Query("dataType") dataType: String = "JSON"
    ): ResponseBody // 이후 DTO 클래스로 교체
}