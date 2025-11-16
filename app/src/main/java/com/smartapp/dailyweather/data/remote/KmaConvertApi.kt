package com.smartapp.dailyweather.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * typ01: 격자 변환 + 중기 예보구역
 */
interface KmaConvertApi {

    // 임의 위·경도 → 격자 변환 (텍스트 응답)
    // 예: /cgi-bin/url/nph-dfs_xy_lonlat?lon=126.9&lat=37.6&help=0
    @GET("cgi-bin/url/nph-dfs_xy_lonlat")
    suspend fun convertLonLatToGrid(
        @Query("lon") lon: Double,
        @Query("lat") lat: Double,
        @Query("help") help: Int = 0
    ): ResponseBody

    // (참고) 중기 예보구역 - 나중에 필요하면 DTO로 변경
    @GET("url/fct_medm_reg.php")
    suspend fun getMidRegions(
        @Query("stn") stn: String? = null,
        @Query("reg") reg: String? = null,
        @Query("tmfc") tmfc: String? = "0",
        @Query("mode") mode: Int? = null,
        @Query("disp") disp: Int? = null,
        @Query("help") help: Int? = null
    ): String
}