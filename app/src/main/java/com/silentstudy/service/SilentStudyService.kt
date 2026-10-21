package com.silentstudy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.silentstudy.MainActivity
import com.silentstudy.R
import com.silentstudy.SilentStudyApp

class SilentStudyService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "silentstudy_foreground_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.silentstudy.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.silentstudy.ACTION_NEXT"
        const val ACTION_STOP = "com.silentstudy.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, SilentStudyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SilentStudyService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createNotificationChannel()
        startForegroundWithNotification()

        val app = application as SilentStudyApp
        app.deskTapSensor.startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as SilentStudyApp
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (app.playbackController.isPlaying.value) {
                    app.playbackController.pause()
                } else {
                    app.playbackController.currentPlayingAnswer.value?.let {
                        app.playbackController.playAnswer(it, app.playbackController.currentPartIndex.value)
                    }
                }
            }
            ACTION_STOP -> {
                app.playbackController.stop()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SilentStudy::ScreenOffDeskWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(1000 * 60 * 120) // 2 hours max
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SilentStudy Library Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors desk knocks and plays private earbud audio"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SilentStudy Active")
            .setContentText("Earbuds-only audio. Desk taps active with screen off.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        val app = application as SilentStudyApp
        app.deskTapSensor.stopListening()
        app.ttsManager.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
