package com.smartapp.dailyweather.ui.home

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import kotlinx.coroutines.launch

class WeatherDetailFragment : Fragment(R.layout.fragment_weather_detail) {

    private val viewModel: WeatherDetailViewModel by activityViewModels()

    private lateinit var forecastRecycler: RecyclerView
    private lateinit var forecastAdapter: WeatherForecastAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔹 상단 버튼
        val btnRestartDetail = view.findViewById<TextView>(R.id.btnRestartDetail)
        val btnRefreshWeather = view.findViewById<ImageButton>(R.id.btnRefreshWeather)

        // 탭
        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        // 상단 정보
        val locationText = view.findViewById<TextView>(R.id.locationText)
        val dateText = view.findViewById<TextView>(R.id.dateText)
        val temperatureText = view.findViewById<TextView>(R.id.temperatureText)

        val summaryAmPmText = view.findViewById<TextView>(R.id.summaryAmPmText)
        val currentTempSummaryText = view.findViewById<TextView>(R.id.currentTempSummaryText)
        val pm10Text = view.findViewById<TextView>(R.id.pm10Text)
        val popText = view.findViewById<TextView>(R.id.popText)

        // ▼ 리사이클러뷰 연결
        forecastRecycler = view.findViewById(R.id.forecastRecycler)
        forecastRecycler.layoutManager = LinearLayoutManager(requireContext())

        // 어댑터 최초 생성
        forecastAdapter = WeatherForecastAdapter(emptyList())
        forecastRecycler.adapter = forecastAdapter

        // 🔴 "처음으로" 버튼: 위치 권한 화면으로 이동 + 관련 DB 초기화
        btnRestartDetail.setOnClickListener {
            val db = DailyWeatherApp.instance.db
            val lastRepo = LastUserStateRepository(db)

            viewLifecycleOwner.lifecycleScope.launch {
                // 마지막 사용자 상태 + 코디 관련 테이블 초기화
                lastRepo.clearLastUserState()
                lastRepo.clearCoordinationTables()

                // 네비그래프 전체(popUpTo nav_intro) 비우고 GPS 화면으로
                findNavController().navigate(
                    R.id.action_weatherDetailFragment_to_locationGpsFragment_restart
                )
            }
        }

        // 🔹 상세 날씨 새로고침 버튼 → 로딩 화면으로 이동 (forceRefresh = true)
        btnRefreshWeather.setOnClickListener {
            val bundle = Bundle().apply {
                putBoolean("forceRefresh", true)
            }
            findNavController().navigate(
                R.id.action_weatherDetailFragment_to_detailWeatherLoadingFragment,
                bundle
            )
        }

        // 탭 이동
        tabOutfit.setOnClickListener { findNavController().navigateUp() }
        tabDetail.isClickable = false

        // 첫 로딩 (이미 로딩돼 있으면 ViewModel 쪽에서 막아줌)
        viewModel.loadDetailWeather()

        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            ui ?: return@observe

            locationText.text = ui.locationName
            dateText.text = ui.dateText
            temperatureText.text = ui.currentTempText

            summaryAmPmText.text = "오전: ${ui.amTempText} / 오후: ${ui.pmTempText}"
            currentTempSummaryText.text = "현재 온도: ${ui.currentTempText}"
            pm10Text.text = "미세먼지: ${ui.airQualityText}"
            popText.text = "강수확률: ${ui.rainText}"

            // 리스트 반영
            forecastAdapter.updateItems(ui.forecastList)
        }
    }

    // ================================
    // 어댑터
    // ================================
    inner class WeatherForecastAdapter(
        private var items: List<DailyRow>
    ) : RecyclerView.Adapter<WeatherForecastAdapter.ForecastViewHolder>() {

        inner class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val date: TextView = view.findViewById(R.id.itemDate)
            val temp: TextView = view.findViewById(R.id.itemTemp)
            val rain: TextView = view.findViewById(R.id.itemRain)  // XML에서 gone (텍스트 안 보임)
            val rainIcon: ImageView = view.findViewById(R.id.itemRainIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_forecast_day, parent, false)
            return ForecastViewHolder(view)
        }

        override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
            val row = items[position]

            holder.date.text = row.dateText

            // 최저 / 최고 색상 처리
            val temp = "${row.minTemp} / ${row.maxTemp}"
            val s = SpannableString(temp)
            s.setSpan(
                ForegroundColorSpan(Color.parseColor("#4A90FF")),
                0,
                temp.indexOf(" /"),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            s.setSpan(
                ForegroundColorSpan(Color.parseColor("#FF6262")),
                temp.indexOf("/ ") + 2,
                temp.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            holder.temp.text = s

            // "70%" → 70 (UI에는 안 보이고 아이콘만 사용)
            val popPercent = row.rainProb.filter { it.isDigit() }.toIntOrNull() ?: 0

            // 6구간 아이콘 매핑
            val iconRes = getPopIconRes(popPercent)
            holder.rainIcon.setImageResource(iconRes)
        }

        /** 🔥 강수확률 6구간 → 아이콘 매핑 */
        private fun getPopIconRes(pop: Int): Int {
            return when (pop) {
                in 0..20   -> R.drawable.pop_020     // 0~20
                in 21..30  -> R.drawable.pop_1030    // 21~30
                in 31..40  -> R.drawable.pop_3040    // 31~40
                in 41..60  -> R.drawable.pop_4060    // 41~60
                in 61..80  -> R.drawable.pop_6080    // 61~80
                else       -> R.drawable.pop_80100   // 81~100
            }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<DailyRow>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
