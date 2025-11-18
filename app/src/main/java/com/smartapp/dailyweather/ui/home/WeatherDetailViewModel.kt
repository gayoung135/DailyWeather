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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

// 7일 예보 한 줄
data class DailyRow(
    val dateText: String,
    val minTemp: String,
    val maxTemp: String,
    val rainProb: String
)

// 상세 날씨 전체 UI 데이터
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

    private var hasLoaded = false   // 중복 로드 방지

    fun loadDetailWeather(force: Boolean = false) {
        if (!force && hasLoaded && _uiState.value != null) {
            Log.d("WeatherDetailVM", "이미 로딩된 상세날씨 → 스킵")
            return
        }

        viewModelScope.launch {
            try {
                val state = lastUserStateRepo.getLatestState() ?: return@launch

                val grid = GridPoint(state.x, state.y)
                val now = timeProvider.nowKorea().withSecond(0).withNano(0)

                // 현재 / 오전 / 오후 기온
                val currentTemp = weatherRepo.getTempAt(grid, now)
                val amTemp = weatherRepo.getTempAt(grid, now.withHour(9))
                val pmTemp = weatherRepo.getTempAt(grid, now.withHour(15))

                // 현재 강수확률
                val pop = weatherRepo.getPopAt(grid, now) ?: 0

                // 위치명 계산

                var admin: String? = state.regionName
                var cityOrGu: String? = state.subRegionName

                val geocoder = Geocoder(appContext, Locale.KOREA)
                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)

                // DB 값이 비면 Geocoder 보완
                val address = if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                ?.firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null

                if (address != null) {
                    val gcAdmin = address.adminArea?.takeIf { it.isNotBlank() }
                    val gcCity =
                        address.locality?.takeIf { it.isNotBlank() }
                            ?: address.subLocality?.takeIf { it.isNotBlank() }
                            ?: address.subAdminArea?.takeIf { it.isNotBlank() }

                    if (admin.isNullOrBlank()) admin = gcAdmin
                    if (cityOrGu.isNullOrBlank()) cityOrGu = gcCity
                }

                // 그래도 없으면 Region 매칭
                if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    val guess = guessRegionFromLatLng(latLng.latitude, latLng.longitude)
                    if (guess != null) {
                        if (admin.isNullOrBlank()) admin = guess.first
                        if (cityOrGu.isNullOrBlank()) cityOrGu = guess.second
                    }
                }

                val locationName = when {
                    !admin.isNullOrBlank() && !cityOrGu.isNullOrBlank() ->
                        "$admin $cityOrGu"
                    !admin.isNullOrBlank() -> admin!!
                    !cityOrGu.isNullOrBlank() -> cityOrGu!!
                    else -> "알 수 없음"
                }

                val sido = normalizeSidoName(admin ?: locationName)

                // 미세먼지
                val air = airRepo.getAirForSido(sido)
                val airText = air?.airQualityLabel ?: "알 수 없음"


                // 중기예보(3~10일)
                val midList = midRepo.getMidTermForecast(sido, now)


                // 내일부터 7일 예보

                val forecast = mutableListOf<DailyRow>()

                for (offset in 1..7) {
                    val date = now.toLocalDate().plusDays(offset.toLong())
                    val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
                    val mid = midList.firstOrNull { it.date == dateStr }

                    val dayStart = date.atStartOfDay(ZoneId.of("Asia/Seoul"))

                    var min = mid?.minTemp
                    var max = mid?.maxTemp
                    var rain = mid?.rainProb

                    // 1~2일 / 중기 부족 → 단기 TMP 보완
                    if (offset <= 2 || min == null || max == null) {
                        val (shortMin, shortMax) = weatherRepo.getDailyMinMaxTemp(grid, dayStart)
                        if (shortMin != null) min = shortMin
                        if (shortMax != null) max = shortMax
                    }

                    // 내일/모레 POP
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

                    val dateText = dayStart.format(
                        DateTimeFormatter.ofPattern("MM월 dd일 (E)", Locale.KOREA)
                    )

                    forecast.add(
                        DailyRow(
                            dateText = dateText,
                            minTemp = min?.let { "${it}°" } ?: "-°",
                            maxTemp = max?.let { "${it}°" } ?: "-°",
                            rainProb = "${rain}%"
                        )
                    )
                }

                val todayStr = now.format(
                    DateTimeFormatter.ofPattern("MM월 dd일 | E요일", Locale.KOREA)
                )

                _uiState.value = WeatherDetailUiModel(
                    locationName = locationName,
                    dateText = todayStr,
                    currentTempText = "${currentTemp}°C",
                    airQualityText = airText,
                    rainText = "${pop}%",
                    amTempText = "${amTemp}°C",
                    pmTempText = "${pmTemp}°C",
                    forecastList = forecast
                )

                hasLoaded = true

            } catch (e: Exception) {
                Log.e("WeatherDetailVM", "loadDetailWeather 실패: ${e.message}", e)
            }
        }
    }

    //  Region 기반 좌표 추정 (기존 로직 그대로 유지)

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

        return if (bestRegion != null && bestSub != null && bestDist < 0.25) {
            bestRegion!! to bestSub!!
        } else null
    }
}
