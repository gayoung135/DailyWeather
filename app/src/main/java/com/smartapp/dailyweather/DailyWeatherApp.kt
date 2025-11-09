package com.smartapp.dailyweather

import android.app.Application

class DailyWeatherApp : Application() {

    override fun onCreate() {
        super.onCreate()


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