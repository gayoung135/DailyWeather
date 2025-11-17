package com.smartapp.dailyweather.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.data.repo.CategoryIds
import com.smartapp.dailyweather.ui.recommend.CoordinationItemUi
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel

class OutfitRecommendationFragment : Fragment(R.layout.fragment_outfit_recommendation) {

    private val viewModel: RecommendationViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 찾기
        val detailButton = view.findViewById<Button>(R.id.detailWeatherButton)
        val outfit1 = view.findViewById<TextView>(R.id.outfit1)
        val outfit2 = view.findViewById<TextView>(R.id.outfit2)
        val weatherInfo = view.findViewById<TextView>(R.id.weatherInfo)
        val essentials = view.findViewById<TextView>(R.id.essentials)

        val tempText = view.findViewById<TextView>(R.id.tempText)
        val pmText = view.findViewById<TextView>(R.id.pmText)
        val rainText = view.findViewById<TextView>(R.id.rainText)

        val moodViews = listOf(
            view.findViewById<View>(R.id.moodColor1),
            view.findViewById<View>(R.id.moodColor2),
            view.findViewById<View>(R.id.moodColor3),
            view.findViewById<View>(R.id.moodColor4),
            view.findViewById<View>(R.id.moodColor5)
        )

        // 상세 날씨 버튼 클릭 시 이동
        detailButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_outfitRecommendationFragment_to_weatherDetailFragment
            )
        }

        // ViewModel 관찰해서 UI 반영 + 로그 찍기
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui == null) return@observe

            Log.d("OutfitFragment", "=== 추천 결과 수신 ===")

            // 1세트 로그
            ui.firstSet.items.forEach { item ->
                Log.d(
                    "OutfitFragment",
                    "1세트 item → category=${item.categoryId}, id=${item.id}, name=${item.displayName}"
                )
            }

            // 2세트 로그
            ui.secondSet.items.forEach { item ->
                Log.d(
                    "OutfitFragment",
                    "2세트 item → category=${item.categoryId}, id=${item.id}, name=${item.displayName}"
                )
            }

            // mood 색상 로그
            ui.moodColors.forEach { color ->
                Log.d(
                    "OutfitFragment",
                    "MoodColor → name=${color.name}, hex=${color.hex}"
                )
            }

            // ───────────────
            // 코디 텍스트 세팅
            // ───────────────
            outfit1.text = buildOutfitText(ui.firstSet.items)
            outfit2.text = buildOutfitText(ui.secondSet.items)

            // 필수품(NECESSITY 카테고리) 합쳐서 표시
            val essentialsText = buildEssentialsText(
                ui.firstSet.items,
                ui.secondSet.items
            )
            essentials.text = if (essentialsText.isBlank()) {
                "필수품 없음"
            } else {
                essentialsText
            }

            // ───────────────
            // 온도 / 미세먼지 / 강수확률 표시
            // ───────────────
            tempText.text = "온도\n${ui.temperatureText}"
            pmText.text = "미세먼지\n${ui.airQualityText}"
            rainText.text = "강수확률\n${ui.rainText}"

            // ───────────────
            // 기분 색상 View들에 색 적용
            // ───────────────
            moodViews.forEach { it.visibility = View.GONE } // 기본은 숨김

            ui.moodColors.take(moodViews.size).forEachIndexed { index, colorUi ->
                val v = moodViews[index]
                v.visibility = View.VISIBLE
                try {
                    val drawable = v.background
                    if (drawable is GradientDrawable) {
                        // 원래 circle_*.xml 모양(oval) 유지하면서 색만 변경
                        drawable.mutate()
                        drawable.setColor(Color.parseColor(colorUi.hex))
                    } else {
                        // 혹시 모양이 GradientDrawable 아니면 fallback
                        v.setBackgroundColor(Color.parseColor(colorUi.hex))
                    }
                } catch (e: Exception) {
                    Log.e("OutfitFragment", "색상 파싱/적용 실패: ${colorUi.hex}", e)
                }
            }
        }

        // 데이터 로드 시작
        viewModel.loadRecommendationAutoSido()
    }

    /**
     * 카테고리별로 상의/하의/아우터/악세사리 묶어서 한 TextView에 표현
     */
    private fun buildOutfitText(items: List<CoordinationItemUi>): String {
        val top = items.firstOrNull { it.categoryId == CategoryIds.TOP }?.displayName ?: "-"
        val bottom = items.firstOrNull { it.categoryId == CategoryIds.BOTTOM }?.displayName ?: "-"
        val outer = items.firstOrNull { it.categoryId == CategoryIds.OUTER }?.displayName ?: "-"
        val acc = items
            .filter { it.categoryId == CategoryIds.ACC }
            .joinToString(" / ") { it.displayName }

        val sb = StringBuilder().apply {
            append("상의: ").append(top).append('\n')
            append("하의: ").append(bottom).append('\n')
            append("아우터: ").append(if (outer == "-") "없음" else outer)
            if (acc.isNotBlank()) {
                append('\n')
                append("악세사리: ").append(acc)
            }
        }
        return sb.toString()
    }

    /**
     * 두 세트에 포함된 NECESSITY(마스크/우산 등) 아이템을 모아서 문자열로 변환
     */
    private fun buildEssentialsText(
        first: List<CoordinationItemUi>,
        second: List<CoordinationItemUi>
    ): String {
        val all = (first + second)
            .filter { it.categoryId == CategoryIds.NECESSITY }
            .map { it.displayName }
            .distinct()

        return all.joinToString(separator = ", ")
    }
}
