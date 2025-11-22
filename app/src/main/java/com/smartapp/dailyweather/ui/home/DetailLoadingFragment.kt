package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R

class DetailLoadingFragment :
    Fragment(R.layout.fragment_detailweather_loading) {

    // WeatherDetailFragment 와 같은 인스턴스를 쓰기 위해 Activity 범위 ViewModel 사용
    private val viewModel: WeatherDetailViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //  새로고침 여부 인자 (기본값 false)
        val forceRefresh = arguments?.getBoolean("forceRefresh", false) ?: false

        // 탭 버튼 찾기
        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        //  예전 backButton 이 하던 일을 "맞춤 옷차림" 탭이 대신 수행
        tabOutfit.setOnClickListener {
            findNavController().navigateUp()
        }

        //  "상세 날씨" 탭은 현재 화면이므로 아무 동작 안 함
        tabDetail.setOnClickListener {
            // Do nothing
        }

        // 이미 상세 날씨가 로딩돼 있고, 새로고침이 아니라면 → 바로 상세 화면으로 이동
        if (viewModel.uiState.value != null && !forceRefresh) {
            findNavController().navigate(
                R.id.action_detailWeatherLoadingFragment_to_weatherDetailFragment
            )
            return
        }

        // 로딩 시작
        if (forceRefresh) {
            viewModel.loadDetailWeather(force = true)
        } else {
            viewModel.loadDetailWeather()
        }

        // uiState 가 채워지는 순간 상세 화면으로 이동
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui != null) {
                findNavController().navigate(
                    R.id.action_detailWeatherLoadingFragment_to_weatherDetailFragment
                )
            }
        }
    }
}
