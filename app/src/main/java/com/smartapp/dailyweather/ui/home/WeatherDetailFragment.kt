package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel

class WeatherDetailFragment : Fragment(R.layout.fragment_weather_detail) {

    // intro 그래프 전체에서 공유 중인 RecommendationViewModel 사용
    private val viewModel: RecommendationViewModel by navGraphViewModels(R.id.nav_intro)

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
            findNavController().navigateUp()
        }

        // 여기서는 추가 로딩 X
        // OutfitLoadingFragment에서 이미 loadRecommendationAutoSido() 실행 + 완료 후에만 진입함

        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            ui ?: return@observe

            // 여기서 필요한 값만 RecommendationUiModel에서 꺼내서 사용
            // (locationText / dateText는 일단 너가 정한 기본값이나 나중에 VM에서 확장 가능)

            // 온도
            temperatureText.text = ui.temperatureText

            // 미세먼지 / 강수확률
            pm10Text.text = ui.airQualityText         // 예: "좋음", "보통" 등
            popText.text = ui.rainText                // 예: "40%", "정보 없음"

            // 필요하다면 간단한 설명 텍스트를 구성
            weatherDescription.text = "현재 기온과 대기질, 강수확률을 종합한 상세 정보입니다."

            // 나머지 텍스트들은 우선 기본값으로 두거나, 간단한 안내 문구만 채워두기
            if (summaryAmPmText.text.isNullOrBlank()) {
                summaryAmPmText.text = "오늘 하루 요약"
            }
            if (currentTempSummaryText.text.isNullOrBlank()) {
                currentTempSummaryText.text = "현재 체감 온도 기준 옷차림을 추천했어요."
            }

            // locationText, dateText는
            // - XML에서 기본값을 세팅해 두거나
            // - 나중에 RecommendationUiModel을 확장해서 넣어줄 수 있음
            // 여기서는 건드리지 않고 두면, 레이아웃 기본 텍스트가 그대로 사용됨
        }
    }
}
