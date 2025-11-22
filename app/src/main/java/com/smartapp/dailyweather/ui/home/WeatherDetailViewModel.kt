package com.smartapp.dailyweather.ui.home

import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.*
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.core.KmaGridConverter
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.normalizeSidoName
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.repo.AirQualityRepository
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import com.smartapp.dailyweather.data.repo.MidTermRepository
import com.smartapp.dailyweather.data.repo.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

data class DailyRow(
    val dateText: String,
    val minTemp: String,
    val maxTemp: String,
    val rainProb: String
)

data class WeatherDetailUiModel(
    val locationName: String,
    val dateText: String,
    val currentTempText: String,
    val airQualityText: String,
    val rainText: String,
    val amTempText: String,
    val pmTempText: String,
    val forecastList: List<DailyRow>
)

class WeatherDetailViewModel : ViewModel() {

    private val db = DailyWeatherApp.instance.db
    private val appContext = DailyWeatherApp.instance.applicationContext
    private val timeProvider: TimeProvider = SystemTimeProvider()

    private val lastUserStateRepo = LastUserStateRepository(db, timeProvider)
    private val weatherRepo = WeatherRepository(timeProvider)
    private val airRepo = AirQualityRepository()
    private val midRepo = MidTermRepository(timeProvider)

    private val _uiState = MutableLiveData<WeatherDetailUiModel?>()
    val uiState: LiveData<WeatherDetailUiModel?> = _uiState

    private var hasLoaded = false

    /** 🔥 처음으로 이동할 때 상세날씨 캐시 초기화 */
    fun resetForRestart() {
        hasLoaded = false
        _uiState.value = null
    }

    fun loadDetailWeather(force: Boolean = false) {

        if (force) {
            hasLoaded = false
            _uiState.value = null
        }

        if (!force && hasLoaded && _uiState.value != null) {
            Log.d("WeatherDetailVM", "이미 로딩된 상세날씨 → 스킵")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = lastUserStateRepo.getLatestState() ?: return@launch
                val grid = GridPoint(state.x, state.y)
                val now = timeProvider.nowKorea().withSecond(0).withNano(0)

                var admin: String? = state.regionName
                var cityOrGu: String? = state.subRegionName

                val geocoder = Geocoder(appContext, Locale.KOREA)
                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)

                val address = if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                ?.firstOrNull()
                        } catch (_: Exception) { null }
                    }
                } else null

                if (address != null) {
                    admin = admin ?: address.adminArea
                    cityOrGu = cityOrGu ?: (
                            address.locality ?: address.subLocality ?: address.subAdminArea
                            )
                }

                if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    val guess = guessRegionFromLatLng(latLng.latitude, latLng.longitude)
                    if (guess != null) {
                        if (admin.isNullOrBlank()) admin = guess.first
                        if (cityOrGu.isNullOrBlank()) cityOrGu = guess.second
                    }
                }

                val locationName = when {
                    !admin.isNullOrBlank() && !cityOrGu.isNullOrBlank() -> "$admin $cityOrGu"
                    !admin.isNullOrBlank() -> admin!!
                    !cityOrGu.isNullOrBlank() -> cityOrGu!!
                    else -> "알 수 없음"
                }

                val sido = normalizeSidoName(admin ?: locationName)

                val todayStr = now.format(
                    DateTimeFormatter.ofPattern("MM월 dd일 | E요일", Locale.KOREA)
                )

                val result = coroutineScope {

                    val currentTempDef = async { weatherRepo.getTempAt(grid, now) }
                    val amTempDef      = async { weatherRepo.getTempAt(grid, now.withHour(9)) }
                    val pmTempDef      = async { weatherRepo.getTempAt(grid, now.withHour(15)) }
                    val popDef         = async { weatherRepo.getPopAt(grid, now) ?: 0 }

                    val airDef         = async { airRepo.getAirForSido(sido) }
                    val midListDef     = async { midRepo.getMidTermForecast(sido, now) }

                    val midList = midListDef.await()

                    val forecastList: List<DailyRow> = (1..7).map { offset ->
                        async {
                            val date = now.toLocalDate().plusDays(offset.toLong())
                            val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)

                            val mid = midList.firstOrNull { it.date == dateStr }
                            val dayStart = date.atStartOfDay(ZoneId.of("Asia/Seoul"))

                            var min = mid?.minTemp
                            var max = mid?.maxTemp
                            var rain = mid?.rainProb

                            if (offset <= 2 || min == null || max == null) {
                                val (shortMin, shortMax) =
                                    weatherRepo.getDailyMinMaxTemp(grid, dayStart)
                                if (shortMin != null) min = shortMin
                                if (shortMax != null) max = shortMax
                            }

                            if (offset <= 2) {
                                val noon = dayStart.withHour(12)
                                val shortPop = weatherRepo.getPopAt(grid, noon)
                                rain = when {
                                    shortPop != null -> shortPop
                                    mid?.rainProb != null -> mid.rainProb
                                    else -> 0
                                }
                            } else {
                                rain = rain ?: 0
                            }

                            DailyRow(
                                dateText = dayStart.format(
                                    DateTimeFormatter.ofPattern("MM월 dd일 (E)", Locale.KOREA)
                                ),
                                minTemp = min?.let { "${it}°" } ?: "-°",
                                maxTemp = max?.let { "${it}°" } ?: "-°",
                                rainProb = "${rain}%"
                            )
                        }
                    }.awaitAll()

                    val currentTemp = currentTempDef.await()
                    val amTemp = amTempDef.await()
                    val pmTemp = pmTempDef.await()
                    val pop = popDef.await()
                    val air = airDef.await()

                    WeatherDetailUiModel(
                        locationName = locationName,
                        dateText = todayStr,
                        currentTempText = "${currentTemp}°C",
                        airQualityText = air.airQualityLabel,
                        rainText = "${pop}%",
                        amTempText = "${amTemp}°C",
                        pmTempText = "${pmTemp}°C",
                        forecastList = forecastList
                    )
                }

                _uiState.postValue(result)
                hasLoaded = true

            } catch (e: Exception) {
                Log.e("WeatherDetailVM", "loadDetailWeather 실패: ${e.message}", e)
            }
        }
    }

    private fun guessRegionFromLatLng(lat: Double, lon: Double): Pair<String, String>? {
        var bestRegion: String? = null
        var bestSub: String? = null
        var bestDist = Double.MAX_VALUE

        for (region in Region.entries) {
            if (region == Region.SELECT) continue
            for (sub in region.subRegions) {
                val dist = (lat - sub.latitude).pow(2) + (lon - sub.longitude).pow(2)
                if (dist < bestDist) {
                    bestDist = dist
                    bestRegion = region.displayName
                    bestSub = sub.name
                }
            }
        }
        return if (bestRegion != null && bestSub != null && bestDist < 0.25)
            bestRegion!! to bestSub!!
        else null
    }
}
