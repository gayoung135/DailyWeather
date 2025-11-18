package com.smartapp.dailyweather.ui.question

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.data.mapper.JsonToDto
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.repo.LastUserStateRepository

class MainQuestionViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DailyWeatherApp.instance.db

    private val lastUserStateRepository: LastUserStateRepository by lazy {
        LastUserStateRepository(db)
    }

    private val _selectedHour = MutableLiveData<Int?>()
    val selectedHour: LiveData<Int?> = _selectedHour

    fun setSelectedHour(hour: Int?) {
        _selectedHour.value = hour
    }

    private val _selectedMood = MutableLiveData<String?>()
    val selectedMood: LiveData<String?> = _selectedMood

    fun setSelectedMood(code: String?) {
        _selectedMood.value = code
    }

    lateinit var enum1: JsonToDto.EnumsDto
    lateinit var enum2: Map<String, List<JsonToDto.MoodColorDto>>
    lateinit var list: List<String>

    fun enumToDTO() {
        val context = getApplication<Application>()
        val enum = JsonToDto.parseFromAssets(context)
        enum1 = enum.enum
        enum2 = enum.moodColor
    }

    fun getPurposeOptions(): List<String> =
        enum1.purpose.map { eng ->
            when (eng.lowercase()) {
                "office" -> "출근"
                "celebration" -> "경사"
                "date" -> "데이트"
                "travel" -> "여행"
                "none" -> "없음"
                else -> eng
            }
        }

    fun getMoodOptions(): List<String> =
        enum2.keys.map { eng ->
            when (eng.lowercase()) {
                "good" -> "좋음"
                "normal" -> "보통"
                "bad" -> "나쁨"
                else -> eng
            }
        }

    fun getStyleOptions(): List<String> =
        enum1.likeStyle.map { eng ->
            when (eng.lowercase()) {
                "street" -> "스트리트"
                "formal" -> "포멀"
                "casual" -> "캐주얼"
                "lovely" -> "러블리"
                "none" -> "없음"
                else -> eng
            }
        }

    // UI(한글) ↔ 코드(영어) 변환
    fun uiToCode(ui: String): String =
        when (ui) {
            // 목적
            "출근" -> "office"
            "경사" -> "celebration"
            "데이트" -> "date"
            "여행" -> "travel"
            "없음" -> "none"

            // 기분
            "좋음" -> "good"
            "보통" -> "normal"
            "나쁨" -> "bad"

            // 스타일
            "스트리트" -> "street"
            "포멀" -> "formal"
            "캐주얼" -> "casual"
            "러블리" -> "lovely"
            "없음" -> "none"

            else -> ui // 이미 "office" 같은 코드가 들어온 경우 그대로 유지
        }

    suspend fun saveLastUserStateWithApi(
        grid: GridPoint,
        regionName: String?,
        subRegionName: String?,
        selectedHourOffset: Int?,
        purposeCode: String?,  // UI에서 들어온 값 (한글일 수도 있음)
        styleCode: String?,
        moodCode: String?,
        skipped: Boolean
    ) {
        val now = SystemTimeProvider().nowKorea()
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val targetTime = if (selectedHourOffset != null) {
            now.plusHours(selectedHourOffset.toLong())
        } else {
            now
        }

        // 여기서 UI 한글 값을 DB 코드(영어)로 변환
        val normalizedPurposeCode = purposeCode?.let { uiToCode(it) }
        val normalizedStyleCode   = styleCode?.let { uiToCode(it) }
        val normalizedMoodCode    = moodCode?.let { uiToCode(it) }

        Log.d("QuestionFlow", " [ViewModel] saveLastUserStateWithApi() 호출")
        Log.d("QuestionFlow", "  - skipped = $skipped")
        Log.d("QuestionFlow", "  - selectedHour(offset) = $selectedHourOffset")
        Log.d("QuestionFlow", "  - targetTime(기온 조회 시각) = $targetTime")
        Log.d("QuestionFlow", "  - grid = (${grid.x}, ${grid.y})")
        Log.d(
            "QuestionFlow",
            "  - (UI 원본) purposeCode = $purposeCode, styleCode = $styleCode, moodCode = $moodCode"
        )
        Log.d(
            "QuestionFlow",
            "  - (DB 코드) purposeCode = $normalizedPurposeCode, styleCode = $normalizedStyleCode, moodCode = $normalizedMoodCode"
        )

        lastUserStateRepository.saveWithCurrentTemp(
            grid = grid,
            regionName = regionName,
            subRegionName = subRegionName,
            targetTimeKst = targetTime,
            purposeCode = normalizedPurposeCode,
            styleCode = normalizedStyleCode,
            moodCode = normalizedMoodCode,
            skipped = skipped
        )
    }
}
