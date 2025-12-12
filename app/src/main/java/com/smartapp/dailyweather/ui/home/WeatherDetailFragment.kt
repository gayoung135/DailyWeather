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

        val btnRestartDetail = view.findViewById<TextView>(R.id.btnRestartDetail)
        val btnRefreshWeather = view.findViewById<ImageButton>(R.id.btnRefreshWeather)

        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        val locationText = view.findViewById<TextView>(R.id.locationText)
        val dateText = view.findViewById<TextView>(R.id.dateText)
        val temperatureText = view.findViewById<TextView>(R.id.temperatureText)

        val summaryAmPmText = view.findViewById<TextView>(R.id.summaryAmPmText)
        val currentTempSummaryText = view.findViewById<TextView>(R.id.currentTempSummaryText)
        val pm10Text = view.findViewById<TextView>(R.id.pm10Text)
        val popText = view.findViewById<TextView>(R.id.popText)

        forecastRecycler = view.findViewById(R.id.forecastRecycler)
        forecastRecycler.layoutManager = LinearLayoutManager(requireContext())

        forecastAdapter = WeatherForecastAdapter(emptyList())
        forecastRecycler.adapter = forecastAdapter

        // "처음으로" → DB 삭제 + ViewModel 캐시 초기화 + 네비게이션
        btnRestartDetail.setOnClickListener {
            val db = DailyWeatherApp.instance.db
            val lastRepo = LastUserStateRepository(db)

            viewLifecycleOwner.lifecycleScope.launch {
                lastRepo.clearLastUserState()
                lastRepo.clearCoordinationTables()

                // 뷰모델 캐시도 초기화 (중요)
                viewModel.resetForRestart()

                findNavController().navigate(
                    R.id.action_weatherDetailFragment_to_locationGpsFragment_restart
                )
            }
        }

        // 상세 날씨 새로고침
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

        // 삭제 부분 -> 여기는 뷰모델 관찰하게
       // viewModel.loadDetailWeather()

        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            ui ?: return@observe

            locationText.text = ui.locationName
            dateText.text = ui.dateText
            temperatureText.text = ui.currentTempText

            summaryAmPmText.text = "오전: ${ui.amTempText} / 오후: ${ui.pmTempText}"
            currentTempSummaryText.text = "현재 온도: ${ui.currentTempText}"
            pm10Text.text = "초미세먼지: ${ui.airQualityText}"
            popText.text = "강수확률: ${ui.rainText}"

            forecastAdapter.updateItems(ui.forecastList)
        }
    }

    // =======================================
    inner class WeatherForecastAdapter(
        private var items: List<DailyRow>
    ) : RecyclerView.Adapter<WeatherForecastAdapter.ForecastViewHolder>() {

        inner class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val date: TextView = view.findViewById(R.id.itemDate)
            val temp: TextView = view.findViewById(R.id.itemTemp)
            val rain: TextView = view.findViewById(R.id.itemRain)
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

            val popPercent = row.rainProb.filter { it.isDigit() }.toIntOrNull() ?: 0
            val iconRes = getPopIconRes(popPercent)
            holder.rainIcon.setImageResource(iconRes)
        }

        private fun getPopIconRes(pop: Int): Int {
            return when (pop) {
                in 0..10   -> R.drawable.pop_010
                in 11..30  -> R.drawable.pop_1030
                in 31..40  -> R.drawable.pop_3040
                in 41..60  -> R.drawable.pop_4060
                in 61..80  -> R.drawable.pop_6080
                else       -> R.drawable.pop_80100
            }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<DailyRow>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
