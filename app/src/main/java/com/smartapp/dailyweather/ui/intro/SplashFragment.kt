package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

// 앱 실행 시 가장 먼저 표시되는 시작(인트로) 화면.
// 로고나 앱 이름 등을 보여준 뒤 일정 시간(1.5초) 후
// 다음 화면(LocationGpsFragment)으로 자동 이동함.
class SplashFragment : Fragment() {

    // onCreateView(): 프래그먼트의 UI를 생성할 때 호출됨.
    // fragment_splash.xml 레이아웃을 불러와서 화면에 표시함.
    override fun onCreateView(
        inflater: LayoutInflater,       // XML 레이아웃을 View 객체로 변환
        container: ViewGroup?,          // 이 프래그먼트를 담을 부모 뷰 (MainActivity)
        savedInstanceState: Bundle?     // 화면 복원 시 사용하는 데이터
    ): View {

        // fragment_splash.xml 파일을 실제 View로 변환
        val v = inflater.inflate(R.layout.fragment_splash, container, false)


        // Handler: 일정 시간 후에 동작을 수행하도록 예약
        // Looper.getMainLooper() → 메인(UI) 스레드에서 실행되도록 지정
        Handler(Looper.getMainLooper()).postDelayed({

            // 1.5초(1500ms) 뒤 실행됨
            // nav_intro.xml에 정의된 action_splash_to_locationGps 경로를 따라
            // 다음 화면(LocationGpsFragment)으로 이동
            findNavController().navigate(R.id.action_splash_to_locationGps)
        }, 1500)        // ← 1500ms = 1.5초 지연 시간

        // 완성된 화면(View) 반환 → 실제로 사용자에게 표시됨
        return v
    }
}

//요약
//“앱 실행 → SplashFragment가 뜸 → 1.5초 대기 →
// 자동으로 LocationGpsFragment로 이동”