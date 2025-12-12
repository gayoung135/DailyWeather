package com.smartapp.dailyweather.ui.intro

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.location.LocationServices
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.MainActivity
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.core.KmaGridConverter
import com.smartapp.dailyweather.data.local.entity.LastUserStateEntity
import com.smartapp.dailyweather.data.repo.LastStateFreshness
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * 앱 시작 시 위치 권한/위치 흐름을 담당하는 Fragment.
 *
 * - 첫 실행(LastUserState 없음)
 *   - 권한 요청 → 허용 시 현재 위치 기반 좌표/날씨 계산 → 질문 화면
 *   - 거부 시 수동 지역 선택 화면
 *
 * - 재진입(LastUserState 존재)
 *   - 24시간 미만: 기존 상태 그대로 사용 → 질문/홈 플로우로 바로 이동
 *   - 24시간 이상:
 *      - 권한 있음: 현재 위치 vs 마지막 위치 거리 비교 후,
 *          - 임계값 미만 → 기존 상태 유지, 질문 화면으로
 *          - 임계값 이상 → 위치 재변환 + 날씨 재호출 + 질문 화면
 *      - 권한 없음: 권한 재요청, 거부 시 수동 지역 선택 화면
 */
class LocationGpsFragment : Fragment() {

    private val locationViewModel: LocationViewModel by navGraphViewModels(R.id.nav_intro) {
        LocationViewModelFactory((requireActivity() as MainActivity).sharedLocationRepo)
    }

    // DB / Repository
    private val db by lazy { DailyWeatherApp.instance.db }
    private val lastUserStateRepo by lazy { LastUserStateRepository(db) }

    // 진입 모드
    private enum class EntryMode {
        FIRST_RUN,              // LastUserState 없음: 완전 첫 진입
        REFRESH_WITH_DISTANCE   // LastUserState 있음 + 24시간 이상 지난 경우
    }

    private var entryMode: EntryMode? = null
    private var latestFreshness: LastStateFreshness? = null

    // 위치 권한 요청 런처
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val granted = fineGranted || coarseGranted

            if (granted) {
                // 권한 허용됨 → 진입 모드에 따라 처리
                when (entryMode) {
                    EntryMode.REFRESH_WITH_DISTANCE -> {
                        // 24시간 이상 + 권한 있음 → 거리 체크 모드
                        getLocationAndRouteWithDistance()
                    }
                    EntryMode.FIRST_RUN, null -> {
                        // 첫 실행이거나 모드를 아직 못 정한 경우 → 기존 방식으로 위치 가져오기
                        getLocationAndRouteWithoutDistance()
                    }
                }
            } else {
                // 권한 거부 → 항상 수동 지역 선택으로
                navigateManualOnce()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_location_gps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 진입 시점에 "마지막 상태 + 24시간 경과 여부" 를 먼저 판단
        viewLifecycleOwner.lifecycleScope.launch {
            decideEntryFlow()
        }
    }

    /**
     * 진입 시 플로우를 결정:
     *  - LastUserState 없음 → FIRST_RUN 모드
     *  - 있으면 24시간 경과 여부로 분기
     */
    private suspend fun decideEntryFlow() {
        val freshness = lastUserStateRepo.getLatestStateFreshness()
        latestFreshness = freshness

        if (freshness == null) {
            // 1) 완전 첫 실행
            Log.d("LocationGps", "No LastUserState → FIRST_RUN")
            entryMode = EntryMode.FIRST_RUN
            requestLocation()
            return
        }

        // DB 값으로 VM 먼저 채우기
        locationViewModel.applyLastState(freshness.state)

        val hoursDiff = freshness.hoursDiff
        Log.d("LocationGps", "LastUserState found, hoursDiff=$hoursDiff")

        if (hoursDiff < 24L) {
            // 2) 24시간 미만 → 기존 상태 그대로 사용
            Log.d("LocationGps", "<24h since last access → reuse state, go next")
            navigateHome()
            return
        }

        // 3) 24시간 이상 지난 경우 → 권한 상태에 따라 행동
        entryMode = EntryMode.REFRESH_WITH_DISTANCE
        requestLocation()
    }

