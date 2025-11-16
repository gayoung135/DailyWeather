package com.smartapp.dailyweather.ui.question

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.smartapp.dailyweather.MainActivity
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.databinding.FragmentMainQuestionBinding
import com.smartapp.dailyweather.databinding.ItemOptionBinding
import com.smartapp.dailyweather.ui.intro.LocationViewModel
import com.smartapp.dailyweather.ui.intro.LocationViewModelFactory
import kotlinx.coroutines.launch

class MainQuestionFragment : Fragment() {

    private val mainQuestionViewModel: MainQuestionViewModel by viewModels()
    lateinit var binding: FragmentMainQuestionBinding

    private val locationViewModel: LocationViewModel by navGraphViewModels(R.id.nav_intro) {
        LocationViewModelFactory((requireActivity() as MainActivity).sharedLocationRepo)
    }

    private lateinit var adapterList: List<OptionRVAdapter>

    private val answerMap = mutableMapOf(
        "목적" to null as String?,
        "기분" to null as String?,
        "스타일" to null as String?
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var selectedHour: Int? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // enumToDTO()가 suspend이므로 lifecycleScope에서 호출
        lifecycleScope.launch {
            mainQuestionViewModel.enumToDTO()  // DB 저장 완료까지 기다림
            setFilterOptions()                 // 그 다음 UI 구성
        }

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
            selectedHour = binding.timePicker.value  // 시간 선택됨
            btnTimeYes.setBackgroundColor(Color.parseColor("#2196F3"))
            btnTimeYes.setTextColor(Color.WHITE)
            btnTimeNo.setBackgroundColor(Color.WHITE)
            btnTimeNo.setTextColor(Color.parseColor("#3E3E3E"))
        }

        btnTimeNo.setOnClickListener {
            selectedHour = null   // 시간 선택 안함
            btnTimeNo.setBackgroundColor(Color.parseColor("#F44336"))
            btnTimeNo.setTextColor(Color.WHITE)
            btnTimeYes.setBackgroundColor(Color.WHITE)
            btnTimeYes.setTextColor(Color.parseColor("#3E3E3E"))
        }

        binding.submitButton.setOnClickListener {
            val grid = locationViewModel.grid.value

            if (grid == null) {
                Toast.makeText(requireContext(), "위치 정보가 아직 없습니다.", Toast.LENGTH_SHORT).show()
                Log.d("MainQuestion", "grid LiveData is null")
                return@setOnClickListener
            }

            val purposeCode = answerMap["목적"]
            val styleCode = answerMap["스타일"]

            Log.d("MainQuestion", "grid=(${grid.x},${grid.y}), purpose=$purposeCode, style=$styleCode")

            mainQuestionViewModel.saveLastUserStateWithApi(
                grid = grid,
                selectedHour = selectedHour,
                purposeCode = purposeCode,
                styleCode = styleCode,
                skipped = false
            )
            findNavController().navigate(R.id.action_mainQuestionFragment_to_outfitRecommendationFragment)
        }
    }

    var map: Map<String, String> = mapOf(Pair("목적", ""),
        Pair("기분", ""), Pair("스타일", ""))

    private fun setFilterOptions() {
        // 영어 코드 → 한글 라벨 매핑
        val purposeLabelMap = mapOf(
            "office" to "출근",
            "celebration" to "모임",
            "date" to "데이트",
            "travel" to "여행",
            "none" to "기타"
        )
        val styleLabelMap = mapOf(
            "formal" to "포멀",
            "casual" to "캐주얼",
            "street" to "스트릿",
            "lovely" to "러블리",
            "none" to "기타"
        )
        val moodLabelMap = mapOf(
            "good" to "좋음",
            "normal" to "보통",
            "bad" to "나쁨"
        )

        val reversePurposeMap = purposeLabelMap.entries.associate { it.value to it.key }
        val reverseStyleMap = styleLabelMap.entries.associate { it.value to it.key }
        val reverseMoodMap = moodLabelMap.entries.associate { it.value to it.key }

        // 1) 데이터 꺼내기
        val purposeList = mainQuestionViewModel.getPurposeOptions()  // 5개
        val moodList = mainQuestionViewModel.getMoodOptions()        // 3개
        val styleList = mainQuestionViewModel.getStyleOptions()      // 5개

        // 2) 라벨 변환
        val purposeLabels = purposeList.map { purposeLabelMap[it] ?: it }
        val moodLabels = moodList.map { moodLabelMap[it] ?: it }
        val styleLabels = styleList.map { styleLabelMap[it] ?: it }

        // 3) 어댑터 3개 생성
        val purposeAdapter = OptionRVAdapter { _, value ->
            answerMap["목적"] = value
        }
        val moodAdapter = OptionRVAdapter { _, value ->
            answerMap["기분"] = value
        }
        val styleAdapter = OptionRVAdapter { _, value ->
            answerMap["스타일"] = value
        }

        // 3) RecyclerView 3개 설정
        binding.apply {

            // 목적
            purposeGroup.apply {
                adapter = purposeAdapter
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                }
            }

            // 기분
            moodGroup.apply {
                adapter = moodAdapter
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                }
            }

            // 스타일
            styleGroup.apply {
                adapter = styleAdapter
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                }
            }
        }

        // 4) 어댑터에 항목 넣기
        purposeAdapter.addOption(purposeLabels)
        moodAdapter.addOption(moodLabels)
        styleAdapter.addOption(styleLabels)
    }
}


class OptionRVAdapter(
    private val onItemClick: (position: Int, value: String) -> Unit
) : RecyclerView.Adapter<OptionRVAdapter.ViewHolder>() {

    private var optionList: List<String> = emptyList()

    // 현재 선택된 포지션 (없으면 -1)
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    fun addOption(optionList: List<String>) {
        this.optionList = optionList
        selectedPosition = RecyclerView.NO_POSITION   // 리스트 바뀌면 선택 초기화
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = optionList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = optionList[position]
        holder.bind(text, position == selectedPosition)
    }

    inner class ViewHolder(
        private val binding: ItemOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(option: String, isSelected: Boolean) {
            val btn = binding.btn

            btn.text = option

            btn.setOnClickListener {
                val oldPos = selectedPosition
                val newPos = bindingAdapterPosition
                if (newPos == RecyclerView.NO_POSITION) return@setOnClickListener

                selectedPosition = newPos

                // 이전 선택 해제
                if (oldPos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(oldPos)
                }
                // 새 선택 적용
                notifyItemChanged(newPos)

                onItemClick(newPos, option)
            }

            // 여기서 선택 여부에 따라 스타일 적용
            if (isSelected) {
                // 선택된 상태
                btn.setTextColor(Color.WHITE)
                btn.backgroundTintList =
                    ColorStateList.valueOf(Color.BLUE)
            } else {
                // 기본 상태
                btn.setTextColor(Color.parseColor("#3E3E3E"))
                btn.backgroundTintList =
                    ColorStateList.valueOf(Color.WHITE)
            }
        }
    }
}