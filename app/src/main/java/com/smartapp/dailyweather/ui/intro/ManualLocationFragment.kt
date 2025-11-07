package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.model.SubRegion
import com.smartapp.dailyweather.data.repo.LocationRepository
import kotlinx.coroutines.launch

// 사용자가 "자동 위치 설정 거부"를 눌렀을 때 이동되는 화면.
// 여기서 직접 지역(시/군/구)을 선택할 수 있는 UI가 표시됨.
// LocationGpsFragment → ManualLocationFragment → SelectQuestionFragment 순서로 이어짐.
class ManualLocationFragment : Fragment() {

    private val repo by lazy { LocationRepository() }

    private val vm: ManualLocationViewModel by viewModels {
        ManualLocationViewModelFactory(repo)
    }

    // onCreateView(): 프래그먼트가 실제 화면(View)을 생성할 때 호출됨.
    // fragment_manual_location.xml 레이아웃을 화면에 표시함.
    override fun onCreateView(
        inflater: LayoutInflater,       // XML을 실제 View 객체로 변환시켜주는 도구
        container: ViewGroup?,          // 프래그먼트를 담는 부모 컨테이너 (Activity)
        savedInstanceState: Bundle?     // 화면 복원 시 사용하는 번들 데이터
    ): View {

        // fragment_manual_location.xml 레이아웃 불러오기
        val v = inflater.inflate(R.layout.fragment_manual_location, container, false)

        val btnSeoul = v.findViewById<Button>(R.id.btnSelectSeoul)

        btnSeoul.setOnClickListener {
            findNavController().navigate(R.id.manualLocationDetailFragment)
        }

        //"다음으로" 버튼 클릭 시 동작
        // 완성된 View 반환 (이게 실제로 사용자에게 보이는 화면)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* === 테스트용 === */
        // 가짜 값 준비: 시/도 & 시/군/구
        val region = Region.SEOUL
        vm.onRegionSelected(region)

        val sub: SubRegion = region.subRegions.first { it.name == "종로구" }
        vm.onSubRegionSelected(sub)

        Log.d("TestManualLocation", "TEST start → ${region.displayName} / ${sub.name}")

        // suspend 함수 호출은 코루틴으로
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val grid = vm.requestGrid()
                Log.d("TestManualLocation", "SUCCESS → grid: x=${grid?.x}, y=${grid?.y}")
            } catch (e: Exception) {
                Log.e("TestManualLocation", "FAIL → ${e.message}", e)
            }
        }
        /* === 테스트용 === */
    }
}

//요약
//자동 위치 설정을 거부한 사용자가
// 직접 지역(시·군·구)을 선택하는 화면
// —> 선택 후 질문 화면(SelectQuestionFragment)으로 이동.