    /** 위치 권한 여부 확인 */
    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return (fine == PackageManager.PERMISSION_GRANTED ||
                coarse == PackageManager.PERMISSION_GRANTED)
    }

    /**
     * 권한이 이미 있으면 바로 위치 로직으로,
     * 없으면 항상 권한 요청을 띄운다.
     */
    private fun requestLocation() {
        if (hasLocationPermission()) {
            when (entryMode) {
                EntryMode.REFRESH_WITH_DISTANCE -> {
                    getLocationAndRouteWithDistance()
                }
                EntryMode.FIRST_RUN, null -> {
                    getLocationAndRouteWithoutDistance()
                }
            }
        } else {
            // Don't allow 상태든 아니든, 권한이 없으면 항상 다시 한 번 요청
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ===========================
    //  위치 가져오기 (기존 방식)
    // ===========================

    /**
     * 기존 로직 그대로:
     *  - 위치를 받아서 바로 processLocationAndConvert 호출
     *  - 실패 시 수동/실패 화면으로 폴백
     */
    @SuppressLint("MissingPermission")
    private fun getLocationAndRouteWithoutDistance() {
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())

        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d("GPS", "[no-distance] lastLocation: ${loc.longitude}, ${loc.latitude}")
                    handleLonLatOrMock(loc.longitude, loc.latitude)
                    return@addOnSuccessListener
                }

                Log.w("GPS", "[no-distance] lastLocation == null, using mock (dev only)")
                // 개발 중: 모의 좌표 사용 (서울시청)
                val mockLon = 126.9780
                val mockLat = 37.5665
                handleLonLatOrMock(mockLon, mockLat)
            }
            .addOnFailureListener { e ->
                Log.e("GPS", "[no-distance] lastLocation failure: ${e.localizedMessage}")
                navigateFailOnce()
            }
    }

    // ===========================
    //  위치 가져오기 (거리 임계값 모드)
    // ===========================

    /**
     * 24시간 이상 + 권한 있는 경우:
     *  - 현재 위치 vs 마지막 상태의 위치 거리 비교
     *  - threshold 미만이면 기존 상태 재사용
     *  - threshold 이상이면 새로 좌표 변환/날씨 재호출
     */
    @SuppressLint("MissingPermission")
    private fun getLocationAndRouteWithDistance() {
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        val lastState = latestFreshness?.state

        // lastState가 없으면 안전하게 기존 방식으로 폴백
        if (lastState == null) {
            Log.w("LocationGps", "getLocationAndRouteWithDistance but lastState=null → fallback")
            getLocationAndRouteWithoutDistance()
            return
        }

        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    Log.w("GPS", "[distance] lastLocation == null → fallback to no-distance")
                    getLocationAndRouteWithoutDistance()
                    return@addOnSuccessListener
                }

                Log.d(
                    "GPS",
                    "[distance] lastLocation: ${loc.longitude}, ${loc.latitude}"
                )

                val movedEnough = hasMovedEnoughFromLast(
                    lastState = lastState,
                    currentLon = loc.longitude,
                    currentLat = loc.latitude,
                    thresholdMeters = 2000.0  // ← 필요하면 1km, 3km 등으로 조절
                )

                if (!movedEnough) {
                    // 거의 같은 위치 → 기존 상태/좌표/날씨 그대로 사용, 질문 화면으로 이동
                    Log.d("LocationGps", "distance < threshold → reuse last state only, go selectQuestion")
                    navigateSelectQuestionOnce()
                } else {
                    // 충분히 이동 → 새로운 위치로 좌표 변환 + 날씨/코디 재계산
                    Log.d("LocationGps", "distance >= threshold → recalc with new location")
                    handleLonLatOrMock(loc.longitude, loc.latitude)
                }
            }
            .addOnFailureListener { e ->
                Log.e("GPS", "[distance] lastLocation failure: ${e.localizedMessage}")
                navigateFailOnce()
            }
    }

    /**
     * 마지막 저장된 격자(x,y)를 위경도로 변환 후,
     * 현재 위경도와의 거리를 계산해서 threshold 이상인지 판정.
     */
    private fun hasMovedEnoughFromLast(
        lastState: LastUserStateEntity,
        currentLon: Double,
        currentLat: Double,
        thresholdMeters: Double
    ): Boolean {
        val lastLatLng = KmaGridConverter.toLatLng(lastState.x, lastState.y)

        val R = 6371000.0 // 지구 반지름(m)
        val dLat = Math.toRadians(currentLat - lastLatLng.latitude)
        val dLon = Math.toRadians(currentLon - lastLatLng.longitude)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lastLatLng.latitude)) *
                cos(Math.toRadians(currentLat)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = R * c   // meter

        Log.d(
            "LocationGps",
            "distance from last=%.1f m (threshold=%.1f m)".format(distance, thresholdMeters)
        )

        return distance >= thresholdMeters
    }

    // ===========================
    //  공통: 위경도 처리/네비게이션
    // ===========================

    private fun handleLonLatOrMock(lon: Double, lat: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d("Flow", "processLocationAndConvert start: $lon, $lat")
            val success = locationViewModel.processLocationAndConvert(lon, lat)
            Log.d("Flow", "processLocationAndConvert result: $success")
            if (success) {
                navigateSelectQuestionOnce()
            } else {
                navigateFailOnce()
            }
        }
    }

    /** 실패 시 수동/실패 화면으로 */
    private fun navigateFailOnce() {
        val nav = findNavController()
        if (isAdded && nav.currentDestination?.id == R.id.locationGpsFragment) {
            Log.d("NAV", "→ locationFailFragment")
            nav.navigate(R.id.action_locationGpsFragment_to_locationFailFragment)
        }
    }

    /** 권한 거부 또는 아예 권한을 안 받겠다고 한 경우 → 수동 지역 선택 화면 */
    private fun navigateManualOnce() {
        val nav = findNavController()
        if (isAdded && nav.currentDestination?.id == R.id.locationGpsFragment) {
            Log.d("NAV", "→ manualLocationFragment")
            nav.navigate(R.id.action_locationGpsFragment_to_manualLocationFragment)
        }
    }

    /** 정상 플로우: 질문 선택 화면으로 이동 */
    private fun navigateSelectQuestionOnce() {
        val nav = findNavController()
        if (isAdded && nav.currentDestination?.id == R.id.locationGpsFragment) {
            Log.d("NAV", "→ selectQuestion")
            nav.navigate(R.id.action_locationGps_to_selectQuestion)
        }
    }

    private fun navigateHome() {
        val nav = findNavController()
        if (isAdded && nav.currentDestination?.id == R.id.locationGpsFragment) {
            Log.d("NAV", "→ OutfitLoadingFragment")
            nav.navigate(R.id.action_locationGpsFragment_to_outfitLoadingFragment)
        }
    }
}