package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.*
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

class LocationGpsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_location_gps, container, false)
        v.findViewById<Button>(R.id.btnAllow).setOnClickListener {
            findNavController().navigate(R.id.action_locationGps_to_selectQuestion) // 더미 이동
        }
        v.findViewById<Button>(R.id.btnManual).setOnClickListener {
            findNavController().navigate(R.id.action_locationGps_to_manualLocation)
        }
        return v
    }
}
