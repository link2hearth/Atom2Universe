package com.Atom2Universe.app

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class AudioPlaybackService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    private var hasStartedForeground = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquired = false

    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var isPlaying: Boolean = false

    // True si l'utilisateur a volontairement lancé la lecture avec batterie déjà ≤ 10%.
    // Dans ce cas on ne recoupe pas automatiquement : c'est son choix conscient.
    // Le flag se reset quand il pause lui-même ou quand la batterie remonte au-dessus du seuil.
    private var userPlayedWhileLow = false

    private fun getBatteryPercent(): Int {
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level == -1 || scale == -1) 100 else level * 100 / scale
    }

    // Coupe la musique si la batterie tombe à ≤ 10% en arrière-plan (écran éteint),
    // sauf si l'utilisateur a explicitement choisi de lire à ce niveau de batterie.
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level == -1 || scale == -1) return
            val percent = level * 100 / scale
            if (percent > 10) {
                // Batterie rechargée au-dessus du seuil → on réinitialise le flag
                userPlayedWhileLow = false
                return
            }
            if (!isPlaying || userPlayedWhileLow) return
            val power = getSystemService(POWER_SERVICE) as? PowerManager
            if (power?.isInteractive == false) {
                // Écran éteint, batterie critique, pas de choix explicite → pause automatique
                handlePauseIntent()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> handlePlaybackUpdate(intent, startForeground = intent.action == ACTION_START)
            ACTION_PLAY -> handlePlayIntent()
            ACTION_PAUSE -> handlePauseIntent()
            ACTION_STOP -> handleStopIntent()
            else -> {
                if (intent != null) {
                    MediaButtonReceiver.handleIntent(mediaSession, intent)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        releaseWakeLock()
    }

    @SuppressLint("MissingPermission") // Permission checked via canShowNotifications()
    private fun handlePlaybackUpdate(intent: Intent, startForeground: Boolean) {
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)

        if (isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        updatePlaybackState()

        if (canShowNotifications()) {
            val notification = buildNotification()
            if (startForeground || !hasStartedForeground) {
                startForeground(NOTIFICATION_ID, notification)
                hasStartedForeground = true
            } else {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun handlePlayIntent() {
        isPlaying = true
        // L'utilisateur appuie sur play avec batterie ≤ 10% → choix conscient, on n'interférera pas
        if (getBatteryPercent() <= 10) userPlayedWhileLow = true
        acquireWakeLock()
        updatePlaybackState()
        notifyPlaybackCommand(COMMAND_PLAY)
        updateNotification()
    }

    private fun handlePauseIntent() {
        isPlaying = false
        userPlayedWhileLow = false
        updatePlaybackState()
        notifyPlaybackCommand(COMMAND_PAUSE)
        updateNotification()
        releaseWakeLock()
    }

    private fun handleStopIntent() {
        isPlaying = false
        userPlayedWhileLow = false
        updatePlaybackState()
        notifyPlaybackCommand(COMMAND_STOP)

        if (!hasStartedForeground) {
            if (canShowNotifications()) {
                startForeground(NOTIFICATION_ID, buildNotification())
                hasStartedForeground = true
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        hasStartedForeground = false
        releaseWakeLock()
        stopSelf()
    }

    private fun updatePlaybackState() {
        val playbackActions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(playbackActions)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
        mediaSession?.isActive = true

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle.ifBlank { getString(R.string.audio_notification_title) })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist.ifBlank { getString(R.string.audio_notification_unknown_artist) })
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handlePlayIntent()
                }

                override fun onPause() {
                    handlePauseIntent()
                }

                override fun onStop() {
                    handleStopIntent()
                }
            })
            isActive = true
        }
    }

    private fun buildNotification(): Notification {
        val contentTitle = currentTitle.takeIf { it.isNotBlank() }
            ?: getString(R.string.audio_notification_title)
        val contentArtist = currentArtist.takeIf { it.isNotBlank() }
            ?: getString(R.string.audio_notification_unknown_artist)

        val activityIntent = Intent(this, AudioHubActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PLAY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentArtist)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setColor(ContextCompat.getColor(this, R.color.media_player_background))

        if (isPlaying) {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_launcher_background,
                    getString(R.string.audio_action_pause),
                    pauseIntent
                )
            )
        } else {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_launcher_background,
                    getString(R.string.audio_action_play),
                    playIntent
                )
            )
        }

        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_launcher_background,
                getString(R.string.audio_action_stop),
                stopIntent
            )
        )

        return builder.build()
    }

    @SuppressLint("MissingPermission") // Permission checked via canShowNotifications()
    private fun updateNotification() {
        if (canShowNotifications()) {
            if (!hasStartedForeground) {
                startForeground(NOTIFICATION_ID, buildNotification())
                hasStartedForeground = true
                return
            }
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun notifyPlaybackCommand(command: String) {
        val broadcast = Intent(ACTION_MEDIA_COMMAND).apply {
            putExtra(EXTRA_MEDIA_COMMAND, command)
        }
        sendBroadcast(broadcast)
    }

    private fun canShowNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_audio_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_audio_description)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLockAcquired && wakeLock?.isHeld == true) {
            return
        }
        val manager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:Audio").apply {
            setReferenceCounted(false)
            acquire()
        }
        wakeLockAcquired = true
    }

    private fun releaseWakeLock() {
        if (!wakeLockAcquired) {
            return
        }
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        wakeLockAcquired = false
    }

    companion object {
        private const val CHANNEL_ID = "audio_playback_channel"
        private const val NOTIFICATION_ID = 10101
        private const val TAG = "AudioPlaybackService"

        internal const val ACTION_START = "com.Atom2Universe.app.action.START_AUDIO"
        internal const val ACTION_UPDATE = "com.Atom2Universe.app.action.UPDATE_AUDIO"
        internal const val ACTION_PLAY = "com.Atom2Universe.app.action.PLAY"
        internal const val ACTION_PAUSE = "com.Atom2Universe.app.action.PAUSE"
        internal const val ACTION_STOP = "com.Atom2Universe.app.action.STOP"
        internal const val ACTION_MEDIA_COMMAND = "com.Atom2Universe.app.action.MEDIA_COMMAND"

        internal const val EXTRA_TITLE = "extra.title"
        internal const val EXTRA_ARTIST = "extra.artist"
        internal const val EXTRA_IS_PLAYING = "extra.is.playing"
        internal const val EXTRA_MEDIA_COMMAND = "extra.media.command"

        internal const val COMMAND_PLAY = "play"
        internal const val COMMAND_PAUSE = "pause"
        internal const val COMMAND_STOP = "stop"

        @Suppress("unused")
        fun startForegroundPlayback(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        @Suppress("unused")
        fun updateMetadata(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        @Suppress("unused")
        fun stopPlayback(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
