package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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


        //"다음으로" 버튼 클릭 시 동작
        // 완성된 View 반환 (이게 실제로 사용자에게 보이는 화면)
        return v
    }

    /** 하위 스피너를 새로 채운 직후 발생하는 최초 콜백 무시용 플래그 */
    private var ignoreSubFirstCallback = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinner: Spinner = view.findViewById(R.id.spinner)
        val spinnerSub: Spinner = view.findViewById(R.id.spinner2)

        val regions = Region.entries.toList()
        val regionNames = regions.map { it.displayName }

        // 상위(시/도)
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            regionNames
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 하위(시/군/구) – 최초 placeholder 어댑터
        fun setSubPlaceholder() {
            spinnerSub.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                listOf("시/군/구")
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            spinnerSub.isEnabled = false
        }
        setSubPlaceholder()

        // 초기 표시: 마지막 항목(SELECT)
        spinner.setSelection(regions.lastIndex, false)

        // 시/도 선택 리스너
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position !in regions.indices) return
                val selectedRegion = regions[position]

                if (selectedRegion == Region.SELECT) {
                    setSubPlaceholder()
                    return
                }

                val names = selectedRegion.subRegions.map { it.name }
                if (names.isEmpty()) {
                    setSubPlaceholder()
                    return
                }

                // 새 어댑터로 교체
                spinnerSub.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    names
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinnerSub.isEnabled = true

                // onItemSelected(0)은 '프로그램에 의한 선택'이므로 무시하도록 플래그 ON
                ignoreSubFirstCallback = true
                spinnerSub.setSelection(0, false)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 시/군/구 선택 리스너
        spinnerSub.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                // 첫 콜백(프로그램 설정)은 무시
                if (ignoreSubFirstCallback) {
                    ignoreSubFirstCallback = false
                    return
                }

                val rPos = spinner.selectedItemPosition
                if (rPos !in regions.indices) return
                val region = regions[rPos]
                if (region == Region.SELECT) return

                val subs = region.subRegions
                if (pos !in subs.indices) return

                val sub = subs[pos]
                vm.onRegionSelected(region)
                vm.onSubRegionSelected(sub)

                // 사용자 선택에 의해서만 이동
                val nav = findNavController()
                if (isAdded && nav.currentDestination?.id == R.id.manualLocationFragment) {
                    nav.navigate(R.id.action_manualLocationFragment_to_selectQuestionFragment)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        // === 테스트용: 실제 변환을 로그로 확인 ===
        val testRegion = Region.SEOUL
        val testSub = testRegion.subRegions.first { it.name == "종로구" }
        vm.onRegionSelected(testRegion)
        vm.onSubRegionSelected(testSub)
        Log.d("TestManualLocation", "TEST start → ${testRegion.displayName} / ${testSub.name}")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val grid = vm.requestGrid()
                Log.d("TestManualLocation", "SUCCESS → grid: x=${grid?.x}, y=${grid?.y}")
            } catch (e: Exception) {
                Log.e("TestManualLocation", "FAIL → ${e.message}", e)
            }
        }
    }
}
//요약
//자동 위치 설정을 거부한 사용자가
// 직접 지역(시·군·구)을 선택하는 화면
// —> 선택 후 질문 화면(SelectQuestionFragment)으로 이동.