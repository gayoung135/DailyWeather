package com.smartapp.dailyweather.ui.intro

import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.*
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.core.KmaGridConverter
import com.smartapp.dailyweather.data.local.entity.LastUserStateEntity
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.model.SubRegion
import com.smartapp.dailyweather.data.repo.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.pow

class LocationViewModel(
    private val repo: LocationRepository
) : ViewModel() {

    private val appContext = DailyWeatherApp.instance.applicationContext

    private val _grid = MutableLiveData<GridPoint>()
    val grid: LiveData<GridPoint> = _grid

    private val _selectedRegion = MutableLiveData<Region>()
    val selectedRegion: LiveData<Region> = _selectedRegion

    private val _selectedSubRegion = MutableLiveData<SubRegion?>()
    val selectedSubRegion: LiveData<SubRegion?> = _selectedSubRegion

    // ==========================
    //  GPS → 격자 + 행정구역 처리
    // ==========================

    suspend fun processLocationAndConvert(
        lon: Double,
        lat: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1) 위경도 → 격자
            val gridPoint = repo.convertLatLngToGrid(longitude = lon, latitude = lat)

            // 2) Geocoder 시도
            val geocoder = Geocoder(appContext, Locale.KOREA)

            var regionName: String? = null
            var subRegionName: String? = null

            val address = try {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            } catch (e: Exception) {
                Log.e("LocationVM", "Geocoder 실패: ${e.message}", e)
                null
            }

            if (address != null) {
                regionName = address.adminArea
                subRegionName =
                    address.locality ?: address.subLocality ?: address.subAdminArea
            }

            // 3) 부족하면 Region 테이블로 추정
            if (regionName.isNullOrBlank() || subRegionName.isNullOrBlank()) {
                val guess = guessRegionFromLatLng(lat, lon)
                if (guess != null) {
                    if (regionName.isNullOrBlank()) regionName = guess.first
                    if (subRegionName.isNullOrBlank()) subRegionName = guess.second
                }
            }

            Log.d(
                "LocationVM",
                "GPS 변환 결과 → grid=(${gridPoint.x},${gridPoint.y}), region=$regionName, sub=$subRegionName"
            )

            // 4) enum 매핑 + LiveData 세팅
            applyRegionToLiveData(
                gridPoint = gridPoint,
                regionName = regionName,
                subRegionName = subRegionName
            )

            true
        } catch (e: Exception) {
            Log.e("LocationVM", "processLocationAndConvert 실패: ${e.message}", e)
            false
        }
    }

    // ==========================
    //  DB에서 읽은 LastUserState 주입
    // ==========================

    /**
     * 앱이 다시 켜졌을 때 LastUserStateEntity를
     * LocationViewModel의 LiveData들에 반영.
     *
     * - grid(x,y)
     * - regionName / subRegionName 이 있으면 그대로 사용
     * - 없으면 격자 → 위경도 → Region 추정으로 채움
     */
    fun applyLastState(state: LastUserStateEntity) {
        val gridPoint = GridPoint(state.x, state.y)

        var regionName = state.regionName
        var subRegionName = state.subRegionName

        if (regionName.isNullOrBlank() || subRegionName.isNullOrBlank()) {
            val latLng = KmaGridConverter.toLatLng(state.x, state.y)
            val guess = guessRegionFromLatLng(latLng.latitude, latLng.longitude)
            if (guess != null) {
                if (regionName.isNullOrBlank()) regionName = guess.first
                if (subRegionName.isNullOrBlank()) subRegionName = guess.second
            }
        }

        applyRegionToLiveData(
            gridPoint = gridPoint,
            regionName = regionName,
            subRegionName = subRegionName
        )
    }

    /** grid + region/subRegion 을 LiveData에 세팅하는 공통 로직 */
    private fun applyRegionToLiveData(
        gridPoint: GridPoint,
        regionName: String?,
        subRegionName: String?
    ) {
        _grid.postValue(gridPoint)

        if (!regionName.isNullOrBlank()) {
            val regionEnum = Region.entries.firstOrNull {
                it.displayName == regionName
            }

            if (regionEnum != null) {
                _selectedRegion.postValue(regionEnum)

                val subEnum = regionEnum.subRegions.firstOrNull {
                    it.name == subRegionName
                } ?: regionEnum.subRegions.firstOrNull()

                _selectedSubRegion.postValue(subEnum)
            } else {
                Log.w("LocationVM", "Region enum에서 [$regionName] 못 찾음")
            }
        }
    }

    // ==========================
    //  수동 선택 관련
    // ==========================

    fun onRegionSelected(region: Region) {
        _selectedRegion.value = region
        _selectedSubRegion.value = null
    }

    fun onSubRegionSelected(sub: SubRegion) {
        _selectedSubRegion.value = sub
    }

    suspend fun requestGrid(): GridPoint? {
        val sub = _selectedSubRegion.value ?: return null
        val gridPoint = repo.convertLatLngToGrid(
            longitude = sub.longitude,
            latitude = sub.latitude
        )
        _grid.value = gridPoint
        return gridPoint
    }

    // ==========================
    //  Region 테이블 기반 추정
    // ==========================

    private fun guessRegionFromLatLng(
        lat: Double,
        lon: Double
    ): Pair<String, String>? {
        var bestRegion: String? = null
        var bestSub: String? = null
        var bestDist = Double.MAX_VALUE

        for (region in Region.entries) {
            if (region == Region.SELECT) continue
            for (sub in region.subRegions) {
                val dist = (lat - sub.latitude).pow(2) + (lon - sub.longitude).pow(2)
                if (dist < bestDist) {
                    bestDist = dist
                    bestRegion = region.displayName
                    bestSub = sub.name
                }
            }
        }

        return if (bestRegion != null && bestSub != null && bestDist < 0.25)
            bestRegion!! to bestSub!!
        else
            null
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