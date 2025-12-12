package com.smartapp.dailyweather.ui.home

import android.os.Bundle
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
import com.smartapp.dailyweather.data.repo.LastUserStateRepository
import com.smartapp.dailyweather.ui.recommend.RecommendationViewModel
import kotlinx.coroutines.launch

class OutfitRefreshLoadingFragment : Fragment(R.layout.fragment_outfit_refresh_loading) {

    // nav_intro 기준 코디 ViewModel
    private val viewModel: RecommendationViewModel by navGraphViewModels(R.id.nav_intro)

    // "처음으로" 눌렀을 때 상세날씨 캐시도 같이 초기화
    private val weatherDetailViewModel: WeatherDetailViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 상단 버튼 / 탭
        val btnRestartAll = view.findViewById<TextView>(R.id.btnRestartAll)
        val btnRefreshOutfit = view.findViewById<ImageButton>(R.id.btnRefreshOutfit)
        val tabOutfit = view.findViewById<MaterialButton>(R.id.tabOutfit)
        val tabDetail = view.findViewById<MaterialButton>(R.id.tabDetail)

        //  "처음으로" 버튼: 로딩 중이든 상관없이 항상 동작
        btnRestartAll.setOnClickListener {
            val db = DailyWeatherApp.instance.db
            val lastRepo = LastUserStateRepository(db)

            viewLifecycleOwner.lifecycleScope.launch {
                // 마지막 사용자 상태 + 코디 관련 테이블 초기화
                lastRepo.clearLastUserState()
                lastRepo.clearCoordinationTables()

                // 상세날씨 ViewModel 캐시도 함께 초기화
                weatherDetailViewModel.resetForRestart()

                // nav_intro 전체 스택 비우고 위치 설정 처음 화면으로
                findNavController().navigate(
                    R.id.action_outfitRefreshLoadingFragment_to_locationGpsFragment_restart
                )
            }
        }

        // 현재 탭(맞춤 옷차림) → 이전 코디 화면으로
        tabOutfit.setOnClickListener {
            findNavController().navigateUp()
        }

        // 상세 날씨 탭 → 액션 ID로 이동해야 popUpTo가 적용됨
        //    → OutfitRefreshLoadingFragment는 백스택에서 삭제됨
        tabDetail.setOnClickListener {
            findNavController().navigate(
                R.id.action_outfitRefreshLoadingFragment_to_detailWeatherLoadingFragment
            )
        }

        // 로딩 중에는 새로고침 버튼 비활성화 (클릭 불가)
        btnRefreshOutfit.isEnabled = false
        btnRefreshOutfit.isClickable = false

        // "코디만" 새로고침
        viewModel.loadRecommendationAutoSido(forceRecreate = true)

        // 기존 uiState 첫 emit 무시
        var firstEmission = true

        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            if (ui == null) return@observe

            if (firstEmission) {
                firstEmission = false
                return@observe
            }

            // 코디 준비되면 코디 화면으로 이동 + 이 로딩 프래그먼트는 popUpTo 설정으로 제거됨
            findNavController().navigate(
                R.id.action_outfitRefreshLoadingFragment_to_outfitRecommendationFragment
            )
        }
    }
}