package com.smartapp.dailyweather.ui.intro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.model.SubRegion
import com.smartapp.dailyweather.data.repo.LocationRepository

class ManualLocationViewModel(private val repo: LocationRepository) : ViewModel() {

    private val _selectedRegion = MutableLiveData<Region>()
    val selectedRegion: LiveData<Region> = _selectedRegion

    private val _selectedSubRegion = MutableLiveData<SubRegion?>()
    val selectedSubRegion: MutableLiveData<SubRegion?> = _selectedSubRegion

    fun onRegionSelected(region: Region) {
        _selectedRegion.value = region
        _selectedSubRegion.value = null
    }

    fun onSubRegionSelected(sub: SubRegion) {
        _selectedSubRegion.value = sub
    }

    suspend fun requestGrid(): GridPoint? {
        val sub = _selectedSubRegion.value ?: return null
        return repo.convertLatLngToGrid(sub.longitude, sub.latitude)
    }
}

class ManualLocationViewModelFactory(
    private val repo: LocationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManualLocationViewModel::class.java)) {
            return ManualLocationViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}