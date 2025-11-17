package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R

class WeatherDetailFragment : Fragment(R.layout.fragment_weather_detail) {

    // 액티비티 전역으로 공유되는 ViewModel
    private val viewModel: WeatherDetailViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        val locationText = view.findViewById<TextView>(R.id.locationText)
        val dateText = view.findViewById<TextView>(R.id.dateText)
        val temperatureText = view.findViewById<TextView>(R.id.temperatureText)
        val weatherDescription = view.findViewById<TextView>(R.id.weatherDescription)

        val summaryAmPmText = view.findViewById<TextView>(R.id.summaryAmPmText)
        val currentTempSummaryText = view.findViewById<TextView>(R.id.currentTempSummaryText)
        val pm10Text = view.findViewById<TextView>(R.id.pm10Text)
        val popText = view.findViewById<TextView>(R.id.popText)

        val dateViews = listOf(
            view.findViewById<TextView>(R.id.date1),
            view.findViewById<TextView>(R.id.date2),
            view.findViewById<TextView>(R.id.date3),
            view.findViewById<TextView>(R.id.date4),
            view.findViewById<TextView>(R.id.date5),
            view.findViewById<TextView>(R.id.date6),
            view.findViewById<TextView>(R.id.date7)
        )
        val tempViews = listOf(
            view.findViewById<TextView>(R.id.temp1),
            view.findViewById<TextView>(R.id.temp2),
            view.findViewById<TextView>(R.id.temp3),
            view.findViewById<TextView>(R.id.temp4),
            view.findViewById<TextView>(R.id.temp5),
            view.findViewById<TextView>(R.id.temp6),
            view.findViewById<TextView>(R.id.temp7)
        )
        val rainViews = listOf(
            view.findViewById<TextView>(R.id.rain1),
            view.findViewById<TextView>(R.id.rain2),
            view.findViewById<TextView>(R.id.rain3),
            view.findViewById<TextView>(R.id.rain4),
            view.findViewById<TextView>(R.id.rain5),
            view.findViewById<TextView>(R.id.rain6),
            view.findViewById<TextView>(R.id.rain7)
        )

        // 왼쪽 탭(맞춤 옷차림)을 누르면 이전 화면으로 이동
        tabOutfit.setOnClickListener {
            findNavController().navigateUp()
        }
        // 오른쪽 탭(상세 날씨)은 현재 화면이라 따로 동작 없음
        tabDetail.isClickable = false

        // 이미 uiState 가 있으면 재요청 안 함
        if (viewModel.uiState.value == null) {
            viewModel.loadDetailWeather()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            ui ?: return@observe

            locationText.text = ui.locationName
            dateText.text = ui.dateText
            temperatureText.text = ui.currentTempText

            weatherDescription.text = "현재 기온과 대기질, 강수확률을 종합한 상세 정보입니다."
            summaryAmPmText.text = "오늘 오전: ${ui.amTempText} / 오후: ${ui.pmTempText}"
            currentTempSummaryText.text = "현재 온도: ${ui.currentTempText}"
            pm10Text.text = "미세먼지: ${ui.airQualityText}"
            popText.text = "강수확률: ${ui.rainText}"

            ui.forecastList.take(7).forEachIndexed { index, row ->
                dateViews[index].text = row.dateText
                tempViews[index].text = "${row.minTemp} / ${row.maxTemp}"
                rainViews[index].text = row.rainProb
            }
        }
    }
}
