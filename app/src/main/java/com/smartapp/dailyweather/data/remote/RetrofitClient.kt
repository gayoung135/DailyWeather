package com.smartapp.dailyweather.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // --- Base URL (KMA) ---
    private const val BASE_TYP01 = "https://apihub.kma.go.kr/api/typ01/"
    private const val BASE_TYP02 = "https://apihub.kma.go.kr/api/typ02/"

    // --- API Key (KMA, 임시 하드코딩) ---
    private const val API_KEY_PARAM = "authKey"
    private const val API_KEY_VALUE = "5L3H99kWQDG9x_fZFiAxAg"

    // --- AirKorea Base URL ---
    private const val AIR_KOREA_BASE = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/"

    // =========================================================
    // 공통: 로깅 인터셉터
    // =========================================================
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 개발 중에만 BODY
    }

    // =========================================================
    // KMA용 OkHttp / Retrofit
    // =========================================================
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

    private val kmaOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authQueryInterceptor)
            .addInterceptor(logging)
            // 타임아웃 여유 있게 늘리기
            .connectTimeout(30, TimeUnit.SECONDS)   // 연결 시도 최대 30초
            .readTimeout(40, TimeUnit.SECONDS)      // 응답 읽기 최대 40초
            .writeTimeout(40, TimeUnit.SECONDS)

            // 전체 호출 최대 시간 (연결+전송+응답 포함)
            .callTimeout(60, TimeUnit.SECONDS)

            // 일시적인 네트워크 끊김일 때 재시도 (선택)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofitTyp01: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_TYP01)
            .client(kmaOkHttp)
            .addConverterFactory(ScalarsConverterFactory.create()) // 먼저 등록
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val retrofitTyp02: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_TYP02)
            .client(kmaOkHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- KMA API 팩토리 ---
    val kmaConvertApi: KmaConvertApi by lazy { retrofitTyp01.create(KmaConvertApi::class.java) }
    val weatherApi: WeatherApi by lazy { retrofitTyp02.create(WeatherApi::class.java) }

    // =========================================================
    // AirKorea용 OkHttp / Retrofit (KMA authKey X)
    // =========================================================
    private val airOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)   // 로그만 공유, authKey는 안 붙임
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofitAir: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AIR_KOREA_BASE)
            .client(airOkHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- AirKorea API 팩토리 ---
    val airKoreaApi: AirKoreaApi by lazy {
        retrofitAir.create(AirKoreaApi::class.java)
    }
}