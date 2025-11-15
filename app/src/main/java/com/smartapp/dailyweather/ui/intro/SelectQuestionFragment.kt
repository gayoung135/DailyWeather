package com.smartapp.dailyweather.ui.intro

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.smartapp.dailyweather.R

// 사용자가 위치 설정(자동/수동)을 완료한 뒤,
// "질문 선택 화면"으로 진입하는 단계.
// → 기분 / 외출 목적 / 선호 스타일 등의 질문 UI가 표시됨.
// Intro Flow의 마지막 단계로, 이후 HomeFragment(메인 탭)으로 이동 예정.
class SelectQuestionFragment : Fragment() {

    // 선택된 버튼 추적 변수 (멤버 변수로 선언)
    private var selectedMood: MaterialButton? = null
    private var selectedPurpose: MaterialButton? = null
    private var selectedStyle: MaterialButton? = null
    private var confirmedHour: String? = null

    // onCreateView(): 프래그먼트 UI를 실제로 그릴 때 호출됨.
    // fragment_select_question.xml 레이아웃을 화면에 표시함.
    override fun onCreateView(
        inflater: LayoutInflater,       // XML을 View로 변환하는 객체
        container: ViewGroup?,          // 프래그먼트를 담는 부모 뷰 (MainActivity)
        savedInstanceState: Bundle?    // 상태 복원용 번들
    ): View {
        // fragment_select_question.xml 불러와서 View 객체로 변환
        // 이 화면에는 사용자의 선택 버튼들이 배치될 예정
        return inflater.inflate(R.layout.fragment_main_question, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 선택 처리 함수
        fun setSelected(buttons: List<MaterialButton>, selected: MaterialButton, group: String) {
            buttons.forEach {
                it.setBackgroundColor(Color.WHITE)
                it.setTextColor(Color.parseColor("#3E3E3E"))
            }
            selected.setBackgroundColor(Color.parseColor("#2196F3"))
            selected.setTextColor(Color.WHITE)

            when (group) {
                "mood" -> selectedMood = selected
                "purpose" -> selectedPurpose = selected
                "style" -> selectedStyle = selected
            }
        }

        // 버튼 연결
        val skipButton = view.findViewById<Button>(R.id.skipButton)

        // 시간 선택: NumberPicker로 대체
        val timePicker = view.findViewById<NumberPicker>(R.id.timePicker)
        timePicker.minValue = 0
        timePicker.maxValue = 23
        timePicker.displayedValues = Array(24) { i -> String.format("%02d", i) }
        timePicker.wrapSelectorWheel = false

        // YES/NO 버튼 연결
        val btnTimeYes = view.findViewById<Button>(R.id.btnTimeYes)
        val btnTimeNo = view.findViewById<Button>(R.id.btnTimeNo)

        btnTimeYes.setOnClickListener {
            val selectedHour = timePicker.value
            confirmedHour = String.format("%02d", selectedHour)
            btnTimeYes.setBackgroundColor(Color.parseColor("#2196F3"))
            btnTimeYes.setTextColor(Color.WHITE)
            btnTimeNo.setBackgroundColor(Color.WHITE)
            btnTimeNo.setTextColor(Color.parseColor("#3E3E3E"))
        }

        btnTimeNo.setOnClickListener {
            confirmedHour = null
            btnTimeNo.setBackgroundColor(Color.parseColor("#F44336"))
            btnTimeNo.setTextColor(Color.WHITE)
            btnTimeYes.setBackgroundColor(Color.WHITE)
            btnTimeYes.setTextColor(Color.parseColor("#3E3E3E"))
        }

        val btnMoodGood = view.findViewById<MaterialButton>(R.id.btnMoodGood)
        val btnMoodNormal = view.findViewById<MaterialButton>(R.id.btnMoodNormal)
        val btnMoodBad = view.findViewById<MaterialButton>(R.id.btnMoodBad)

        val btnPurposeWork = view.findViewById<MaterialButton>(R.id.btnPurposeWork)
        val btnPurposeEvent = view.findViewById<MaterialButton>(R.id.btnPurposeEvent)
        val btnPurposeDate = view.findViewById<MaterialButton>(R.id.btnPurposeDate)
        val btnPurposeTrip = view.findViewById<MaterialButton>(R.id.btnPurposeTrip)
        val btnPurposeNone = view.findViewById<MaterialButton>(R.id.btnPurposeNone)

        val btnStyleStreet = view.findViewById<MaterialButton>(R.id.btnStyleStreet)
        val btnStyleFormal = view.findViewById<MaterialButton>(R.id.btnStyleFormal)
        val btnStyleCasual = view.findViewById<MaterialButton>(R.id.btnStyleCasual)
        val btnStyleLovely = view.findViewById<MaterialButton>(R.id.btnStyleLovely)
        val btnStyleNone = view.findViewById<MaterialButton>(R.id.btnStyleNone)

        // 버튼 그룹 정의
        val moodButtons = listOf(btnMoodGood, btnMoodNormal, btnMoodBad)
        val purposeButtons = listOf(btnPurposeWork, btnPurposeEvent, btnPurposeDate, btnPurposeTrip, btnPurposeNone)
        val styleButtons = listOf(btnStyleStreet, btnStyleFormal, btnStyleCasual, btnStyleLovely, btnStyleNone)

        // 클릭 이벤트 연결
        moodButtons.forEach { btn -> btn.setOnClickListener { setSelected(moodButtons, btn, "mood") } }
        purposeButtons.forEach { btn -> btn.setOnClickListener { setSelected(purposeButtons, btn, "purpose") } }
        styleButtons.forEach { btn -> btn.setOnClickListener { setSelected(styleButtons, btn, "style") } }

        skipButton.setOnClickListener {
            findNavController().navigate(R.id.action_selectQuestionFragment_to_outfitRecommendationFragment)
        }

        val submitButton = view.findViewById<Button>(R.id.submitButton)
        submitButton.setOnClickListener {
            val mood = selectedMood?.text?.toString() ?: "선택 안 함"
            val purpose = selectedPurpose?.text?.toString() ?: "선택 안 함"
            val style = selectedStyle?.text?.toString() ?: "선택 안 함"
            val time = confirmedHour ?: "선택 안 함"

            Log.d("선택 결과", "시간: $time, 기분: $mood, 목적: $purpose, 스타일: $style")

            // TODO: 선택값들을 ViewModel이나 Bundle로 넘겨서 다음 화면에서 활용 가능
            findNavController().navigate(R.id.action_selectQuestionFragment_to_outfitRecommendationFragment)
        }
    }
}