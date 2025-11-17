package com.smartapp.dailyweather.data.remote

import com.smartapp.dailyweather.data.remote.dto.AirCityResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AirKoreaApi {

    // 시도별 실시간 측정정보 조회
    @GET("getCtprvnRltmMesureDnsty")
    suspend fun getCityRealtime(
        @Query("serviceKey") serviceKey: String,
        @Query("returnType") returnType: String = "json",
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("pageNo") pageNo: Int = 1,
        @Query("sidoName") sidoName: String,
        @Query("ver") ver: String = "1.5"
    ): AirCityResponse
}