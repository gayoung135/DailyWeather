package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.smartapp.dailyweather.MainActivity
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.ui.question.MainQuestionViewModel
import kotlinx.coroutines.launch

// 사용자가 위치 설정(자동/수동)을 완료한 뒤,
// "질문 선택 화면"으로 진입하는 단계.
// → 기분 / 외출 목적 / 선호 스타일 등의 질문 UI가 표시됨.
// Intro Flow의 마지막 단계로, 이후 HomeFragment(메인 탭)으로 이동 예정.
class SelectQuestionFragment : Fragment() {

    private val mainQuestionViewModel: MainQuestionViewModel by activityViewModels()
    private val locationViewModel: LocationViewModel by navGraphViewModels(R.id.nav_intro) {
        LocationViewModelFactory((requireActivity() as MainActivity).sharedLocationRepo)
    }

    // OutfitLoading 으로 이미 이동했는지 여부 (스킵 버튼용)
    private var hasNavigatedToOutfitLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // fragment_select_question.xml 불러와서 View 객체로 변환
        return inflater.inflate(R.layout.fragment_select_question, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnYes = view.findViewById<Button>(R.id.btnYes)
        val btnSkip = view.findViewById<Button>(R.id.btnSkip)

        // "좋아요" 버튼 클릭 시
        btnYes.setOnClickListener {
            // 여러 번 눌러도 한 번만 동작하도록 현재 목적지 체크
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.selectQuestionFragment) {
                navController.navigate(
                    R.id.action_selectQuestionFragment_to_mainQuestionFragment,
                    null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.selectQuestionFragment, true)
                        .build()
                )
            } else {
                Log.d(
                    "SelectQuestion",
                    "btnYes 클릭 시 현재 목적지가 selectQuestionFragment 가 아님 → navigate 스킵. current=${navController.currentDestination}"
                )
            }
        }

        // "스킵할래요" 버튼 클릭 시 → 바로 LastUserStateEntity 저장 + 이동
        btnSkip.setOnClickListener {
            val grid = locationViewModel.grid.value

            if (grid == null) {
                Toast.makeText(requireContext(), "위치 정보가 아직 없습니다.", Toast.LENGTH_SHORT).show()
                Log.d("SelectQuestion", "grid LiveData is null")
                return@setOnClickListener
            }

            // 선택된 시/도 / 시·군·구 가져오기 (수동 선택이면 값 있고, GPS면 null)
            val regionName = locationViewModel.selectedRegion.value?.displayName
            val subRegionName = locationViewModel.selectedSubRegion.value?.name

            viewLifecycleOwner.lifecycleScope.launch {
                val now = SystemTimeProvider().nowKorea()
                Log.d("SelectQuestion", "스킵 흐름 - now(KST) = $now")

                // suspend 함수라 여기서 끝날 때까지 기다렸다가
                mainQuestionViewModel.saveLastUserStateWithApi(
                    grid = grid,
                    regionName = regionName,
                    subRegionName = subRegionName,
                    selectedHourOffset = null,
                    purposeCode = null,
                    styleCode = null,
                    moodCode = null,
                    skipped = true
                )

                Log.d("SelectQuestion", "LastUserState 저장 완료 → 추천 화면으로 이동")

                // 현재 목적지가 selectQuestionFragment 이고,
                //  아직 OutfitLoading 으로 이동한 적이 없을 때만 네비게이션
                val navController = findNavController()
                if (!hasNavigatedToOutfitLoading &&
                    navController.currentDestination?.id == R.id.selectQuestionFragment
                ) {
                    hasNavigatedToOutfitLoading = true
                    navController.navigate(
                        R.id.action_selectQuestionFragment_to_outfitLoadingFragment
                    )
                } else {
                    Log.d(
                        "SelectQuestion",
                        "이미 이동했거나 현재 목적지가 아님 → navigate 스킵. current=${navController.currentDestination}"
                    )
                }
            }
        }
    }
}

//요약
//위치 설정 완료 후 사용자에게
// “기분 / 목적 / 스타일” 등 질문을 보여주는 화면 —>
// 이후 맞춤 추천(HomeFragment)으로 이어지는 인트로 마지막 단계.
