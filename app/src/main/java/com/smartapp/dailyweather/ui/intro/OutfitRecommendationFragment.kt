package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.smartapp.dailyweather.R

class OutfitRecommendationFragment : Fragment(R.layout.fragment_outfit_recommendation) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 예시: 질문지 응답 기반으로 추천 코디 변경
        val userAnswer = "더운 날씨에 활동적" // 실제 앱에서는 ViewModel이나 Bundle로 받아올 수 있음

        val outfit1 = view.findViewById<TextView>(R.id.outfit1)
        val outfit2 = view.findViewById<TextView>(R.id.outfit2)
        val weatherInfo = view.findViewById<TextView>(R.id.weatherInfo)
        val essentials = view.findViewById<TextView>(R.id.essentials)

        // 날씨 정보 표시
        weatherInfo.text = "32°C / 미세먼지: 나쁨 / 초미세먼지: 높음"

        // 추천 코디 변경
        if (userAnswer.contains("더운")) {
            outfit1.text = "상의: 민소매\n하의: 반바지\n아우터: 없음\n신발: 샌들"
            outfit2.text = "상의: 반팔\n하의: 반바지\n아우터: 얇은 셔츠\n신발: 슬리퍼"
            essentials.text = "마스크, 선크림, 물병"
        } else {
            outfit1.text = "상의: 긴팔\n하의: 긴바지\n아우터: 얇은 가디건\n신발: 운동화"
            outfit2.text = "상의: 반팔\n하의: 긴바지\n아우터: 얇은 셔츠\n신발: 단화"
            essentials.text = "마스크, 우산, 보온용품"
        }
    }
}