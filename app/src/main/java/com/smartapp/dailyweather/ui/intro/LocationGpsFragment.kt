package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

// 이 Fragment는 앱 실행 초기에 위치 권한을 설정하는 화면.
// nav_intro.xml 내에서 연결되어 있으며,
// Splash → LocationGps → SelectQuestion 순으로 이동함.
class LocationGpsFragment : Fragment() {

    // onCreateView(): 프래그먼트의 UI를 생성할 때 호출됨.
    // 즉, 이 함수가 fragment_location_gps.xml 화면을 실제로 띄워줌.
    override fun onCreateView(
        inflater: LayoutInflater,           // XML 레이아웃을 화면(View)으로 변환시켜주는 객체
        container: ViewGroup?,              // 프래그먼트를 담는 부모 컨테이너 (MainActivity)
        savedInstanceState: Bundle?         // 화면 복원 시 필요한 데이터
    ): View {
        // fragment_location_gps.xml 파일을 불러와서 View 객체로 변환
        val v = inflater.inflate(R.layout.fragment_location_gps, container, false)

        // "허용" 버튼 클릭 시 동작
        // -> 다음 단계(SelectQuestionFragment)로 화면 이동
        v.findViewById<Button>(R.id.btnAllow).setOnClickListener {
            // nav_intro.xml에 정의된 action_locationGps_to_selectQuestion 사용
            findNavController().navigate(R.id.action_locationGps_to_allowQuestionIntro) // 더미 이동
        }

        // "취소" 버튼 클릭 시 동작
        // -> 수동 위치 설정 화면(ManualLocationFragment)으로 이동
        v.findViewById<Button>(R.id.btnManual).setOnClickListener {
            findNavController().navigate(R.id.action_locationGps_to_manualLocation)
        }

        // 완성된 화면(View)을 반환 → 실제로 사용자에게 보이는 부분
        return v
    }
}

//요약
//앱 최초 실행 후 자동 위치 설정을 요청하는 화면
// —> “허용” 누르면 다음 단계로,
// “취소” 시 수동 위치 선택 화면으로 이동.
