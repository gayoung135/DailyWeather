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
    private val mainQuestionViewModel: MainQuestionViewModel by viewModels()
    lateinit var binding: FragmentMainQuestionBinding


    // OutfitLoading 으로 이미 이동했는지 여부
    private var hasNavigatedToOutfitLoading = false

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

        // KST 기준 현재 시간 (분/초 0초로 정규화)
        val now = SystemTimeProvider().nowKorea()
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // "지금 이후 24시간" 라벨 생성 (오늘/내일)
        val hourLabels = Array(24) { idx ->
            val t = now.plusHours((idx + 1).toLong())  // idx 0 → +1시간

            val dayLabel = if (t.toLocalDate().isEqual(now.toLocalDate())) {
                "오늘"
            } else {
                "내일"
            }

            val hourStr = String.format("%02d", t.hour)
            "$dayLabel ${hourStr}시"
        }

        // NumberPicker 세팅
        timePicker.displayedValues = null // 기존 값 초기화
        timePicker.minValue = 0
        timePicker.maxValue = hourLabels.size - 1   // 0 ~ 23
        timePicker.wrapSelectorWheel = false
        timePicker.displayedValues = hourLabels
        timePicker.value = 0  // 기본값: "지금 기준 다음 정시"

        var selectedHourOffset: Int? = null

        val btnTimeYes = view.findViewById<Button>(R.id.btnTimeYes)
        val btnTimeNo = view.findViewById<Button>(R.id.btnTimeNo)

        btnTimeYes.setOnClickListener {
            val index = timePicker.value       // 0 ~ 23
            val offset = index + 1            // +1시간 ~ +24시간

            selectedHourOffset = offset
            mainQuestionViewModel.setSelectedHour(offset)

            btnTimeYes.setBackgroundColor(Color.parseColor("#2196F3"))
            btnTimeYes.setTextColor(Color.WHITE)
            btnTimeNo.setBackgroundColor(Color.WHITE)
            btnTimeNo.setTextColor(Color.parseColor("#3E3E3E"))
        }

        btnTimeNo.setOnClickListener {
            selectedHourOffset = null
            mainQuestionViewModel.setSelectedHour(null)

            btnTimeNo.setBackgroundColor(Color.parseColor("#F44336"))
            btnTimeNo.setTextColor(Color.WHITE)
            btnTimeYes.setBackgroundColor(Color.WHITE)
            btnTimeYes.setTextColor(Color.parseColor("#3E3E3E"))
        }

        binding.submitButton.setOnClickListener {
            val grid = locationViewModel.grid.value ?: run {
                Toast.makeText(requireContext(), "위치 정보가 아직 없습니다.", Toast.LENGTH_SHORT).show()
                Log.d("MainQuestion", "grid LiveData is null")
                return@setOnClickListener
            }

            val purposeCode = answerMap["목적"]
            val styleCode = answerMap["스타일"]
            val moodCode = answerMap["기분"]

            Log.d(
                "MainQuestion",
                "grid=(${grid.x},${grid.y}), purpose=$purposeCode, style=$styleCode, mood=$moodCode"
            )

            viewLifecycleOwner.lifecycleScope.launch {
                mainQuestionViewModel.saveLastUserStateWithApi(
                    grid = grid,
                    selectedHourOffset = selectedHourOffset,
                    purposeCode = purposeCode,
                    styleCode = styleCode,
                    moodCode = moodCode,
                    skipped = false
                )

                Log.d("MainQuestion", "질문 응답 저장 완료 → 추천 화면 이동")

                // 현재 목적지가 mainQuestionFragment 이고, 아직 안 갔을 때만 이동
                val navController = findNavController()
                if (!hasNavigatedToOutfitLoading &&
                    navController.currentDestination?.id == R.id.mainQuestionFragment
                ) {
                    hasNavigatedToOutfitLoading = true
                    navController.navigate(
                        R.id.action_mainQuestionFragment_to_outfitLoadingFragment
                    )
                } else {
                    Log.d(
                        "MainQuestion",
                        "이미 이동했거나 현재 목적지가 아님 → navigate 스킵. currentDest=${navController.currentDestination}"
                    )
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
                    ColorStateList.valueOf(Color.BLUE)
            } else {
                btn.setTextColor(Color.parseColor("#3E3E3E"))
                btn.backgroundTintList =
                    ColorStateList.valueOf(Color.WHITE)
            }
        }
    }
}