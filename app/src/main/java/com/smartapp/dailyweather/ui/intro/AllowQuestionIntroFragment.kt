package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R

class AllowQuestionIntroFragment : Fragment(R.layout.fragment_allow_question_intro) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // "좋아요" 버튼 클릭 시
        view.findViewById<Button>(R.id.btnAllow).setOnClickListener {
            findNavController().navigate(
                R.id.action_allowQuestionIntro_to_selectQuestion,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.allowQuestionIntroFragment, true)
                    .build()
            )
        }

        // "스킵할래요" 버튼 클릭 시
        view.findViewById<Button>(R.id.btnSkip).setOnClickListener {
            findNavController().navigate(
                R.id.action_allowQuestionIntro_to_selectQuestion,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.allowQuestionIntroFragment, true)
                    .build()
            )
        }
    }
}