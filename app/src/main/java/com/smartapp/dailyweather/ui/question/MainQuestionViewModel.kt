package com.smartapp.dailyweather.ui.question

import android.app.Application
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
        purposeCode: String?,
        styleCode: String?,
        skipped: Boolean
    ) {
        viewModelScope.launch {
            val now = SystemTimeProvider().nowKorea()  // 또는 TimeProvider 주입
            lastUserStateRepository.saveWithCurrentTemp(
                grid = grid,
                targetTimeKst = now,
                purposeCode = purposeCode,
                styleCode = styleCode,
                skipped = skipped
            )
        }
    }
}