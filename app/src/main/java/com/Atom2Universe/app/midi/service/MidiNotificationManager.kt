package com.Atom2Universe.app.midi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.ui.MidiPlayerActivity

/**
 * Gestionnaire des notifications MediaStyle pour le lecteur MIDI
 * Affiche les contrôles de lecture dans la notification
 */
class MidiNotificationManager(
    private val context: Context,
    @Suppress("unused") private val service: MidiPlaybackService
) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Crée la notification pour le foreground service
     * @param track Track actuellement en lecture
     * @param playbackState État de lecture (playing, paused, etc.)
     * @param sessionToken Token de la MediaSession
     * @return La notification créée
     */
    fun buildNotification(
        track: MidiTrack?,
        playbackState: Int,
        sessionToken: MediaSessionCompat.Token
    ): Notification {

        val isPlaying = playbackState == PlaybackStateCompat.STATE_PLAYING

        // Intent pour ouvrir l'activity quand on clique sur la notification
        val contentIntent = createContentIntent()

        // Style MediaStyle pour afficher les contrôles
        val mediaStyle = MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Affiche 3 boutons en mode compact

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(track?.title ?: context.getString(R.string.app_name))
            .setContentText(track?.artist ?: "Unknown Artist")
            .setSubText(track?.album)
            .setSmallIcon(android.R.drawable.ic_media_play) // TODO: Créer icône custom ic_notification
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(createStopIntent())
            .setColor(ContextCompat.getColor(context, R.color.media_player_background))

        // Bouton Previous
        builder.addAction(
            createAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                MidiPlaybackService.ACTION_SKIP_PREVIOUS
            )
        )

        // Bouton Play/Pause
        if (isPlaying) {
            builder.addAction(
                createAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    MidiPlaybackService.ACTION_PAUSE
                )
            )
        } else {
            builder.addAction(
                createAction(
                    android.R.drawable.ic_media_play,
                    "Play",
                    MidiPlaybackService.ACTION_PLAY
                )
            )
        }

        // Bouton Next
        builder.addAction(
            createAction(
                android.R.drawable.ic_media_next,
                "Next",
                MidiPlaybackService.ACTION_SKIP_NEXT
            )
        )

        // Bouton Stop (affiché uniquement en mode étendu)
        builder.addAction(
            createAction(
                android.R.drawable.ic_delete,
                "Stop",
                MidiPlaybackService.ACTION_STOP
            )
        )

        return builder.build()
    }

    /**
     * Crée le canal de notification (Android 8+)
     * Vérifie d'abord si le canal existe déjà pour éviter les recréations inutiles
     */
    private fun createNotificationChannel() {
        // BUG FIX 3.31: Vérifier si le canal existe déjà avant de le créer
        val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existingChannel != null) {
            return // Canal existe déjà, pas besoin de le recréer
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "MIDI Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "MIDI player playback controls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Crée un PendingIntent pour ouvrir l'activity MIDI player
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MidiPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Crée un PendingIntent pour une action de la notification
     */
    private fun createAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(context, MidiPlaybackService::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }

    /**
     * Crée un PendingIntent pour stopper le service (swipe notification)
     */
    private fun createStopIntent(): PendingIntent {
        val intent = Intent(context, MidiPlaybackService::class.java).apply {
            action = MidiPlaybackService.ACTION_STOP
        }

        return PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL_ID = "midi_playback_channel"
        const val NOTIFICATION_ID = 20203 // Unique ID pour ce service

        private const val REQUEST_CODE_CONTENT = 1001
        private const val REQUEST_CODE_STOP = 1002
    }
}
