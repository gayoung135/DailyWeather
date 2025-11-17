package com.smartapp.dailyweather.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.data.repo.CategoryIds
import com.smartapp.dailyweather.ui.recommend.CoordinationItemUi
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel

class OutfitRecommendationFragment : Fragment(R.layout.fragment_outfit_recommendation) {

    // nav_intro 그래프 기준으로 OutfitLoadingFragment와 동일 ViewModel 공유
    private val viewModel: RecommendationViewModel by navGraphViewModels(R.id.nav_intro)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val detailButton = view.findViewById<Button>(R.id.detailWeatherButton)
        val outfit1 = view.findViewById<TextView>(R.id.outfit1)
        val outfit2 = view.findViewById<TextView>(R.id.outfit2)
        val essentials = view.findViewById<TextView>(R.id.essentials)

        val tempText = view.findViewById<TextView>(R.id.tempText)
        val pmText = view.findViewById<TextView>(R.id.pmText)
        val rainText = view.findViewById<TextView>(R.id.rainText)

        // 🔹 오늘의 추천 색깔 전체 영역 (제목 + 박스)
        val moodSection = view.findViewById<View>(R.id.moodSection)

        val moodViews = listOf(
            view.findViewById<View>(R.id.moodColor1),
            view.findViewById<View>(R.id.moodColor2),
            view.findViewById<View>(R.id.moodColor3),
            view.findViewById<View>(R.id.moodColor4),
            view.findViewById<View>(R.id.moodColor5)
        )

        // 상세 날씨 화면으로 이동
        detailButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_outfitRecommendationFragment_to_weatherDetailFragment
            )
        }

        // 여기서는 "관찰만" 한다. 로직 실행 X
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui == null) return@observe

            Log.d("OutfitFragment", "=== 추천 결과 수신 ===")

            // ──────────────
            // 코디 텍스트
            // ──────────────
            outfit1.text = buildOutfitText(ui.firstSet.items)
            outfit2.text = buildOutfitText(ui.secondSet.items)

            // 필수품
            val essentialsText = buildEssentialsText(
                ui.firstSet.items,
                ui.secondSet.items
            )
            essentials.text = if (essentialsText.isBlank()) "필수품 없음" else essentialsText

            // ──────────────
            // 온도 / 미세먼지 / 강수확률
            // ──────────────
            tempText.text = "온도\n${ui.temperatureText}"
            pmText.text = "미세먼지\n${ui.airQualityText}"
            rainText.text = "강수확률\n${ui.rainText}"

            // ──────────────
            // 기분 색상 섹션 처리
            // ──────────────

            if (ui.moodColors.isEmpty()) {
                // 스킵한 경우: 색상 섹션 전체 숨김
                moodSection.visibility = View.GONE
                moodViews.forEach { it.visibility = View.GONE }
                return@observe
            } else {
                // 선택한 경우: 섹션 보이게
                moodSection.visibility = View.VISIBLE
            }

            // 기본은 모두 숨기고
            moodViews.forEach { it.visibility = View.GONE }

            // moodColors 개수만큼 원을 보여주며 색 적용
            ui.moodColors.take(moodViews.size).forEachIndexed { index, colorUi ->
                val v = moodViews[index]
                v.visibility = View.VISIBLE
                try {
                    val drawable = v.background
                    if (drawable is GradientDrawable) {
                        drawable.mutate()
                        drawable.setColor(Color.parseColor(colorUi.hex))
                    } else {
                        v.setBackgroundColor(Color.parseColor(colorUi.hex))
                    }
                } catch (e: Exception) {
                    Log.e("OutfitFragment", "색상 파싱/적용 실패: ${colorUi.hex}", e)
                }
            }
        }
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

        return buildString {
            append("아우터: ").append(if (outer == "-") "없음" else outer).append('\n')
            append("상의: ").append(top).append('\n')
            append("하의: ").append(bottom).append('\n')
            append("악세사리: ").append(acc)
        }
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

        return all.joinToString(", ")
    }
}
