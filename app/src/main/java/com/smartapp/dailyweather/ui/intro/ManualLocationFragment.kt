package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

class ManualLocationFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_manual_location, container, false)
        v.findViewById<Button>(R.id.btnGoQuestion).setOnClickListener {
            findNavController().navigate(R.id.action_manualLocation_to_selectQuestion)
        }
        return v
    }
}
