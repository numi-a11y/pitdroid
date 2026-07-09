package com.bonstead.pitdroid

import androidx.fragment.app.Fragment
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.text.format.Time
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

import com.bonstead.pitdroid.AlarmSettingsDialog.AlarmDialogListener
import com.bonstead.pitdroid.HeaterMeter.NamedSample

class DashFragment : Fragment(), HeaterMeter.Listener, AlarmDialogListener {
    private lateinit var mFanSpeed: TextView
    private val mProbeNames = arrayOfNulls<TextView>(HeaterMeter.kNumProbes)
    private val mProbeVals = arrayOfNulls<TextView>(HeaterMeter.kNumProbes)
    private val mProbeTimes = arrayOfNulls<TextView>(HeaterMeter.kNumProbes)
    private lateinit var mPitDelta: TextView

    private lateinit var mLastUpdate: TextView
    private var mServerTime = 0
    private val mTime = Time()

    // FIXED: Added nullable ? types to signature to match modern Fragment API
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dash, container, false)

        // FIXED: Cleaned up the casting syntax
        mFanSpeed = view.findViewById<TextView>(R.id.fanSpeedVal)

        mProbeNames[0] = view.findViewById<TextView>(R.id.probe0Name)
        mProbeNames[1] = view.findViewById<TextView>(R.id.probe1Name)
        mProbeNames[2] = view.findViewById<TextView>(R.id.probe2Name)
        mProbeNames[3] = view.findViewById<TextView>(R.id.probe3Name)

        mProbeVals[0] = view.findViewById<TextView>(R.id.probe0Val)
        mProbeVals[1] = view.findViewById<TextView>(R.id.probe1Val)
        mProbeVals[2] = view.findViewById<TextView>(R.id.probe2Val)
        mProbeVals[3] = view.findViewById<TextView>(R.id.probe3Val)

        mProbeTimes[1] = view.findViewById<TextView>(R.id.probe1Time)
        mProbeTimes[2] = view.findViewById<TextView>(R.id.probe2Time)
        mProbeTimes[3] = view.findViewById<TextView>(R.id.probe3Time)

        mPitDelta = view.findViewById<TextView>(R.id.probe0Delta)
        mLastUpdate = view.findViewById<TextView>(R.id.lastUpdate)

        val probeIds = intArrayOf(R.id.probe0Alarm, R.id.probe1Alarm, R.id.probe2Alarm, R.id.probe3Alarm)
        for (p in 0 until HeaterMeter.kNumProbes) {
            setAlarmClickListener(view, probeIds[p], p)
            updateAlarmButtonImage(view, probeIds[p], p)
        }

        setDefaults()
        HeaterMeter.addListener(this)

        return view
    }

    private fun setAlarmClickListener(view: View, id: Int, index: Int) {
        val button = view.findViewById<ImageButton>(id)
        button.setOnClickListener {
            val dialog = AlarmSettingsDialog()

            val bundle = Bundle()
            bundle.putInt("probeIndex", index)
            dialog.arguments = bundle

            dialog.mListener = this@DashFragment

            // FIXED: Use modern AndroidX parentFragmentManager
            dialog.show(parentFragmentManager, "AlarmDialog")
        }
    }

    override fun onFinishAlarmDialog(probeIndex: Int) {
        var id = 0

        when (probeIndex) {
            0 -> id = R.id.probe0Alarm
            1 -> id = R.id.probe1Alarm
            2 -> id = R.id.probe2Alarm
            3 -> id = R.id.probe3Alarm
        }

        // FIXED: Safely unwrapped the nullable fragment view
        view?.let {
            updateAlarmButtonImage(it, id, probeIndex)
        }

        // FIXED: Use requireContext() and requireActivity() to satisfy null safety
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        HeaterMeter.preferencesChanged(prefs)

        val mainActivity = requireActivity() as MainActivity
        mainActivity.updateAlarmService()
    }

    private fun updateAlarmButtonImage(view: View, id: Int, index: Int) {
        val button = view.findViewById<ImageButton>(id)

        if (HeaterMeter.mProbeLoAlarm[index] > 0 || HeaterMeter.mProbeHiAlarm[index] > 0) {
            button.setImageResource(R.mipmap.ic_alarm_set)
        } else {
            button.setImageResource(R.mipmap.ic_alarm_unset)
        }
    }

    private fun setDefaults() {
        mFanSpeed.text = "-"

        for (p in 0 until HeaterMeter.kNumProbes) {
            mProbeNames[p]?.text = "-"
            mProbeVals[p]?.text = "-"
            mProbeTimes[p]?.text = ""
        }
        mPitDelta.text = ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        HeaterMeter.removeListener(this)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        if (latestSample == null) {
            setDefaults()
        } else {
            mFanSpeed.text = latestSample.mFanSpeed.toInt().toString() + "%"

            for (p in 0 until HeaterMeter.kNumProbes) {
                if (latestSample.mProbeNames[p] == null) {
                    mProbeNames[p]?.text = "-"
                } else {
                    mProbeNames[p]?.text = latestSample.mProbeNames[p] + ": "
                }

                if (latestSample.mProbes[p].isNaN()) {
                    mProbeVals[p]?.text = "-"
                } else {
                    mProbeVals[p]?.text = HeaterMeter.formatTemperature(latestSample.mProbes[p])
                }

                if (HeaterMeter.formatAlarm(p, latestSample.mProbes[p]).isNotEmpty()) {
                    mProbeVals[p]?.setTextColor(Color.RED)
                } else {
                    mProbeVals[p]?.setTextColor(Color.BLACK)
                }

                if (mProbeTimes[p] != null) {
                    val timeUntilAlarm = HeaterMeter.getTemperatureChangeText(p)
                    if (timeUntilAlarm != null) {
                        mProbeTimes[p]?.text = timeUntilAlarm
                    } else {
                        mProbeTimes[p]?.text = ""
                    }
                }
            }

            if (java.lang.Double.isNaN(latestSample.mProbes[0])) {
                mPitDelta.text = ""
            } else {
                val delta = latestSample.mProbes[0] - latestSample.mSetPoint
                if (delta > 0) {
                    mPitDelta.text = HeaterMeter.formatTemperature(delta) + " above set temp"
                } else {
                    mPitDelta.text = HeaterMeter.formatTemperature(-delta) + " below set temp"
                }
            }

            if (mServerTime < latestSample.mTime) {
                mTime.setToNow()
                mLastUpdate.text = mTime.format("%r")
                mServerTime = latestSample.mTime
            }
        }
    }
}