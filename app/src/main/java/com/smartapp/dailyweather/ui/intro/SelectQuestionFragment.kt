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

    // onCreateView(): 프래그먼트 UI를 실제로 그릴 때 호출됨.
    // fragment_select_question.xml 레이아웃을 화면에 표시함.
    override fun onCreateView(
        inflater: LayoutInflater,       // XML을 View로 변환하는 객체
        container: ViewGroup?,          // 프래그먼트를 담는 부모 뷰 (MainActivity)
        savedInstanceState: Bundle?    // 상태 복원용 번들
    ): View {

        // fragment_select_question.xml 불러와서 View 객체로 변환
        // 이 화면에는 사용자의 선택 버튼들이 배치될 예정
        return inflater.inflate(R.layout.fragment_select_question, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // "좋아요" 버튼 클릭 시
        view.findViewById<Button>(R.id.btnYes).setOnClickListener {
            findNavController().navigate(
                R.id.action_selectQuestionFragment_to_mainQuestionFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.selectQuestionFragment, true)
                    .build()
            )
        }

        // "스킵할래요" 버튼 클릭 시 → 바로 LastUserStateEntity 저장 + 이동
        view.findViewById<Button>(R.id.btnSkip).setOnClickListener {
            Log.d("SelectQuestion", "btnSkip 클릭 → 질문 스킵, 현재 시간 기준으로 기온 조회 요청")

            // 1) GridPoint 가져오기
            val grid = locationViewModel.grid.value

            if (grid == null) {
                Toast.makeText(requireContext(), "위치 정보가 아직 없습니다.", Toast.LENGTH_SHORT).show()
                Log.d("SelectQuestion", "grid LiveData is null")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val now = SystemTimeProvider().nowKorea()

                Log.d("SelectQuestion", "스킵 흐름 - now(KST) = $now")

                // selectedHour 를 null 로 넘기면 → 뷰모델에서 '현재 시간' 사용
                mainQuestionViewModel.saveLastUserStateWithApi(
                    grid = grid,
                    selectedHour = null,   // ← 질문 스킵 → 현재 시간 사용
                    purposeCode = null,
                    styleCode = null,
                    skipped = true
                )

                // findNavController().navigate(R.id.action_selectQuestionFragment_to_homeFragment)
            }
        }
    }
}

//요약
//위치 설정 완료 후 사용자에게
// “기분 / 목적 / 스타일” 등 질문을 보여주는 화면 —>
// 이후 맞춤 추천(HomeFragment)으로 이어지는 인트로 마지막 단계.