package com.bonstead.pitdroid

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.Time
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bonstead.pitdroid.HeaterMeter.NamedSample

class GaugeFragment : Fragment(), HeaterMeter.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var mGauge: GaugeView
    private val mProbeHands = arrayOfNulls<GaugeHandView>(HeaterMeter.kNumProbes)
    private lateinit var mSetPoint: GaugeHandView

    private lateinit var mLastUpdate: TextView
    private var mServerTime = 0
    private val mTime = Time()
    private var mSettingPit = false

    // FIXED: Added nullable ? types to signature
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_gauge, container, false)

        // FIXED: Modernized view casting
        mGauge = view.findViewById<GaugeView>(R.id.thermometer)
        mProbeHands[0] = view.findViewById<GaugeHandView>(R.id.pitHand)
        mProbeHands[1] = view.findViewById<GaugeHandView>(R.id.probe1Hand)
        mProbeHands[2] = view.findViewById<GaugeHandView>(R.id.probe2Hand)
        mProbeHands[3] = view.findViewById<GaugeHandView>(R.id.probe3Hand)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val masterLayout = view.findViewById<LinearLayout>(R.id.masterLayout)
            masterLayout.orientation = LinearLayout.HORIZONTAL

            val gaugeLayout = view.findViewById<LinearLayout>(R.id.gaugeLayout)
            gaugeLayout.orientation = LinearLayout.HORIZONTAL
        }

        mSetPoint = view.findViewById<GaugeHandView>(R.id.setPoint)
        mSetPoint.mListener = object : GaugeHandView.Listener {
            override fun onValueChanged(value: Float) {
                mSettingPit = true

                val setTempView = inflater.inflate(R.layout.dialog_settemp, null)

                val picker = setTempView.findViewById<NumberPicker>(R.id.temperature)
                picker.minValue = mGauge.minValue
                picker.maxValue = mGauge.maxValue
                picker.value = value.toInt()

                // FIXED: Use requireActivity() instead of activity
                val builder = AlertDialog.Builder(requireActivity())
                builder.setView(setTempView)
                builder.setTitle("New pit set temp")
                builder.setPositiveButton("Set") { _, _ ->
                    val newTemp = picker.value

                    val trd = Thread(Runnable {
                        HeaterMeter.changePitSetTemp(newTemp)
                        mSettingPit = false
                    })
                    trd.start()
                }
                    .setNegativeButton("Cancel") { _, _ -> mSettingPit = false }
                    .create().show()
            }
        }

        mLastUpdate = view.findViewById<TextView>(R.id.lastUpdate)

        return view
    }

    override fun onResume() {
        super.onResume()
        HeaterMeter.addListener(this)

        // FIXED: Use requireContext() instead of activity.application.baseContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        updatePrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        HeaterMeter.removeListener(this)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        if (latestSample != null) {
            for (p in 0 until HeaterMeter.kNumProbes) {
                if (latestSample.mProbes[p].isNaN()) {
                    mProbeHands[p]?.visibility = View.GONE
                    mProbeHands[p]?.setHandTarget(0f)
                } else {
                    mProbeHands[p]?.visibility = View.VISIBLE
                    mProbeHands[p]?.setHandTarget(latestSample.mProbes[p].toFloat())
                }

                if (p > 0 && !latestSample.mProbes[p].isNaN()) {
                    mProbeHands[p]?.name = latestSample.mProbeNames[p]
                }
            }

            if (!latestSample.mSetPoint.isNaN() && !mSetPoint.isDragging && !mSettingPit)
                mSetPoint.setHandTarget(latestSample.mSetPoint.toFloat())

            if (mServerTime < latestSample.mTime) {
                mTime.setToNow()
                mLastUpdate.text = mTime.format("%r")
                mServerTime = latestSample.mTime
            }
        }
    }

    // FIXED: Added nullable ? types to signature
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updatePrefs(sharedPreferences)
    }

    private fun updatePrefs(sharedPreferences: SharedPreferences?) {
        // FIXED: Inline replacement for the missing SettingsFragment.getMinMax function.
        // We safely attempt to read strings and convert them, defaulting to 0 - 500 if missing.
        val minTempStr = sharedPreferences?.getString("minTemp", "0") ?: "0"
        val maxTempStr = sharedPreferences?.getString("maxTemp", "500") ?: "500"

        val minTemp = minTempStr.toIntOrNull() ?: 0
        val maxTemp = maxTempStr.toIntOrNull() ?: 500

        mGauge.updateRange(minTemp, maxTemp)
    }
}