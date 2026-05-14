package com.Atom2Universe.app.midi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.Atom2Universe.app.AudioFocusManager
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.sf2.Sf2Voice
import com.Atom2Universe.app.midi.sf2.VelocityCurve
import com.Atom2Universe.app.stats.StatsTracker
import kotlinx.coroutines.*

/**
 * Service de playback MIDI en arrière-plan
 * Utilise MediaBrowserServiceCompat pour la compatibilité avec MediaSession
 * Gère: MidiSynthesizerManager (Sonivox/Sf2Engine), Queue, Notifications, Audio Focus
 */
class MidiPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var synthesizerManager: MidiSynthesizerManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var queueManager: PlaybackQueueManager
    private lateinit var notificationManager: MidiNotificationManager
    private lateinit var audioManager: AudioManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isForegroundService = false

    // Wake lock pour garder le CPU actif pendant la lecture (écran éteint)
    private var wakeLock: PowerManager.WakeLock? = null

    // État actuel
    private var currentPlaybackState = PlaybackStateCompat.STATE_NONE

    // BroadcastReceiver pour les commandes du widget
    private val widgetCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> handlePlayPause()
                ACTION_SKIP_NEXT -> handleSkipNext()
                ACTION_SKIP_PREVIOUS -> handleSkipPrevious()
            }
        }
    }

    // BroadcastReceiver pour l'état de l'écran (allumé/éteint)
    // Quand l'écran s'éteint, Android throttle le CPU (gouverneur powersave), ce qui
    // perturbe les threads workers du SF2Engine → craquements. On passe en rendu séquentiel.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> synthesizerManager.setScreenOff(true)
                Intent.ACTION_SCREEN_ON  -> synthesizerManager.setScreenOff(false)
            }
        }
    }

    // Temps de démarrage de la lecture (pour le seuil 3s du Previous)
    private var playbackStartTimeMs: Long = 0L

    // Audio focus handling
    private var wasPlayingBeforeAudioFocusLoss = false
    private var currentVolume = 1.0f  // Track current volume for ducking

    private val audioFocusListener = object : AudioFocusManager.AudioFocusListener {
        override fun onAudioFocusPause() {
            wasPlayingBeforeAudioFocusLoss = currentPlaybackState == PlaybackStateCompat.STATE_PLAYING
            if (wasPlayingBeforeAudioFocusLoss) {
                handlePause()
            }
        }

        override fun onAudioFocusResume() {
            if (wasPlayingBeforeAudioFocusLoss) {
                wasPlayingBeforeAudioFocusLoss = false
                handlePlay()
            }
        }

        override fun onAudioFocusDuck() {
            synthesizerManager.setVolume(0.2f)
        }

        override fun onAudioFocusUnduck() {
            synthesizerManager.setVolume(currentVolume)
        }
    }

    companion object {
        private const val TAG = "MidiPlaybackService"

        // Media browser root ID
        const val MEDIA_ROOT_ID = "MIDI_LIBRARY"

        // Actions pour les intents
        const val ACTION_PLAY = "com.Atom2Universe.app.midi.PLAY"
        const val ACTION_PAUSE = "com.Atom2Universe.app.midi.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.Atom2Universe.app.midi.PLAY_PAUSE"
        const val ACTION_STOP = "com.Atom2Universe.app.midi.STOP"
        const val ACTION_SKIP_NEXT = "com.Atom2Universe.app.midi.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.Atom2Universe.app.midi.SKIP_PREVIOUS"
        const val ACTION_RELOAD_SOUNDFONT = "com.Atom2Universe.app.midi.RELOAD_SOUNDFONT"
        const val ACTION_SET_REVERB = "com.Atom2Universe.app.midi.SET_REVERB"
        const val ACTION_SET_EQ_ENABLED = "com.Atom2Universe.app.midi.SET_EQ_ENABLED"
        const val ACTION_SET_EQ_BAND = "com.Atom2Universe.app.midi.SET_EQ_BAND"
        const val ACTION_REFRESH_AFTER_PRACTICE = "com.Atom2Universe.app.midi.REFRESH_AFTER_PRACTICE"

        // Broadcast sent when SF2 loading is complete
        const val ACTION_SF2_LOAD_COMPLETE = "com.Atom2Universe.app.midi.SF2_LOAD_COMPLETE"
        const val EXTRA_SF2_LOAD_SUCCESS = "sf2_load_success"

        // Extras pour les intents
        const val EXTRA_SOUNDFONT_PATH = "soundfont_path"
        const val EXTRA_REVERB_PRESET = "reverb_preset"
        const val EXTRA_EQ_ENABLED = "eq_enabled"
        const val EXTRA_EQ_BAND = "eq_band"
        const val EXTRA_EQ_MILLIBELS = "eq_millibels"
        const val EXTRA_HYBRID_MODE = "hybrid_mode"
        const val EXTRA_HYBRID_PROGRAMS = "hybrid_programs"
        const val EXTRA_HYBRID_DRUMS_SF2 = "hybrid_drums_sf2"
        const val EXTRA_FLUIDSYNTH_MODE = "fluidsynth_mode"

        // Custom actions for shuffle/repeat
        const val ACTION_TOGGLE_SHUFFLE = "com.Atom2Universe.app.midi.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "com.Atom2Universe.app.midi.CYCLE_REPEAT"

        // Custom command names for MediaController
        const val COMMAND_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
        const val COMMAND_CYCLE_REPEAT = "CYCLE_REPEAT"
        const val COMMAND_SYNC_VISUALIZER = "SYNC_VISUALIZER"

        // Extras keys for playback state
        const val EXTRA_SHUFFLE_ENABLED = "shuffle_enabled"
        const val EXTRA_REPEAT_MODE = "repeat_mode"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_POSITION_MS = "position_ms"

        // Seuil pour Previous (3 secondes)
        private const val PREVIOUS_THRESHOLD_MS = 3000L
    }

    override fun onCreate() {
        super.onCreate()

        // Initialiser les composants
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        synthesizerManager = MidiSynthesizerManager(this)
        queueManager = PlaybackQueueManager()
        notificationManager = MidiNotificationManager(this, this)

        // Initialiser le wake lock pour garder le CPU actif pendant la lecture
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "A2U:MidiPlaybackWakeLock"
        )

        // Initialiser MediaSession
        setupMediaSession()

        // Setup callbacks
        setupCallbacks()

        // Initialiser le synthétiseur (Sonivox, SF2, ou Hybride selon settings)
        serviceScope.launch {
            initializeSynthesizer()
        }

        // Enregistrer le BroadcastReceiver pour les commandes widget
        registerWidgetCommandReceiver()

        // Enregistrer le receiver pour l'état de l'écran
        registerScreenStateReceiver()

        // Appliquer l'état initial : si le service démarre avec l'écran déjà éteint,
        // activer immédiatement le mode séquentiel (isInteractive = false = écran éteint)
        if (!powerManager.isInteractive) {
            synthesizerManager.setScreenOff(true)
        }
    }

    private fun registerWidgetCommandReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_SKIP_NEXT)
            addAction(ACTION_SKIP_PREVIOUS)
        }
        ContextCompat.registerReceiver(
            this,
            widgetCommandReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun registerScreenStateReceiver() {
        // ACTION_SCREEN_OFF / ACTION_SCREEN_ON ne peuvent être reçus que via un receiver
        // enregistré dynamiquement (pas dans le manifest). Pas besoin de permission spéciale.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Gère les actions de notification
        when (intent?.action) {
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_PLAY_PAUSE -> handlePlayPause()
            ACTION_STOP -> handleStop()
            ACTION_SKIP_NEXT -> handleSkipNext()
            ACTION_SKIP_PREVIOUS -> handleSkipPrevious()
            ACTION_TOGGLE_SHUFFLE -> handleToggleShuffle()
            ACTION_CYCLE_REPEAT -> handleCycleRepeat()
            ACTION_RELOAD_SOUNDFONT -> {
                val soundFontPath = intent.getStringExtra(EXTRA_SOUNDFONT_PATH) ?: ""
                val isHybridMode = intent.getBooleanExtra(EXTRA_HYBRID_MODE, false)
                val isFluidSynthMode = intent.getBooleanExtra(EXTRA_FLUIDSYNTH_MODE, false)
                val hybridPrograms = intent.getIntegerArrayListExtra(EXTRA_HYBRID_PROGRAMS)?.toSet() ?: emptySet()
                val useSf2ForDrums = intent.getBooleanExtra(EXTRA_HYBRID_DRUMS_SF2, false)
                handleReloadSoundFont(soundFontPath, isHybridMode, isFluidSynthMode, hybridPrograms, useSf2ForDrums)
            }
            ACTION_SET_REVERB -> {
                val preset = intent.getIntExtra(EXTRA_REVERB_PRESET, 1)
                setReverb(preset)
            }
            ACTION_SET_EQ_ENABLED -> {
                val enabled = intent.getBooleanExtra(EXTRA_EQ_ENABLED, false)
                synthesizerManager.setEqualizerEnabled(enabled)
                serviceScope.launch {
                    SettingsRepository(applicationContext).setMidiEqEnabled(enabled)
                }
            }
            ACTION_SET_EQ_BAND -> {
                val band = intent.getIntExtra(EXTRA_EQ_BAND, 0)
                val millibels = intent.getIntExtra(EXTRA_EQ_MILLIBELS, 0)
                synthesizerManager.setEqualizerBandLevel(band, millibels)
                serviceScope.launch {
                    SettingsRepository(applicationContext).setMidiEqBandLevel(band, millibels)
                }
            }
            ACTION_REFRESH_AFTER_PRACTICE -> {
                handleRefreshAfterPractice()
            }
        }

        return START_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        // TODO: Charger les tracks/playlists depuis Room Database
        // Pour l'instant, retourne une liste vide
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(widgetCommandReceiver)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: unregisterReceiver failed", e)
        }

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: unregisterReceiver screenStateReceiver failed", e)
        }

        try {
            // Abandonner l'audio focus
            AudioFocusManager.abandonFocus()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: abandonFocus failed", e)
        }

        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: serviceScope.cancel failed", e)
        }

        try {
            synthesizerManager.release()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: synthesizerManager.release failed", e)
        }

        // BUG FIX 3.10: Toujours nettoyer le callback MediaSession même en cas d'exception
        // Le callback doit être retiré avant release() pour éviter les fuites mémoire
        try {
            mediaSession.setCallback(null)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: mediaSession.setCallback(null) failed", e)
        }

        try {
            mediaSession.isActive = false
            mediaSession.release()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: mediaSession.release failed", e)
        }

        try {
            releaseWakeLock()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: releaseWakeLock failed", e)
        }

        try {
            if (isForegroundService) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: stopForeground failed", e)
        }

        // Réinitialiser l'état partagé seulement si on était vraiment arrêté
        // (pas si le système tue le service alors qu'on était en pause)
        if (currentPlaybackState == PlaybackStateCompat.STATE_STOPPED) {
            MidiPlaybackState.onPlaybackStopped(this)
            MidiPlaybackState.reset()
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Retourne true pour recevoir onRebind() quand un client se reconnecte
        // Le service continue de tourner si en lecture (grâce à startForeground)
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    // === MediaSession Setup ===

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MidiPlaybackService").apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(mediaSessionCallback)

            // État initial
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .setActions(getAvailableActions())
                    .build()
            )

            isActive = true
        }

        sessionToken = mediaSession.sessionToken
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            handlePlay()
        }

        override fun onPause() {
            handlePause()
        }

        override fun onStop() {
            handleStop()
        }

        override fun onSkipToNext() {
            handleSkipNext()
        }

        override fun onSkipToPrevious() {
            handleSkipPrevious()
        }

        override fun onSeekTo(pos: Long) {
            handleSeekTo(pos)
        }

        override fun onCommand(command: String?, extras: Bundle?, cb: android.os.ResultReceiver?) {
            when (command) {
                COMMAND_TOGGLE_SHUFFLE -> handleToggleShuffle()
                COMMAND_CYCLE_REPEAT -> handleCycleRepeat()
                COMMAND_SYNC_VISUALIZER -> handleSyncVisualizer()
            }
        }

        override fun onPlayFromUri(uri: android.net.Uri?, extras: Bundle?) {
            if (extras == null) {
                return
            }

            // Vérifier si c'est une queue de tracks ou un track unique
            val queueSize = extras.getInt("queue_size", 0)

            if (queueSize > 0) {
                // Queue de tracks
                val startIndex = extras.getInt("start_index", 0)
                val tracks = mutableListOf<MidiTrack>()

                for (i in 0 until queueSize) {
                    val trackId = extras.getLong("track_${i}_id", -1)
                    val trackPath = extras.getString("track_${i}_path") ?: ""
                    val trackTitle = extras.getString("track_${i}_title") ?: "Unknown"
                    val trackArtist = extras.getString("track_${i}_artist") ?: "Unknown"
                    val trackAlbum = extras.getString("track_${i}_album") ?: ""

                    if (trackId != -1L && trackPath.isNotEmpty()) {
                        tracks.add(MidiTrack(
                            id = trackId,
                            title = trackTitle,
                            artist = trackArtist,
                            album = trackAlbum,
                            filePath = trackPath,
                            duration = 0L,
                            dateAdded = System.currentTimeMillis(),
                            fileSize = 0L
                        ))
                    }
                }

                if (tracks.isNotEmpty()) {
                    // IMPORTANT: Stopper et réinitialiser l'état avant de changer de piste
                    synthesizerManager.stop()
                    currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

                    queueManager.setQueue(tracks, startIndex)

                    // Vérifie si le shuffle doit être activé
                    val enableShuffle = extras.getBoolean("enable_shuffle", false)
                    if (enableShuffle) {
                        if (!queueManager.isShuffleEnabled()) {
                            queueManager.toggleShuffle()
                        }
                    }

                    handlePlay()
                }
            } else {
                // Track unique (legacy)
                val trackId = extras.getLong("track_id", -1)
                val trackPath = extras.getString("track_path") ?: ""
                val trackTitle = extras.getString("track_title") ?: "Unknown"
                val trackArtist = extras.getString("track_artist") ?: "Unknown"

                if (trackId == -1L || trackPath.isEmpty()) {
                    return
                }

                // IMPORTANT: Stopper et réinitialiser l'état avant de changer de piste
                synthesizerManager.stop()
                currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

                val track = MidiTrack(
                    id = trackId,
                    title = trackTitle,
                    artist = trackArtist,
                    album = "",
                    filePath = trackPath,
                    duration = 0L,
                    dateAdded = System.currentTimeMillis(),
                    fileSize = 0L
                )

                queueManager.setQueue(listOf(track), 0)
                handlePlay()
            }
        }
    }

    // === Callbacks Setup ===

    private fun setupCallbacks() {
        // Synthesizer callbacks
        synthesizerManager.setOnStateChangeListener { _ ->
            // TODO: Mettre à jour MediaSession metadata
        }

        synthesizerManager.setOnCompletionListener {
            handleSkipNext() // Auto-play next
        }

        synthesizerManager.setOnErrorListener { _ ->
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        }

        // Position change callback - met à jour l'état du playback périodiquement
        synthesizerManager.setOnPositionChangedListener { positionMs, durationMs ->
            // Mettre à jour l'état pour que les clients reçoivent la position
            // positionUpdateOnly=true évite le spam de notifications widget
            if (currentPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
                updatePlaybackState(
                    currentPlaybackState,
                    positionMs,
                    durationMs,
                    positionUpdateOnly = true
                )
            }
        }

        // Queue callbacks
        queueManager.setOnCurrentTrackChangedListener { track ->
            updateMediaMetadata(track)
            updateNotification()
        }
    }

    /**
     * Met à jour les métadonnées MediaSession (titre, artiste, etc.)
     * @param forceRefreshDuration Si true, récupère la durée depuis le synthétiseur
     */
    private fun updateMediaMetadata(track: MidiTrack?, forceRefreshDuration: Boolean = false) {
        if (track == null) {
            mediaSession.setMetadata(null)
            return
        }

        // Utilise la durée du synthétiseur si disponible et supérieure à 0
        val actualDuration = if (forceRefreshDuration) {
            val engineDuration = synthesizerManager.getDuration()
            if (engineDuration > 0) engineDuration else track.duration
        } else {
            track.duration
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, track.filePath)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, actualDuration)
            .build()

        mediaSession.setMetadata(metadata)
    }

    // === Playback Actions ===

    private fun handlePlay() {
        // Demander l'audio focus avant de démarrer/reprendre
        AudioFocusManager.requestFocus(audioFocusListener)

        // Re-initialize synthesizer if it was released (e.g. after handleStop)
        if (synthesizerManager.needsReinitialization()) {
            serviceScope.launch {
                initializeSynthesizer()
                handlePlay()  // Retry after re-initialization
            }
            return
        }

        // Si on était en pause, reprendre
        if (currentPlaybackState == PlaybackStateCompat.STATE_PAUSED) {
            try {
                synthesizerManager.resume()
                // Configure audio routing to speaker if USB MIDI is connected
                synthesizerManager.autoConfigureAudioOutput()
                playbackStartTimeMs = System.currentTimeMillis() - synthesizerManager.getCurrentPosition()
                StatsTracker.resumeMidiSession()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                acquireWakeLock()
                updateNotification()
            } catch (_: Exception) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            }
            return
        }

        val currentTrack = queueManager.getCurrentTrack()

        if (currentTrack == null) {
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            return
        }

        try {
            // Charge le fichier MIDI
            val loaded = synthesizerManager.loadMidiFile(currentTrack.filePath)

            if (!loaded) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }

            // Démarre le playback
            val started = synthesizerManager.start()

            if (started) {
                // Configure audio routing to speaker if USB MIDI is connected
                synthesizerManager.autoConfigureAudioOutput()

                playbackStartTimeMs = System.currentTimeMillis()
                // Met à jour les métadonnées avec la durée réelle du fichier MIDI chargé
                updateMediaMetadata(currentTrack, forceRefreshDuration = true)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startForegroundWithNotification()
                acquireWakeLock()

                // Démarrer le tracking des statistiques pour MIDI
                val midiFileName = currentTrack.title.takeIf { it.isNotBlank() }
                    ?: currentTrack.filePath.substringAfterLast("/")
                StatsTracker.startMidiSession(
                    midiFileName = midiFileName,
                    isPracticeMode = false
                )
            } else {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            }

        } catch (_: Exception) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun handlePause() {
        try {
            synthesizerManager.pause()
            StatsTracker.pauseMidiSession()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            releaseWakeLock()
            updateNotification()
        } catch (_: Exception) { }
    }

    private fun handlePlayPause() {
        when (currentPlaybackState) {
            PlaybackStateCompat.STATE_PLAYING -> handlePause()
            PlaybackStateCompat.STATE_PAUSED -> handlePlay()
            PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.STATE_NONE -> handlePlay()
            else -> {
                // En cas d'erreur ou autre état, essayer de jouer
                handlePlay()
            }
        }
    }

    private fun handleStop() {
        try {
            synthesizerManager.stop()
            // Release all engines (including FluidSynth Oboe driver) since the service
            // is stopping. This frees the audio driver so practice mode can use FluidSynth.
            // Engines will be re-initialized in initializeSynthesizer() on next onCreate().
            synthesizerManager.release()
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            releaseWakeLock()

            // Terminer le tracking de la session MIDI
            StatsTracker.endMidiSession()

            // Abandonner l'audio focus et désenregistrer le listener
            // Note: AudioFocusManager.abandonFocus() met currentListener à null,
            // ce qui empêche les callbacks d'être appelés après l'arrêt
            AudioFocusManager.abandonFocus()

            // Reset local audio focus state
            wasPlayingBeforeAudioFocusLoss = false

            if (isForegroundService) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundService = false
            }

            stopSelf()
        } catch (_: Exception) { }
    }

    private fun handleSkipNext() {
        val nextTrack = queueManager.skipToNext()

        if (nextTrack != null) {
            // IMPORTANT: Utiliser stopForTrackTransition() au lieu de stop()
            // Cela garde les streams audio Oboe actifs pour éviter les race conditions
            // et crashes SIGBUS lors de la transition entre pistes en background
            synthesizerManager.stopForTrackTransition()
            // Réinitialiser l'état pour que handlePlay() charge le nouveau fichier
            currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
            handlePlay()
        } else {
            handleStop()
        }
    }

    private fun handleSkipPrevious() {
        // Seuil 3 secondes: si on a joué moins de 3s, passer au précédent
        // sinon, redémarrer le morceau actuel
        val elapsedMs = System.currentTimeMillis() - playbackStartTimeMs

        // IMPORTANT: Utiliser stopForTrackTransition() au lieu de stop()
        // Cela garde les streams audio Oboe actifs pour éviter les race conditions
        synthesizerManager.stopForTrackTransition()
        // Réinitialiser l'état pour que handlePlay() charge le fichier
        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

        if (elapsedMs >= PREVIOUS_THRESHOLD_MS) {
            // Redémarrer le morceau actuel
            handlePlay()
        } else {
            // Passer au morceau précédent
            queueManager.skipToPrevious()
            handlePlay()
        }
    }

    private fun handleToggleShuffle() {
        queueManager.toggleShuffle()
        updatePlaybackState(currentPlaybackState)
    }

    private fun handleCycleRepeat() {
        queueManager.cycleRepeatMode()
        updatePlaybackState(currentPlaybackState)
    }

    private fun handleSyncVisualizer() {
        synthesizerManager.syncVisualizerToCurrentPosition()
    }

    private fun handleReloadSoundFont(
        soundFontPath: String,
        isHybridMode: Boolean = false,
        isFluidSynthMode: Boolean = false,
        hybridPrograms: Set<Int> = emptySet(),
        useSf2ForDrums: Boolean = false
    ) {
        android.util.Log.i("MidiPlaybackService", "handleReloadSoundFont: path=$soundFontPath, isFluidSynth=$isFluidSynthMode, isHybrid=$isHybridMode")

        serviceScope.launch {
            // Stop current playback
            synthesizerManager.stop()

            // Configure operating mode
            val selectedMode: MidiSynthesizerManager.OperatingMode
            if (isFluidSynthMode && soundFontPath.isNotBlank()) {
                selectedMode = MidiSynthesizerManager.OperatingMode.FLUIDSYNTH_ONLY
                synthesizerManager.setOperatingMode(selectedMode)
            } else if (isHybridMode && soundFontPath.isNotBlank() && hybridPrograms.isNotEmpty()) {
                selectedMode = MidiSynthesizerManager.OperatingMode.HYBRID
                synthesizerManager.setOperatingMode(selectedMode, hybridPrograms, useSf2ForDrums)
            } else if (soundFontPath.isNotBlank()) {
                selectedMode = MidiSynthesizerManager.OperatingMode.SF2_ONLY
                synthesizerManager.setOperatingMode(selectedMode)
            } else {
                selectedMode = MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY
                synthesizerManager.setOperatingMode(selectedMode)
            }
            android.util.Log.i("MidiPlaybackService", "handleReloadSoundFont: selectedMode=$selectedMode")

            // Reload with new configuration
            val success = reloadSoundFont(soundFontPath)
            android.util.Log.i("MidiPlaybackService", "handleReloadSoundFont: reload success=$success")

            // Broadcast that SF2 loading is complete
            val broadcastIntent = Intent(ACTION_SF2_LOAD_COMPLETE).apply {
                setPackage(packageName)
                putExtra(EXTRA_SF2_LOAD_SUCCESS, success)
            }
            sendBroadcast(broadcastIntent)
        }
    }

    private fun handleSeekTo(positionMs: Long) {
        try {
            synthesizerManager.seekTo(positionMs)
            // Mettre à jour le temps de départ pour le seuil Previous
            playbackStartTimeMs = System.currentTimeMillis() - positionMs
            updatePlaybackState(currentPlaybackState, positionMs)
        } catch (_: Exception) { }
    }

    private fun handleRefreshAfterPractice() {
        serviceScope.launch {
            try {
                synthesizerManager.stop()

                val settingsRepository = SettingsRepository(applicationContext)
                val soundFontPath = getSoundFontPath().orEmpty()
                val synthMode = settingsRepository.getSynthMode()
                val sf2Programs = settingsRepository.getHybridSf2Programs()
                val useSf2ForDrums = settingsRepository.isHybridUseSf2ForDrums()

                when (synthMode) {
                    SettingsRepository.SYNTH_MODE_HYBRID -> {
                        if (soundFontPath.isNotBlank() && sf2Programs.isNotEmpty()) {
                            synthesizerManager.setOperatingMode(
                                MidiSynthesizerManager.OperatingMode.HYBRID,
                                sf2Programs,
                                useSf2ForDrums
                            )
                        } else {
                            synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                        }
                    }
                    SettingsRepository.SYNTH_MODE_SF2 -> {
                        if (soundFontPath.isNotBlank()) {
                            synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SF2_ONLY)
                        } else {
                            synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                        }
                    }
                    SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> {
                        if (soundFontPath.isNotBlank()) {
                            synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.FLUIDSYNTH_ONLY)
                        } else {
                            synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                        }
                    }
                    else -> {
                        synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                    }
                }

                synthesizerManager.reloadSoundFont(soundFontPath)
                loadSf2Settings()

                queueManager.getCurrentTrack()?.let { track ->
                    synthesizerManager.loadMidiFile(track.filePath)
                    updateMediaMetadata(track, forceRefreshDuration = true)
                }

                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            } catch (_: Exception) {
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            }
        }
    }

    // === State Management ===

    private fun updatePlaybackState(
        state: Int,
        positionMs: Long? = null,
        durationMs: Long? = null,
        positionUpdateOnly: Boolean = false
    ) {
        val stateChanged = currentPlaybackState != state
        currentPlaybackState = state

        val currentPosition = positionMs ?: synthesizerManager.getCurrentPosition()
        val duration = durationMs?.takeIf { it > 0 } ?: synthesizerManager.getDuration()

        // Inclure l'état shuffle/repeat et position/durée dans les extras
        val extras = Bundle().apply {
            putBoolean(EXTRA_SHUFFLE_ENABLED, queueManager.isShuffleEnabled())
            putInt(EXTRA_REPEAT_MODE, queueManager.getRepeatMode().ordinal)
            putLong(EXTRA_POSITION_MS, currentPosition)
            putLong(EXTRA_DURATION_MS, duration)
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, currentPosition, 1.0f)
            .setActions(getAvailableActions())
            .setExtras(extras)
            .build()

        mediaSession.setPlaybackState(playbackState)

        // Mettre à jour l'état partagé pour le widget
        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        val track = queueManager.getCurrentTrack()
        val hasNext = queueManager.hasNext()
        val hasPrevious = queueManager.hasPrevious()

        // Pour les mises à jour de position, utiliser la version silencieuse
        // Le widget sera notifié uniquement lors de vrais changements d'état
        if (positionUpdateOnly && !stateChanged) {
            MidiPlaybackState.updateStateQuiet(track, isPlaying, hasNext, hasPrevious)
        } else {
            MidiPlaybackState.updateState(this, track, isPlaying, hasNext, hasPrevious)
        }

        // Enregistrer/désenregistrer auprès de AudioPlaybackManager (seulement si état change)
        if (stateChanged) {
            if (isPlaying) {
                MidiPlaybackState.onPlaybackStarted(this) {
                    // Callback pour arrêter le playback MIDI si une autre source démarre
                    handleStop()
                }
            } else if (state == PlaybackStateCompat.STATE_STOPPED) {
                MidiPlaybackState.onPlaybackStopped(this)
            }
        }
    }

    private fun getAvailableActions(): Long {
        return PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
    }

    // === Notification Management ===

    private fun startForegroundWithNotification() {
        if (!isForegroundService) {
            val notification = notificationManager.buildNotification(
                queueManager.getCurrentTrack(),
                currentPlaybackState,
                mediaSession.sessionToken
            )

            startForeground(MidiNotificationManager.NOTIFICATION_ID, notification)
            isForegroundService = true
        } else {
            updateNotification()
        }
    }

    private fun updateNotification() {
        if (isForegroundService) {
            val notification = notificationManager.buildNotification(
                queueManager.getCurrentTrack(),
                currentPlaybackState,
                mediaSession.sessionToken
            )

            val notifManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notifManager.notify(MidiNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    // === Public API ===

    /**
     * Définit la queue de lecture et démarre la lecture
     */
    @Suppress("unused")
    fun playTracks(tracks: List<MidiTrack>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
        handlePlay()
    }

    /**
     * Ajoute un track à la queue
     */
    @Suppress("unused")
    fun addToQueue(track: MidiTrack) {
        queueManager.addToQueue(track)
    }

    /**
     * Récupère le gestionnaire de queue
     */
    @Suppress("unused")
    fun getQueueManager(): PlaybackQueueManager {
        return queueManager
    }

    /**
     * Initialise le synthétiseur MIDI avec un SoundFont optionnel
     * (Sf2Engine si SF2 fourni, sinon Sonivox)
     */
    @Suppress("unused")
    fun initializeSf2Engine(soundFontPath: String): Boolean {
        return synthesizerManager.initialize(soundFontPath)
    }

    /**
     * Initializes the synthesizer based on current settings.
     * Supports Sonivox, SF2, and Hybrid modes.
     */
    private suspend fun initializeSynthesizer() {
        val settingsRepository = SettingsRepository(applicationContext)

        // Get SoundFont path (the imported SF2, not cleared when switching to Sonivox)
        val soundFontPath = getSoundFontPath()

        // Get the selected synthesizer mode
        val synthMode = settingsRepository.getSynthMode()
        val sf2Programs = settingsRepository.getHybridSf2Programs()
        val useSf2ForDrums = settingsRepository.isHybridUseSf2ForDrums()

        // Determine operating mode based on user selection
        val initSuccess: Boolean
        when (synthMode) {
            SettingsRepository.SYNTH_MODE_HYBRID -> {
                if (!soundFontPath.isNullOrBlank() && sf2Programs.isNotEmpty()) {
                    synthesizerManager.setOperatingMode(
                        MidiSynthesizerManager.OperatingMode.HYBRID,
                        sf2Programs,
                        useSf2ForDrums
                    )
                    initSuccess = synthesizerManager.initialize(soundFontPath)
                } else {
                    synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                    initSuccess = synthesizerManager.initialize("")
                }
            }
            SettingsRepository.SYNTH_MODE_SF2 -> {
                if (!soundFontPath.isNullOrBlank()) {
                    synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SF2_ONLY)
                    initSuccess = synthesizerManager.initialize(soundFontPath)
                } else {
                    synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                    initSuccess = synthesizerManager.initialize("")
                }
            }
            SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> {
                if (!soundFontPath.isNullOrBlank()) {
                    synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.FLUIDSYNTH_ONLY)
                    initSuccess = synthesizerManager.initialize(soundFontPath)
                } else {
                    synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                    initSuccess = synthesizerManager.initialize("")
                }
            }
            else -> {
                // SONIVOX_ONLY (default)
                synthesizerManager.setOperatingMode(MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY)
                initSuccess = synthesizerManager.initialize("")
            }
        }

        if (initSuccess) {
            // Load saved SF2 velocity curve setting
            loadSf2Settings()
        }
    }

    /**
     * Loads saved SF2 engine settings (velocity curve, reverb, etc.)
     * Applied globally to Sf2Voice and synthesizer for all future notes.
     */
    private suspend fun loadSf2Settings() {
        withContext(Dispatchers.IO) {
            try {
                val settingsRepository = SettingsRepository(applicationContext)

                // Load velocity curve
                val velocityCurveIndex = settingsRepository.getSf2VelocityCurve()
                val curve = when (velocityCurveIndex) {
                    0 -> VelocityCurve.LINEAR
                    1 -> VelocityCurve.CONCAVE
                    2 -> VelocityCurve.SOFT
                    3 -> VelocityCurve.HARD
                    else -> VelocityCurve.CONCAVE
                }
                Sf2Voice.velocityCurve = curve

                // Load reverb preset
                val reverbPreset = settingsRepository.getReverbPreset()
                synthesizerManager.setReverb(reverbPreset)
            } catch (_: Exception) { }
        }
    }

    /**
     * Récupère le chemin du SoundFont configuré depuis les settings
     */
    private suspend fun getSoundFontPath(): String? {
        return withContext(Dispatchers.IO) {
            val settingsRepository = SettingsRepository(applicationContext)
            settingsRepository.getSoundFontPath()
        }
    }

    /**
     * Recharge le SoundFont (appelé depuis l'Activity après un changement de SF2)
     *
     * @param soundFontPath Chemin vers le nouveau fichier SF2, ou chaîne vide pour Sonivox
     * @return true si le rechargement a réussi, false sinon
     */
    fun reloadSoundFont(soundFontPath: String): Boolean {
        return synthesizerManager.reloadSoundFont(soundFontPath)
    }

    /**
     * Sets the reverb preset.
     * @param preset -1 = Off, 0 = Large Hall, 1 = Hall, 2 = Chamber, 3 = Room
     */
    fun setReverb(preset: Int) {
        synthesizerManager.setReverb(preset)
    }

    /**
     * Sets chorus preset.
     * @param preset -1 = Off, 0 = Light, 1 = Default, 2 = Rich
     */
    @Suppress("unused")
    fun setChorus(preset: Int) {
        synthesizerManager.setChorus(preset)
    }

    // === Wake Lock Management ===

    /**
     * Acquiert le wake lock pour garder le CPU actif pendant la lecture.
     * Nécessaire pour que les tâches planifiées (ex: onCompletion) s'exécutent
     * même quand l'écran est éteint ou l'app en arrière-plan.
     */
    private fun acquireWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    // Timeout de 30 minutes pour éviter les fuites de batterie
                    // Le wake lock est renouvelé automatiquement tant que la lecture continue
                    lock.acquire(30 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {
            // Ignorer les erreurs de wake lock
        }
    }

    /**
     * Libère le wake lock.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                }
            }
        } catch (_: Exception) {
            // Ignorer les erreurs de wake lock
        }
    }
}
