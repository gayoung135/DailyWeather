package com.smartapp.dailyweather.ui.intro

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartapp.dailyweather.data.model.ConvertResult
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.model.LatLng
import com.smartapp.dailyweather.data.repo.LocationRepository
import kotlinx.coroutines.launch

class LocationGpsViewModel( private val repo: LocationRepository )
    : ViewModel() {
    suspend fun processLocationAndConvert(lon: Double, lat: Double): Boolean {
        return try {
            if (!isInKorea(lon, lat)) {
                Log.w("isInKorea", "한국 영역 밖: lon=$lon, lat=$lat")
                false
            } else {
                val grid = repo.convertLatLngToGrid(lon, lat)
                Log.d("Location", "grid=${grid.x},${grid.y}")
                true
            }
        } catch (e: Exception) {
            Log.e("KMAconvert", "변환 실패: ${e.message}")
            false
        }
    }

    private fun isInKorea(lon: Double, lat: Double) =
        lon in 124.0..132.0 && lat in 33.0..39.0
}

class LocationGpsViewModelFactory(
    private val locationRepository: LocationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationGpsViewModel::class.java)) {
            return LocationGpsViewModel(locationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}