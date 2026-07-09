package com.bonstead.pitdroid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bonstead.pitdroid.HeaterMeter.NamedSample

class BigDashFragment : Fragment(), HeaterMeter.Listener {

    private lateinit var pitTempVal: TextView
    private lateinit var meatTempVal: TextView
    private lateinit var tendernessProgress: ProgressBar
    private lateinit var tendernessVal: TextView
    private lateinit var holdingSwitch: SwitchCompat
    private lateinit var resetCookButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_big_dash, container, false)

        pitTempVal = view.findViewById(R.id.pitTempVal)
        meatTempVal = view.findViewById(R.id.meatTempVal)
        tendernessProgress = view.findViewById(R.id.tendernessProgress)
        tendernessVal = view.findViewById(R.id.tendernessVal)
        holdingSwitch = view.findViewById(R.id.holdingSwitch)
        resetCookButton = view.findViewById(R.id.resetCookButton)

        // Initialize UI with current state
        holdingSwitch.isChecked = HeaterMeter.mIsHolding
        updateTendernessUI()

        // Handle Holding Switch toggle
        holdingSwitch.setOnCheckedChangeListener { _, isChecked ->
            HeaterMeter.mIsHolding = isChecked
            savePreferences()
        }

        // Handle Reset Button click
        resetCookButton.setOnClickListener {
            HeaterMeter.mAccumulatedTenderness = 0.0
            HeaterMeter.mLastTendernessUpdate = System.currentTimeMillis()
            updateTendernessUI()
            savePreferences()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        HeaterMeter.addListener(this)
        updateTendernessUI()
    }

    override fun onPause() {
        super.onPause()
        HeaterMeter.removeListener(this)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        if (latestSample == null) {
            pitTempVal.text = "-"
            meatTempVal.text = "-"
            return
        }

        // Update Pit Temp (Probe 0)
        val pitTemp = latestSample.mProbes[0]
        if (pitTemp.isNaN()) {
            pitTempVal.text = "-"
        } else {
            pitTempVal.text = "${pitTemp.toInt()}°"
        }

        // Update Meat Temp (Probe 1)
        val meatTemp = latestSample.mProbes[1]
        if (meatTemp.isNaN()) {
            meatTempVal.text = "-"
        } else {
            meatTempVal.text = "${meatTemp.toInt()}°"
        }

        // Update the Tenderness UI on every network tick
        updateTendernessUI()
    }

    private fun updateTendernessUI() {
        val tenderness = HeaterMeter.mAccumulatedTenderness

        // Update text with one decimal place
        tendernessVal.text = String.format("%.1f%%", tenderness)

        // Update progress bar (multiply by 10 because max is 1000 for 0.1% resolution)
        tendernessProgress.progress = (tenderness * 10).toInt()
    }

    private fun savePreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        editor.putString("accumulatedTenderness", HeaterMeter.mAccumulatedTenderness.toString())
        editor.putBoolean("isHolding", HeaterMeter.mIsHolding)
        editor.putLong("lastTendernessUpdate", HeaterMeter.mLastTendernessUpdate)
        editor.apply()
    }
}