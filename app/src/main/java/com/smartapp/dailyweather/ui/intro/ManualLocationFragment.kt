package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

// 사용자가 "자동 위치 설정 거부"를 눌렀을 때 이동되는 화면.
// 여기서 직접 지역(시/군/구)을 선택할 수 있는 UI가 표시됨.
// LocationGpsFragment → ManualLocationFragment → SelectQuestionFragment 순서로 이어짐.
class ManualLocationFragment : Fragment() {

    // onCreateView(): 프래그먼트가 실제 화면(View)을 생성할 때 호출됨.
    // fragment_manual_location.xml 레이아웃을 화면에 표시함.
    override fun onCreateView(
        inflater: LayoutInflater,       // XML을 실제 View 객체로 변환시켜주는 도구
        container: ViewGroup?,          // 프래그먼트를 담는 부모 컨테이너 (Activity)
        savedInstanceState: Bundle?     // 화면 복원 시 사용하는 번들 데이터
    ): View {

        // fragment_manual_location.xml 레이아웃 불러오기
        val v = inflater.inflate(R.layout.fragment_manual_location, container, false)

        //"다음으로" 버튼 클릭 시 동작
        // -> 질문 화면(SelectQuestionFragment)으로 이동
        // nav_intro.xml에 정의된 action_manualLocation_to_selectQuestion 사용
        v.findViewById<Button>(R.id.btnGoQuestion).setOnClickListener {
            findNavController().navigate(R.id.action_manualLocation_to_selectQuestion)
        }
        // 완성된 View 반환 (이게 실제로 사용자에게 보이는 화면)
        return v
    }
}

//요약
//자동 위치 설정을 거부한 사용자가
// 직접 지역(시·군·구)을 선택하는 화면
// —> 선택 후 질문 화면(SelectQuestionFragment)으로 이동.