package com.Atom2Universe.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.Atom2Universe.app.AudioPlaybackManager
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.midi.service.MidiPlaybackState
import com.Atom2Universe.app.music.MusicPlaybackHolder
import com.Atom2Universe.app.music.MusicQueuePersistence
import com.Atom2Universe.app.radio.RadioPlaybackHolder

/**
 * Contrôleur unifié pour le widget audio.
 *
 * Gère les trois sources audio (Music, Radio, MIDI) et fournit une interface
 * unifiée pour le widget. Détecte automatiquement quelle source est active
 * et route les commandes vers le bon holder.
 */
object MusicWidgetController {

    private const val TAG = "MusicWidgetController"

    enum class ActiveSource {
        NONE, MUSIC, RADIO, MIDI
    }

    data class WidgetState(
        val source: ActiveSource = ActiveSource.NONE,
        val isPlaying: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtUri: String? = null,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false
    )

    private var appContext: Context? = null

    /**
     * Initialise le controller avec le contexte de l'application.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "MusicWidgetController initialized")
    }

    /**
     * Détecte quelle source audio est actuellement active.
     * Priorité: vérifie d'abord AudioPlaybackManager (source enregistrée),
     * puis les états réels des players, puis si MIDI a une piste chargée.
     */
    fun getActiveSource(): ActiveSource {
        // 1. Vérifier la source enregistrée dans AudioPlaybackManager
        val registeredSource = when {
            AudioPlaybackManager.isPlaying(AudioPlaybackManager.AudioSource.MIDI) -> ActiveSource.MIDI
            AudioPlaybackManager.isPlaying(AudioPlaybackManager.AudioSource.MUSIC) -> ActiveSource.MUSIC
            AudioPlaybackManager.isPlaying(AudioPlaybackManager.AudioSource.RADIO) -> ActiveSource.RADIO
            else -> null
        }

        if (registeredSource != null) {
            Log.d(TAG, "getActiveSource: registered source = $registeredSource")
            return registeredSource
        }

        // 2. Vérifier l'état réel des players (au cas où AudioPlaybackManager n'est pas synchronisé)
        val activeSource = when {
            MidiPlaybackState.isPlaying() -> ActiveSource.MIDI
            MusicPlaybackHolder.isPlaying() -> ActiveSource.MUSIC
            RadioPlaybackHolder.isPlaying() -> ActiveSource.RADIO
            // 3. Si MIDI a une piste chargée (même en pause), considérer MIDI comme actif
            MidiPlaybackState.getCurrentTrack() != null -> ActiveSource.MIDI
            else -> ActiveSource.NONE
        }

        Log.d(TAG, "getActiveSource: detected source = $activeSource")
        return activeSource
    }

