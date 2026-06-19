package com.Atom2Universe.app.music

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import com.Atom2Universe.app.AudioFocusManager
import com.Atom2Universe.app.AudioPlaybackManager
import com.Atom2Universe.app.music.equalizer.MusicEqualizerManager
import com.Atom2Universe.app.music.equalizer.dsp.DspEqualizerProcessor
import com.Atom2Universe.app.music.equalizer.dsp.DspRenderersFactory
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.widget.MusicWidgetController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

object MusicPlaybackHolder {

    private const val TAG = "MusicPlaybackHolder"

    private var player: ExoPlayer? = null
    // Listener ExoPlayer stocké pour pouvoir être retiré dans release()
    private var exoPlayerListener: Player.Listener? = null
    // CopyOnWriteArrayList pour éviter ConcurrentModificationException lors de l'itération
    private val listeners = CopyOnWriteArrayList<PlayerListener>()
    private val handler = Handler(Looper.getMainLooper())

    // DSP Equalizer processor - processes audio samples directly
    private var _equalizerProcessor: DspEqualizerProcessor? = null

    /**
     * Get the DSP equalizer processor for connecting to MusicEqualizerManager.
     * Returns null if player hasn't been created yet.
     */
    val equalizerProcessor: DspEqualizerProcessor?
        get() = _equalizerProcessor

    // Référence au contexte pour la persistance de la queue
    private var appContext: Context? = null

    // Lock pour synchroniser les opérations sur la playlist
    private val playlistLock = Any()

    @Volatile
    private var playlist: List<MusicTrack> = emptyList()
    @Volatile
    private var currentIndex: Int = -1

    // ID de la playlist en cours de lecture (null si lecture hors playlist)
    var currentPlaylistId: String? = null
        private set

    // Mode édition de playlist : quand activé, les modifications de la queue sont synchronisées
    var isPlaylistEditModeEnabled: Boolean = false
        private set

    var shuffleEnabled: Boolean = false
        private set

    // File de lecture mélangée : indices dans playlist dans l'ordre aléatoire.
    // shuffleQueuePosition pointe sur la piste courante (0 = première jouée).
    private var shuffleQueue: List<Int> = emptyList()
    private var shuffleQueuePosition: Int = 0

    var repeatMode: RepeatMode = RepeatMode.OFF
        private set

    // Play count tracking - incrémente à 50% de la durée
    // Bug fix: Utilise AtomicBoolean pour garantir l'atomicité du check-then-set
    private val playCountIncrementedForCurrentTrack = java.util.concurrent.atomic.AtomicBoolean(false)

    // CoroutineScope avec SupervisorJob pour permettre la cancellation
    private var supervisorJob = SupervisorJob()
    private var coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    enum class RepeatMode {
        OFF, ONE, ALL
    }

    // Audio focus handling
    private var wasPlayingBeforeAudioFocusLoss = false
    private var originalVolume = 1.0f

    private val audioFocusListener = object : AudioFocusManager.AudioFocusListener {
        override fun onAudioFocusPause() {
            val ctx = appContext ?: return
            wasPlayingBeforeAudioFocusLoss = player?.isPlaying == true
            if (wasPlayingBeforeAudioFocusLoss) {
                pause(ctx)
            }
        }

        override fun onAudioFocusResume() {
            // Bug 5.22: Ne pas reprendre automatiquement après une interruption audio.
            // L'utilisateur doit explicitement appuyer sur play pour reprendre.
            // On reset juste le flag pour que la prochaine lecture fonctionne normalement.
            wasPlayingBeforeAudioFocusLoss = false
        }

        override fun onAudioFocusDuck() {
            player?.let {
                originalVolume = it.volume
                it.volume = 0.2f
            }
        }

        override fun onAudioFocusUnduck() {
            player?.volume = originalVolume
        }
    }

