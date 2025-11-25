package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import kotlinx.coroutines.launch

class DetailLoadingFragment :
    Fragment(R.layout.fragment_detailweather_loading) {

    // 상세 날씨용 ViewModel (Activity 범위)
    private val viewModel: WeatherDetailViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 새로고침 여부 인자 (기본값 false)
        val forceRefresh = arguments?.getBoolean("forceRefresh", false) ?: false

        // 상단 버튼들
        val btnRestartDetail = view.findViewById<TextView>(R.id.btnRestartDetail)
        val btnRefreshWeather = view.findViewById<ImageButton>(R.id.btnRefreshWeather)

        // 탭 버튼
        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        // "처음으로" 버튼: 로딩 중이어도 항상 동작
        btnRestartDetail.setOnClickListener {
            val db = DailyWeatherApp.instance.db
            val lastRepo = LastUserStateRepository(db)

            viewLifecycleOwner.lifecycleScope.launch {
                // 마지막 사용자 상태 + 코디 관련 테이블 초기화
                lastRepo.clearLastUserState()
                lastRepo.clearCoordinationTables()

                // 상세날씨 ViewModel 캐시도 초기화
                viewModel.resetForRestart()

                // nav_intro 전체 비우고 위치 설정 처음 화면으로 이동
                findNavController().navigate(
                    R.id.action_detailWeatherLoadingFragment_to_locationGpsFragment_restart
                )
            }
        }

        // 로딩 중에는 새로고침 버튼 막기 (중복 새로고침 방지)
        btnRefreshWeather.isEnabled = false
        btnRefreshWeather.isClickable = false

        // "맞춤 옷차림" 탭 → 코디 화면으로 돌아가기
        tabOutfit.setOnClickListener {
            findNavController().popBackStack(
                R.id.outfitRecommendationFragment,
                false
            )
        }

        // "상세 날씨" 탭은 현재 화면이라 동작 없음
        tabDetail.setOnClickListener { /* no-op */ }

        // 이미 상세 날씨가 있고, 새로고침이 아니라면 바로 상세 화면으로 이동
        if (viewModel.uiState.value != null && !forceRefresh) {
            findNavController().navigate(
                R.id.action_detailWeatherLoadingFragment_to_weatherDetailFragment
            )
            return
        }

        // "상세 날씨만" 로딩
        if (forceRefresh) {
            viewModel.loadDetailWeather(force = true)
        } else {
            viewModel.loadDetailWeather()
        }

        // 날씨 데이터 준비되면 상세 화면으로 이동
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui != null) {
                findNavController().navigate(
                    R.id.action_detailWeatherLoadingFragment_to_weatherDetailFragment
                )
            }
        }
    }
}
