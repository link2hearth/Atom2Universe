package com.Atom2Universe.app.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.Atom2Universe.app.R

class MusicForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START = "com.Atom2Universe.app.music.START"
        const val ACTION_UPDATE = "com.Atom2Universe.app.music.UPDATE"
        const val ACTION_STOP = "com.Atom2Universe.app.music.STOP"
        const val ACTION_PLAY_PAUSE = "com.Atom2Universe.app.music.PLAY_PAUSE"
        const val ACTION_NEXT = "com.Atom2Universe.app.music.NEXT"
        const val ACTION_PREV = "com.Atom2Universe.app.music.PREV"
        const val ACTION_DISMISS = "com.Atom2Universe.app.music.DISMISS"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
    }

    private var mediaSession: MediaSessionCompat? = null
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            setupMediaSession()

            // S'assure que le sync manager est initialisé pour sauvegarder les play counts
            MusicPopmSyncManager.init(this)
        } catch (e: Exception) {
            // En cas d'erreur, nettoyer la MediaSession pour éviter les fuites
            mediaSession?.release()
            mediaSession = null
            throw e
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Null check sur intent pour éviter les NPE
        if (intent == null) {
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.music_unknown_title)
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: getString(R.string.music_unknown_artist)
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                updateForegroundState(createNotification())
                updateMediaSession()
            }
            ACTION_UPDATE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                updateForegroundState(createNotification())
                updateMediaSession()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_DISMISS -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY_PAUSE -> {
                MusicPlaybackHolder.togglePlayPause(this)
            }
            ACTION_NEXT -> {
                MusicPlaybackHolder.skipToNext(this)
            }
            ACTION_PREV -> {
                MusicPlaybackHolder.skipToPrevious(this)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.music_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.music_notification_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(channel)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayerSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    MusicPlaybackHolder.resume(this@MusicForegroundService)
                }

                override fun onPause() {
                    MusicPlaybackHolder.pause(this@MusicForegroundService)
                }

                override fun onSkipToNext() {
                    MusicPlaybackHolder.skipToNext(this@MusicForegroundService)
                }

                override fun onSkipToPrevious() {
                    MusicPlaybackHolder.skipToPrevious(this@MusicForegroundService)
                }

                override fun onStop() {
                    MusicPlaybackHolder.stop(this@MusicForegroundService)
                }

                override fun onSeekTo(pos: Long) {
                    MusicPlaybackHolder.seekTo(pos)
                }
            })
            isActive = true
        }
    }

    private fun updateMediaSession() {
        mediaSession?.let { session ->
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, MusicPlaybackHolder.getDuration())
                .build()
            session.setMetadata(metadata)

            val state = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    MusicPlaybackHolder.getPosition(),
                    1f
                )
                .build()
            session.setPlaybackState(state)
        }
    }

    private fun createNotification(): Notification {
        // Bug 5.24: Utiliser FLAG_IMMUTABLE seul pour les PendingIntents sans extras.
        // FLAG_UPDATE_CURRENT n'est pas nécessaire car ces intents n'ont pas d'extras
        // à mettre à jour, et FLAG_IMMUTABLE garantit que l'intent ne peut pas être modifié.
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MusicPlayerActivity::class.java).apply {
                // FLAG_ACTIVITY_SINGLE_TOP : si MusicPlayerActivity est déjà au sommet,
                // on rappelle onNewIntent au lieu de créer une 2e instance.
                // Combiné avec FLAG_ACTIVITY_CLEAR_TOP : si FullPlayerActivity est au dessus,
                // on la retire proprement et on revient sur MusicPlayerActivity.
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicForegroundService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicForegroundService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicForegroundService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getService(
            this,
            5,
            Intent(this, MusicForegroundService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.music_previous), prevIntent)
            .addAction(playPauseIcon, getString(if (isPlaying) R.string.music_action_pause else R.string.music_action_play), playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.music_next), nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.music_action_stop), stopIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .setAutoCancel(!isPlaying)
            .setColor(ContextCompat.getColor(this, R.color.media_player_background))

        if (!isPlaying) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.audio_action_dismiss),
                dismissIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun updateForegroundState(notification: Notification) {
        // Le service doit rester en foreground aussi longtemps qu'un média est actif
        // (en lecture OU en pause), sinon Android tue le service après ~10 min
        // en mode background (limite Android 8.0+).
        // stopForeground ne sera appelé que via ACTION_STOP / ACTION_DISMISS.
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Sur Android 12+, startForeground peut échouer si appelé depuis l'arrière-plan
            // La lecture continue quand même, on met juste à jour la notification si possible
            try {
                updateNotification()
            } catch (_: Exception) {
                // Ignore silently
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.release()

        // Essaye de traiter les modifications POPM en attente avant la destruction
        MusicPopmSyncManager.forceProcessAll()

        super.onDestroy()
    }
}
