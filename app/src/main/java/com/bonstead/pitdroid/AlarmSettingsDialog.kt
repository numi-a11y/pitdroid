package com.bonstead.pitdroid

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

// MODERNIZED: Uses AndroidX DialogFragment
class AlarmSettingsDialog : DialogFragment() {
    var mListener: AlarmDialogListener? = null

    interface AlarmDialogListener {
        fun onFinishAlarmDialog(probeIndex: Int)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // FIXED: Safe unwrapping of arguments
        val probeIndex = arguments?.getInt("probeIndex", 0) ?: 0

        // MODERNIZED: Use AndroidX AlertDialog and requireActivity()
        val builder = AlertDialog.Builder(requireActivity())

        // Get the layout inflater safely
        val inflater = requireActivity().layoutInflater

        // Inflate and set the layout for the dialog
        val view = inflater.inflate(R.layout.dialog_alarm, null)

        builder.setView(view).setPositiveButton(R.string.ok) { _, _ ->
            // FIXED: Cleaned up casting and added safe parsing to prevent NumberFormat crashes
            val textBelow = view.findViewById<EditText>(R.id.belowTemp)
            var loValue = textBelow.text.toString().toIntOrNull() ?: 0

            val checkBelow = view.findViewById<CheckBox>(R.id.belowCheck)
            if (!checkBelow.isChecked) {
                loValue *= -1
            }

            val textAbove = view.findViewById<EditText>(R.id.aboveTemp)
            var hiValue = textAbove.text.toString().toIntOrNull() ?: 0

            val checkAbove = view.findViewById<CheckBox>(R.id.aboveCheck)
            if (!checkAbove.isChecked) {
                hiValue *= -1
            }

            // Set the new settings on the HeaterMeter and tell it to save them
            HeaterMeter.mProbeLoAlarm[probeIndex] = loValue
            HeaterMeter.mProbeHiAlarm[probeIndex] = hiValue

            // FIXED: Safe call operator for the listener
            mListener?.onFinishAlarmDialog(probeIndex)
        }.setNegativeButton(R.string.cancel, null)

        var loVal = HeaterMeter.mProbeLoAlarm[probeIndex]
        var hiVal = HeaterMeter.mProbeHiAlarm[probeIndex]
        var loEnabled = true
        var hiEnabled = true

        if (loVal < 0) {
            loEnabled = false
            loVal *= -1
        }
        if (hiVal < 0) {
            hiEnabled = false
            hiVal *= -1
        }

        val checkBelow = view.findViewById<CheckBox>(R.id.belowCheck)
        checkBelow.isChecked = loEnabled

        val textBelow = view.findViewById<EditText>(R.id.belowTemp)
        textBelow.setText(loVal.toString())

        val checkAbove = view.findViewById<CheckBox>(R.id.aboveCheck)
        checkAbove.isChecked = hiEnabled

        val textAbove = view.findViewById<EditText>(R.id.aboveTemp)
        textAbove.setText(hiVal.toString())

        return builder.create()
    }
}