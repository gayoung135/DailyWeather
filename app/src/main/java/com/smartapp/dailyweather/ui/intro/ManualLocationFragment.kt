package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.smartapp.dailyweather.MainActivity
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.repo.LocationRepository
import kotlinx.coroutines.launch

// 사용자가 "자동 위치 설정 거부"를 눌렀을 때 이동되는 화면.
// 여기서 직접 지역(시/군/구)을 선택할 수 있는 UI가 표시됨.
// LocationGpsFragment → ManualLocationFragment → SelectQuestionFragment 순서로 이어짐.
class ManualLocationFragment : Fragment() {

    private val locationViewModel: LocationViewModel by navGraphViewModels(R.id.nav_intro) {
        LocationViewModelFactory((requireActivity() as MainActivity).sharedLocationRepo)
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
    /** 상위 스피너도 프로그램으로 힌트 선택 직후 콜백 무시 플래그 */
    private var ignoreTopFirstCallback = false
    /** 상위 스피너가 '사용자 터치'로 열렸는지 표시하는 플래그 */
    private var userTouchTop = false
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinner: Spinner = view.findViewById(R.id.spinner)
        val spinnerSub: Spinner = view.findViewById(R.id.spinner2)

        val regions = Region.entries.toList()
        // Region.SELECT(“시/도”)는 실제 목록에서 제외한 리스트
        val regionsNoHint = regions.filter { it != Region.SELECT }
        // 표시용 이름 리스트 + 맨 끝에 힌트 문구 추가
        val regionNamesWithHint = regionsNoHint.map { it.displayName } + "시/도"

        // 상위(시/도) — 힌트는 드롭다운에서 숨김
        spinner.adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            regionNamesWithHint
        ) {
            override fun getCount(): Int = super.getCount() - 1 // 힌트 제외
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 상위 스피너: 처음엔 힌트가 선택된 상태로 시작
        ignoreTopFirstCallback = true
        spinner.setSelection((spinner.adapter as ArrayAdapter<*>).count, false)
        // setSelection이 콜백을 안 줄 수도 있으니, 다음 프레임에 플래그를 내려줌
        spinner.post { ignoreTopFirstCallback = false }

        // 사용자가 실제로 스피너를 터치했는지 기록
        spinner.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
                if (ev?.action == MotionEvent.ACTION_DOWN) {
                    userTouchTop = true
                }
                return false
            }
        })

        // 하위(시/군/구) – 최초 placeholder 어댑터
        fun setSubPlaceholder() {
            val adapter = object : ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                listOf("시/군/구")
            ) {

            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            spinnerSub.adapter = adapter
            spinnerSub.isEnabled = false
            spinnerSub.setSelection(0, false)
        }
        setSubPlaceholder()

        // 초기 표시: 마지막 항목(SELECT)  ← 상위 스피너에서 힌트 선택으로 대체되었으므로 불필요 (삭제)
        // spinner.setSelection(regions.lastIndex, false)

        // 시/도 선택 리스너
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {

                // ★ 변경: isPressed 체크 제거, 대신 '사용자 터치' 플래그로 판별
                if (!userTouchTop) return
                userTouchTop = false

                // 프로그램에 의한 첫 콜백은 무시
                if (ignoreTopFirstCallback) {
                    ignoreTopFirstCallback = false
                    return
                }

                // 드롭다운에는 힌트가 제외된 상태이므로 regionsNoHint 기준으로 처리
                if (position !in regionsNoHint.indices) {
                    setSubPlaceholder()
                    return
                }
                val selectedRegion = regionsNoHint[position]

                val names = selectedRegion.subRegions.map { it.name }
                if (names.isEmpty()) {
                    setSubPlaceholder()
                    return
                }

                // 새 어댑터로 교체
                val dataWithHint = names + "시/군/구 선택"
                val adapter = object : ArrayAdapter<String>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    dataWithHint
                ) {
                    // 드롭다운에는 마지막 힌트를 제외한 실제 항목만 노출
                    override fun getCount(): Int = super.getCount() - 1
                }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinnerSub.adapter = adapter
                spinnerSub.isEnabled = true

                // onItemSelected(0)은 '프로그램에 의한 선택'이므로 무시하도록 플래그 ON
                ignoreSubFirstCallback = true
                spinnerSub.setSelection(adapter.count, false) // 힌트가 선택된 상태로 시작
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

                // 상위 스피너는 regionsNoHint 기준 인덱스 사용
                val rPos = spinner.selectedItemPosition
                if (rPos !in regionsNoHint.indices) return
                val region = regionsNoHint[rPos]

                val subs = region.subRegions
                // 힌트를 드롭다운에서 제외했으므로 pos는 실제 인덱스 그대로 사용
                if (pos !in subs.indices) return

                val sub = subs[pos]
                locationViewModel.onRegionSelected(region)
                locationViewModel.onSubRegionSelected(sub)

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
        locationViewModel.onRegionSelected(testRegion)
        locationViewModel.onSubRegionSelected(testSub)
        Log.d("TestManualLocation", "TEST start → ${testRegion.displayName} / ${testSub.name}")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val grid = locationViewModel.requestGrid()
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
