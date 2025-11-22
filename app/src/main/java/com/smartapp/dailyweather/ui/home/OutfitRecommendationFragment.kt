package com.smartapp.dailyweather.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.DailyWeatherApp
import com.smartapp.dailyweather.data.repo.CategoryIds
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import com.smartapp.dailyweather.ui.recommend.CoordinationItemUi
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel
import kotlinx.coroutines.launch

class OutfitRecommendationFragment : Fragment(R.layout.fragment_outfit_recommendation) {

    // nav_intro 그래프 기준으로 OutfitLoadingFragment와 동일 ViewModel 공유
    private val viewModel: RecommendationViewModel by navGraphViewModels(R.id.nav_intro)

    // 🔥 상세날씨 ViewModel(Activity 범위)도 가져와서 초기화에 사용
    private val weatherDetailViewModel: WeatherDetailViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔹 상단 버튼들
        val btnRestartAll = view.findViewById<TextView>(R.id.btnRestartAll)
        val btnRefreshOutfit = view.findViewById<ImageButton>(R.id.btnRefreshOutfit)

        // 탭 버튼
        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        val outfit1 = view.findViewById<TextView>(R.id.outfit1)
        val outfit2 = view.findViewById<TextView>(R.id.outfit2)
        val essentials = view.findViewById<TextView>(R.id.essentials)

        // 첫 번째 코디 UI 요소 연결
        val top1 = view.findViewById<TextView>(R.id.outfit_top_1)
        val bottom1 = view.findViewById<TextView>(R.id.outfit_bottom_1)
        val outer1 = view.findViewById<TextView>(R.id.outfit_outer_1)
        val acc1 = view.findViewById<TextView>(R.id.outfit_acc_1)

        // 두 번째 코디 UI 요소 연결
        val top2 = view.findViewById<TextView>(R.id.outfit_top_2)
        val bottom2 = view.findViewById<TextView>(R.id.outfit_bottom_2)
        val outer2 = view.findViewById<TextView>(R.id.outfit_outer_2)
        val acc2 = view.findViewById<TextView>(R.id.outfit_acc_2)

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

        // ===================== 버튼 동작 =====================

        // 〈처음으로 : 위치 권한 화면(locationGpsFragment)으로 이동 + 관련 DB 초기화
        btnRestartAll.setOnClickListener {
            val db = DailyWeatherApp.instance.db
            val lastRepo = LastUserStateRepository(db)

            viewLifecycleOwner.lifecycleScope.launch {
                // 마지막 사용자 상태 + 코디 관련 테이블 초기화
                lastRepo.clearLastUserState()
                lastRepo.clearCoordinationTables()

                // 상세날씨 ViewModel 캐시도 완전히 리셋 (중복 로딩 / 이전 값 재사용 방지)
                weatherDetailViewModel.resetForRestart()

                // 네비게이션 그래프 전체(popUpTo nav_intro) 초기화 후 GPS 화면으로
                findNavController().navigate(
                    R.id.action_outfitRecommendationFragment_to_locationGpsFragment_restart
                )
            }
        }

        // 새로고침 아이콘 → 코디 새로고침 로딩 화면으로
        btnRefreshOutfit.setOnClickListener {
            findNavController().navigate(
                R.id.action_outfitRecommendationFragment_to_outfitRefreshLoadingFragment
            )
        }

        // 현재 화면 탭(맞춤 옷차림)은 별도 동작 없음
        tabOutfit.setOnClickListener {
            // 필요 시 스크롤 맨 위로 등 추가 가능
        }

        // 오른쪽 탭을 누르면 상세날씨(로딩 → 상세)로 이동
        tabDetail.setOnClickListener {
            findNavController().navigate(
                R.id.action_outfitRecommendationFragment_to_weatherDetailFragment
            )
        }

        // ===================== UI State 관찰 =====================
        // 여기서는 "관찰만" 한다. 로직 실행 X
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui == null) return@observe

            Log.d("OutfitFragment", "=== 추천 결과 수신 ===")

            // ──────────────
            // 코디 텍스트 (기존 TextView 호환용)
            // ──────────────
            outfit1.text = buildOutfitText(ui.firstSet.items)
            outfit2.text = buildOutfitText(ui.secondSet.items)

            // UI에 직접 추천값 반영
            val first = ui.firstSet.items
            val second = ui.secondSet.items

            fun resolveItemText(items: List<CoordinationItemUi>, id: Int): String {
                return items.firstOrNull { it.categoryId == id }?.displayName ?: "-"
            }

            // 첫 번째 추천 세트
            top1.text = resolveItemText(first, CategoryIds.TOP)
            bottom1.text = resolveItemText(first, CategoryIds.BOTTOM)
            outer1.text = resolveItemText(first, CategoryIds.OUTER)
            val accItems1 = first.filter { it.categoryId == CategoryIds.ACC }
                .joinToString(" / ") { it.displayName }
            acc1.text = if (accItems1.isBlank()) "-" else accItems1

            // 두 번째 추천 세트
            top2.text = resolveItemText(second, CategoryIds.TOP)
            bottom2.text = resolveItemText(second, CategoryIds.BOTTOM)
            outer2.text = resolveItemText(second, CategoryIds.OUTER)
            val accItems2 = second.filter { it.categoryId == CategoryIds.ACC }
                .joinToString(" / ") { it.displayName }
            acc2.text = if (accItems2.isBlank()) "-" else accItems2

            // 필수품
            val essentialsText = buildEssentialsText(
                ui.firstSet.items,
                ui.secondSet.items
            )
            essentials.text = if (essentialsText.isBlank()) "필수품 없음" else essentialsText

            // ──────────────
            // 온도 / 미세먼지 / 강수확률
            // ──────────────
            tempText.text = "${ui.temperatureText}"
            pmText.text = "${ui.airQualityText}"
            rainText.text = "${ui.rainText}"

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
