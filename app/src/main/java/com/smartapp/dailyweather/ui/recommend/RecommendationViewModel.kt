package com.smartapp.dailyweather.ui.recommend

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
import com.smartapp.dailyweather.core.normalizeSidoName
import com.smartapp.dailyweather.core.parseKstDateTime
import com.smartapp.dailyweather.data.mapper.JsonToDto
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.repo.AirQualityRepository
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import com.smartapp.dailyweather.data.repo.RecommendationRepository
import com.smartapp.dailyweather.data.repo.RecommendationWithMood
import com.smartapp.dailyweather.data.repo.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RecommendationViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DailyWeatherApp.instance.db
    private val timeProvider = SystemTimeProvider()

    private val lastUserStateRepo = LastUserStateRepository(db, timeProvider)
    private val airRepo = AirQualityRepository()
    private val weatherRepo = WeatherRepository(timeProvider)

    private val recommendRepo = RecommendationRepository(
        db,
        lastUserStateRepo,
        airRepo,
        timeProvider
    )

    private val _uiState = MutableLiveData<RecommendationUiModel>()
    val uiState: LiveData<RecommendationUiModel> = _uiState

    fun loadRecommendationAutoSido() {
        viewModelScope.launch {
            try {
                Log.d("RecommendVM", "=== 추천 로직 시작 ===")

                val state = lastUserStateRepo.getLatestState()
                    ?: run {
                        Log.e("RecommendVM", "최근 LastUserState 없음")
                        return@launch
                    }

                Log.d(
                    "RecommendVM",
                    "최근 상태: tempId=${state.tempId}, purpose=${state.purposeId}, style=${state.styleId}, grid=(${state.x}, ${state.y}), moodCode=${state.moodCode}"
                )

                val targetTimeKst = timeProvider.parseKstDateTime(state.lastAccessTime)
                Log.d(
                    "RecommendVM",
                    "추천 대상 시간(KST, lastUserState 기준) = $targetTimeKst"
                )

                val grid = GridPoint(state.x, state.y)

                // 2) 격자 → 위경도
                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)
                Log.d(
                    "RecommendVM",
                    "격자→위경도 결과 lat=${latLng.latitude}, lon=${latLng.longitude}"
                )

                // 3) Geocoder → sidoName
                val context = getApplication<Application>()
                val geocoder = Geocoder(context, Locale.KOREA)

                val address = withContext(Dispatchers.IO) {
                    try {
                        geocoder.getFromLocation(
                            latLng.latitude,
                            latLng.longitude,
                            1
                        )?.firstOrNull()
                    } catch (e: Exception) {
                        Log.e("RecommendVM", "Geocoder 실패: ${e.message}")
                        null
                    }
                }

                if (address == null) {
                    Log.w("RecommendVM", "Geocoder에서 주소를 찾지 못함 → 기본값 서울 사용")
                }

                val adminArea = address?.adminArea ?: "서울특별시"
                Log.d("RecommendVM", "Geocoder adminArea = $adminArea")

                val sidoName = normalizeSidoName(adminArea)
                Log.d("RecommendVM", "정규화된 sidoName(에어코리아용) = $sidoName")

                val moodCode = state.moodCode

                // ─────────────────────────────
                // [화면용 날씨 정보 계산]
                // ─────────────────────────────

                // 1) 온도: LastUserState에 이미 ℃ 값이 들어있다고 가정
                val tempC = state.tempId
                val tempText = "${tempC}°C"

                // 2) 미세먼지 등급 문자열
                val airCode = airRepo.getAirQualityCodeForRegion(sidoName)
                val airText = when (airCode) {
                    "good" -> "좋음"
                    "normal" -> "보통"
                    "bad" -> "나쁨"
                    "very_bad" -> "매우 나쁨"
                    else -> "정보 없음"
                }
                Log.d("RecommendVM", "airCode=$airCode, airText=$airText")

                // 3) 강수확률 POP (%)
                val popPercent: Int? = try {
                    weatherRepo.getPopAt(grid, targetTimeKst)
                } catch (e: Exception) {
                    Log.e("RecommendVM", "POP 조회 실패(뷰모델): ${e.message}", e)
                    null
                }
                val rainText = popPercent?.let { "${it}%" } ?: "정보 없음"
                Log.d("RecommendVM", "POP(%)=$popPercent, rainText=$rainText")

                // ─────────────────────────────
                // 4) 코디 추천: DB에 있으면 재사용, 없으면 새로 생성
                // ─────────────────────────────

                // 4-1) 우선 DB에 저장된 코디 세트가 있는지 시도
                val cachedDomain: RecommendationWithMood? = try {
                    val domain = recommendRepo.loadRecommendationFromDbForLatestState()
                    Log.d("RecommendVM", "기존 저장된 코디 세트 발견 → 재사용")
                    domain
                } catch (e: Exception) {
                    Log.w(
                        "RecommendVM",
                        "저장된 코디 세트 없음 또는 로드 실패 → 새로 생성 예정: ${e.message}"
                    )
                    null
                }

                // 4-2) 없으면 새로 생성 + DB 저장 후 다시 로드
                val finalDomain: RecommendationWithMood = if (cachedDomain != null) {
                    cachedDomain
                } else {
                    Log.d("RecommendVM", "저장된 코디 없음 → 새 추천 생성 시작...")
                    recommendRepo.createRecommendationForLatestState(
                        sidoName = sidoName,
                        moodCode = moodCode
                    )
                    recommendRepo.loadRecommendationFromDbForLatestState()
                }

                // 5) moodId 기반 색상 로드
                val moodColors = loadMoodColorsForMood(finalDomain.moodId)

                // 6) UI 모델로 변환 (온도/미세먼지/강수확률 텍스트 포함)
                val uiModel = RecommendationUiModel.fromDomain(
                    domain = finalDomain,
                    moodColors = moodColors,
                    temperatureText = tempText,
                    airQualityText = airText,
                    rainText = rainText
                )
                _uiState.value = uiModel

                Log.d("RecommendVM", "=== 추천 로직 완료 ===")

            } catch (e: Exception) {
                Log.e("RecommendVM", " 로직 실패: ${e.message}", e)
            }
        }
    }

    private suspend fun loadMoodColorsForMood(moodId: Int?): List<MoodColorUi> {
        if (moodId == null) return emptyList()

        val colors = db.MoodColorDao().getByMood(moodId)

        return colors.map {
            MoodColorUi(
                name = it.cname,
                hex = it.hex
            )
        }
    }

    // (지금은 사용 안하지만 참고용)
    private fun loadMoodColorsFromJson(context: Application): List<MoodColorUi> {
        val dto = JsonToDto.parseFromAssets(context)
        return dto.moodColor.flatMap { (_, colorList) ->
            colorList.map { colorDto ->
                MoodColorUi(
                    name = colorDto.cname,
                    hex = colorDto.hex
                )
            }
        }
    }
}

