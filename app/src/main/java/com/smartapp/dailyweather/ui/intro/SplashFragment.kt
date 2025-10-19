package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

class SplashFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_splash, container, false)
        // 레이아웃만 확인하는 단계: 1.5초 뒤 다음 화면 이동
        Handler(Looper.getMainLooper()).postDelayed({
            findNavController().navigate(R.id.action_splash_to_locationGps)
        }, 1500)
        return v
    }
}
