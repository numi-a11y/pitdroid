package com.bonstead.pitdroid

import com.bonstead.pitdroid.HeaterMeter.NamedSample

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class AlarmService : Service() {

    private var mServiceAlarm: PendingIntent? = null
    private var mStatusChannel = ""
    private var mAlarmChannel = ""

    override fun onCreate() {
        super.onCreate()

        val alarmIntent = Intent(this, AlarmService::class.java)
        mServiceAlarm = PendingIntent.getService(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mStatusChannel = createNotificationChannel("pitdroidstatus", false)
        mAlarmChannel = createNotificationChannel("pitdroidalarm", true)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "onDestroy")
        }

        cancelAlarm()
        mServiceAlarm = null
        stopForeground(true)
    }

    private fun scheduleAlarm() {
        val nextTime = System.currentTimeMillis() + HeaterMeter.mBackgroundUpdateTime * 60 * 1000
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // FIXED: Safely unwrap the nullable PendingIntent
        mServiceAlarm?.let { safeAlarm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, safeAlarm)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextTime, safeAlarm)
            }
        } ?: Log.e(TAG, "Cannot schedule alarm: PendingIntent is null.")
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mServiceAlarm?.let { alarmManager.cancel(it) }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(kAlarmNotificationId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "onStartCommand")
        }

        val mgr = baseContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PitDroid:AlarmService")
        lock.acquire(30000)

        cancelAlarm()
        updateStatusNotification(null)

        Thread(Runnable {
            val sample = HeaterMeter.sample
            updateStatusNotification(sample)
            updateAlarmNotification(sample)
            scheduleAlarm()
            lock.release()
        }).start()

        return START_STICKY
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, isAlarm: Boolean): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "PitDroid Background Service"
            var importance = NotificationManager.IMPORTANCE_NONE
            if (isAlarm)
                importance = NotificationManager.IMPORTANCE_HIGH

            val chan = NotificationChannel(channelId, channelName, importance)
            chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            if (isAlarm) {
                val alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                chan.setSound(
                    alarmTone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(chan)
            return channelId
        } else {
            return if (isAlarm) "alarm" else ""
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createBuilder(icon: Int, intent: PendingIntent?, channel: String): Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channel)
        } else {
            Notification.Builder(this)
        }

        builder.setSmallIcon(icon)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusIcon = Icon.createWithResource(this.packageName, icon)

            // FIXED: Safely unwrap the intent before attaching the action
            intent?.let { safeIntent ->
                val statusAction = Notification.Action.Builder(statusIcon, "Close", safeIntent).build()
                builder.addAction(statusAction)
            }
        } else if (channel == "alarm") {
            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            builder.setSound(alert, AudioManager.STREAM_ALARM)
        }

        return builder
    }

    private fun updateStatusNotification(latestSample: NamedSample?) {
        var contentText = ""

        if (latestSample != null) {
            for (p in 0 until HeaterMeter.kNumProbes) {
                if (!latestSample.mProbes[p].isNaN()) {
                    if (contentText.isNotEmpty()) {
                        contentText += " "
                    }
                    contentText += latestSample.mProbeNames[p] + ": "
                    contentText += HeaterMeter.formatTemperature(latestSample.mProbes[p])
                }
            }
        } else {
            contentText = getString(R.string.alarm_service_info)
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val statusIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("close", true)
        }
        val closePendingIntent = PendingIntent.getActivity(
            this, 1, closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = createBuilder(R.mipmap.ic_status, closePendingIntent, mStatusChannel)
            .setContentTitle("PitDroid Monitor")
            .setContentText(contentText)
            .setOngoing(true)

        // FIXED: Safely set the content intent
        statusIntent?.let {
            builder.setContentIntent(it)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(kStatusNotificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(kStatusNotificationId, builder.build())
        }
    }

    private fun updateAlarmNotification(latestSample: NamedSample?) {
        var contentText = ""
        var hasAlarms = false

        if (latestSample != null) {
            for (p in 0 until HeaterMeter.kNumProbes) {
                val alarmText = HeaterMeter.formatAlarm(p, latestSample.mProbes[p])
                if (alarmText.isNotEmpty()) {
                    hasAlarms = true
                    if (contentText.isNotEmpty()) {
                        contentText += "\n"
                    }
                    contentText += latestSample.mProbeNames[p] + " " + alarmText
                }
            }
        } else {
            if (HeaterMeter.mAlarmOnLostConnection) {
                hasAlarms = true
            }
            contentText = getText(R.string.no_server).toString()
        }

        if (hasAlarms) {
            val alarmIntent = Intent(this, MainActivity::class.java)
            val alarmPendingIntent = PendingIntent.getActivity(
                this, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmBuilder = createBuilder(R.mipmap.ic_status, alarmPendingIntent, mAlarmChannel)
                .setContentTitle("PitDroid Alarm")
                .setContentText(contentText)

            // FIXED: Safely set the content intent
            alarmPendingIntent?.let {
                alarmBuilder.setContentIntent(it)
            }

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isSilentMode = am.ringerMode != AudioManager.RINGER_MODE_NORMAL

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(kAlarmNotificationId, alarmBuilder.build())
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        internal const val TAG = "AlarmService"
        internal const val kStatusNotificationId = 1
        internal const val kAlarmNotificationId = 2
    }
}