/* ---------- 화면에서 쓰기 좋은 UI 모델 ---------- */

data class CoordinationItemUi(
    val id: String,
    val displayName: String,
    val categoryId: Int
)

data class CoordinationUiSet(
    val items: List<CoordinationItemUi>
)

/** 기분 색상 UI용 */
data class MoodColorUi(
    val name: String,   // "노랑"
    val hex: String     // "#FFD54F"
)

data class RecommendationUiModel(
    val firstSet: CoordinationUiSet,
    val secondSet: CoordinationUiSet,
    val moodColors: List<MoodColorUi>,
    val temperatureText: String,   // 예: "14°C"
    val airQualityText: String,    // 예: "좋음"
    val rainText: String           // 예: "20%" 또는 "정보 없음"
) {
    companion object {
        fun fromDomain(
            domain: RecommendationWithMood,
            moodColors: List<MoodColorUi>,
            temperatureText: String,
            airQualityText: String,
            rainText: String
        ): RecommendationUiModel {

            val first = CoordinationUiSet(
                items = domain.firstSet.items.map {
                    CoordinationItemUi(
                        id = it.itemId,
                        displayName = it.displayName,
                        categoryId = it.categoryId
                    )
                }
            )

            val second = CoordinationUiSet(
                items = domain.secondSet.items.map {
                    CoordinationItemUi(
                        id = it.itemId,
                        displayName = it.displayName,
                        categoryId = it.categoryId
                    )
                }
            )

            return RecommendationUiModel(
                firstSet = first,
                secondSet = second,
                moodColors = moodColors,
                temperatureText = temperatureText,
                airQualityText = airQualityText,
                rainText = rainText
            )
        }
    }
}
