package com.smartapp.dailyweather.data.model

data class LatLng(val latitude: Double, val longitude: Double)
data class GridPoint(val x: Int, val y: Int)

sealed class ConvertResult {
    data object Loading : ConvertResult()
    data class Success(val latLng: LatLng, val grid: GridPoint) : ConvertResult()
    data class Error(val message: String, val cause: Throwable? = null) : ConvertResult()
}