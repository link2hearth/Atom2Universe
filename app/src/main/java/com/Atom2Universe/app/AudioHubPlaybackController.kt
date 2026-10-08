package com.Atom2Universe.app

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media3.common.PlaybackException
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.music.MusicPlaybackHolder
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.radio.RadioPlaybackHolder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Controlleur centralisé pour le Hub Audio.
 * Ecoute les 3 sources audio (Music, Radio, MIDI) et fournit un état unifié.
 */
class AudioHubPlaybackController(private val context: Context) {

    private val TAG = "AudioHubPlaybackCtrl"

    // État unifié
    enum class ActiveSource {
        NONE, MUSIC, RADIO, MIDI
    }

    data class PlaybackState(
        val source: ActiveSource = ActiveSource.NONE,
        val isPlaying: Boolean = false,
        val title: String? = null,
        val subtitle: String? = null
    )

    interface Listener {
        fun onPlaybackStateChanged(state: PlaybackState)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    @Volatile private var currentState = PlaybackState()
    private val handler = Handler(Looper.getMainLooper())
    private val stateLock = Any()

    // MediaBrowser pour le service MIDI
    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null

    // Listeners pour Music et Radio
    private val musicListener = object : MusicPlaybackHolder.PlayerListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            Log.d(TAG, "Music playback state changed: $isPlaying")
            updateFromMusic(isPlaying, MusicPlaybackHolder.getCurrentTrack())
        }

        override fun onTrackChanged(track: MusicTrack?) {
            Log.d(TAG, "Music track changed: ${track?.title}")
            updateFromMusic(MusicPlaybackHolder.isPlaying(), track)
        }

