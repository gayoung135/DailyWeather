package com.smartapp.dailyweather.ui.home

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.core.KmaGridConverter
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.normalizeSidoName
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.repo.AirQualityRepository
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import com.smartapp.dailyweather.data.repo.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeatherDetailUiModel(
    val locationName: String,
    val dateText: String,
    val currentTempText: String,
    val description: String,
    val amPmSummary: String,
    val currentTempSummary: String,
    val pm10Text: String,
    val popText: String
)

class WeatherDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DailyWeatherApp.instance.db
    private val timeProvider: TimeProvider = SystemTimeProvider()

    private val lastUserStateRepo = LastUserStateRepository(db, timeProvider)
    private val weatherRepo = WeatherRepository(timeProvider)
    private val airRepo = AirQualityRepository()

    private val _uiState = MutableLiveData<WeatherDetailUiModel>()
    val uiState: LiveData<WeatherDetailUiModel> = _uiState

    fun loadCurrentWeather() {
        viewModelScope.launch {
            try {
                // 1) 마지막으로 사용자가 선택했던 위치(grid) 기준
                val state = lastUserStateRepo.getLatestState()
                    ?: run {
                        Log.w("WeatherDetailVM", "LastUserState 없음 → UI 업데이트 생략")
                        return@launch
                    }

                val grid = GridPoint(state.x, state.y)

                // 2) 현재 시각 (KST 기준, 분/초 0으로 정리)
                val now = timeProvider.nowKorea()
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)

                // 3) 현재 온도 (기상청 단기예보 TMP)
                val currentTemp: Int? = try {
                    weatherRepo.getTempAt(grid, now)
                } catch (e: Exception) {
                    Log.e("WeatherDetailVM", "현재 TMP 조회 실패: ${e.message}", e)
                    null
                }

                // 4) 현재 시각 기준 강수확률 POP
                val popPercent: Int? = try {
                    weatherRepo.getPopAt(grid, now)
                } catch (e: Exception) {
                    Log.e("WeatherDetailVM", "현재 POP 조회 실패: ${e.message}", e)
                    null
                }

                // 5) 위치 이름 + 시/도 이름 → 대기질 조회
                val context = getApplication<Application>()
                val geocoder = Geocoder(context, Locale.KOREA)

                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)

                val address = withContext(Dispatchers.IO) {
                    try {
                        geocoder.getFromLocation(
                            latLng.latitude,
                            latLng.longitude,
                            1
                        )?.firstOrNull()
                    } catch (e: Exception) {
                        Log.e("WeatherDetailVM", "Geocoder 실패: ${e.message}")
                        null
                    }
                }

                val adminArea = address?.adminArea ?: "서울특별시"
                val subLocality = address?.subLocality   // 구/동 이름 있을 수도 있음
                val locationName = subLocality ?: adminArea

                val sidoName = normalizeSidoName(adminArea)

                val airCode = try {
                    airRepo.getAirQualityCodeForRegion(sidoName)
                } catch (e: Exception) {
                    Log.e("WeatherDetailVM", "대기질 조회 실패: ${e.message}", e)
                    null
                }

                val pmLabel = when (airCode) {
                    "good" -> "좋음"
                    "normal" -> "보통"
                    "bad" -> "나쁨"
                    "very_bad" -> "매우 나쁨"
                    null -> "정보 없음"
                    else -> airCode ?: "정보 없음"
                }

                // 6) 날짜 텍스트
                val dateText = now.format(
                    DateTimeFormatter.ofPattern("MM월 dd일 | E요일", Locale.KOREA)
                )

                // 7) 메인 설명 문구 (원하면 더 예쁘게 바꿔도 됨)
                val descBuilder = StringBuilder().apply {
                    if (currentTemp != null) {
                        append("현재 기온은 ${currentTemp}°C")
                    } else {
                        append("현재 기온 정보를 불러오지 못했어요")
                    }
                    if (popPercent != null) {
                        append(", 강수확률은 ${popPercent}% ")
                        append(if (popPercent >= 60) "높아요." else "에요.")
                    }
                    append(" 대기질은 $pmLabel 입니다.")
                }

                val amPmSummary = "오전/오후 기준 상세 요약은 추후 추가 예정" // 필요하면 별도 API 로직 추가

                val ui = WeatherDetailUiModel(
                    locationName = locationName,
                    dateText = dateText,
                    currentTempText = currentTemp?.let { "${it}°C" } ?: "NULL°C",
                    description = descBuilder.toString(),
                    amPmSummary = amPmSummary,
                    currentTempSummary = "현재 온도: " + (currentTemp?.let { "${it}°C" } ?: "NULL°C"),
                    pm10Text = "미세먼지: $pmLabel",
                    popText = "강수확률: " + (popPercent?.let { "${it}%" } ?: "NULL%")
                )

                _uiState.value = ui

            } catch (e: Exception) {
                Log.e("WeatherDetailVM", "loadCurrentWeather 실패: ${e.message}", e)
            }
        }
    }
}