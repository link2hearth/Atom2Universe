package com.Atom2Universe.app.midi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.Atom2Universe.app.AudioFocusManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.sf2.Sf2Voice
import com.Atom2Universe.app.midi.sf2.VelocityCurve
import com.Atom2Universe.app.stats.StatsTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

/**
 * Service de playback MIDI en arrière-plan
 * Utilise MediaBrowserServiceCompat pour la compatibilité avec MediaSession
 * Gère: MidiSynthesizerManager (Sonivox/Sf2Engine), Queue, Notifications, Audio Focus
 *
 * Toutes les opérations sur les moteurs passent par un fil unique (« MidiEngine ») :
 * chargements de SoundFont et de MIDI hors du fil principal, exécutés dans l'ordre d'arrivée,
 * jamais deux à la fois.
 */
class MidiPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var synthesizerManager: MidiSynthesizerManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var queueManager: PlaybackQueueManager
    private lateinit var notificationManager: MidiNotificationManager
    private lateinit var audioManager: AudioManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val engineExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "MidiEngine") }
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()
    private val engineMutex = Mutex()
    @Volatile private var destroyed = false

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isForegroundService = false
    // Notification laissée en place après une longue pause (service sorti du premier plan)
    private var notificationDetached = false

    // Wake lock pour garder le CPU actif pendant la lecture (écran éteint)
    private var wakeLock: PowerManager.WakeLock? = null

    // État actuel
    @Volatile private var currentPlaybackState = PlaybackStateCompat.STATE_NONE

    // Publication vers MediaSession : l'état et la position ne doivent pas se croiser
    private val publishLock = Any()
    @Volatile private var lastPositionPublishMs = 0L

    // Position où reprendre un morceau après un changement de SoundFont ou de moteur
    // (le nouveau moteur repart sans fichier MIDI chargé). Liée au fichier pour ne pas
    // s'appliquer à un autre morceau.
    private var pendingResumeFilePath: String? = null
    private var pendingResumePositionMs: Long = 0L

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
                Intent.ACTION_SCREEN_OFF -> onEngine { synthesizerManager.setScreenOff(true) }
                Intent.ACTION_SCREEN_ON -> onEngine { synthesizerManager.setScreenOff(false) }
            }
        }
    }

    // Longue pause : le service quitte le premier plan (la notification reste)
    private val detachForegroundRunnable = Runnable {
        onEngine {
            if (currentPlaybackState == PlaybackStateCompat.STATE_PAUSED && isForegroundService) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForegroundService = false
                notificationDetached = true
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
            onEngine { synthesizerManager.setVolume(0.2f) }
        }

        override fun onAudioFocusUnduck() {
            val volume = currentVolume
            onEngine { synthesizerManager.setVolume(volume) }
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

        // Position publiée environ deux fois par seconde (chaque publication est un appel
        // inter-processus) : l'écran n'affiche que les secondes et ne la lit que là
        private const val POSITION_PUBLISH_INTERVAL_MS = 450L

        // Pause plus longue que ça : le service quitte le premier plan
        private const val FOREGROUND_DETACH_DELAY_MS = 5 * 60 * 1000L

        /** Vrai entre onCreate et onDestroy : le hub ne s'y lie que s'il tourne déjà. */
        @Volatile var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

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
        ).apply {
            // Non compté : chaque acquire() repart sur un nouveau délai, un seul release() suffit
            setReferenceCounted(false)
        }

        // Initialiser MediaSession
        setupMediaSession()

        // Setup callbacks
        setupCallbacks()

        // Enregistrer le BroadcastReceiver pour les commandes widget
        registerWidgetCommandReceiver()

        // Enregistrer le receiver pour l'état de l'écran
        registerScreenStateReceiver()

        // Appliquer l'état initial : si le service démarre avec l'écran déjà éteint,
        // activer immédiatement le mode séquentiel (isInteractive = false = écran éteint)
        if (!powerManager.isInteractive) {
            onEngine { synthesizerManager.setScreenOff(true) }
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
                onEngine { synthesizerManager.setEqualizerEnabled(enabled) }
                serviceScope.launch {
                    SettingsRepository(applicationContext).setMidiEqEnabled(enabled)
                }
            }
            ACTION_SET_EQ_BAND -> {
                val band = intent.getIntExtra(EXTRA_EQ_BAND, 0)
                val millibels = intent.getIntExtra(EXTRA_EQ_MILLIBELS, 0)
                onEngine { synthesizerManager.setEqualizerBandLevel(band, millibels) }
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
        destroyed = true
        isRunning = false
        mainHandler.removeCallbacks(detachForegroundRunnable)

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
            // Rendre l'audio focus, seulement s'il est encore à nous
            AudioFocusManager.abandonFocus(audioFocusListener)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: abandonFocus failed", e)
        }

        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: serviceScope.cancel failed", e)
        }

        // Les moteurs se libèrent sur leur fil, après l'opération en cours (jamais pendant
        // un chargement). Le fil s'arrête ensuite.
        try {
            engineExecutor.execute {
                try {
                    synthesizerManager.release()
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "onDestroy: synthesizerManager.release failed", e)
                }
            }
            engineExecutor.shutdown()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "onDestroy: engine thread shutdown failed", e)
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

    /**
     * Exécute [block] sur le fil des moteurs, après les opérations déjà demandées.
     * Utilisable depuis n'importe quel fil (boutons, notification, rappels des moteurs).
     */
    private fun onEngine(block: suspend () -> Unit) {
        if (destroyed) return
        serviceScope.launch(engineDispatcher) {
            engineMutex.withLock {
                if (destroyed) return@withLock
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Engine operation failed", e)
                }
            }
        }
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
                    // Vide = inconnu : le texte traduit est choisi à l'affichage
                    val trackTitle = extras.getString("track_${i}_title") ?: ""
                    val trackArtist = extras.getString("track_${i}_artist") ?: ""
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
                    val enableShuffle = extras.getBoolean("enable_shuffle", false)
                    onEngine {
                        // IMPORTANT: Stopper et réinitialiser l'état avant de changer de piste
                        synthesizerManager.stop()
                        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

                        queueManager.setQueue(tracks, startIndex)

                        // Vérifie si le shuffle doit être activé
                        if (enableShuffle) {
                            if (!queueManager.isShuffleEnabled()) {
                                queueManager.toggleShuffle()
                            }
                        }

                        doPlay()
                    }
                }
            } else {
                // Track unique (legacy)
                val trackId = extras.getLong("track_id", -1)
                val trackPath = extras.getString("track_path") ?: ""
                val trackTitle = extras.getString("track_title") ?: ""
                val trackArtist = extras.getString("track_artist") ?: ""

                if (trackId == -1L || trackPath.isEmpty()) {
                    return
                }

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

                onEngine {
                    // IMPORTANT: Stopper et réinitialiser l'état avant de changer de piste
                    synthesizerManager.stop()
                    currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

                    queueManager.setQueue(listOf(track), 0)
                    doPlay()
                }
            }
        }
    }

    // === Callbacks Setup ===

    private fun setupCallbacks() {
        // Synthesizer callbacks
        synthesizerManager.setOnStateChangeListener { _ ->
            // TODO: Mettre à jour MediaSession metadata
        }

        // Appelés depuis les fils des moteurs : on repasse par le fil des moteurs
        synthesizerManager.setOnCompletionListener {
            handleSkipNext() // Auto-play next
        }

        synthesizerManager.setOnErrorListener { _ ->
            onEngine { updatePlaybackState(PlaybackStateCompat.STATE_ERROR) }
        }

        // Position : publiée au plus deux fois par seconde, sans toucher à l'état de lecture
        synthesizerManager.setOnPositionChangedListener { positionMs, durationMs ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastPositionPublishMs >= POSITION_PUBLISH_INTERVAL_MS) {
                lastPositionPublishMs = now
                publishPosition(positionMs, durationMs)
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
        if (destroyed) return

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

        // Titre ou artiste vide = inconnu : texte traduit
        val title = track.title.ifBlank { getString(R.string.midi_engine_unknown_title) }
        val artist = track.artist.ifBlank { getString(R.string.midi_engine_unknown_artist) }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, track.filePath)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, actualDuration)
            .build()

        mediaSession.setMetadata(metadata)
    }

    // === Playback Actions ===
    // handleXxx() : point d'entrée depuis n'importe quel fil, met l'opération en file.
    // doXxx() : l'opération elle-même, sur le fil des moteurs.

    private fun handlePlay() = onEngine { doPlay() }

    private fun handlePause() = onEngine { doPause() }

    private fun handlePlayPause() = onEngine {
        // Décidé sur le fil des moteurs : l'état tient compte des opérations déjà en file
        when (currentPlaybackState) {
            PlaybackStateCompat.STATE_PLAYING -> doPause()
            else -> doPlay()
        }
    }

    private fun handleStop() = onEngine { doStop() }

    private fun handleSkipNext() = onEngine { doSkipNext() }

    private fun handleSkipPrevious() = onEngine { doSkipPrevious() }

    private fun handleSeekTo(positionMs: Long) = onEngine { doSeekTo(positionMs) }

    private fun handleToggleShuffle() = onEngine {
        queueManager.toggleShuffle()
        updatePlaybackState(currentPlaybackState)
    }

    private fun handleCycleRepeat() = onEngine {
        queueManager.cycleRepeatMode()
        updatePlaybackState(currentPlaybackState)
    }

    private fun handleSyncVisualizer() = onEngine {
        synthesizerManager.syncVisualizerToCurrentPosition()
    }

    private fun handleReloadSoundFont(
        soundFontPath: String,
        isHybridMode: Boolean = false,
        isFluidSynthMode: Boolean = false,
        hybridPrograms: Set<Int> = emptySet(),
        useSf2ForDrums: Boolean = false
    ) = onEngine {
        doReloadSoundFont(soundFontPath, isHybridMode, isFluidSynthMode, hybridPrograms, useSf2ForDrums)
    }

    private fun handleRefreshAfterPractice() = onEngine { doRefreshAfterPractice() }

    private suspend fun doPlay() {
        // Demander l'audio focus avant de démarrer/reprendre. AudioFocusManager est partagé
        // avec les autres lecteurs de l'appli, qui l'utilisent depuis le fil principal.
        withContext(Dispatchers.Main) {
            AudioFocusManager.requestFocus(audioFocusListener)
        }

        // Re-initialize synthesizer if it was released (e.g. after handleStop)
        if (synthesizerManager.needsReinitialization()) {
            initializeSynthesizer()
            if (synthesizerManager.needsReinitialization()) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }
        }

        // Si on était en pause, reprendre — seulement si le moteur est réellement en pause.
        // Après un changement de SoundFont ou de moteur, le nouveau moteur n'a pas de MIDI
        // chargé : « reprendre » ne ferait rien, il faut recharger le fichier (plus bas).
        if (currentPlaybackState == PlaybackStateCompat.STATE_PAUSED &&
            synthesizerManager.getState() == MidiEngine.State.PAUSED
        ) {
            try {
                synthesizerManager.resume()
                // Configure audio routing to speaker if USB MIDI is connected
                synthesizerManager.autoConfigureAudioOutput()
                playbackStartTimeMs = System.currentTimeMillis() - synthesizerManager.getCurrentPosition()
                StatsTracker.resumeMidiSession()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                acquireWakeLock()
                // Après une longue pause, le service n'est plus au premier plan
                startForegroundWithNotification()
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

                // Reprise à la position d'avant un changement de SoundFont ou de moteur
                val resumeAt = if (pendingResumeFilePath == currentTrack.filePath) pendingResumePositionMs else 0L
                pendingResumeFilePath = null
                pendingResumePositionMs = 0L
                if (resumeAt > 0L) {
                    synthesizerManager.seekTo(resumeAt)
                    playbackStartTimeMs = System.currentTimeMillis() - resumeAt
                }
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

    private fun doPause() {
        try {
            synthesizerManager.pause()
            StatsTracker.pauseMidiSession()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            releaseWakeLock()
            updateNotification()
            // Pause qui dure : le service quittera le premier plan
            mainHandler.removeCallbacks(detachForegroundRunnable)
            mainHandler.postDelayed(detachForegroundRunnable, FOREGROUND_DETACH_DELAY_MS)
        } catch (_: Exception) { }
    }

    private suspend fun doStop() {
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

            // Rendre l'audio focus s'il est encore à nous : une autre source (radio, musique)
            // a pu le prendre entre-temps, il ne faut pas le lui retirer
            withContext(Dispatchers.Main) {
                AudioFocusManager.abandonFocus(audioFocusListener)
                // Reset local audio focus state
                wasPlayingBeforeAudioFocusLoss = false
            }

            mainHandler.removeCallbacks(detachForegroundRunnable)
            if (isForegroundService) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundService = false
            } else if (notificationDetached) {
                val notifManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                notifManager.cancel(MidiNotificationManager.NOTIFICATION_ID)
            }
            notificationDetached = false

            stopSelf()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
    }

    private suspend fun doSkipNext() {
        val nextTrack = queueManager.skipToNext()

        if (nextTrack != null) {
            // IMPORTANT: Utiliser stopForTrackTransition() au lieu de stop()
            // Cela garde les streams audio Oboe actifs pour éviter les race conditions
            // et crashes SIGBUS lors de la transition entre pistes en background
            synthesizerManager.stopForTrackTransition()
            // Réinitialiser l'état pour que doPlay() charge le nouveau fichier
            currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
            doPlay()
        } else {
            doStop()
        }
    }

    private suspend fun doSkipPrevious() {
        // Seuil 3 secondes: si on a joué moins de 3s, passer au précédent
        // sinon, redémarrer le morceau actuel
        val elapsedMs = System.currentTimeMillis() - playbackStartTimeMs

        // IMPORTANT: Utiliser stopForTrackTransition() au lieu de stop()
        // Cela garde les streams audio Oboe actifs pour éviter les race conditions
        synthesizerManager.stopForTrackTransition()
        // Réinitialiser l'état pour que doPlay() charge le fichier
        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

        if (elapsedMs < PREVIOUS_THRESHOLD_MS) {
            // Passer au morceau précédent (sinon : redémarrer le morceau actuel)
            queueManager.skipToPrevious()
        }
        doPlay()
    }

    private suspend fun doReloadSoundFont(
        soundFontPath: String,
        isHybridMode: Boolean,
        isFluidSynthMode: Boolean,
        hybridPrograms: Set<Int>,
        useSf2ForDrums: Boolean
    ) {
        android.util.Log.i(TAG, "handleReloadSoundFont: path=$soundFontPath, isFluidSynth=$isFluidSynthMode, isHybrid=$isHybridMode")

        // Mémoriser l'état de lecture : le nouveau moteur repartira sans fichier MIDI chargé
        val wasPlaying = currentPlaybackState == PlaybackStateCompat.STATE_PLAYING
        val wasPaused = currentPlaybackState == PlaybackStateCompat.STATE_PAUSED
        val currentTrack = queueManager.getCurrentTrack()
        val resumePositionMs = if (wasPlaying || wasPaused) synthesizerManager.getCurrentPosition() else 0L

        // Stop current playback
        synthesizerManager.stop()

        // Configure operating mode
        val selectedMode: MidiSynthesizerManager.OperatingMode
        if (isFluidSynthMode && soundFontPath.isNotBlank()) {
            selectedMode = MidiSynthesizerManager.OperatingMode.FLUIDSYNTH_ONLY
            synthesizerManager.setOperatingMode(selectedMode)
        } else if (isHybridMode && soundFontPath.isNotBlank() && hybridPrograms.isNotEmpty()) {
            selectedMode = MidiSynthesizerManager.OperatingMode.HYBRID
            synthesizerManager.setOperatingMode(
                selectedMode, hybridPrograms, useSf2ForDrums, isHybridOnFluidSynth()
            )
        } else if (soundFontPath.isNotBlank()) {
            selectedMode = MidiSynthesizerManager.OperatingMode.SF2_ONLY
            synthesizerManager.setOperatingMode(selectedMode)
        } else {
            selectedMode = MidiSynthesizerManager.OperatingMode.SONIVOX_ONLY
            synthesizerManager.setOperatingMode(selectedMode)
        }
        android.util.Log.i(TAG, "handleReloadSoundFont: selectedMode=$selectedMode")

        // Reload with new configuration
        val success = reloadSoundFont(soundFontPath)
        android.util.Log.i(TAG, "handleReloadSoundFont: reload success=$success")

        // L'ancien état (lecture / pause) ne vaut plus pour le nouveau moteur : sans ce
        // retour à STOPPED, le bouton Lecture envoyait « pause » puis « reprendre » à un
        // moteur sans fichier chargé, et rien ne se passait.
        if (wasPlaying || wasPaused) {
            pendingResumeFilePath = currentTrack?.filePath
            pendingResumePositionMs = resumePositionMs
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, positionMs = resumePositionMs)
            if (wasPlaying) {
                // La lecture continue avec le nouveau son, au même endroit
                doPlay()
            } else {
                releaseWakeLock()
            }
        }

        // Broadcast that SF2 loading is complete
        val broadcastIntent = Intent(ACTION_SF2_LOAD_COMPLETE).apply {
            setPackage(packageName)
            putExtra(EXTRA_SF2_LOAD_SUCCESS, success)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun doSeekTo(positionMs: Long) {
        try {
            synthesizerManager.seekTo(positionMs)
            // Mettre à jour le temps de départ pour le seuil Previous
            playbackStartTimeMs = System.currentTimeMillis() - positionMs
            updatePlaybackState(currentPlaybackState, positionMs)
        } catch (_: Exception) { }
    }

    private suspend fun doRefreshAfterPractice() {
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
                            useSf2ForDrums,
                            isHybridOnFluidSynth()
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }
    }

    // === State Management ===

    private fun buildPlaybackState(state: Int, positionMs: Long, durationMs: Long): PlaybackStateCompat {
        // Inclure l'état shuffle/repeat et position/durée dans les extras
        val extras = Bundle().apply {
            putBoolean(EXTRA_SHUFFLE_ENABLED, queueManager.isShuffleEnabled())
            putInt(EXTRA_REPEAT_MODE, queueManager.getRepeatMode().ordinal)
            putLong(EXTRA_POSITION_MS, positionMs)
            putLong(EXTRA_DURATION_MS, durationMs)
        }

        return PlaybackStateCompat.Builder()
            .setState(state, positionMs, 1.0f)
            .setActions(getAvailableActions())
            .setExtras(extras)
            .build()
    }

    private fun updatePlaybackState(
        state: Int,
        positionMs: Long? = null,
        durationMs: Long? = null
    ) {
        if (destroyed) return

        val currentPosition = positionMs ?: synthesizerManager.getCurrentPosition()
        val duration = durationMs?.takeIf { it > 0 } ?: synthesizerManager.getDuration()

        val stateChanged = synchronized(publishLock) {
            val changed = currentPlaybackState != state
            currentPlaybackState = state
            mediaSession.setPlaybackState(buildPlaybackState(state, currentPosition, duration))
            lastPositionPublishMs = SystemClock.elapsedRealtime()
            changed
        }

        // Mettre à jour l'état partagé pour le widget (notifié seulement s'il change vraiment)
        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        val track = queueManager.getCurrentTrack()
        val hasNext = queueManager.hasNext()
        val hasPrevious = queueManager.hasPrevious()
        MidiPlaybackState.updateState(this, track, isPlaying, hasNext, hasPrevious)

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

    /**
     * Publie la position pendant la lecture, sans changer l'état (appelé depuis les fils des moteurs).
     */
    private fun publishPosition(positionMs: Long, durationMs: Long) {
        if (destroyed) return
        val duration = durationMs.takeIf { it > 0 } ?: synthesizerManager.getDuration()
        synchronized(publishLock) {
            // Une pause ou un arrêt publié entre-temps l'emporte
            if (currentPlaybackState != PlaybackStateCompat.STATE_PLAYING) return
            mediaSession.setPlaybackState(
                buildPlaybackState(PlaybackStateCompat.STATE_PLAYING, positionMs, duration)
            )
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
        mainHandler.removeCallbacks(detachForegroundRunnable)
        if (destroyed) return

        if (!isForegroundService) {
            val notification = notificationManager.buildNotification(
                queueManager.getCurrentTrack(),
                currentPlaybackState,
                mediaSession.sessionToken
            )

            try {
                startForeground(MidiNotificationManager.NOTIFICATION_ID, notification)
                isForegroundService = true
                notificationDetached = false
            } catch (e: Exception) {
                // Android 12+ peut refuser le premier plan depuis l'arrière-plan (reprise après
                // une longue pause) : la lecture continue, la notification est mise à jour
                android.util.Log.w(TAG, "startForeground refused", e)
                updateNotification()
            }
        } else {
            updateNotification()
        }
    }

    private fun updateNotification() {
        if (destroyed) return
        if (isForegroundService || notificationDetached) {
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
        onEngine {
            queueManager.setQueue(tracks, startIndex)
            doPlay()
        }
    }

    /**
     * Ajoute un track à la queue
     */
    @Suppress("unused")
    fun addToQueue(track: MidiTrack) {
        onEngine { queueManager.addToQueue(track) }
    }

    /**
     * Récupère le gestionnaire de queue
     */
    @Suppress("unused")
    fun getQueueManager(): PlaybackQueueManager {
        return queueManager
    }

    /**
     * Le moteur à SoundFont du mode hybride est-il FluidSynth ? (réglage du dialogue des synthétiseurs)
     */
    private suspend fun isHybridOnFluidSynth(): Boolean {
        return SettingsRepository(applicationContext).getHybridBaseEngine() ==
            SettingsRepository.SYNTH_MODE_FLUIDSYNTH
    }

    /**
     * Initializes the synthesizer based on current settings.
     * Supports Sonivox, SF2, FluidSynth and Hybrid modes.
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
                        useSf2ForDrums,
                        isHybridOnFluidSynth()
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
     * Lecture des réglages sur IO, application au moteur sur le fil des moteurs.
     */
    private suspend fun loadSf2Settings() {
        val reverbPreset = withContext(Dispatchers.IO) {
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
                settingsRepository.getReverbPreset()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        reverbPreset?.let { synthesizerManager.setReverb(it) }
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
     * Recharge le SoundFont (sur le fil des moteurs)
     *
     * @param soundFontPath Chemin vers le nouveau fichier SF2, ou chaîne vide pour Sonivox
     * @return true si le rechargement a réussi, false sinon
     */
    private fun reloadSoundFont(soundFontPath: String): Boolean {
        return synthesizerManager.reloadSoundFont(soundFontPath)
    }

    /**
     * Sets the reverb preset.
     * @param preset -1 = Off, 0 = Large Hall, 1 = Hall, 2 = Chamber, 3 = Room
     */
    fun setReverb(preset: Int) {
        onEngine { synthesizerManager.setReverb(preset) }
    }

    /**
     * Sets chorus preset.
     * @param preset -1 = Off, 0 = Light, 1 = Default, 2 = Rich
     */
    @Suppress("unused")
    fun setChorus(preset: Int) {
        onEngine { synthesizerManager.setChorus(preset) }
    }

    // === Wake Lock Management ===

    /**
     * Acquiert le wake lock pour garder le CPU actif pendant la lecture.
     * Nécessaire pour que les tâches planifiées (ex: onCompletion) s'exécutent
     * même quand l'écran est éteint ou l'app en arrière-plan.
     */
    private fun acquireWakeLock() {
        try {
            // Timeout de 30 minutes pour éviter les fuites de batterie. Appelé à chaque
            // morceau et à chaque reprise : le délai repart de zéro (verrou non compté),
            // une longue file d'attente n'est donc pas coupée au bout de 30 minutes.
            wakeLock?.acquire(30 * 60 * 1000L)
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
