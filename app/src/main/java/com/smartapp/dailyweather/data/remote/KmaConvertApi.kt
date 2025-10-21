package com.smartapp.dailyweather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/* =======================
* Service 인터페이스 (세팅 단계용 최소 형태)
* ======================= */

/**
 * typ01: 격자 변환 + 중기 예보구역
 */
interface KmaConvertApi {
    // 임의 위·경도 → 격자 변환
    @GET("cgi-bin/url/nph-dfs_xy_lonlat")
    suspend fun convertLonLatToGrid(
        @Query("lon") lon: Double,
        @Query("lat") lat: Double,
        @Query("help") help: Int = 0
    ): String // typ01은 텍스트 응답 가능 → 우선 String

    // 중기 예보구역
    @GET("url/fct_medm_reg.php")
    suspend fun getMidRegions(
        @Query("stn") stn: String? = null,
        @Query("reg") reg: String? = null,
        @Query("tmfc") tmfc: String? = "0", // 0: 최신
        @Query("mode") mode: Int? = null,
        @Query("disp") disp: Int? = null,
        @Query("help") help: Int? = null
    ): String // 필요하면 DTO로 교체
}