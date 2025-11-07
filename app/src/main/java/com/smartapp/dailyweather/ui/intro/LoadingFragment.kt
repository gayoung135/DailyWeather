package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

class LoadingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }

    override fun onResume() {
        super.onResume()

        view?.postDelayed({

            val permissionGranted = arguments?.getBoolean("permissionGranted") ?: false

            if (permissionGranted) {
                // 허용 → 질문 화면
                findNavController().navigate(R.id.selectQuestionFragment)
            } else {
                // 거부 → 수동 위치 화면
                findNavController().navigate(R.id.manualLocationFragment)
            }

        }, 2000)
    }

}
