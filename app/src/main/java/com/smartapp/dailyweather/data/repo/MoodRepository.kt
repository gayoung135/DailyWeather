package com.smartapp.dailyweather.data.repo

import com.smartapp.dailyweather.data.local.AppDatabase
import com.smartapp.dailyweather.data.local.entity.MoodColorEntity

data class MoodWithColors(
    val moodCode: String,
    val colors: List<MoodColorEntity>
)

class MoodRepository(
    private val db: AppDatabase
) {
    private val moodDao = db.MoodDao()
    private val moodColorDao = db.MoodColorDao()

    /** mood_code로 mood_id 찾고, 해당 mood의 색상들을 모두 가져온다 */
    suspend fun getMoodWithColorsOrNull(moodCode: String?): MoodWithColors? {
        if (moodCode.isNullOrBlank()) return null

        val mood = moodDao.getByCode(moodCode) ?: return null
        val colors = moodColorDao.getByMood(mood.moodId)

        return MoodWithColors(
            moodCode = mood.moodCode,
            colors = colors
        )
    }
}