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

    fun getPurposeOptions(): List<String> = enum1.purpose
    fun getMoodOptions(): List<String> = enum2.keys.toList()
    fun getStyleOptions(): List<String> = enum1.likeStyle

    suspend fun saveLastUserStateWithApi(
        grid: GridPoint,
        regionName: String?,
        subRegionName: String?,
        selectedHourOffset: Int?,
        purposeCode: String?,
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

        Log.d("QuestionFlow", " [ViewModel] saveLastUserStateWithApi() 호출")
        Log.d("QuestionFlow", "  - skipped = $skipped")
        Log.d("QuestionFlow", "  - selectedHour(offset) = $selectedHourOffset")
        Log.d("QuestionFlow", "  - targetTime(기온 조회 시각) = $targetTime")
        Log.d("QuestionFlow", "  - grid = (${grid.x}, ${grid.y})")
        Log.d("QuestionFlow", "  - purposeCode = $purposeCode, styleCode = $styleCode, moodCode = $moodCode")

        lastUserStateRepository.saveWithCurrentTemp(
            grid = grid,
            regionName = regionName,
            subRegionName = subRegionName,
            targetTimeKst = targetTime,
            purposeCode = purposeCode,
            styleCode = styleCode,
            moodCode = moodCode,
            skipped = skipped
        )
    }
}