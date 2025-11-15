package com.smartapp.dailyweather.ui.question

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.data.local.AppDatabase
import com.smartapp.dailyweather.data.mapper.JsonToDto
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import kotlinx.coroutines.launch

class MainQuestionViewModel (app: Application) : AndroidViewModel(app) {

    private val db = DailyWeatherApp.instance.db

    private val lastUserStateRepository: LastUserStateRepository by lazy {
        LastUserStateRepository(db)
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

    fun saveLastUserStateWithApi(
        grid: GridPoint,
        selectedHour: Int?,
        purposeCode: String?,
        styleCode: String?,
        skipped: Boolean
    ) {
        viewModelScope.launch {
            val now = SystemTimeProvider().nowKorea()

            // 사용자가 시간 선택 안 했다면 현재 시간 유지
            val targetTime = if (selectedHour != null) {
                now.withHour(selectedHour).withMinute(0).withSecond(0)
            } else {
                now
            }

            Log.d("QuestionFlow", "[ViewModel] saveLastUserStateWithApi() 호출")
            Log.d("QuestionFlow", " - skipped = $skipped")
            Log.d("QuestionFlow", " - selectedHour = $selectedHour")
            Log.d("QuestionFlow", " - targetTime(기온 조회 시각) = $targetTime")
            Log.d("QuestionFlow", " - grid = (${grid.x}, ${grid.y})")
            Log.d("QuestionFlow", " - purposeCode = $purposeCode, styleCode = $styleCode")


            lastUserStateRepository.saveWithCurrentTemp(
                grid = grid,
                targetTimeKst = targetTime,   // ← 수정된 targetTime 반영
                purposeCode = purposeCode,
                styleCode = styleCode,
                skipped = skipped
            )
        }
    }
}