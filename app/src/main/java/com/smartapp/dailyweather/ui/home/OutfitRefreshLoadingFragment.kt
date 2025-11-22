package com.smartapp.dailyweather.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel

class OutfitRefreshLoadingFragment : Fragment(R.layout.fragment_outfit_refresh_loading) {

    // nav_intro 기준으로 RecommendationViewModel 공유
    private val viewModel: RecommendationViewModel by navGraphViewModels(R.id.nav_intro)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 상단 버튼 / 탭
        val btnRestartAll = view.findViewById<TextView>(R.id.btnRestartAll)
        val btnRefreshOutfit = view.findViewById<ImageButton>(R.id.btnRefreshOutfit)
        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        // 〈처음으로 : 인트로 맨 처음으로
        btnRestartAll.setOnClickListener {
            findNavController().navigate(R.id.networkGateFragment)
        }

        // 로딩 화면에서 새로고침 아이콘은 이미 새로고침 중이니까 비활성 느낌으로
        btnRefreshOutfit.isEnabled = false
        btnRefreshOutfit.alpha = 0.4f

        // 현재 탭(맞춤 옷차림) → 이전 화면(코디 화면)으로 돌아가기
        tabOutfit.setOnClickListener {
            findNavController().navigateUp()
        }

        // 상세 날씨 탭 → 상세 날씨 로딩 화면으로
        tabDetail.setOnClickListener {
            findNavController().navigate(R.id.detailWeatherLoadingFragment)
        }

        // 🔵 새로고침용: 캐시 무시하고 코디 다시 생성 (forceRecreate = true)
        viewModel.loadRecommendationAutoSido(forceRecreate = true)

        // 🔵 기존 uiState 값 때문에 바로 튕겨나가지 않도록 첫 emit 한 번 무시
        var firstEmission = true

        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui == null) return@observe

            if (firstEmission) {
                firstEmission = false
                return@observe
            }

            // 데이터 준비되면 코디 화면으로 이동, 현재 로딩은 스택에서 제거
            findNavController().navigate(
                R.id.action_outfitRefreshLoadingFragment_to_outfitRecommendationFragment
            )
        }
    }
}
