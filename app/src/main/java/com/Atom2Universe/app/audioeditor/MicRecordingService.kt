package com.Atom2Universe.app.audioeditor

import android.annotation.SuppressLint
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
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.R

class MicRecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                acquireWakeLock()
                showNotification()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, AudioHubActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopBroadcast = PendingIntent.getBroadcast(
            this, 0,
            Intent(ACTION_STOP_FROM_NOTIFICATION).apply { `package` = packageName },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mic_recording_notif_title))
            .setContentText(getString(R.string.mic_recording_notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_launcher_background,
                    getString(R.string.mic_recording_action_stop_notif),
                    stopBroadcast
                )
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.mic_recording_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.mic_recording_channel_description)
        }
        (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
            ?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:Record")
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "MicRecordingService"
        private const val CHANNEL_ID = "mic_recording_channel"
        internal const val NOTIFICATION_ID = 10103

        const val ACTION_START = "com.Atom2Universe.app.action.START_MIC_RECORDING"
        const val ACTION_STOP = "com.Atom2Universe.app.action.STOP_MIC_RECORDING"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.Atom2Universe.app.action.MIC_RECORDING_STOP_NOTIF"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MicRecordingService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MicRecordingService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
