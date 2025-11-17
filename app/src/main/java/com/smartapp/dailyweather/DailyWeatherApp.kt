package com.smartapp.dailyweather

import android.app.Application
import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
import androidx.room.Room
import com.smartapp.dailyweather.data.local.AppDatabase
import com.smartapp.dailyweather.data.mapper.JsonToEntity


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyWeatherApp : Application() {

    // 전역에서 쓸 Room DB
    lateinit var db: AppDatabase
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 1) Room DB 인스턴스 생성
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "DailyWeather7.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val prefs = getSharedPreferences("seed_prefs", MODE_PRIVATE)
        val seeded = prefs.getBoolean("db_seeded_v7", false)

        appScope.launch {
            try {
                // [개발용] 매 실행마다 LastUserState만 초기화
                db.lastUserStateDao().clearAll()

                // DB가 비었는지  검사 (카테고리만 체크)
                val isEmpty = db.categoryDao().getAll().isEmpty()

                if (!seeded || isEmpty) {
                    JsonToEntity.seedFromAssets(
                        context = this@DailyWeatherApp,
                        db = db,
                        assetFile = "style.json",
                        clearBefore = true // 최초/복구 시는 깔끔히 넣기
                    )
                    prefs.edit().putBoolean("db_seeded_v7", true).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    companion object {
        lateinit var instance: DailyWeatherApp
            private set
        fun context() = instance.applicationContext
    }

    init {
        instance = this
    }
}
