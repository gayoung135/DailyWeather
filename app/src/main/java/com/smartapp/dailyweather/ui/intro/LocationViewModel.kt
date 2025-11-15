package com.smartapp.dailyweather.ui.intro

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.model.SubRegion
import com.smartapp.dailyweather.data.repo.LocationRepository


class LocationViewModel (private val repo: LocationRepository) : ViewModel() {

    private val _grid = MutableLiveData<GridPoint>()
    val grid: LiveData<GridPoint> = _grid

    suspend fun processLocationAndConvert(lon: Double, lat: Double): Boolean {
        return try {
            if (!isInKorea(lon, lat)) {
                Log.w("isInKorea", "한국 영역 밖: lon=$lon, lat=$lat")
                false
            } else {
                val gridPoint = repo.convertLatLngToGrid(lon, lat)
                Log.d("Location", "grid=${gridPoint.x},${gridPoint.y}")

                _grid.value = gridPoint

                true
            }
        } catch (e: Exception) {
            Log.e("KMAconvert", "변환 실패: ${e.message}")
            false
        }
    }

    private fun isInKorea(lon: Double, lat: Double) =
        lon in 124.0..132.0 && lat in 33.0..39.0

    private val _selectedRegion = MutableLiveData<Region>()
    val selectedRegion: LiveData<Region> = _selectedRegion

    private val _selectedSubRegion = MutableLiveData<SubRegion?>()
    val selectedSubRegion: LiveData<SubRegion?> = _selectedSubRegion

    fun onRegionSelected(region: Region) {
        _selectedRegion.value = region
        _selectedSubRegion.value = null
    }

    fun onSubRegionSelected(sub: SubRegion) {
        _selectedSubRegion.value = sub
    }

    suspend fun requestGrid(): GridPoint? {
        val sub = _selectedSubRegion.value ?: return null
        val gridPoint = repo.convertLatLngToGrid(sub.longitude, sub.latitude)

        _grid.value = gridPoint
        return gridPoint
    }
}


class LocationViewModelFactory(
    private val locationRepository: LocationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            return LocationViewModel(locationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}