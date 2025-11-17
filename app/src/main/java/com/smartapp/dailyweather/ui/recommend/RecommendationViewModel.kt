package com.smartapp.dailyweather.ui.recommend

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RecommendationViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DailyWeatherApp.instance.db
    private val timeProvider = SystemTimeProvider()

    private val lastUserStateRepo = LastUserStateRepository(db, timeProvider)
    private val airRepo = AirQualityRepository()
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

                // 4-1) 추천 생성 + DB 저장
                Log.d("RecommendVM", "추천 생성 시작...")
                recommendRepo.createRecommendationForLatestState(
                    sidoName = sidoName,
                    moodCode = moodCode
                )

                // 4-2) DB에서 다시 읽어오기 (moodId 포함)
                val domain = recommendRepo.loadRecommendationFromDbForLatestState()

                // 5) moodId 기반 색상 로드
                val moodColors = loadMoodColorsForMood(domain.moodId)

                val uiModel = RecommendationUiModel.fromDomain(
                    domain = domain,
                    moodColors = moodColors
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

    // 필요 시 JSON에서 직접 로드하는 버전 (지금은 안 씀)
    private fun loadMoodColorsFromJson(context: Context): List<MoodColorUi> {
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
    val moodColors: List<MoodColorUi>
) {
    companion object {
        fun fromDomain(
            domain: RecommendationWithMood,
            moodColors: List<MoodColorUi>
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
                moodColors = moodColors
            )
        }
    }
}