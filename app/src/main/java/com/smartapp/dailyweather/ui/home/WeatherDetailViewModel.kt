package com.smartapp.dailyweather.ui.home

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.*
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.core.KmaGridConverter
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.normalizeSidoName
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.repo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class WeatherDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DailyWeatherApp.instance.db
    private val timeProvider: TimeProvider = SystemTimeProvider()

    private val lastUserStateRepo = LastUserStateRepository(db, timeProvider)
    private val weatherRepo = WeatherRepository(timeProvider)
    private val airRepo = AirQualityRepository()
    private val midRepo = MidTermRepository(timeProvider)

    private val _uiState = MutableLiveData<WeatherDetailUiModel>()
    val uiState: LiveData<WeatherDetailUiModel> = _uiState


    fun loadDetailWeather() {
        viewModelScope.launch {
            try {
                val state = lastUserStateRepo.getLatestState() ?: return@launch

                val grid = GridPoint(state.x, state.y)
                val now = timeProvider.nowKorea().withMinute(0).withSecond(0).withNano(0)

                val currentTemp = weatherRepo.getTempAt(grid, now)
                val pop = weatherRepo.getPopAt(grid, now) ?: 0

                // 위치 이름
                val context = getApplication<Application>()
                val geocoder = Geocoder(context, Locale.KOREA)
                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)

                val address = withContext(Dispatchers.IO) {
                    try { geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)?.firstOrNull() }
                    catch (_: Exception) { null }
                }

                val admin = address?.adminArea ?: "서울특별시"
                val sub = address?.subLocality
                val locationName = sub ?: admin
                val sido = normalizeSidoName(admin)

                // 미세먼지
                val air = airRepo.getAirForSido(sido)
                val airText = air?.airQualityLabel ?: "알 수 없음"

                // 중기예보
                val mid = midRepo.getMidTermForecast(sido, now)

                val list = mid.take(7).map { d ->
                    val z = ZonedDateTime.parse(d.date + "T00:00+09:00[Asia/Seoul]",
                        DateTimeFormatter.ISO_ZONED_DATE_TIME
                    )
                    DailyRow(
                        dateText = z.format(DateTimeFormatter.ofPattern("MM월 dd일 (E)", Locale.KOREA)),
                        minTemp = d.minTemp?.let { "${it}°" } ?: "-°",
                        maxTemp = d.maxTemp?.let { "${it}°" } ?: "-°",
                        rainProb = d.rainProb?.let { "${it}%" } ?: "0%"
                    )
                }

                val today = now.format(DateTimeFormatter.ofPattern("MM월 dd일 | E요일", Locale.KOREA))

                _uiState.value = WeatherDetailUiModel(
                    locationName = locationName,
                    dateText = today,
                    currentTempText = "${currentTemp}°C",
                    airQualityText = airText,
                    rainText = "${pop}%",
                    forecastList = list
                )

            } catch (e: Exception) {
                Log.e("WeatherDetailVM", "loadDetailWeather 실패: ${e.message}", e)
            }
        }
    }
}