    /**
     * Retourne l'état actuel du widget.
     */
    fun getCurrentState(context: Context): WidgetState {
        val source = getActiveSource()

        return when (source) {
            ActiveSource.MUSIC -> {
                val track = MusicPlaybackHolder.getCurrentTrack()
                WidgetState(
                    source = ActiveSource.MUSIC,
                    isPlaying = MusicPlaybackHolder.isPlaying(),
                    title = track?.title ?: "",
                    artist = track?.artist ?: "",
                    album = track?.album ?: "",
                    albumArtUri = track?.albumArtUri?.toString(),
                    hasNext = MusicPlaybackHolder.getPlaylist().size > 1,
                    hasPrevious = MusicPlaybackHolder.getPlaylist().size > 1
                )
            }
            ActiveSource.RADIO -> {
                val station = RadioPlaybackHolder.getCurrentStation()
                val streamTitle = RadioPlaybackHolder.getCurrentTitle()
                val streamArtist = RadioPlaybackHolder.getCurrentArtist()
                // Fallback pour le titre: stream metadata -> station name -> "Radio"
                val displayTitle = when {
                    !streamTitle.isNullOrBlank() -> streamTitle
                    station?.name?.isNotBlank() == true -> station.name
                    else -> "Radio"
                }
                // Artist: stream artist si différent du titre, sinon station name
                val displayArtist = when {
                    !streamArtist.isNullOrBlank() && streamArtist != streamTitle -> streamArtist
                    station?.name?.isNotBlank() == true && station.name != displayTitle -> station.name
                    else -> ""
                }
                WidgetState(
                    source = ActiveSource.RADIO,
                    isPlaying = RadioPlaybackHolder.isPlaying(),
                    title = displayTitle,
                    artist = displayArtist,
                    album = "", // Radio n'a pas d'album
                    albumArtUri = null, // Radio n'a pas de cover
                    hasNext = false, // Radio n'a pas de next/prev
                    hasPrevious = false
                )
            }
            ActiveSource.MIDI -> {
                // MIDI - récupère les infos depuis MidiPlaybackState
                val track = MidiPlaybackState.getCurrentTrack()
                val displayTitle = if (!track?.title.isNullOrBlank()) track!!.title else "MIDI"
                val displayArtist = if (!track?.artist.isNullOrBlank()) track!!.artist else ""
                WidgetState(
                    source = ActiveSource.MIDI,
                    isPlaying = MidiPlaybackState.isPlaying(),
                    title = displayTitle,
                    artist = displayArtist,
                    album = "", // MIDI n'a pas d'album
                    albumArtUri = null,
                    hasNext = MidiPlaybackState.hasNext(),
                    hasPrevious = MidiPlaybackState.hasPrevious()
                )
            }
            ActiveSource.NONE -> {
                // Aucune source active - charger les infos sauvegardées
                // Priorité: queue musique, puis dernière station radio
                val savedQueue = MusicQueuePersistence.loadQueue(context)
                if (savedQueue != null && savedQueue.tracks.isNotEmpty()) {
                    val currentTrack = savedQueue.tracks.getOrNull(savedQueue.currentIndex)
                        ?: savedQueue.tracks.first()
                    WidgetState(
                        source = ActiveSource.NONE,
                        isPlaying = false,
                        title = currentTrack.title,
                        artist = currentTrack.artist,
                        album = currentTrack.album,
                        albumArtUri = currentTrack.albumArtUri,
                        hasNext = savedQueue.tracks.size > 1,
                        hasPrevious = savedQueue.tracks.size > 1
                    )
                } else {
                    // Pas de queue musique - vérifier s'il y a une dernière station radio
                    val lastStation = RadioPlaybackHolder.loadLastStation(context)
                    if (lastStation != null) {
                        WidgetState(
                            source = ActiveSource.NONE,
                            isPlaying = false,
                            title = lastStation.name,
                            artist = "Radio",
                            album = "",
                            albumArtUri = null,
                            hasNext = false,
                            hasPrevious = false
                        )
                    } else {
                        WidgetState(
                            source = ActiveSource.NONE,
                            isPlaying = false,
                            title = "",
                            artist = "",
                            album = "",
                            albumArtUri = null,
                            hasNext = false,
                            hasPrevious = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Bascule play/pause pour la source active.
     * Si aucune source n'est active, restaure et joue la dernière queue musique.
     */
    fun playPause(context: Context) {
        val source = getActiveSource()
        Log.d(TAG, "playPause called, active source: $source")

        when (source) {
            ActiveSource.MUSIC -> {
                MusicPlaybackHolder.togglePlayPause(context)
            }
            ActiveSource.RADIO -> {
                // Pour la radio: Stop déconnecte du flux, Play reconnecte
                // (au lieu de pause qui garde le buffer actif)
                if (RadioPlaybackHolder.isPlaying()) {
                    RadioPlaybackHolder.stop(context)
                } else {
                    // Reconnecte à la dernière station
                    RadioPlaybackHolder.reconnect(context)
                }
            }
            ActiveSource.MIDI -> {
                // MIDI est contrôlé via MidiPlaybackService
                startMidiService(context, MidiPlaybackService.ACTION_PLAY_PAUSE)
            }
            ActiveSource.NONE -> {
                // Aucune source active - essayer de restaurer la dernière source utilisée
                Log.d(TAG, "No active source, attempting to restore last playback")

                // Priorité: essayer de restaurer la queue musique d'abord
                val musicRestored = MusicPlaybackHolder.restoreAndPlay(context)
                if (!musicRestored) {
                    // Si pas de queue musique, essayer de reconnecter à la radio
                    val radioRestored = RadioPlaybackHolder.reconnect(context)
                    if (!radioRestored) {
                        Log.w(TAG, "Failed to restore any playback source")
                    }
                }
            }
        }

        // Mettre à jour le widget
        updateWidgets(context)
    }

    /**
     * Passe à la piste suivante.
     */
    fun next(context: Context) {
        val source = getActiveSource()
        Log.d(TAG, "next called, active source: $source")

        when (source) {
            ActiveSource.MUSIC -> {
                MusicPlaybackHolder.skipToNext(context)
            }
            ActiveSource.MIDI -> {
                startMidiService(context, MidiPlaybackService.ACTION_SKIP_NEXT)
            }
            else -> {
                // Radio n'a pas de next
            }
        }

        updateWidgets(context)
    }

    /**
     * Passe à la piste précédente.
     */
    fun previous(context: Context) {
        val source = getActiveSource()
        Log.d(TAG, "previous called, active source: $source")

        when (source) {
            ActiveSource.MUSIC -> {
                MusicPlaybackHolder.skipToPrevious(context)
            }
            ActiveSource.MIDI -> {
                startMidiService(context, MidiPlaybackService.ACTION_SKIP_PREVIOUS)
            }
            else -> {
                // Radio n'a pas de previous
            }
        }

        updateWidgets(context)
    }

    /**
     * Met à jour tous les widgets.
     * Appelé quand l'état de lecture change.
     */
    fun updateWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, MusicWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            if (widgetIds.isNotEmpty()) {
                Log.d(TAG, "Updating ${widgetIds.size} widgets")
                val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets", e)
        }
    }

    /**
     * Notifie le controller qu'un changement d'état a eu lieu.
     * Appelé par les différents holders de playback.
     */
    fun notifyStateChanged(context: Context) {
        updateWidgets(context)
    }

    /**
     * Démarre le service MIDI avec l'action spécifiée.
     * Utilise startForegroundService sur Android 8+ si le service n'est pas déjà en premier plan.
     */
    private fun startMidiService(context: Context, action: String) {
        try {
            val intent = Intent(context, MidiPlaybackService::class.java).apply {
                this.action = action
            }
            Log.d(TAG, "Starting MIDI service with action: $action")

            // Si le service est déjà actif (MIDI joue), startService suffit
            // Sinon, utiliser startForegroundService
            if (MidiPlaybackState.isPlaying()) {
                // Service en premier plan, startService suffit
                context.startService(intent)
            } else {
                // Service pas en premier plan, utiliser startForegroundService
                context.startForegroundService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MIDI service", e)
        }
    }
}