        override fun onProgressChanged(position: Long, duration: Long) {}
        override fun onError(error: PlaybackException) {}
        override fun onPlaylistChanged(playlist: List<MusicTrack>) {}
        override fun onPlayCountIncremented(track: MusicTrack, newCount: Long) {}
    }

    private val radioListener = object : RadioPlaybackHolder.PlayerListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            Log.d(TAG, "Radio playback state changed: $isPlaying")
            updateFromRadio(isPlaying)
        }

        override fun onMetadataChanged(artist: String?, title: String?) {
            Log.d(TAG, "Radio metadata changed: $artist - $title")
            updateFromRadio(RadioPlaybackHolder.isPlaying())
        }

        override fun onError(error: PlaybackException) {}
        override fun onRecordingStateChanged(recording: Boolean) {}
    }

    // MediaController callback pour MIDI
    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "MIDI playback state changed: ${state?.state}")
            updateFromMidi()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Log.d(TAG, "MIDI metadata changed: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
            updateFromMidi()
        }
    }

    // MediaBrowser connection callback
    private val mediaBrowserConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "MediaBrowser connected to MIDI service")
            // Post to main thread and synchronize mediaController modification
            handler.post {
                try {
                    val browser = mediaBrowser ?: return@post
                    val token = browser.sessionToken
                    synchronized(stateLock) {
                        mediaController = MediaControllerCompat(context, token).apply {
                            registerCallback(mediaControllerCallback, handler)
                        }
                    }
                    // Check initial MIDI state
                    updateFromMidi()
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to MediaController", e)
                }
            }
        }

        override fun onConnectionSuspended() {
            Log.d(TAG, "MediaBrowser connection suspended")
            handler.post {
                synchronized(stateLock) {
                    mediaController?.unregisterCallback(mediaControllerCallback)
                    mediaController = null
                }
            }
        }

        override fun onConnectionFailed() {
            Log.e(TAG, "MediaBrowser connection failed")
        }
    }

    /**
     * Démarre l'écoute de toutes les sources audio.
     */
    fun start() {
        Log.d(TAG, "Starting AudioHubPlaybackController")

        // Enregistre les listeners Music et Radio
        MusicPlaybackHolder.addListener(musicListener)
        RadioPlaybackHolder.addListener(radioListener)

        // Connecte au service MIDI via MediaBrowser seulement si pas deja connecte
        if (mediaBrowser?.isConnected != true) {
            mediaBrowser?.disconnect()
            mediaBrowser = MediaBrowserCompat(
                context,
                ComponentName(context, MidiPlaybackService::class.java),
                mediaBrowserConnectionCallback,
                null
            )
            mediaBrowser?.connect()
        }

        // Vérifie l'état initial
        refreshState()
    }

    /**
     * Arrête l'écoute et libère les ressources.
     */
    fun stop() {
        Log.d(TAG, "Stopping AudioHubPlaybackController")

        MusicPlaybackHolder.removeListener(musicListener)
        RadioPlaybackHolder.removeListener(radioListener)

        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaController = null

        // Deconnecter proprement le MediaBrowser
        mediaBrowser?.let { browser ->
            if (browser.isConnected) {
                browser.disconnect()
            }
        }
        mediaBrowser = null

        listeners.clear()
    }

    /**
     * Ajoute un listener pour recevoir les mises à jour d'état.
     */
    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            // Envoie l'état actuel immédiatement
            listener.onPlaybackStateChanged(currentState)
        }
    }

    /**
     * Retire un listener.
     */
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Toggle play/pause pour la source active.
     */
    fun togglePlayPause() {
        when (currentState.source) {
            ActiveSource.MUSIC -> {
                MusicPlaybackHolder.togglePlayPause(context)
            }
            ActiveSource.RADIO -> {
                if (RadioPlaybackHolder.isPlaying()) {
                    RadioPlaybackHolder.pause(context)
                } else {
                    RadioPlaybackHolder.resume(context)
                }
            }
            ActiveSource.MIDI -> {
                mediaController?.let { controller ->
                    controller.transportControls?.let { controls ->
                        if (currentState.isPlaying) {
                            controls.pause()
                        } else {
                            controls.play()
                        }
                    }
                }
            }
            ActiveSource.NONE -> {
                // Rien à faire
            }
        }
    }

    /**
     * Arrête la lecture de la source active.
     */
    fun stopPlayback() {
        when (currentState.source) {
            ActiveSource.MUSIC -> {
                MusicPlaybackHolder.stop(context)
            }
            ActiveSource.RADIO -> {
                RadioPlaybackHolder.stop(context)
            }
            ActiveSource.MIDI -> {
                mediaController?.let { controller ->
                    controller.transportControls?.stop()
                }
            }
            ActiveSource.NONE -> {
                // Rien à faire
            }
        }
    }

    /**
     * Rafraîchit l'état depuis toutes les sources.
     * Priorise les sources qui jouent activement.
     */
    fun refreshState() {
        synchronized(stateLock) {
            Log.d(TAG, "refreshState - Music: playing=${MusicPlaybackHolder.isPlaying()}, track=${MusicPlaybackHolder.getCurrentTrack()?.title}")
            Log.d(TAG, "refreshState - Radio: playing=${RadioPlaybackHolder.isPlaying()}, paused=${RadioPlaybackHolder.isPaused()}, station=${RadioPlaybackHolder.getCurrentStation()?.name}")

            // D'abord, vérifie quelle source est en lecture active
            val musicPlaying = MusicPlaybackHolder.isPlaying()
            val radioPlaying = RadioPlaybackHolder.isPlaying()
            val radioPaused = RadioPlaybackHolder.isPaused()

            // Priorise les sources en lecture
            when {
                musicPlaying -> {
                    updateFromMusicInternal(true, MusicPlaybackHolder.getCurrentTrack())
                    return
                }
                radioPlaying || radioPaused -> {
                    // Radio est en lecture ou en pause (a une station active)
                    updateFromRadioInternal(radioPlaying)
                    return
                }
            }

            // Vérifie MIDI
            val controller = mediaController
            if (controller != null) {
                val state = controller.playbackState?.state
                val midiPlaying = state == PlaybackStateCompat.STATE_PLAYING
                val midiPaused = state == PlaybackStateCompat.STATE_PAUSED
                if (midiPlaying || midiPaused) {
                    updateFromMidiInternal()
                    return
                }
            }

            // Ensuite, vérifie les sources avec du contenu mais pas en lecture
            val radioStation = RadioPlaybackHolder.getCurrentStation()
            if (radioStation != null) {
                updateFromRadioInternal(false)
                return
            }

            val musicTrack = MusicPlaybackHolder.getCurrentTrack()
            if (musicTrack != null) {
                updateFromMusicInternal(false, musicTrack)
                return
            }

            // Aucune source active
            updateStateInternal(PlaybackState())
        }
    }

    private fun updateFromMusic(isPlaying: Boolean, track: MusicTrack?) {
        synchronized(stateLock) {
            updateFromMusicInternal(isPlaying, track)
        }
    }

    private fun updateFromMusicInternal(isPlaying: Boolean, track: MusicTrack?) {
        Log.d(TAG, "updateFromMusic - isPlaying=$isPlaying, track=${track?.title}")

        // Si Music ne joue pas et qu'une autre source joue, ne pas écraser
        if (!isPlaying) {
            val radioPlaying = RadioPlaybackHolder.isPlaying()
            val radioPaused = RadioPlaybackHolder.isPaused()
            if (radioPlaying || radioPaused) {
                Log.d(TAG, "updateFromMusic - Radio is active, not overriding")
                return
            }

            val controller = mediaController
            if (controller != null) {
                val midiState = controller.playbackState?.state
                if (midiState == PlaybackStateCompat.STATE_PLAYING || midiState == PlaybackStateCompat.STATE_PAUSED) {
                    Log.d(TAG, "updateFromMusic - MIDI is active, not overriding")
                    return
                }
            }
        }

        if (track != null || isPlaying) {
            val newState = PlaybackState(
                source = ActiveSource.MUSIC,
                isPlaying = isPlaying,
                title = track?.title ?: context.getString(R.string.audio_hub_music_title),
                subtitle = track?.artist
            )
            updateStateInternal(newState)
        } else if (currentState.source == ActiveSource.MUSIC) {
            // Music était actif mais plus de track
            checkOtherSourcesInternal()
        }
    }

    private fun updateFromRadio(isPlaying: Boolean) {
        synchronized(stateLock) {
            updateFromRadioInternal(isPlaying)
        }
    }

    private fun updateFromRadioInternal(isPlaying: Boolean) {
        val station = RadioPlaybackHolder.getCurrentStation()
        val title = RadioPlaybackHolder.getCurrentTitle()
        val artist = RadioPlaybackHolder.getCurrentArtist()
        val isPaused = RadioPlaybackHolder.isPaused()

        Log.d(TAG, "updateFromRadio - isPlaying=$isPlaying, isPaused=$isPaused, station=${station?.name}, title=$title, artist=$artist")

        // Si Radio ne joue pas activement (ni en lecture ni en pause), vérifier les autres sources
        if (!isPlaying && !isPaused) {
            val musicPlaying = MusicPlaybackHolder.isPlaying()
            if (musicPlaying) {
                Log.d(TAG, "updateFromRadio - Music is playing, not overriding")
                return
            }

            val controller = mediaController
            if (controller != null) {
                val midiState = controller.playbackState?.state
                if (midiState == PlaybackStateCompat.STATE_PLAYING) {
                    Log.d(TAG, "updateFromRadio - MIDI is playing, not overriding")
                    return
                }
            }
        }

        // Radio est active si elle joue, est en pause, ou a une station chargée
        val radioActive = isPlaying || isPaused || station != null

        if (radioActive) {
            // Fallback pour le titre: stream metadata -> station name -> default
            val displayTitle = when {
                !title.isNullOrBlank() -> title
                station?.name?.isNotBlank() == true -> station.name
                else -> context.getString(R.string.audio_hub_radio_title)
            }
            // Subtitle: artist si différent du titre, sinon station name
            val displaySubtitle = when {
                !artist.isNullOrBlank() && artist != title -> artist
                station?.name?.isNotBlank() == true && station.name != displayTitle -> station.name
                else -> null
            }

            val newState = PlaybackState(
                source = ActiveSource.RADIO,
                isPlaying = isPlaying,
                title = displayTitle,
                subtitle = displaySubtitle
            )
            updateStateInternal(newState)
        } else if (currentState.source == ActiveSource.RADIO) {
            // Radio était actif mais plus de station
            checkOtherSourcesInternal()
        }
    }

    private fun updateFromMidi() {
        synchronized(stateLock) {
            updateFromMidiInternal()
        }
    }

    private fun updateFromMidiInternal() {
        val controller = mediaController ?: return
        val playbackState = controller.playbackState
        val metadata = controller.metadata

        val isMidiPlaying = playbackState?.state == PlaybackStateCompat.STATE_PLAYING
        val isMidiPaused = playbackState?.state == PlaybackStateCompat.STATE_PAUSED
        val hasMidiContent = isMidiPlaying || isMidiPaused || metadata != null

        Log.d(TAG, "updateFromMidi - playing=$isMidiPlaying, paused=$isMidiPaused, hasContent=$hasMidiContent")

        // Si MIDI ne joue pas et qu'une autre source joue, ne pas écraser
        if (!isMidiPlaying) {
            val musicPlaying = MusicPlaybackHolder.isPlaying()
            val radioPlaying = RadioPlaybackHolder.isPlaying()
            val radioPaused = RadioPlaybackHolder.isPaused()
            if (musicPlaying || radioPlaying || radioPaused) {
                Log.d(TAG, "updateFromMidi - Another source is active, not overriding")
                return
            }
        }

        if (hasMidiContent) {
            val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)

            // Fallback pour le titre: metadata -> default
            val displayTitle = if (!rawTitle.isNullOrBlank()) rawTitle else context.getString(R.string.audio_hub_midi_title)
            // Subtitle seulement si non vide
            val displaySubtitle = if (!rawArtist.isNullOrBlank()) rawArtist else null

            val newState = PlaybackState(
                source = ActiveSource.MIDI,
                isPlaying = isMidiPlaying,
                title = displayTitle,
                subtitle = displaySubtitle
            )
            updateStateInternal(newState)
        } else if (currentState.source == ActiveSource.MIDI) {
            // MIDI était actif mais plus de contenu
            checkOtherSourcesInternal()
        }
    }

    private fun checkOtherSourcesInternal() {
        Log.d(TAG, "checkOtherSources - looking for active sources")

        // Vérifie d'abord les sources en lecture active
        val musicPlaying = MusicPlaybackHolder.isPlaying()
        val radioPlaying = RadioPlaybackHolder.isPlaying()
        val radioPaused = RadioPlaybackHolder.isPaused()

        when {
            musicPlaying -> {
                updateFromMusicInternal(true, MusicPlaybackHolder.getCurrentTrack())
                return
            }
            radioPlaying || radioPaused -> {
                updateFromRadioInternal(radioPlaying)
                return
            }
        }

        // Vérifie MIDI
        val controller = mediaController
        if (controller != null) {
            val state = controller.playbackState?.state
            val midiPlaying = state == PlaybackStateCompat.STATE_PLAYING
            val midiPaused = state == PlaybackStateCompat.STATE_PAUSED
            if (midiPlaying || midiPaused) {
                updateFromMidiInternal()
                return
            }
        }

        // Vérifie les sources avec contenu mais pas en lecture
        val radioStation = RadioPlaybackHolder.getCurrentStation()
        if (radioStation != null) {
            updateFromRadioInternal(false)
            return
        }

        val musicTrack = MusicPlaybackHolder.getCurrentTrack()
        if (musicTrack != null) {
            updateFromMusicInternal(false, musicTrack)
            return
        }

        // Aucune source active
        updateStateInternal(PlaybackState())
    }

    private fun updateStateInternal(newState: PlaybackState) {
        if (currentState != newState) {
            Log.d(TAG, "State changed: $newState")
            currentState = newState
            val stateSnapshot = currentState
            handler.post {
                listeners.forEach { it.onPlaybackStateChanged(stateSnapshot) }
            }
        }
    }
}
