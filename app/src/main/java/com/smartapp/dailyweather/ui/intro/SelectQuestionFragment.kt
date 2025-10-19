package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.smartapp.dailyweather.R

class SelectQuestionFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_select_question, container, false)
    }
}