    interface PlayerListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onTrackChanged(track: MusicTrack?)
        fun onProgressChanged(position: Long, duration: Long)
        fun onError(error: PlaybackException)
        fun onPlaylistChanged(playlist: List<MusicTrack>)
        fun onPlayCountIncremented(track: MusicTrack, newCount: Long)
    }

    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        // Stocker le contexte pour la persistance
        if (appContext == null) {
            appContext = context.applicationContext
        }

        if (player == null) {
            // Create DSP equalizer processor if not already created
            if (_equalizerProcessor == null) {
                _equalizerProcessor = DspEqualizerProcessor()
                Log.d(TAG, "Created DSP Equalizer Processor")
            }

            val dspProcessor = _equalizerProcessor!!

            // Create custom RenderersFactory with DSP audio processing
            val renderersFactory = DspRenderersFactory(context.applicationContext, dspProcessor)

            // Custom LoadControl with reduced audio buffer for faster EQ response
            // Default max buffer is 50s, we reduce to 10s
            // Default buffer for playback is 2.5s, we reduce to 1s
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    1000,   // minBufferMs (default 50000)
                    10000,  // maxBufferMs (default 50000)
                    500,    // bufferForPlaybackMs (default 2500)
                    1000    // bufferForPlaybackAfterRebufferMs (default 5000)
                )
                .build()

            // Créer le listener et le stocker pour pouvoir le retirer dans release()
            exoPlayerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            handleTrackEnded(context)
                        }
                        Player.STATE_READY -> {
                            notifyPlaybackStateChanged(player?.isPlaying == true)
                        }
                        Player.STATE_BUFFERING, Player.STATE_IDLE -> {
                            // États intermédiaires - pas d'action requise
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    notifyPlaybackStateChanged(isPlaying)
                    if (isPlaying) {
                        startProgressUpdates()
                        startForegroundService(context)
                        StatsTracker.resumeMusicSession()
                    } else {
                        stopProgressUpdates()
                        StatsTracker.pauseMusicSession()
                    }
                    updateForegroundService(context)
                }

                override fun onPlayerError(error: PlaybackException) {
                    listeners.forEach { it.onError(error) }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Note: notifyTrackChanged est déjà appelé dans playCurrentTrack()
                    // Ce callback est utile pour les transitions automatiques (fin de track avec repeat)
                    // mais on évite les doubles appels en vérifiant si le track a vraiment changé
                    if (getCurrentTrack() == null) return
                    // Le callback est parfois appelé juste après playCurrentTrack avec le même track
                    // On ignore ces appels redondants

                    // Reset DSP filter states on track change to avoid audio artifacts
                    dspProcessor.resetFilters()
                }
            }

            player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_LOCAL)  // Maintient le CPU actif pendant la lecture (écran éteint)
                .build().apply {
                addListener(exoPlayerListener!!)
                // Désactiver la gestion du focus audio interne d'ExoPlayer :
                // AudioFocusManager est la seule source de vérité pour le focus audio.
                // Sans ça, ExoPlayer reprend tout seul la lecture quand il reçoit AUDIOFOCUS_GAIN,
                // même pendant un appel téléphonique (bug de reprise intempestive).
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false  // handleAudioFocus = false
                )
            }

            Log.d(TAG, "ExoPlayer created with DSP Equalizer Processor")

            // Initialize and attach equalizer immediately so it works without opening the EQ UI
            MusicEqualizerManager.initialize(context.applicationContext)
            MusicEqualizerManager.attachToProcessor(dspProcessor)
            Log.d(TAG, "Equalizer attached to DSP processor")

            // Also attach to audio session for standard Android AudioEffect API (virtualizer fallback)
            val audioSessionId = player?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                MusicEqualizerManager.attachToAudioSession(audioSessionId)
                Log.d(TAG, "Equalizer attached to audio session $audioSessionId")
            }
        }
        return player ?: throw IllegalStateException("Player should not be null after initialization")
    }

    fun setPlaylist(tracks: List<MusicTrack>, playlistId: String? = null) {
        synchronized(playlistLock) {
            playlist = tracks.toList()
            currentIndex = -1
            currentPlaylistId = playlistId
            shuffleQueue = emptyList()
            shuffleQueuePosition = 0
        }
        listeners.forEach { it.onPlaylistChanged(tracks) }
        saveQueueState()
    }

    /**
     * Définit l'ID de la playlist en cours (utilisé après création d'une playlist depuis la queue)
     */
    fun setCurrentPlaylistId(playlistId: String?) {
        currentPlaylistId = playlistId
        saveQueueState()
    }

    /**
     * Active/désactive le mode édition de playlist
     */
    fun togglePlaylistEditMode(): Boolean {
        isPlaylistEditModeEnabled = !isPlaylistEditModeEnabled
        return isPlaylistEditModeEnabled
    }

    /**
     * Définit le mode édition de playlist
     */
    @Suppress("unused")
    fun setPlaylistEditMode(enabled: Boolean) {
        isPlaylistEditModeEnabled = enabled
    }

    /**
     * Sauvegarde l'état actuel de la queue pour persistence.
     * Permet au widget de reprendre la lecture après redémarrage.
     */
    private fun saveQueueState() {
        val ctx = appContext ?: return
        if (playlist.isEmpty()) {
            MusicQueuePersistence.clearQueue(ctx)
            return
        }
        MusicQueuePersistence.saveQueue(
            ctx,
            playlist,
            currentIndex,
            player?.currentPosition ?: 0,
            shuffleEnabled,
            repeatMode,
            currentPlaylistId
        )
    }

    /**
     * Met à jour l'ordre de la playlist tout en préservant la piste en cours de lecture.
     * Utilisé après un réarrangement par drag & drop.
     */
    fun updatePlaylistOrder(tracks: List<MusicTrack>) {
        val updatedPlaylist: List<MusicTrack>
        synchronized(playlistLock) {
            val currentTrack = getCurrentTrack()
            playlist = tracks.toList()
            updatedPlaylist = playlist

            // Retrouver l'index de la piste en cours dans la nouvelle liste
            currentIndex = if (currentTrack != null) {
                playlist.indexOfFirst { it.id == currentTrack.id }
            } else {
                -1
            }
        }
        listeners.forEach { it.onPlaylistChanged(updatedPlaylist) }
        saveQueueState()
    }

    /**
     * Retire un track de la playlist en conservant un état de lecture cohérent.
     * Retourne true si le track était présent et a été retiré.
     */
    fun removeTrackFromQueue(context: Context, track: MusicTrack): Boolean {
        val updatedPlaylist: List<MusicTrack>
        val shouldStop: Boolean
        val shouldPlay: Boolean
        val trackToNotify: MusicTrack?

        synchronized(playlistLock) {
            val index = playlist.indexOfFirst { it.id == track.id }
            if (index == -1) return false

            val wasCurrent = index == currentIndex
            val wasActive = player?.isPlaying == true || player?.playWhenReady == true

            val mutablePlaylist = playlist.toMutableList()
            mutablePlaylist.removeAt(index)
            playlist = mutablePlaylist
            updatedPlaylist = playlist

            shouldStop = wasCurrent && playlist.isEmpty()
            shouldPlay = wasCurrent && !playlist.isEmpty() && wasActive
            trackToNotify = if (wasCurrent && !playlist.isEmpty() && !wasActive) {
                playlist.getOrNull(index.coerceAtMost(playlist.size - 1))
            } else null

            when {
                index < currentIndex -> currentIndex--
                wasCurrent -> {
                    if (playlist.isEmpty()) {
                        currentIndex = -1
                    } else {
                        currentIndex = index.coerceAtMost(playlist.size - 1)
                    }
                }
            }
        }

        if (shouldStop) {
            stop(context)
        } else if (shouldPlay) {
            playCurrentTrack(context)
        } else if (trackToNotify != null) {
            notifyTrackChanged(trackToNotify)
            updateForegroundService(context)
        }

        listeners.forEach { it.onPlaylistChanged(updatedPlaylist) }
        saveQueueState()
        return true
    }

    /**
     * Adds a track to play immediately after the current one
     */
    fun playNext(track: MusicTrack) {
        val updatedPlaylist: List<MusicTrack>
        val wasEmpty: Boolean
        val notifyTrack: Boolean

        synchronized(playlistLock) {
            wasEmpty = playlist.isEmpty()

            if (wasEmpty) {
                playlist = listOf(track)
                currentIndex = 0
                updatedPlaylist = playlist
                notifyTrack = true
            } else {
                val mutablePlaylist = playlist.toMutableList()
                // Insert after current track
                val insertIndex = (currentIndex + 1).coerceIn(0, mutablePlaylist.size)
                mutablePlaylist.add(insertIndex, track)
                playlist = mutablePlaylist
                updatedPlaylist = playlist
                notifyTrack = false
            }
        }

        listeners.forEach { it.onPlaylistChanged(updatedPlaylist) }
        if (notifyTrack) {
            listeners.forEach { it.onTrackChanged(track) }
        }
        saveQueueState()
    }

    /**
     * Adds a track at the end of the queue
     */
    fun addToQueue(track: MusicTrack) {
        val updatedPlaylist: List<MusicTrack>
        val wasEmpty: Boolean

        synchronized(playlistLock) {
            wasEmpty = playlist.isEmpty()

            val mutablePlaylist = playlist.toMutableList()
            mutablePlaylist.add(track)
            playlist = mutablePlaylist
            updatedPlaylist = playlist

            if (wasEmpty) {
                currentIndex = 0
            }
        }

        if (wasEmpty) {
            listeners.forEach { it.onTrackChanged(track) }
        }
        listeners.forEach { it.onPlaylistChanged(updatedPlaylist) }
        saveQueueState()
    }

    @OptIn(UnstableApi::class)
    fun play(context: Context, track: MusicTrack) {
        val index = playlist.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            currentIndex = index
            if (shuffleEnabled) buildShuffleQueue()
            playCurrentTrack(context)
        } else {
            // Bug 5.15: Log warning and notify listeners when track not found in playlist
            Log.w(TAG, "play() called but track not found in playlist: ${track.title} (id=${track.id})")
            val error = PlaybackException(
                "Track not found in playlist: ${track.title}",
                null,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            )
            listeners.forEach { listener ->
                try {
                    listener.onError(error)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener of track not found", e)
                }
            }
        }
    }

    fun playAtIndex(context: Context, index: Int) {
        if (index in playlist.indices) {
            currentIndex = index
            if (shuffleEnabled) buildShuffleQueue()
            playCurrentTrack(context)
        }
    }

    @OptIn(UnstableApi::class)
    private fun playCurrentTrack(context: Context) {
        val track = playlist.getOrNull(currentIndex) ?: return

        // Vérifier que le fichier existe et est accessible avant de commencer
        val filePath = track.filePath
        if (filePath != null) {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                val error = PlaybackException(
                    "File not found: ${track.title}",
                    null,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                )
                listeners.forEach { listener ->
                    try {
                        listener.onError(error)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error notifying listener of file not found", e)
                    }
                }
                return
            }
            if (!file.canRead()) {
                Log.e(TAG, "File not readable: $filePath")
                val error = PlaybackException(
                    "Cannot read file: ${track.title}",
                    null,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                )
                listeners.forEach { listener ->
                    try {
                        listener.onError(error)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error notifying listener of file not readable", e)
                    }
                }
                return
            }
        }

        // Demander l'audio focus avant de démarrer la lecture.
        // Si le focus est refusé (appel téléphonique en cours, etc.), on n'initialise pas.
        if (!AudioFocusManager.requestFocus(audioFocusListener)) return

        val p = getOrCreatePlayer(context)

        try {
            val mediaItem = MediaItem.fromUri(track.playbackUri)
            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true

            notifyTrackChanged(track)
            startForegroundService(context)

            // Démarrer le tracking des statistiques
            StatsTracker.startMusicSession(
                trackTitle = track.title,
                trackArtist = track.artist,
                trackAlbum = track.album,
                trackAlbumArtist = track.albumArtist
            )

            // Sauvegarder immédiatement pour que le widget puisse reprendre
            saveQueueState()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${track.title}", e)
            // Notifier les listeners de l'erreur
            val error = PlaybackException(
                "Cannot play track: ${e.message}",
                e,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            )
            listeners.forEach { listener ->
                try {
                    listener.onError(error)
                } catch (ex: Exception) {
                    Log.e(TAG, "Error notifying listener of playback error", ex)
                }
            }
        }
    }

    fun pause(context: Context) {
        // S'assurer que le contexte est disponible pour la sauvegarde
        if (appContext == null) {
            appContext = context.applicationContext
        }
        player?.pause()
        // Sauvegarder l'état pour pouvoir reprendre après kill de l'app
        saveQueueState()
    }

    fun resume(context: Context) {
        // S'assurer que le contexte est disponible
        if (appContext == null) {
            appContext = context.applicationContext
        }

        // Demander l'audio focus avant de reprendre.
        // Si le focus est refusé (appel téléphonique en cours, etc.), on ne reprend pas.
        if (!AudioFocusManager.requestFocus(audioFocusListener)) return

        // Si le player est null mais qu'on a une queue chargée, initialiser le player
        if (player == null && playlist.isNotEmpty() && currentIndex >= 0) {
            val track = playlist.getOrNull(currentIndex) ?: return
            val p = getOrCreatePlayer(context)
            val mediaItem = MediaItem.fromUri(track.playbackUri)
            p.setMediaItem(mediaItem)
            p.prepare()

            // Restaurer la position si disponible
            val savedQueue = MusicQueuePersistence.loadQueue(context)
            if (savedQueue != null && savedQueue.positionMs > 0) {
                p.seekTo(savedQueue.positionMs)
            }

            p.playWhenReady = true
            notifyTrackChanged(track)
            startForegroundService(context)
            return
        }

        player?.let {
            if (!it.isPlaying) {
                it.play()
                startForegroundService(context)
            }
        }
    }

    fun togglePlayPause(context: Context) {
        // S'assurer que le contexte est disponible
        if (appContext == null) {
            appContext = context.applicationContext
        }

        val p = player
        if (p != null) {
            if (p.isPlaying) {
                pause(context)
            } else {
                resume(context)
            }
        } else if (playlist.isNotEmpty() && currentIndex >= 0) {
            // Player null mais queue chargée - démarrer la lecture
            resume(context)
        }
    }

    fun stop(context: Context) {
        try {
            player?.stop()
            player?.clearMediaItems()
            stopForegroundService(context)
            stopProgressUpdates()
        } finally {
            // Garantir que ces opérations de nettoyage sont toujours effectuées
            // même si une exception se produit ci-dessus

            // Terminer le tracking de la session de musique
            StatsTracker.endMusicSession()

            // Abandonner l'audio focus
            AudioFocusManager.abandonFocus()

            // Désenregistre la source MUSIC pour permettre à d'autres sources de jouer
            AudioPlaybackManager.unregisterPlayback(AudioPlaybackManager.AudioSource.MUSIC)

            // Plus aucun fichier en lecture, permet au sync manager de traiter les modifications en attente
            MusicPopmSyncManager.setCurrentlyPlayingFile(null)
            com.Atom2Universe.app.music.lyrics.LyricsManager.setCurrentlyPlayingFile(null)
        }
    }

    private fun buildShuffleQueue() {
        if (playlist.isEmpty()) {
            shuffleQueue = emptyList()
            shuffleQueuePosition = 0
            return
        }
        val current = currentIndex
        val indices = playlist.indices.toMutableList()
        indices.shuffle()
        // La piste courante est placée en position 0 (déjà jouée)
        if (current in playlist.indices) {
            val pos = indices.indexOf(current)
            if (pos != 0) {
                indices.removeAt(pos)
                indices.add(0, current)
            }
        }
        shuffleQueue = indices
        shuffleQueuePosition = 0
    }

    fun skipToNext(context: Context) {
        if (playlist.isEmpty()) return

        val nextIndex = if (shuffleEnabled) {
            if (playlist.size == 1) {
                currentIndex.coerceIn(0, playlist.lastIndex)
            } else {
                // Reconstruit la file si elle est désynchronisée (ex: playlist modifiée)
                if (shuffleQueue.size != playlist.size) buildShuffleQueue()

                val nextPos = shuffleQueuePosition + 1
                when {
                    nextPos < shuffleQueue.size -> {
                        shuffleQueuePosition = nextPos
                        shuffleQueue[nextPos]
                    }
                    repeatMode == RepeatMode.ALL -> {
                        // Tous les titres joués → on re-mélange pour le tour suivant
                        buildShuffleQueue()
                        shuffleQueuePosition = minOf(1, shuffleQueue.lastIndex)
                        shuffleQueue[shuffleQueuePosition]
                    }
                    else -> {
                        // RepeatMode.OFF : tous les titres ont été joués, on s'arrête
                        stop(context)
                        return
                    }
                }
            }
        } else {
            when (repeatMode) {
                RepeatMode.ONE -> currentIndex
                RepeatMode.ALL -> (currentIndex + 1) % playlist.size
                RepeatMode.OFF -> {
                    val next = currentIndex + 1
                    if (next >= playlist.size) {
                        stop(context)
                        return
                    }
                    next
                }
            }
        }

        currentIndex = nextIndex
        playCurrentTrack(context)
    }

    fun skipToPrevious(context: Context) {
        if (playlist.isEmpty()) return

        player?.let {
            if (it.currentPosition > 3000) {
                // Réinitialiser le tracking pour permettre un nouveau comptage à 50%
                resetPlayCountTracking()
                it.seekTo(0)
                return
            }
        }

        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else if (repeatMode == RepeatMode.ALL) {
            playlist.size - 1
        } else {
            0
        }

        currentIndex = prevIndex
        playCurrentTrack(context)
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        // Reset DSP filter states on seek to avoid audio artifacts
        _equalizerProcessor?.resetFilters()

        // Si on revient au début du morceau (< 10% ou < 5 secondes),
        // réinitialiser le tracking pour permettre un nouveau comptage à 50%
        if (playCountIncrementedForCurrentTrack.get()) {
            val duration = player?.duration ?: 0L
            val threshold = if (duration > 0) {
                minOf(duration / 10, 5000L)  // 10% de la durée ou 5 secondes max
            } else {
                5000L  // 5 secondes par défaut
            }
            if (position < threshold) {
                Log.d(TAG, "Seek to beginning detected, resetting play count tracking")
                resetPlayCountTracking()
            }
        }
    }

    @Suppress("unused")
    fun seekForward(ms: Long = 10000) {
        player?.let {
            val newPosition = (it.currentPosition + ms).coerceAtMost(it.duration)
            seekTo(newPosition)
        }
    }

    @Suppress("unused")
    fun seekBackward(ms: Long = 10000) {
        player?.let {
            val newPosition = (it.currentPosition - ms).coerceAtLeast(0)
            seekTo(newPosition)
        }
    }

    fun toggleShuffle(): Boolean {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled) buildShuffleQueue()
        saveQueueState()
        return shuffleEnabled
    }

    fun cycleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        saveQueueState()
        return repeatMode
    }

    private fun handleTrackEnded(context: Context) {
        when (repeatMode) {
            RepeatMode.ONE -> {
                // Réinitialiser le tracking pour compter une nouvelle écoute
                resetPlayCountTracking()
                player?.seekTo(0)
                player?.play()
            }
            RepeatMode.ALL -> {
                skipToNext(context)
            }
            RepeatMode.OFF -> {
                if (currentIndex < playlist.size - 1) {
                    skipToNext(context)
                } else {
                    stop(context)
                }
            }
        }
    }

    fun getCurrentTrack(): MusicTrack? {
        synchronized(playlistLock) {
            return playlist.getOrNull(currentIndex)
        }
    }

    fun getPlaylist(): List<MusicTrack> {
        synchronized(playlistLock) {
            return playlist.toList()
        }
    }

    /**
     * Met à jour les tracks dans la playlist quand leurs IDs changent (après édition de tags).
     * Match les tracks par filePath car c'est la seule propriété stable.
     * Bug fix: Ajout de synchronized(playlistLock) pour éviter les race conditions.
     */
    fun updateTracksInPlaylist(updates: List<Pair<MusicTrack, MusicTrack>>) {
        if (updates.isEmpty()) return

        // Crée une map filePath -> nouveau track
        val updatesByPath = updates.mapNotNull { (old, new) ->
            old.filePath?.let { it to new }
        }.toMap()

        if (updatesByPath.isEmpty()) return

        val updatedPlaylist: List<MusicTrack>
        synchronized(playlistLock) {
            if (playlist.isEmpty()) return

            // Met à jour la playlist
            playlist = playlist.map { track ->
                track.filePath?.let { path -> updatesByPath[path] } ?: track
            }
            updatedPlaylist = playlist
        }

        // Notifie les listeners si la playlist a changé (hors du lock pour éviter deadlock)
        listeners.forEach { it.onPlaylistChanged(updatedPlaylist) }
    }

    fun getCurrentIndex(): Int = currentIndex

    /**
     * Retourne le prochain track dans la queue selon le mode de lecture actuel.
     * Retourne null en mode shuffle (imprévisible) ou s'il n'y a pas de suivant.
     */
    fun getNextTrack(): MusicTrack? {
        synchronized(playlistLock) {
            if (playlist.isEmpty() || currentIndex < 0) return null
            return when {
                shuffleEnabled -> null
                repeatMode == RepeatMode.ONE -> playlist.getOrNull(currentIndex)
                repeatMode == RepeatMode.ALL -> playlist.getOrNull((currentIndex + 1) % playlist.size)
                else -> playlist.getOrNull(currentIndex + 1)
            }
        }
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun getPosition(): Long = player?.currentPosition ?: 0

    fun getDuration(): Long = player?.duration ?: 0

    @OptIn(UnstableApi::class)
    fun getAudioSessionId(): Int = player?.audioSessionId ?: 0

    @Suppress("unused")
    val repeatEnabled: Boolean
        get() = repeatMode != RepeatMode.OFF

    fun toggleRepeat() {
        cycleRepeatMode()
    }

    fun addListener(listener: PlayerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: PlayerListener) {
        listeners.remove(listener)
    }

    private fun notifyPlaybackStateChanged(isPlaying: Boolean) {
        listeners.forEach { listener ->
            try {
                listener.onPlaybackStateChanged(isPlaying)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener of playback state change", e)
            }
        }
        // Notifier le widget
        appContext?.let { MusicWidgetController.notifyStateChanged(it) }
    }

    private fun notifyTrackChanged(track: MusicTrack) {
        Log.d(TAG, "notifyTrackChanged: trackId=${track.id}, title='${track.title}'")

        // Réinitialise le tracking du play count pour le nouveau track
        resetPlayCountTracking()

        // Notifie le sync manager du fichier actuellement en lecture
        MusicPopmSyncManager.setCurrentlyPlayingFile(track.filePath)
        com.Atom2Universe.app.music.lyrics.LyricsManager.setCurrentlyPlayingFile(track.filePath)

        // Notifie l'égaliseur du changement de piste pour appliquer le preset approprié
        // (preset global ou override par piste/album/artiste)
        Log.d(TAG, "notifyTrackChanged: Calling MusicEqualizerManager.onTrackChanged")
        MusicEqualizerManager.onTrackChanged(track)

        // L'auto-fetch des paroles est maintenant géré par FullPlayerActivity
        // via loadAndShowLyrics() pour éviter les recherches en double et afficher
        // les résultats immédiatement quand ils arrivent

        listeners.forEach { listener ->
            try {
                listener.onTrackChanged(track)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener of track change", e)
            }
        }

        // Sauvegarde l'index courant pour la persistance
        appContext?.let { ctx ->
            MusicQueuePersistence.updateCurrentIndex(ctx, currentIndex)
            // Notifier le widget du changement de track
            MusicWidgetController.notifyStateChanged(ctx)
        }
    }

    private var progressRunnable: Runnable? = null
    private var positionSaveCounter = 0

    private fun startProgressUpdates() {
        stopProgressUpdates()
        positionSaveCounter = 0
        progressRunnable = object : Runnable {
            override fun run() {
                player?.let {
                    val position = it.currentPosition
                    val duration = it.duration

                    listeners.forEach { listener ->
                        listener.onProgressChanged(position, duration)
                    }

                    // Check if we've reached 50% and should increment play count
                    checkAndIncrementPlayCount(position, duration)

                    // Sauvegarde la position toutes les 30 secondes (60 * 500ms)
                    positionSaveCounter++
                    if (positionSaveCounter >= 60) {
                        positionSaveCounter = 0
                        appContext?.let { ctx -> MusicQueuePersistence.updatePosition(ctx, position) }
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(progressRunnable!!)
    }

    /**
     * Vérifie si on a atteint 50% de la durée et incrémente le compteur si nécessaire.
     * Le compteur n'est incrémenté qu'une seule fois par lecture.
     * Bug 5.19: Ensures MusicPlayCountManager is initialized before incrementing.
     * Bug fix: Utilise compareAndSet pour garantir l'atomicité et éviter les double incréments.
     */
    private fun checkAndIncrementPlayCount(position: Long, duration: Long) {
        // Ne fait rien si déjà incrémenté pour ce track ou si durée invalide
        if (playCountIncrementedForCurrentTrack.get() || duration <= 0) return

        val percentage = (position.toDouble() / duration.toDouble()) * 100

        // Incrémente à 50% de la durée
        // Bug fix: compareAndSet garantit qu'un seul thread peut passer de false à true
        if (percentage >= 50.0 && playCountIncrementedForCurrentTrack.compareAndSet(false, true)) {
            val track = getCurrentTrack() ?: return
            val ctx = appContext ?: return

            // Incrémente en arrière-plan et notifie l'UI
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Bug 5.19: Ensure MusicPlayCountManager is initialized before incrementing
                    // This is necessary when restoring from widget after app kill
                    if (!MusicPlayCountManager.isLoaded()) {
                        Log.d(TAG, "MusicPlayCountManager not initialized, initializing now...")
                        MusicPlayCountManager.init(ctx)
                    }

                    MusicStatsManager.incrementPlayCount(track, position, duration)
                    val newCount = MusicStatsManager.getPlayCount(track)
                    Log.d(TAG, "Play count incremented for: ${track.title} -> $newCount")

                    // Notifier l'UI sur le thread principal
                    withContext(Dispatchers.Main) {
                        listeners.forEach { it.onPlayCountIncremented(track, newCount) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error incrementing play count for: ${track.title}", e)
                }
            }
        }
    }

    /**
     * Réinitialise le tracking du play count pour le nouveau track.
     * Appelé lors d'un changement de track.
     */
    private fun resetPlayCountTracking() {
        playCountIncrementedForCurrentTrack.set(false)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun startForegroundService(context: Context) {
        val track = getCurrentTrack() ?: return
        val intent = Intent(context, MusicForegroundService::class.java).apply {
            action = MusicForegroundService.ACTION_START
            putExtra(MusicForegroundService.EXTRA_TITLE, track.title)
            putExtra(MusicForegroundService.EXTRA_ARTIST, track.artist)
            putExtra(MusicForegroundService.EXTRA_IS_PLAYING, isPlaying())
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // Sur Android 12+, startForegroundService peut échouer si l'app est en arrière-plan
            // (ForegroundServiceStartNotAllowedException).
            // On ne peut rien faire - la lecture continue quand même grâce au wake lock.
            Log.w(TAG, "Cannot start foreground service from background: ${e.message}")
        }
    }

    fun updateForegroundService(context: Context) {
        val track = getCurrentTrack() ?: return
        val intent = Intent(context, MusicForegroundService::class.java).apply {
            action = MusicForegroundService.ACTION_UPDATE
            putExtra(MusicForegroundService.EXTRA_TITLE, track.title)
            putExtra(MusicForegroundService.EXTRA_ARTIST, track.artist)
            putExtra(MusicForegroundService.EXTRA_IS_PLAYING, isPlaying())
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // Le service n'est peut-être pas en cours - pas grave, la lecture continue
            Log.w(TAG, "Cannot update foreground service: ${e.message}")
        }
    }

    private fun stopForegroundService(context: Context) {
        val intent = Intent(context, MusicForegroundService::class.java).apply {
            action = MusicForegroundService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot stop foreground service: ${e.message}")
        }
    }

    fun release(context: Context) {
        // Arrêter les mises à jour de progression d'abord pour éviter les fuites du Handler
        stopProgressUpdates()

        stop(context)

        // Retirer le listener ExoPlayer avant de release le player
        exoPlayerListener?.let { listener ->
            player?.removeListener(listener)
        }
        exoPlayerListener = null

        player?.release()
        player = null
        listeners.clear()
        playlist = emptyList()
        currentIndex = -1

        // Annuler tous les jobs en cours et recréer le scope pour une utilisation future
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)
    }

    /**
     * Restaure la queue depuis la persistance et démarre la lecture.
     * Utilisé par le widget pour reprendre après redémarrage.
     * Fonctionne même si l'app a été tuée (ne nécessite pas MusicLibrary).
     * Bug fix: Initialise MusicPlayCountManager de manière synchrone avant la lecture.
     *
     * @return true si la restauration et le démarrage ont réussi
     */
    fun restoreAndPlay(context: Context): Boolean {
        // Stocker le contexte
        if (appContext == null) {
            appContext = context.applicationContext
        }

        val savedQueue = MusicQueuePersistence.loadQueue(context) ?: return false

        // Convertir les tracks persistés en MusicTrack (ne nécessite pas MusicLibrary)
        val tracks = MusicQueuePersistence.tracksFromSavedQueue(savedQueue)
        if (tracks.isEmpty()) {
            Log.w(TAG, "No tracks in saved queue")
            return false
        }

        // Bug fix: Initialiser MusicPlayCountManager de manière synchrone pour éviter
        // les race conditions si un track court atteint 50% avant la fin de l'init.
        // L'init est rapide car elle utilise un double-checked locking.
        kotlinx.coroutines.runBlocking {
            try {
                MusicPlayCountManager.init(context.applicationContext)
                Log.d(TAG, "MusicPlayCountManager initialized for widget restore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init MusicPlayCountManager", e)
            }
        }

        // Restaurer la playlist
        playlist = tracks
        currentIndex = savedQueue.currentIndex.coerceIn(0, tracks.size - 1)
        shuffleEnabled = savedQueue.shuffleEnabled
        repeatMode = savedQueue.repeatMode
        currentPlaylistId = savedQueue.playlistId

        // Notifier les listeners
        listeners.forEach { it.onPlaylistChanged(playlist) }

        // Démarrer la lecture
        val track = playlist.getOrNull(currentIndex) ?: return false
        val p = getOrCreatePlayer(context)

        val mediaItem = MediaItem.fromUri(track.playbackUri)
        p.setMediaItem(mediaItem)
        p.prepare()

        // Restaurer la position si sauvegardée
        if (savedQueue.positionMs > 0) {
            p.seekTo(savedQueue.positionMs)
        }

        p.playWhenReady = true

        notifyTrackChanged(track)
        startForegroundService(context)

        Log.i(TAG, "Queue restored and playback started: ${tracks.size} tracks, index=$currentIndex")
        return true
    }

    /**
     * Vérifie si une queue sauvegardée existe.
     */
    @Suppress("unused")
    fun hasSavedQueue(context: Context): Boolean {
        return MusicQueuePersistence.hasQueue(context)
    }

    /**
     * Restaure la queue depuis la persistance SANS démarrer la lecture.
     * Utilisé quand l'activity est ouverte depuis le widget après un kill de l'app.
     * Retourne true si une queue a été restaurée.
     * Bug fix: Initialise MusicPlayCountManager de manière synchrone.
     */
    fun restoreQueueOnly(context: Context): Boolean {
        // Stocker le contexte
        if (appContext == null) {
            appContext = context.applicationContext
        }

        // Si une queue est déjà chargée, ne rien faire
        if (playlist.isNotEmpty() && currentIndex >= 0) {
            Log.d(TAG, "Queue already loaded, skipping restore")
            return true
        }

        val savedQueue = MusicQueuePersistence.loadQueue(context) ?: return false

        // Convertir les tracks persistés en MusicTrack
        val tracks = MusicQueuePersistence.tracksFromSavedQueue(savedQueue)
        if (tracks.isEmpty()) {
            Log.w(TAG, "No tracks in saved queue")
            return false
        }

        // Bug fix: Initialiser MusicPlayCountManager de manière synchrone
        kotlinx.coroutines.runBlocking {
            try {
                MusicPlayCountManager.init(context.applicationContext)
                Log.d(TAG, "MusicPlayCountManager initialized for queue restore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init MusicPlayCountManager", e)
            }
        }

        // Restaurer la playlist
        playlist = tracks
        currentIndex = savedQueue.currentIndex.coerceIn(0, tracks.size - 1)
        shuffleEnabled = savedQueue.shuffleEnabled
        repeatMode = savedQueue.repeatMode
        currentPlaylistId = savedQueue.playlistId

        // Notifier les listeners
        listeners.forEach { it.onPlaylistChanged(playlist) }

        // Notifier du track actuel (sans lecture)
        val track = playlist.getOrNull(currentIndex)
        if (track != null) {
            notifyTrackChanged(track)
        }

        Log.i(TAG, "Queue restored (no playback): ${tracks.size} tracks, index=$currentIndex, playlistId=$currentPlaylistId")
        return true
    }
}
