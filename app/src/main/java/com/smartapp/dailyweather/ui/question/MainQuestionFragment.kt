package com.smartapp.dailyweather.ui.question

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.smartapp.dailyweather.MainActivity
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.databinding.FragmentMainQuestionBinding
import com.smartapp.dailyweather.databinding.ItemOptionBinding
import com.smartapp.dailyweather.ui.intro.LocationViewModel
import com.smartapp.dailyweather.ui.intro.LocationViewModelFactory
import kotlinx.coroutines.launch

class MainQuestionFragment : Fragment() {
    private var isTimeSelected = false
    private val mainQuestionViewModel: MainQuestionViewModel by viewModels()
    lateinit var binding: FragmentMainQuestionBinding

    private var hasNavigatedToOutfitLoading = false

    // 추가: 저장 중인지 여부 (더블 클릭 방지용)
    private var isSaving = false

    private val locationViewModel: LocationViewModel by navGraphViewModels(R.id.nav_intro) {
        LocationViewModelFactory((requireActivity() as MainActivity).sharedLocationRepo)
    }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setFilterOptions()

        val timePicker = view.findViewById<NumberPicker>(R.id.timePicker)

        // 현재 시간
        val now = SystemTimeProvider().nowKorea()
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val hourLabels = Array(24) { idx ->
            val t = now.plusHours((idx + 1).toLong())

            val dayLabel = if (t.toLocalDate().isEqual(now.toLocalDate())) {
                "오늘"
            } else {
                "내일"
            }

            val hourStr = String.format("%02d", t.hour)
            "$dayLabel ${hourStr}시"
        }

        // NumberPicker 설정
        timePicker.displayedValues = null
        timePicker.minValue = 0
        timePicker.maxValue = hourLabels.size - 1
        timePicker.wrapSelectorWheel = false
        timePicker.displayedValues = hourLabels
        timePicker.value = 0

        var selectedHourOffset: Int? = null
        var isSkipped: Boolean = false // 추가: 스킵 여부 저장할 변수

        val btnTimeYes = view.findViewById<Button>(R.id.btnTimeYes)
        val btnTimeNo = view.findViewById<Button>(R.id.btnTimeNo)

        btnTimeYes.setOnClickListener {
            isTimeSelected = true
            val index = timePicker.value
            val offset = index + 1

            selectedHourOffset = offset
            isSkipped = false  // 시간 YES → skipped=false
            mainQuestionViewModel.setSelectedHour(offset)

            btnTimeYes.setBackgroundColor(Color.parseColor("#77ADFE"))
            btnTimeYes.setTextColor(Color.WHITE)
            btnTimeNo.setBackgroundColor(Color.WHITE)
            btnTimeNo.setTextColor(Color.parseColor("#3E3E3E"))
        }

        btnTimeNo.setOnClickListener {
            isTimeSelected = true
            selectedHourOffset = null
            isSkipped = true   // 시간 NO → skipped=true
            mainQuestionViewModel.setSelectedHour(null)

            btnTimeNo.setBackgroundColor(Color.parseColor("#f78d86"))
            btnTimeNo.setTextColor(Color.WHITE)
            btnTimeYes.setBackgroundColor(Color.WHITE)
            btnTimeYes.setTextColor(Color.parseColor("#3E3E3E"))
        }

        binding.submitButton.setOnClickListener {

            // 추가: 이미 저장 중이면 바로 무시 → 더블클릭 막기
            if (isSaving) {
                Log.d("MainQuestion", "이미 저장 중이라 클릭 무시")
                return@setOnClickListener
            }
            if (!isTimeSelected) {
                Toast.makeText(requireContext(), "YES 또는 NO를 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val grid = locationViewModel.grid.value ?: run {
                Toast.makeText(requireContext(), "위치 정보가 아직 없습니다.", Toast.LENGTH_SHORT).show()
                Log.d("MainQuestion", "grid LiveData is null")
                return@setOnClickListener
            }

            // 2) 목적 / 기분 / 스타일 선택 여부 확인
            val purposeCode = answerMap["목적"]
            val styleCode = answerMap["스타일"]
            val moodCode = answerMap["기분"]

            val missingFields = mutableListOf<String>()
            if (purposeCode == null) missingFields += "외출 목적"
            if (moodCode == null)   missingFields += "기분"
            if (styleCode == null)  missingFields += "스타일"

            if (missingFields.isNotEmpty()) {
                val msg = missingFields.joinToString(", ") + "을(를) 선택해주세요."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val regionName = locationViewModel.selectedRegion.value?.displayName
            val subRegionName = locationViewModel.selectedSubRegion.value?.name

            viewLifecycleOwner.lifecycleScope.launch {

                try {
                    //  추가: 저장 시작 플래그 + 버튼 잠시 비활성화
                    isSaving = true
                    binding.submitButton.isEnabled = false

                    mainQuestionViewModel.saveLastUserStateWithApi(
                        grid = grid,
                        regionName = regionName,
                        subRegionName = subRegionName,
                        selectedHourOffset = selectedHourOffset,
                        purposeCode = purposeCode,
                        styleCode = styleCode,
                        moodCode = moodCode,
                        skipped = isSkipped   //  핵심 수정
                    )

                    val navController = findNavController()
                    if (!hasNavigatedToOutfitLoading &&
                        navController.currentDestination?.id == R.id.mainQuestionFragment
                    ) {
                        hasNavigatedToOutfitLoading = true
                        navController.navigate(
                            R.id.action_mainQuestionFragment_to_outfitLoadingFragment
                        )
                    }
                } finally {
                    // 추가: 저장 끝 → 다시 클릭 가능
                    isSaving = false
                    binding.submitButton.isEnabled = true
                }
            }
        }
    }

    private fun setFilterOptions() {
        mainQuestionViewModel.enumToDTO()

        val purposeList = mainQuestionViewModel.getPurposeOptions()
        val moodList = mainQuestionViewModel.getMoodOptions()
        val styleList = mainQuestionViewModel.getStyleOptions()

        val purposeAdapter = OptionRVAdapter { _, value ->
            answerMap["목적"] = value
        }
        val moodAdapter = OptionRVAdapter { _, value ->
            answerMap["기분"] = value
            mainQuestionViewModel.setSelectedMood(value)
        }
        val styleAdapter = OptionRVAdapter { _, value ->
            answerMap["스타일"] = value
        }

        binding.apply {
            purposeGroup.apply {
                adapter = purposeAdapter
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                }
            }

            moodGroup.apply {
                adapter = moodAdapter
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                }
            }

            styleGroup.apply {
                adapter = styleAdapter
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                }
            }
        }

        purposeAdapter.addOption(purposeList)
        moodAdapter.addOption(moodList)
        styleAdapter.addOption(styleList)
    }
}

class OptionRVAdapter(
    private val onItemClick: (position: Int, value: String) -> Unit
) : RecyclerView.Adapter<OptionRVAdapter.ViewHolder>() {

    private var optionList: List<String> = emptyList()
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    fun addOption(optionList: List<String>) {
        this.optionList = optionList
        selectedPosition = RecyclerView.NO_POSITION
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

                if (oldPos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(oldPos)
                }
                notifyItemChanged(newPos)

                onItemClick(newPos, option)
            }

            if (isSelected) {
                btn.setTextColor(Color.WHITE)
                btn.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#77ADFE"))
            } else {
                btn.setTextColor(Color.parseColor("#3E3E3E"))
                btn.backgroundTintList =
                    ColorStateList.valueOf(Color.WHITE)
            }
        }
    }
}
