package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R

class DetailWeatherLoadingFragment :
    Fragment(R.layout.fragment_detailweather_loading) {

    // WeatherDetailFragment 와 같은 인스턴스를 쓰기 위해 Activity 범위 ViewModel 사용
    private val viewModel: WeatherDetailViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton = view.findViewById<MaterialButton>(R.id.backButton)
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // 이미 상세 날씨가 로딩돼 있다면 바로 상세 화면으로 이동
        if (viewModel.uiState.value != null) {
            findNavController().navigate(
                R.id.action_detailWeatherLoadingFragment_to_weatherDetailFragment
            )
            return
        }

        // 아직이면 로딩 시작 (ViewModel 쪽에서 hasLoaded 체크하고 한 번만 호출되도록 구현)
        viewModel.loadDetailWeather()

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
