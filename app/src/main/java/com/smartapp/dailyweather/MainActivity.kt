package com.smartapp.dailyweather

import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartapp.dailyweather.core.NetworkObserver
import com.smartapp.dailyweather.data.remote.RetrofitClient
import com.smartapp.dailyweather.data.repo.LocationRepository
import com.smartapp.dailyweather.ui.intro.LocationViewModel
import com.smartapp.dailyweather.ui.intro.LocationViewModelFactory
import kotlinx.coroutines.launch

// 앱 실행 시 가장 먼저 호출되는 액티비티(Activity).
// 모든 Fragment(Splash, LocationGps, ManualLocation 등)를 담는 "컨테이너" 역할을 함.
// 즉, 네비게이션(nav_intro.xml)에 정의된 화면 흐름을 실제로 표시해주는 Activity임.
class MainActivity : AppCompatActivity() {

    private lateinit var networkObserver: NetworkObserver
    private var hasNavigated = false
    private var networkDialog: AlertDialog? = null

    val sharedLocationRepo by lazy { LocationRepository() }
    val locationViewModel: LocationViewModel by viewModels {
        LocationViewModelFactory((this@MainActivity).sharedLocationRepo)
    }


    // onCreate(): 액티비티가 처음 실행될 때 호출되는 함수
    // 여기서 레이아웃을 설정하고, 시스템 UI(상태바 등) 처리를 함.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        // 상태바와 내비게이션바를 투명하게 만들어 전체화면 느낌을 주는 설정
        enableEdgeToEdge()

        // 화면 레이아웃 설정
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this) {
            // 아무 것도 안 함 → 시스템 뒤로가기 완전 무시
        }

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

        networkObserver = NetworkObserver(this)
        observeNetwork()

    }

    override fun onStart() {
        super.onStart()
        networkObserver.start()
    }

    override fun onStop() {
        super.onStop()
        networkObserver.stop()
        dismissNetworkDialog()
    }

    private fun observeNetwork() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkObserver.isOnline.collect { online ->
                    if (online) {
                        dismissNetworkDialog()
                        navigateToLocationIfNeeded()
                    } else {
                        showNetworkBlockDialog()
                    }
                }
            }
        }
    }

    private fun navController() =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController

    private fun navigateToLocationIfNeeded() {
        if (hasNavigated) return
        val nav = navController()
        val current = nav.currentDestination?.id
        if (current == R.id.networkGateFragment) { // startDestination에서만 이동
            hasNavigated = true
            nav.navigate(R.id.locationGpsFragment)
        }
    }

    private fun showNetworkBlockDialog() {
        if (networkDialog?.isShowing == true) return
        networkDialog = MaterialAlertDialogBuilder(this)
            .setTitle("네트워크 연결 필요")
            .setMessage("인터넷이 연결되어 있지 않아 앱을 시작할 수 없습니다.")
            .setCancelable(false)
            .setPositiveButton("닫기") { _, _ ->
                finishAffinity() // 앱 종료
            }
            .create()
        networkDialog?.show()
    }

    private fun dismissNetworkDialog() {
        networkDialog?.dismiss()
        networkDialog = null
    }

}

//요약
//“MainActivity는 앱의 ‘무대’고,
// 그 위에서 여러 Fragment들이 장면처럼 교체되며 실행된다.”