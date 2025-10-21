package com.smartapp.dailyweather.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit + OkHttp 공용 싱글턴
 * - typ01: 텍스트/CSV 가능 → Scalars + Gson (순서 중요: Scalars 먼저)
 * - typ02: JSON 위주 → Gson
 * - 모든 요청에 authKey 자동 부착 (쿼리 파라미터)
 */
object RetrofitClient {
    // --- Base URL ---
    private const val BASE_TYP01 = "https://apihub.kma.go.kr/api/typ01/"
    private const val BASE_TYP02 = "https://apihub.kma.go.kr/api/typ02/"

    // --- API Key (임시 하드코딩) ---
    private const val API_KEY_PARAM = "authKey"
    private const val API_KEY_VALUE = "5L3H99kWQDG9x_fZFiAxAg"

    // --- OkHttp 공통 설정 ---
    private val authQueryInterceptor = Interceptor { chain ->
        val req = chain.request()
        val urlWithKey = req.url.newBuilder()
            .addQueryParameter(API_KEY_PARAM, API_KEY_VALUE)
            .build()
        val newReq = req.newBuilder()
            .url(urlWithKey)
            .header("User-Agent", "WeatheringFit/1.0 (Android)")
            .build()
        chain.proceed(newReq)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 개발 중에만 BODY
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authQueryInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // --- Retrofit 인스턴스 2개 ---
    private val retrofitTyp01: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_TYP01)
            .client(okHttp)
            .addConverterFactory(ScalarsConverterFactory.create()) // 먼저 등록
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val retrofitTyp02: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_TYP02)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- API 팩토리  ---
    val kmaConvertApi: KmaConvertApi by lazy { retrofitTyp01.create(KmaConvertApi::class.java) }
    val weatherApi: WeatherApi by lazy { retrofitTyp02.create(WeatherApi::class.java) }
}