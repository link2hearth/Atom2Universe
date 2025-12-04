package com.example.atom2univers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasStartedForeground = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var isPlaying: Boolean = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // No-op: the web player should decide when to resume.
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isPlaying = false
                updatePlaybackState()
                notifyPlaybackCommand(COMMAND_PAUSE)
                updateNotification()
            }

            else -> {
                // No-op
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
        setupMediaSession()
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
        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        releaseWakeLock()
    }

    private fun handlePlaybackUpdate(intent: Intent, startForeground: Boolean) {
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)

        if (isPlaying && !requestAudioFocus()) {
            stopSelf()
            return
        }

        if (isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        if (!isPlaying) {
            abandonAudioFocus()
        }

        updatePlaybackState()
        val notification = buildNotification()
        if (startForeground || !hasStartedForeground) {
            startForeground(NOTIFICATION_ID, notification)
            hasStartedForeground = true
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun handlePlayIntent() {
        isPlaying = true
        if (!requestAudioFocus()) {
            stopSelf()
            return
        }
        acquireWakeLock()
        updatePlaybackState()
        notifyPlaybackCommand(COMMAND_PLAY)
        updateNotification()
    }

    private fun handlePauseIntent() {
        isPlaying = false
        updatePlaybackState()
        notifyPlaybackCommand(COMMAND_PAUSE)
        updateNotification()
        abandonAudioFocus()
        releaseWakeLock()
    }

    private fun handleStopIntent() {
        isPlaying = false
        updatePlaybackState()
        notifyPlaybackCommand(COMMAND_STOP)
        stopForeground(STOP_FOREGROUND_REMOVE)
        hasStartedForeground = false
        abandonAudioFocus()
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

        val activityIntent = Intent(this, MainActivity::class.java)
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

    private fun updateNotification() {
        if (!hasStartedForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            hasStartedForeground = true
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            manager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun notifyPlaybackCommand(command: String) {
        val broadcast = Intent(ACTION_MEDIA_COMMAND).apply {
            putExtra(EXTRA_MEDIA_COMMAND, command)
        }
        sendBroadcast(broadcast)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_audio_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_audio_description)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val manager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:Audio").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "audio_playback_channel"
        private const val NOTIFICATION_ID = 10101
        private const val TAG = "AudioPlaybackService"

        internal const val ACTION_START = "com.example.atom2univers.action.START_AUDIO"
        internal const val ACTION_UPDATE = "com.example.atom2univers.action.UPDATE_AUDIO"
        internal const val ACTION_PLAY = "com.example.atom2univers.action.PLAY"
        internal const val ACTION_PAUSE = "com.example.atom2univers.action.PAUSE"
        internal const val ACTION_STOP = "com.example.atom2univers.action.STOP"
        internal const val ACTION_MEDIA_COMMAND = "com.example.atom2univers.action.MEDIA_COMMAND"

        internal const val EXTRA_TITLE = "extra.title"
        internal const val EXTRA_ARTIST = "extra.artist"
        internal const val EXTRA_IS_PLAYING = "extra.is.playing"
        internal const val EXTRA_MEDIA_COMMAND = "extra.media.command"

        internal const val COMMAND_PLAY = "play"
        internal const val COMMAND_PAUSE = "pause"
        internal const val COMMAND_STOP = "stop"

        fun startForegroundPlayback(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateMetadata(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopPlayback(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
