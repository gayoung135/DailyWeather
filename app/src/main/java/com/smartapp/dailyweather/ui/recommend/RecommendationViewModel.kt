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
import com.smartapp.dailyweather.data.repo.AirQualityUi
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import com.smartapp.dailyweather.data.repo.RecommendationRepository
import com.smartapp.dailyweather.data.repo.RecommendationWithMood
import com.smartapp.dailyweather.data.repo.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        timeProvider
    )

    private val _uiState = MutableLiveData<RecommendationUiModel>()
    val uiState: LiveData<RecommendationUiModel> = _uiState

    // 중복 호출 방지용 플래그
    private var isLoading = false

    /**
     * 코디 추천 + 화면용 날씨 정보 로딩
     *
     * @param forceRecreate
     *  - false (기본): DB에 저장된 코디 세트가 있으면 재사용, 없으면 새로 생성
     *  - true (새로고침): skipped=true(스킵/시간NO)이면 온도 재계산 + 항상 코디 세트 새로 생성
     */
    fun loadRecommendationAutoSido(forceRecreate: Boolean = false) {

        // 이미 로딩 중이고, 그냥 재사용 모드면(=forceRecreate=false) 호출 스킵
        if (isLoading && !forceRecreate) {
            Log.d("RecommendVM", "이미 추천 로딩 중 → 추가 호출 스킵 (forceRecreate=false)")
            return
        }

        // 실제 로딩 시작
        isLoading = true

        viewModelScope.launch {
            try {
                Log.d("RecommendVM", "=== 추천 로직 시작 (forceRecreate=$forceRecreate) ===")

                // 1) 마지막 사용자 상태 로드
                var state = lastUserStateRepo.getLatestState()
                    ?: run {
                        Log.e("RecommendVM", "최근 LastUserState 없음")
                        return@launch
                    }

                Log.d(
                    "RecommendVM",
                    "최근 상태(초기): tempId=${state.tempId}, tempCelsius=${state.tempCelsius}, " +
                            "purpose=${state.purposeId}, style=${state.styleId}, " +
                            "grid=(${state.x}, ${state.y}), moodCode=${state.moodCode}, skipped=${state.skipped}"
                )

                // 새로고침 + skipped=true(스킵 OR 시간 NO) → now 기준으로 temp 다시 조회 + UPDATE
                if (forceRecreate && state.skipped) {
                    state = lastUserStateRepo.refreshLatestStateTempIfSkipped(state)
                    Log.d(
                        "RecommendVM",
                        "새로고침: skipped=true라 현재 기온으로 상태 갱신 후 사용 → " +
                                "tempId=${state.tempId}, tempCelsius=${state.tempCelsius}, lastAccess=${state.lastAccessTime}"
                    )
                }

                val grid = GridPoint(state.x, state.y)

                // ─────────────────────────────
                // 2) 위치 정보 (격자 → 위경도 → 행정구역)
                //   - 수동 선택(regionName/subRegionName) 우선
                //   - 없으면 Geocoder(GPS)로 admin/cityOrGu 추론
                // ─────────────────────────────
                val latLng = KmaGridConverter.toLatLng(grid.x, grid.y)
                Log.d(
                    "RecommendVM",
                    "격자→위경도 결과 lat=${latLng.latitude}, lon=${latLng.longitude}"
                )

                val context = getApplication<Application>()
                val geocoder = Geocoder(context, Locale.KOREA)

                var admin: String? = state.regionName
                var cityOrGu: String? = state.subRegionName

                val address = if (admin.isNullOrBlank() || cityOrGu.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
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
                } else {
                    null
                }

                if (address != null) {
                    if (admin.isNullOrBlank()) {
                        admin = address.adminArea
                    }
                    if (cityOrGu.isNullOrBlank()) {
                        cityOrGu =
                            address.locality ?: address.subLocality ?: address.subAdminArea
                    }
                }

                if (admin.isNullOrBlank()) {
                    admin = "서울특별시"
                }

                val locationName = when {
                    !admin.isNullOrBlank() && !cityOrGu.isNullOrBlank() -> "$admin $cityOrGu"
                    !admin.isNullOrBlank() -> admin!!
                    !cityOrGu.isNullOrBlank() -> cityOrGu!!
                    else -> "알 수 없음"
                }

                val sidoName = normalizeSidoName(admin!!)
                Log.d("RecommendVM", "정규화된 sidoName(에어코리아용) = $sidoName, cityOrGu=$cityOrGu")

                // ─────────────────────────────
                // 3) 화면용 날씨 정보 계산
                // ─────────────────────────────

                // (1) 온도: DB에 저장해 둔 실제 기온(tempCelsius)을 그대로 사용
                val tempC = state.tempCelsius
                val tempText = if (tempC != null) {
                    "${tempC}°C"
                } else {
                    "정보 없음"
                }
                Log.d("RecommendVM", "화면용 온도 tempCelsius=$tempC, text=$tempText")

                // (2) 미세먼지 등급 (24시간 평균 기준으로 Outfit 화면에 표시)
                val airUi: AirQualityUi = try {
                    airRepo.getAirForOutfit(
                        sidoName = sidoName,
                        stationNameHint = cityOrGu
                    )
                } catch (e: Exception) {
                    Log.e("RecommendVM", "대기질 조회 실패: ${e.message}", e)
                    AirQualityUi(code = "normal", airQualityLabel = "보통")
                }

                val airText = airUi.airQualityLabel
                val airCode24h = airUi.code
                Log.d("RecommendVM", "air(code24h=${airUi.code}, label=${airText})")

                // (3) 강수확률 POP (%): "외출 시간(lastAccessTime)" 기준으로 조회
                val targetTimeKst = timeProvider.parseKstDateTime(state.lastAccessTime)
                Log.d("RecommendVM", "화면용 POP 기준 시간(KST) = $targetTimeKst")

                val popPercent: Int = try {
                    weatherRepo.getPopAt(grid, targetTimeKst) ?: 0
                } catch (e: Exception) {
                    Log.e("RecommendVM", "POP 조회 실패(뷰모델): ${e.message}", e)
                    0
                }
                val rainText = "${popPercent}%"
                Log.d("RecommendVM", "POP(외출시간 기준 %)=$popPercent, rainText=$rainText")

                // ─────────────────────────────
                // 4) 코디 추천: forceRecreate 여부에 따라 처리
                // ─────────────────────────────

                // 기본 모드에서는 DB에 저장된 코디 세트가 있으면 재사용
                val cachedDomain: RecommendationWithMood? =
                    if (!forceRecreate) {
                        try {
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
                    } else {
                        // 새로고침 모드에서는 무조건 새로 생성할 것이므로 캐시는 사용 안 함
                        null
                    }

                val finalDomain: RecommendationWithMood =
                    if (cachedDomain != null) {
                        cachedDomain
                    } else {
                        Log.d(
                            "RecommendVM",
                            if (forceRecreate)
                                "forceRecreate=true → 항상 새 추천 생성 시작..."
                            else
                                "저장된 코디 없음 → 새 추천 생성 시작..."
                        )

                        // 최신 LastUserState 기준으로 코디 2세트 생성 + DB 저장
                        recommendRepo.createRecommendationForLatestState(
                            airQualityCode24h = airCode24h,
                            moodCode = state.moodCode
                        )

                        // 방금 만든 코디 세트를 다시 로드
                        recommendRepo.loadRecommendationFromDbForLatestState()
                    }

                // 5) moodId 기반 색상 로드
                val moodColors = loadMoodColorsForMood(finalDomain.moodId)

                // 6) 제목에 쓸 "추천 시간" 라벨 생성
                val titleText = buildRecommendationTitle(targetTimeKst)

                // 7) UI 모델로 변환
                val uiModel = RecommendationUiModel.fromDomain(
                    domain = finalDomain,
                    moodColors = moodColors,
                    temperatureText = tempText,
                    airQualityText = airText,
                    rainText = rainText,
                    recommendationTitle = titleText
                )
                _uiState.value = uiModel

                Log.d("RecommendVM", "=== 추천 로직 완료 ===")

            } catch (e: Exception) {
                Log.e("RecommendVM", "추천 로직 실패: ${e.message}", e)
            } finally {
                // 로딩 끝났으니 상태 해제
                isLoading = false
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

    /**
     * 제목 문자열 생성
     */
    private fun buildRecommendationTitle(targetTimeKst: ZonedDateTime): String {
        val datePart = targetTimeKst.format(
            DateTimeFormatter.ofPattern("MM월 dd일", Locale.KOREA)
        )

        val hour = targetTimeKst.hour
        val minute = targetTimeKst.minute

        val (ampmLabel, hour12) = when {
            hour == 0 -> "AM" to 12
            hour in 1..11 -> "AM" to hour
            hour == 12 -> "PM" to 12
            else -> "PM" to (hour - 12)
        }

        val timePart = String.format("%s %d:%02d", ampmLabel, hour12, minute)

        return "$datePart $timePart 추천 옷차림"
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
    val rainText: String,          // 예: "20%" 또는 "정보 없음"
    val recommendationTitle: String // 예: "11월 26일 오전 5시 추천 옷차림"
) {
    companion object {
        fun fromDomain(
            domain: RecommendationWithMood,
            moodColors: List<MoodColorUi>,
            temperatureText: String,
            airQualityText: String,
            rainText: String,
            recommendationTitle: String
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
                rainText = rainText,
                recommendationTitle = recommendationTitle
            )
        }
    }
}