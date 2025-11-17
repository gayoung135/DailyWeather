package com.smartapp.dailyweather.ui.home


import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R
import androidx.navigation.fragment.findNavController

class WeatherDetailFragment : Fragment(R.layout.fragment_weather_detail) {

    private val viewModel: WeatherDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 바인딩
        val backButton = view.findViewById<MaterialButton>(R.id.backButton)
        val locationText = view.findViewById<TextView>(R.id.locationText)
        val dateText = view.findViewById<TextView>(R.id.dateText)
        val temperatureText = view.findViewById<TextView>(R.id.temperatureText)
        val weatherDescription = view.findViewById<TextView>(R.id.weatherDescription)

        val summaryAmPmText = view.findViewById<TextView>(R.id.summaryAmPmText)
        val currentTempSummaryText = view.findViewById<TextView>(R.id.currentTempSummaryText)
        val pm10Text = view.findViewById<TextView>(R.id.pm10Text)
        val popText = view.findViewById<TextView>(R.id.popText)

        // 뒤로가기 / 옷차림 화면으로 이동
        backButton.setOnClickListener {
            // 네 네비게이션 그래프 구조에 맞게 수정
            findNavController().navigateUp()
        }

        // LiveData 관찰해서 UI 갱신
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            ui ?: return@observe

            locationText.text = ui.locationName
            dateText.text = ui.dateText
            temperatureText.text = ui.currentTempText

            currentTempSummaryText.text = ui.currentTempSummary
            pm10Text.text = ui.pm10Text
            popText.text = ui.popText
        }

        // 현재 시간 기준 데이터 로드 시작
        viewModel.loadCurrentWeather()
    }
}