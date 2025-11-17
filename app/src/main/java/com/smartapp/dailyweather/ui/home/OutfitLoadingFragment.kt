package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.data.repo.CategoryIds
import com.smartapp.dailyweather.ui.recommend.CoordinationItemUi
import com.smartapp.dailyweather.ui.recommend.RecommendationUiModel
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel

class OutfitLoadingFragment : Fragment(R.layout.fragment_outfit_loading) {

    // nav_intro 그래프 기준으로 RecommendationViewModel 공유
    private val viewModel: RecommendationViewModel by navGraphViewModels(R.id.nav_intro)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 추천 + 상세날씨 로직은 여기서 "한 번만" 실행
        viewModel.loadRecommendationAutoSido()

        // 데이터 변화 감지
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui == null) return@observe

            if (!isUiReady(ui)) {
                // 아직 준비 안 됨 → 계속 로딩 화면 유지
                return@observe
            }

            // 코디 + 날씨가 전부 준비 완료 → 코디 추천 화면으로 이동
            findNavController().navigate(
                R.id.action_outfitLoadingFragment_to_outfitRecommendationFragment
            )
        }
    }

    // ui가 "완전히 준비됐는지" 검사 (코디 + 날씨 둘 다)
    private fun isUiReady(ui: RecommendationUiModel): Boolean {

        fun List<CoordinationItemUi>.has(catId: Int) =
            any { it.categoryId == catId && it.displayName.isNotBlank() && it.displayName != "-" }

        val first = ui.firstSet.items
        val second = ui.secondSet.items

        // 두 세트 모두 상·하·아우터·ACC가 "-"가 아닌 이름으로 있어야 함
        val outfitReady =
            first.has(CategoryIds.TOP) &&
                    first.has(CategoryIds.BOTTOM) &&
                    first.has(CategoryIds.OUTER) &&
                    first.has(CategoryIds.ACC) &&
                    second.has(CategoryIds.TOP) &&
                    second.has(CategoryIds.BOTTOM) &&
                    second.has(CategoryIds.OUTER) &&
                    second.has(CategoryIds.ACC)

        // 날씨 텍스트가 플레이스홀더가 아니어야 함
        fun notPlaceholder(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            if (text == "-" || text == "-°C") return false
            if (text == "NULL" || text == "NULL°C" || text == "NULL%") return false
            return true
        }

        val weatherReady =
            notPlaceholder(ui.temperatureText) &&
                    notPlaceholder(ui.airQualityText) &&
                    notPlaceholder(ui.rainText)

        // 👉 코디 + 날씨가 모두 준비되었을 때만 true
        return outfitReady && weatherReady
    }
}
