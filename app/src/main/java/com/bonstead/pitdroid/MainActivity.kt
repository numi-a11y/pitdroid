package com.bonstead.pitdroid

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val mScheduler = Executors.newScheduledThreadPool(1)
    private var mUpdateTimer: ScheduledFuture<*>? = null
    private var mAllowServiceShutdown = false
    private val mHandler = IncomingHandler(this)

    private val mUpdate = Runnable {
        val data = HeaterMeter.updateThread()
        mHandler.sendMessage(mHandler.obtainMessage(0, data))
    }

    private fun openFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // LOAD the saved tenderness score (Renamed to dataPrefs to avoid conflicts)
        val dataPrefs = getSharedPreferences("PitDroidData", Context.MODE_PRIVATE)
        HeaterMeter.mAccumulatedTenderness = dataPrefs.getFloat("saved_tenderness", 0f).toDouble()

        // MODERNIZED: Ask for Android 13+ Notification Permissions right when the app opens
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        if (supportFragmentManager.findFragmentById(R.id.fragment) == null) {
            openFragment(GaugeFragment())
        }

        // Original settings prefs (kept as 'prefs' so the rest of the code works)
        val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        prefs.registerOnSharedPreferenceChangeListener(this)

        HeaterMeter.initPreferences(prefs)

        updateScreenOn()
        updateAlarmService()

        if (intent.hasExtra("close")) {
            showCloseMessage()
        }

        val navView = findViewById<BottomNavigationView>(R.id.navigation)
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_big_dash -> {
                    openFragment(BigDashFragment())
                    true
                }
                R.id.navigation_dash -> {
                    openFragment(DashFragment())
                    true
                }
                R.id.navigation_graph -> {
                    openFragment(GraphFragment())
                    true
                }
                R.id.navigation_gauge -> {
                    openFragment(GaugeFragment())
                    true
                }
                R.id.navigation_settings -> {
                    openFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mUpdateTimer != null) {
            mUpdateTimer!!.cancel(false)
            mUpdateTimer = null
        }

        // SAVE the exact tenderness score before Android puts the app to sleep
        val dataPrefs = getSharedPreferences("PitDroidData", Context.MODE_PRIVATE)
        dataPrefs.edit().putFloat("saved_tenderness", HeaterMeter.mAccumulatedTenderness.toFloat()).apply()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (mUpdateTimer == null) {
            mUpdateTimer = mScheduler.scheduleAtFixedRate(mUpdate, 0, HeaterMeter.kMinSampleTime, TimeUnit.MILLISECONDS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        if (mAllowServiceShutdown) {
            stopAlarmService()
        }
    }

    // FIXED: Explicitly use the Main Looper to comply with modern Android strictness
    internal class IncomingHandler(activity: MainActivity) : Handler(Looper.getMainLooper()) {
        private val mActivity: WeakReference<MainActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            val activity = mActivity.get()
            if (activity != null) {
                HeaterMeter.updateMain(msg.obj)

                if (HeaterMeter.mLastStatusMessage != null) {
                    val context = activity.applicationContext
                    val text = HeaterMeter.mLastStatusMessage
                    val duration = Toast.LENGTH_SHORT

                    val toast = Toast.makeText(context, text, duration)
                    toast.show()

                    HeaterMeter.mLastStatusMessage = null
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences?.let {
            HeaterMeter.initPreferences(it)
            updateScreenOn()
        }
    }

    private fun updateScreenOn() {
        if (HeaterMeter.mKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun updateAlarmService() {
        if (HeaterMeter.hasAlarms()) {
            stopAlarmService()
            startAlarmService()
        } else {
            stopAlarmService()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun startAlarmService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, AlarmService::class.java))
        } else {
            startService(Intent(this, AlarmService::class.java))
        }
    }

    private fun stopAlarmService() {
        stopService(Intent(this, AlarmService::class.java))
    }

    private fun showCloseMessage() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm")
        builder.setMessage("You have alarms set, are you sure you want to exit?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            mAllowServiceShutdown = true
            dialog.dismiss()
            finish()
        }
        builder.setNegativeButton("No", null)
        val alert = builder.create()
        alert.show()
    }

    companion object {
        internal val TAG = "MainActivity"
    }
}