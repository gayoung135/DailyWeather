package com.smartapp.dailyweather.ui.intro

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.ViewModelInitializer
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.LocationServices
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.data.repo.LocationRepository
import kotlinx.coroutines.launch

// 이 Fragment는 앱 실행 초기에 위치 권한을 설정하는 화면
// nav_intro.xml 내에서 연결되어 있으며,
// Splash → LocationGps → SelectQuestion 순으로 이동
class LocationGpsFragment : Fragment() {

    private val locationRepository by lazy { LocationRepository() }

    private val locationGpsViewModel: LocationGpsViewModel by viewModels {
        LocationGpsViewModelFactory(locationRepository)
    }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fineGranted || coarseGranted) {
                getLocationAndRoute()
            } else {
                navigateManualOnce()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        val v = inflater.inflate(R.layout.fragment_location_gps, container, false)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestLocation()
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            getLocationAndRoute()
        } else {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndRoute() {
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())

        // 1) lastLocation 시도
        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d("GPS", "lastLocation: ${loc.longitude}, ${loc.latitude}")
                    handleLonLatOrMock(loc.longitude, loc.latitude)
                    return@addOnSuccessListener
                }

                Log.w("GPS", "lastLocation == null, trying getCurrentLocation()")

                // 2) getCurrentLocation 폴백 (API 30+)
                val cts = com.google.android.gms.tasks.CancellationTokenSource()
                fused.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).addOnSuccessListener { cur ->
                    if (cur != null) {
                        Log.d("GPS", "getCurrentLocation: ${cur.longitude}, ${cur.latitude}")
                        handleLonLatOrMock(cur.longitude, cur.latitude)
                    } else {
                        Log.w("GPS", "getCurrentLocation == null, using mock (dev only)")
                        // 3) 개발 중: 모의 좌표 사용 (서울시청)
                        val mockLon = 126.9780
                        val mockLat = 37.5665
                        handleLonLatOrMock(mockLon, mockLat)
                    }
                }.addOnFailureListener { e ->
                    Log.e("GPS", "getCurrentLocation failure: ${e.localizedMessage}")
                    // 4) 최종 폴백: 수동 선택
                    navigateManualOnce()
                }
            }
            .addOnFailureListener { e ->
                Log.e("GPS", "lastLocation failure: ${e.localizedMessage}")
                navigateManualOnce()
            }
    }

    private fun handleLonLatOrMock(lon: Double, lat: Double) {
        // suspend 호출은 코루틴으로
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d("Flow", "processLocationAndConvert start: $lon, $lat")
            val success = locationGpsViewModel.processLocationAndConvert(lon, lat)
            Log.d("Flow", "processLocationAndConvert result: $success")
            if (success) {
                navigateSelectQuestionOnce()
            } else {
                navigateManualOnce()
            }
        }
    }

    /** 중복 네비게이션 방지 가드 */
    private fun navigateManualOnce() {
        val nav = findNavController()
        if (isAdded && nav.currentDestination?.id == R.id.locationGpsFragment) {
            Log.d("NAV", "→ manualLocation")
            nav.navigate(R.id.action_locationGps_to_manualLocation)
        }
    }

    private fun navigateSelectQuestionOnce() {
        val nav = findNavController()
        if (isAdded && nav.currentDestination?.id == R.id.locationGpsFragment) {
            Log.d("NAV", "→ selectQuestion")
            nav.navigate(R.id.action_locationGps_to_selectQuestion)
        }
    }
}

//요약
//앱 최초 실행 후 자동 위치 설정을 요청하는 화면
// —> “허용” 누르면 다음 단계로,
//“취소” 시 수동 위치 선택 화면으로 이동