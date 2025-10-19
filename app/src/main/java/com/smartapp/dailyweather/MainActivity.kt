package com.smartapp.dailyweather

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// 앱 실행 시 가장 먼저 호출되는 액티비티(Activity).
// 모든 Fragment(Splash, LocationGps, ManualLocation 등)를 담는 "컨테이너" 역할을 함.
// 즉, 네비게이션(nav_intro.xml)에 정의된 화면 흐름을 실제로 표시해주는 Activity임.
class MainActivity : AppCompatActivity() {

    // onCreate(): 액티비티가 처음 실행될 때 호출되는 함수
    // 여기서 레이아웃을 설정하고, 시스템 UI(상태바 등) 처리를 함.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 상태바와 내비게이션바를 투명하게 만들어 전체화면 느낌을 주는 설정
        enableEdgeToEdge()

        // 화면 레이아웃 설정
        setContentView(R.layout.activity_main)

        // 시스템 바(상태바/내비게이션바) 높이만큼 패딩을 자동으로 적용해
        // 콘텐츠가 바 밑으로 깎이지 않도록 조정해줌.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->

            // 시스템 바(상단 상태바, 하단 네비게이션바)의 여백 크기 가져오기
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 해당 여백만큼 padding 적용 → UI가 겹치지 않게 유지
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            // 원래의 insets 객체 반환 (필수)
            insets
        }
    }
}

//요약
//“MainActivity는 앱의 ‘무대’고,
// 그 위에서 여러 Fragment들이 장면처럼 교체되며 실행된다.”