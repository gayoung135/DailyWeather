// ui/home/WeatherDetailViewModel.kt
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
import com.smartapp.dailyweather.data.repo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

// 7일 예보 1줄
data class DailyRow(
    val dateText: String,
    val minTemp: String,
    val maxTemp: String,
    val rainProb: String
)

// 상세날씨 전체 UI 데이터
data class WeatherDetailUiModel(
    val locationName: String,
    val dateText: String,
    val currentTempText: String,   // 현재 시각 온도
    val airQualityText: String,
    val rainText: String,
    val amTempText: String,        // 오늘 오전 온도
    val pmTempText: String,        // 오늘 오후 온도
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

    // 이미 로딩한 적 있는지
    private var hasLoaded = false

    fun loadDetailWeather(force: Boolean = false) {
        // 이미 로드된 데이터가 있고 강제 새로고침이 아니면 스킵
        if (!force && hasLoaded && _uiState.value != null) {
            Log.d("WeatherDetailVM", "이미 로딩된 상세날씨 → 재요청 스킵")
            return
        }

        viewModelScope.launch {
            try {
                val state = lastUserStateRepo.getLatestState() ?: return@launch

                val grid = GridPoint(state.x, state.y)
                val now = timeProvider.nowKorea()
                    .withSecond(0)
                    .withNano(0)

                // 현재/오전/오후 온도
                val currentTemp = weatherRepo.getTempAt(grid, now)
                val amTime = now.withHour(9).withMinute(0)
                val pmTime = now.withHour(15).withMinute(0)
                val amTemp = weatherRepo.getTempAt(grid, amTime)
                val pmTemp = weatherRepo.getTempAt(grid, pmTime)

                // 강수확률
                val pop = weatherRepo.getPopAt(grid, now) ?: 0

                // ---------------- 위치 이름 계산 ----------------

                // 1순위: 사용자가 선택했을 때 DB에 저장된 행정구역 이름
                var admin: String? = state.regionName      // 시/도
                var cityOrGu: String? = state.subRegionName // 시/군/구

                val geocoder = Geocoder(appContext, Locale.KOREA)
                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)

                // 2순위: DB에 값이 비어 있을 때만 Geocoder 로 보완
                val address = if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            geocoder.getFromLocation(
                                latLng.latitude,
                                latLng.longitude,
                                1
                            )?.firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else {
                    null
                }

                if (address != null) {
                    val gcAdmin = address.adminArea?.takeIf { it.isNotBlank() }
                    val gcCityOrGu =
                        address.locality?.takeIf { it.isNotBlank() }
                            ?: address.subLocality?.takeIf { it.isNotBlank() }
                            ?: address.subAdminArea?.takeIf { it.isNotBlank() }

                    Log.d(
                        "WeatherDetailVM",
                        "addr admin=${address.adminArea}, locality=${address.locality}, " +
                                "subLocality=${address.subLocality}, subAdmin=${address.subAdminArea}"
                    )

                    if (admin.isNullOrBlank() && !gcAdmin.isNullOrBlank()) admin = gcAdmin
                    if (cityOrGu.isNullOrBlank() && !gcCityOrGu.isNullOrBlank()) cityOrGu = gcCityOrGu
                }

                // 3순위: 그래도 비면 위경도 기반 Region 추정
                if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    val guessed = guessRegionFromLatLng(latLng.latitude, latLng.longitude)
                    if (guessed != null) {
                        if (admin.isNullOrBlank()) admin = guessed.first
                        if (cityOrGu.isNullOrBlank()) cityOrGu = guessed.second
                        Log.d(
                            "WeatherDetailVM",
                            "Region 매칭으로 보정 → admin=$admin, cityOrGu=$cityOrGu"
                        )
                    }
                }

                // 최종 표시용
                val locationName = when {
                    !admin.isNullOrBlank() && !cityOrGu.isNullOrBlank() ->
                        "$admin $cityOrGu"          // 예: "부산광역시 연제구"
                    !admin.isNullOrBlank() ->
                        admin
                    !cityOrGu.isNullOrBlank() ->
                        cityOrGu
                    else ->
                        "알 수 없음"
                }

                // 시/도 이름 → 미세먼지용 normalize
                val sido = normalizeSidoName(admin ?: locationName)

                // ---------------- 위치 이름 계산 끝 ----------------

                // 미세먼지
                val air = airRepo.getAirForSido(sido)
                val airText = air?.airQualityLabel ?: "알 수 없음"

                // 중기예보
                val midList = midRepo.getMidTermForecast(sido, now)

                // 내일부터 7일
                val forecast = mutableListOf<DailyRow>()
                for (offset in 1..7) {
                    val date = now.toLocalDate().plusDays(offset.toLong())
                    val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
                    val mid = midList.firstOrNull { it.date == dateStr }

                    var min = mid?.minTemp
                    var max = mid?.maxTemp
                    val rain = mid?.rainProb

                    val dayStart = date.atStartOfDay(ZoneId.of("Asia/Seoul"))

                    // 1~2일 뒤거나, 중기 값이 없으면 단기예보로 보완
                    if (offset <= 2 || min == null || max == null) {
                        val (shortMin, shortMax) =
                            weatherRepo.getDailyMinMaxTemp(grid, dayStart)
                        if (shortMin != null) min = shortMin
                        if (shortMax != null) max = shortMax
                    }

                    val dateText = dayStart.format(
                        DateTimeFormatter.ofPattern("MM월 dd일 (E)", Locale.KOREA)
                    )

                    forecast.add(
                        DailyRow(
                            dateText = dateText,
                            minTemp = min?.let { "${it}°" } ?: "-°",
                            maxTemp = max?.let { "${it}°" } ?: "-°",
                            rainProb = rain?.let { "${it}%" } ?: "0%"
                        )
                    )
                }

                val today = now.format(
                    DateTimeFormatter.ofPattern("MM월 dd일 | E요일", Locale.KOREA)
                )

                _uiState.value = WeatherDetailUiModel(
                    locationName = locationName,
                    dateText = today,
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

    // 기존 guessRegionFromLatLng 는 그대로 사용
    private fun guessRegionFromLatLng(lat: Double, lon: Double): Pair<String, String>? {
        var bestRegionName: String? = null
        var bestSubName: String? = null
        var bestDist = Double.MAX_VALUE

        for (region in Region.entries) {
            if (region == Region.SELECT) continue

            for (sub in region.subRegions) {
                val dLat = lat - sub.latitude
                val dLon = lon - sub.longitude
                val dist = dLat.pow(2) + dLon.pow(2)

                if (dist < bestDist) {
                    bestDist = dist
                    bestRegionName = region.displayName
                    bestSubName = sub.name
                }
            }
        }

        return if (bestRegionName != null && bestSubName != null && bestDist < 0.25) {
            bestRegionName!! to bestSubName!!
        } else {
            null
        }
    }
}
