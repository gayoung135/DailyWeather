package com.smartapp.dailyweather.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartapp.dailyweather.R
import com.smartapp.dailyweather.databinding.FragmentMainQuestionBinding

class SelectQuestionFragment : Fragment() {

    private var _binding: FragmentMainQuestionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.skipButton.setOnClickListener {
            Toast.makeText(requireContext(), "SKIP 선택됨", Toast.LENGTH_SHORT).show()
        }

        binding.submitButton.setOnClickListener {
            val time = binding.timeGroup.checkedRadioButtonId.let {
                view.findViewById<RadioButton>(it)?.text
            }
            val confirm = binding.confirmGroup.checkedRadioButtonId.let {
                view.findViewById<RadioButton>(it)?.text
            }
            val purpose = binding.purposeGroup.checkedRadioButtonId.let {
                view.findViewById<RadioButton>(it)?.text
            }
            val mood = binding.moodGroup.checkedRadioButtonId.let {
                view.findViewById<RadioButton>(it)?.text
            }
            val style = binding.styleGroup.checkedRadioButtonId.let {
                view.findViewById<RadioButton>(it)?.text
            }

            Toast.makeText(
                requireContext(),
                "선택 완료:\n시간: $time\n확인: $confirm\n목적: $purpose\n기분: $mood\n스타일: $style",
                Toast.LENGTH_LONG
            ).show()

            findNavController().navigate(
                R.id.action_selectQuestion_to_outfitRecommendation,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.selectQuestionFragment, true)
                    .build()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}