package com.Atom2Universe.app.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.Atom2Universe.app.AudioFocusManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.widget.MusicWidgetController
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton qui gère le player Radio pour permettre la lecture en background.
 * Utilise un Service Foreground avec MediaSession pour les contrôles média Android.
 */
object RadioPlaybackHolder {

    private const val TAG = "RadioPlaybackHolder"
    private const val NOTIFICATION_UPDATE_DEBOUNCE_MS = 500L
    private const val PREFS_NAME = "radio_prefs"
    private const val KEY_LAST_STATION_ID = "last_station_id"
    private const val KEY_LAST_STATION_NAME = "last_station_name"
    private const val KEY_LAST_STATION_URL = "last_station_url"
    private const val KEY_LAST_STATION_COUNTRY = "last_station_country"
    private const val KEY_LAST_STATION_LANGUAGE = "last_station_language"
    private const val KEY_LAST_STATION_FAVICON = "last_station_favicon"
    private const val KEY_LAST_STATION_BITRATE = "last_station_bitrate"
    private const val MAX_RECONNECT_ATTEMPTS = 3
    private const val INITIAL_RECONNECT_DELAY_MS = 1000L

    private var player: ExoPlayer? = null
    @Volatile
    private var appContext: Context? = null
    private var currentStation: RadioStation? = null
    private var lastStation: RadioStation? = null  // Mémorise la dernière station même après stop
    @Volatile
    private var isRecording = false
    private val listeners = CopyOnWriteArrayList<PlayerListener>()

    // Flag pour éviter les démarrages multiples du service foreground (Bug 4.6)
    private val isForegroundServiceStarted = AtomicBoolean(false)

    // Métadonnées actuelles (artiste/titre du flux) - stockées de manière atomique
    private data class StreamMetadata(val artist: String?, val title: String?)
    @Volatile
    private var currentMetadata: StreamMetadata = StreamMetadata(null, null)

    // Debounce pour éviter les mises à jour de notification trop fréquentes (Bug 4.9 - volatile)
    @Volatile
    private var lastNotificationUpdate = 0L
    private val notificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile
    private var pendingNotificationUpdate: Runnable? = null
    private val debounceLock = Any()

    // Audio focus handling (Bug 4.10 - AtomicBoolean pour thread-safety)
    private val wasPlayingBeforeAudioFocusLoss = AtomicBoolean(false)
    @Volatile
    private var originalVolume = 1.0f

    // Reconnexion automatique (Bug 4.14)
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null

    // Bug 4.25: Gestion de la connectivité réseau
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var isNetworkAvailable = true
    private val wasPlayingBeforeNetworkLoss = AtomicBoolean(false)

    private val audioFocusListener = object : AudioFocusManager.AudioFocusListener {
        override fun onAudioFocusPause() {
            val ctx = appContext ?: return
            val wasPlaying = isPlaying()
            wasPlayingBeforeAudioFocusLoss.set(wasPlaying)
            if (wasPlaying) {
                pause(ctx)
            }
        }

        override fun onAudioFocusResume() {
            val ctx = appContext ?: return
            if (wasPlayingBeforeAudioFocusLoss.compareAndSet(true, false)) {
                resume(ctx)
            }
        }

        override fun onAudioFocusDuck() {
            appContext ?: return
            player?.let {
                originalVolume = it.volume
                it.volume = 0.2f
            }
        }

        override fun onAudioFocusUnduck() {
            appContext ?: return
            player?.volume = originalVolume
        }
    }

    /**
     * Types d'erreurs de lecture pour distinguer les erreurs récupérables des non-récupérables
     */
    enum class PlaybackErrorType {
        /** Erreur réseau - reconnexion automatique possible */
        NETWORK,
        /** Erreur de décodage - station incompatible, reconnexion inutile */
        DECODING,
        /** Erreur inconnue */
        UNKNOWN
    }

    interface PlayerListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onMetadataChanged(artist: String?, title: String?)
        fun onError(error: PlaybackException)
        fun onRecordingStateChanged(recording: Boolean)

        /**
         * Callback avec information sur le type d'erreur pour permettre une UI adaptée
         * @param error L'exception de lecture
         * @param errorType Le type d'erreur (NETWORK, DECODING, UNKNOWN)
         * @param willRetry true si une reconnexion automatique va être tentée
         */
        fun onErrorWithDetails(error: PlaybackException, errorType: PlaybackErrorType, willRetry: Boolean) {
            // Default: delegate to simple onError for backward compatibility
            onError(error)
        }

        /**
         * Bug 4.24: Callback quand les permissions sont insuffisantes pour la notification foreground
         * L'UI peut utiliser ce callback pour demander les permissions ou afficher un message
         */
        fun onNotificationPermissionMissing() {
            // Default: no-op, UI can override to handle
        }

        /**
         * Bug 4.25: Callback quand la connectivité réseau change
         * @param isAvailable true si le réseau est disponible, false sinon
         */
        fun onNetworkStateChanged(isAvailable: Boolean) {
            // Default: no-op, UI can override to show message
        }
    }

    /**
     * Bug 4.25: Configure le monitoring de la connectivité réseau
     */
    private fun setupConnectivityMonitoring(context: Context) {
        if (connectivityCallback != null) return // Déjà configuré

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        // Vérifier l'état initial
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                val wasUnavailable = !isNetworkAvailable
                isNetworkAvailable = true

                // Notifier les listeners
                listeners.forEach { it.onNetworkStateChanged(true) }

                // Si on était en train de jouer avant la perte de connexion, tenter de reconnecter
                val ctx = appContext
                if (wasUnavailable && wasPlayingBeforeNetworkLoss.compareAndSet(true, false) && ctx != null) {
                    Log.d(TAG, "Network restored, attempting to reconnect")
                    notificationHandler.post {
                        reconnect(ctx)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                // Vérifier s'il y a d'autres réseaux disponibles
                val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val stillHasNetwork = cm?.activeNetwork != null

                if (!stillHasNetwork) {
                    // Mémoriser si on était en train de jouer
                    if (isPlaying()) {
                        wasPlayingBeforeNetworkLoss.set(true)
                    }
                    isNetworkAvailable = false

                    // Notifier les listeners
                    listeners.forEach { it.onNetworkStateChanged(false) }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet != isNetworkAvailable) {
                    isNetworkAvailable = hasInternet
                    listeners.forEach { it.onNetworkStateChanged(hasInternet) }
                }
            }
        }

        connectivityCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            Log.d(TAG, "Connectivity monitoring started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
            connectivityCallback = null
        }
    }

    /**
     * Bug 4.25: Désactive le monitoring de la connectivité
     */
    private fun teardownConnectivityMonitoring() {
        val callback = connectivityCallback ?: return
        connectivityCallback = null

        val ctx = appContext ?: return
        val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        try {
            connectivityManager.unregisterNetworkCallback(callback)
            Log.d(TAG, "Connectivity monitoring stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }

    /**
     * Vérifie si le réseau est actuellement disponible
     */
    @Suppress("unused")
    fun isNetworkAvailable(): Boolean = isNetworkAvailable

    /**
     * Initialise ou récupère le player.
     */
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        appContext = context.applicationContext

        // Bug 4.25: Configurer le monitoring de connectivité
        setupConnectivityMonitoring(context.applicationContext)

        if (player == null) {
            Log.d(TAG, "Creating new ExoPlayer instance")
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(playerListener)
            }
        }
        return player ?: throw IllegalStateException("Failed to create ExoPlayer instance")
    }

    fun isPlaying(): Boolean {
        val p = player ?: return false
        // isPlaying est true seulement quand le player joue activement
        // playWhenReady indique l'intention de jouer (même si buffering)
        return p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_READY)
    }

    fun isPaused(): Boolean {
        val p = player ?: return false
        return !p.playWhenReady && p.playbackState == Player.STATE_READY
    }

    fun getCurrentStation(): RadioStation? = currentStation
    fun isCurrentlyRecording(): Boolean = isRecording
    fun getCurrentArtist(): String? = currentMetadata.artist
    fun getCurrentTitle(): String? = currentMetadata.title

    @Suppress("unused")
    fun isStopped(): Boolean {
        val p = player ?: return true
        return p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED
    }

    @Suppress("unused")
    fun getLastStation(): RadioStation? = lastStation

    @Suppress("unused")
    fun hasLastStation(): Boolean = lastStation != null

    /**
     * Charge la dernière station depuis les préférences (après kill de l'app)
     */
    fun loadLastStation(context: Context): RadioStation? {
        if (lastStation != null) return lastStation

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_LAST_STATION_ID, null) ?: return null
        val name = prefs.getString(KEY_LAST_STATION_NAME, null) ?: return null
        val url = prefs.getString(KEY_LAST_STATION_URL, null) ?: return null
        val country = prefs.getString(KEY_LAST_STATION_COUNTRY, "") ?: ""
        val language = prefs.getString(KEY_LAST_STATION_LANGUAGE, "") ?: ""
        val favicon = prefs.getString(KEY_LAST_STATION_FAVICON, "") ?: ""
        val bitrate = prefs.getInt(KEY_LAST_STATION_BITRATE, -1).let { if (it == -1) null else it }

        lastStation = RadioStation(
            id = id,
            name = name,
            url = url,
            country = country,
            language = language,
            favicon = favicon,
            bitrate = bitrate
        )
        return lastStation
    }

    /**
     * Sauvegarde la station actuelle dans les préférences (Bug 4.12 - utilise apply() async)
     */
    private fun saveLastStation(context: Context, station: RadioStation) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_LAST_STATION_ID, station.id)
            putString(KEY_LAST_STATION_NAME, station.name)
            putString(KEY_LAST_STATION_URL, station.url)
            putString(KEY_LAST_STATION_COUNTRY, station.country)
            putString(KEY_LAST_STATION_LANGUAGE, station.language)
            putString(KEY_LAST_STATION_FAVICON, station.favicon)
            putInt(KEY_LAST_STATION_BITRATE, station.bitrate ?: -1)
        }  // Async au lieu de commit() synchrone
    }

    /**
     * Bug 4.22: Valide l'URL de la station avant de la passer à ExoPlayer
     * @return Uri valide ou null si l'URL est invalide
     */
    private fun validateAndParseUrl(url: String): Uri? {
        if (url.isBlank()) {
            Log.w(TAG, "Station URL is blank")
            return null
        }

        // Vérifier le scheme http/https
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Station URL has invalid scheme: $url")
            return null
        }

        return try {
            val uri = url.toUri()
            // Vérifier que l'URI a un host valide
            if (uri.host.isNullOrBlank()) {
                Log.w(TAG, "Station URL has no valid host: $url")
                return null
            }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse station URL: $url", e)
            null
        }
    }

    /**
     * Démarre la lecture d'une station
     * @return true si la lecture a démarré, false si l'URL est invalide
     */
    fun play(context: Context, station: RadioStation, forceReload: Boolean = false): Boolean {
        // Bug 4.22: Valider l'URL avant de continuer
        val stationUri = validateAndParseUrl(station.url)
        if (stationUri == null) {
            Log.e(TAG, "Cannot play station with invalid URL: ${station.url}")
            // Notifier les listeners de l'erreur
            listeners.forEach { listener ->
                listener.onErrorWithDetails(
                    PlaybackException("Invalid station URL", null, PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE),
                    PlaybackErrorType.UNKNOWN,
                    willRetry = false
                )
            }
            return false
        }

        // Demander l'audio focus avant de démarrer
        AudioFocusManager.requestFocus(audioFocusListener)

        val p = getOrCreatePlayer(context)
        currentStation = station
        lastStation = station  // Mémorise pour pouvoir reconnecter après stop

        // Sauvegarde en SharedPreferences pour persistence après kill
        saveLastStation(context, station)

        val currentUri = p.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        val shouldReload = forceReload || currentUri != station.url

        if (shouldReload) {
            val mediaItem = MediaItem.fromUri(stationUri)
            p.setMediaItem(mediaItem)
            p.prepare()
        }

        p.playWhenReady = true
        p.play()

        startForegroundService(context)

        // Démarrer le tracking des statistiques pour la radio
        StatsTracker.startRadioSession(station.name)

        return true
    }

    /**
     * Reconnecte à la dernière station (après stop ou kill de l'app)
     */
    fun reconnect(context: Context): Boolean {
        val station = lastStation ?: loadLastStation(context) ?: return false
        play(context, station, forceReload = true)
        return true
    }

    /**
     * Reprend la lecture (après pause)
     */
    fun resume(context: Context) {
        // Demander l'audio focus avant de reprendre
        AudioFocusManager.requestFocus(audioFocusListener)

        player?.play()
        updateServiceNotification(context)
    }

    /**
     * Met en pause
     */
    fun pause(context: Context) {
        player?.pause()
        updateServiceNotification(context)
    }

    /**
     * Arrête la lecture (déconnecte du flux)
     * Note: lastStation est préservée pour permettre la reconnexion
     */
    fun stop(context: Context) {
        stopInternal(context, stopService = true)
    }

    internal fun stopFromService(context: Context) {
        stopInternal(context, stopService = false)
    }

    private fun stopInternal(context: Context, stopService: Boolean) {
        // Mémorise la station avant de la clear
        if (currentStation != null) {
            lastStation = currentStation
        }

        // Annule toute mise à jour différée pour éviter de relancer le service après un stop
        pendingNotificationUpdate?.let { notificationHandler.removeCallbacks(it) }
        pendingNotificationUpdate = null

        player?.stop()
        player?.clearMediaItems()
        currentStation = null
        currentMetadata = StreamMetadata(null, null)

        // Terminer le tracking de la session radio
        StatsTracker.endRadioSession()

        // Abandonner l'audio focus
        AudioFocusManager.abandonFocus()

        if (isRecording) {
            setRecordingState(context, false)
        }
        if (stopService) {
            stopForegroundService(context)
        }

        // Notifier le widget
        MusicWidgetController.notifyStateChanged(context)
    }

    /**
     * Change l'état d'enregistrement
     */
    fun setRecordingState(context: Context, recording: Boolean) {
        isRecording = recording
        listeners.forEach { it.onRecordingStateChanged(recording) }
        updateServiceNotification(context)
    }

    /**
     * Libère complètement le player et les ressources associées
     */
    fun release(context: Context) {
        // Annule les mises à jour en attente (Bug 4.9)
        synchronized(debounceLock) {
            pendingNotificationUpdate?.let { notificationHandler.removeCallbacks(it) }
            pendingNotificationUpdate = null
        }

        // Annule les tentatives de reconnexion (Bug 4.14)
        cancelReconnect()

        // Bug 4.25: Désactiver le monitoring de connectivité
        teardownConnectivityMonitoring()

        // Abandonner l'audio focus
        AudioFocusManager.abandonFocus()

        stopForegroundService(context)
        player?.removeListener(playerListener)
        player?.release()
        player = null
        // Bug 4.7: Nullifier appContext pour éviter les fuites mémoire
        appContext = null
        currentStation = null
        currentMetadata = StreamMetadata(null, null)
        isRecording = false
        listeners.clear()
        // Reset les flags (Bug 4.10 et 4.25)
        wasPlayingBeforeAudioFocusLoss.set(false)
        wasPlayingBeforeNetworkLoss.set(false)
        isNetworkAvailable = true
        reconnectAttempts = 0

        // Libère le client HTTP partagé
        RadioRepository.releaseClient()
    }

    /**
     * Annule les tentatives de reconnexion en cours
     */
    private fun cancelReconnect() {
        reconnectRunnable?.let { notificationHandler.removeCallbacks(it) }
        reconnectRunnable = null
        reconnectAttempts = 0
    }

    /**
     * Tente une reconnexion avec backoff exponentiel (Bug 4.14)
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS), giving up")
            reconnectAttempts = 0
            return
        }

        val station = currentStation ?: lastStation ?: return
        val delay = INITIAL_RECONNECT_DELAY_MS * (1L shl reconnectAttempts)  // Backoff exponentiel
        reconnectAttempts++

        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delay}ms")

        val runnable = Runnable {
            val ctx = appContext
            if (ctx != null && (currentStation != null || lastStation != null)) {
                Log.d(TAG, "Attempting reconnect #$reconnectAttempts")
                play(ctx, station, forceReload = true)
            }
            reconnectRunnable = null
        }
        reconnectRunnable = runnable
        notificationHandler.postDelayed(runnable, delay)
    }

    fun addListener(listener: PlayerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: PlayerListener) {
        listeners.remove(listener)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Vérifier que le holder n'a pas été released avant de traiter l'événement
            val ctx = appContext ?: return
            Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
            // Met à jour la notification immédiatement pour les changements d'état Play/Pause
            updateServiceNotification(ctx)
            // Notifier le widget
            MusicWidgetController.notifyStateChanged(ctx)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Vérifier que le holder n'a pas été released avant de traiter l'événement
            val ctx = appContext ?: return
            Log.d(TAG, "onPlaybackStateChanged: $playbackState")
            // Bug 4.14: Reset le compteur de reconnexion si la lecture démarre avec succès
            if (playbackState == Player.STATE_READY) {
                reconnectAttempts = 0
                cancelReconnect()
            }
            // Met à jour la notification pour les changements d'état importants
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                updateServiceNotification(ctx)
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Vérifier que le holder n'a pas été released avant de traiter l'événement
            val ctx = appContext ?: return
            val artist = mediaMetadata.artist?.toString()
            val title = mediaMetadata.title?.toString()
            currentMetadata = StreamMetadata(artist, title)
            Log.d(TAG, "onMediaMetadataChanged: artist=$artist, title=$title")
            // Notifie les listeners (UI) immédiatement
            listeners.forEach { it.onMetadataChanged(artist, title) }
            // Met à jour la notification avec DEBOUNCE pour éviter d'affecter l'audio
            updateServiceNotificationDebounced(ctx)
            // Notifier le widget des nouvelles métadonnées
            MusicWidgetController.notifyStateChanged(ctx)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Bug 4.20: Distinguer erreur réseau (reconnectable) vs décodage (non-reconnectable)
            Log.e(TAG, "onPlayerError: ${error.message}, code=${error.errorCode}", error.cause)

            // Classifier le type d'erreur
            val errorType = classifyError(error)
            val shouldReconnect = errorType == PlaybackErrorType.NETWORK

            // Bug 4.14: Tenter une reconnexion automatique avec backoff exponentiel (erreurs réseau uniquement)
            val ctx = appContext
            val willRetry = shouldReconnect && ctx != null && (currentStation != null || lastStation != null)

            // Notifier avec les détails de l'erreur
            listeners.forEach { listener ->
                listener.onErrorWithDetails(error, errorType, willRetry)
            }

            if (willRetry) {
                scheduleReconnect()
            }
        }
    }

    /**
     * Bug 4.20: Classifie l'erreur ExoPlayer en type récupérable ou non
     */
    private fun classifyError(error: PlaybackException): PlaybackErrorType {
        return when (error.errorCode) {
            // Erreurs réseau - reconnexion possible
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlaybackErrorType.NETWORK

            // Erreurs de décodage - reconnexion inutile, format incompatible
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> PlaybackErrorType.DECODING

            // Erreurs inconnues
            else -> PlaybackErrorType.UNKNOWN
        }
    }

    /**
     * Bug 4.24: Vérifie si les permissions pour les notifications sont accordées
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pas besoin de permission avant Android 13
        }
    }

    private fun startForegroundService(context: Context) {
        // Si la permission notification est manquante, on ne peut PAS appeler
        // startForegroundService() : Android exige que le service appelle startForeground()
        // dans les secondes qui suivent, sinon l'app crashe (ForegroundServiceDidNotStartInTimeException).
        // On notifie l'UI et on abandonne le service - la lecture ne fonctionnera qu'en foreground.
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted, cannot start foreground service")
            listeners.forEach { it.onNotificationPermissionMissing() }
            return
        }

        // Bug 4.6: Éviter les démarrages multiples du service foreground
        if (!isForegroundServiceStarted.compareAndSet(false, true)) {
            Log.d(TAG, "Foreground service already started, skipping duplicate start")
            return
        }
        val intent = Intent(context, RadioForegroundService::class.java).apply {
            action = RadioForegroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    private fun updateServiceNotification(context: Context) {
        val intent = Intent(context, RadioForegroundService::class.java).apply {
            action = RadioForegroundService.ACTION_UPDATE
        }
        context.startService(intent)
    }

    /**
     * Met à jour la notification avec debounce pour éviter les mises à jour trop fréquentes
     * qui pourraient affecter la lecture audio
     */
    private fun updateServiceNotificationDebounced(context: Context) {
        synchronized(debounceLock) {
            val now = System.currentTimeMillis()
            val timeSinceLastUpdate = now - lastNotificationUpdate

            // Annule la mise à jour en attente
            pendingNotificationUpdate?.let { notificationHandler.removeCallbacks(it) }
            pendingNotificationUpdate = null

            if (timeSinceLastUpdate >= NOTIFICATION_UPDATE_DEBOUNCE_MS) {
                // Assez de temps s'est écoulé, met à jour immédiatement
                lastNotificationUpdate = now
                updateServiceNotification(context)
            } else {
                // Trop récent, planifie une mise à jour différée
                val runnable = Runnable {
                    synchronized(debounceLock) {
                        lastNotificationUpdate = System.currentTimeMillis()
                        pendingNotificationUpdate = null
                    }
                    updateServiceNotification(context)
                }
                pendingNotificationUpdate = runnable
                notificationHandler.postDelayed(
                    runnable,
                    NOTIFICATION_UPDATE_DEBOUNCE_MS - timeSinceLastUpdate
                )
            }
        }
    }

    private fun stopForegroundService(context: Context) {
        // Reset le flag de démarrage du service (Bug 4.6)
        isForegroundServiceStarted.set(false)
        // Sur Android 12+ (API 31), startService() avec ACTION_STOP peut échouer
        // si l'app est en background. Utiliser stopService() directement est plus fiable.
        val intent = Intent(context, RadioForegroundService::class.java)
        context.stopService(intent)
    }
}

/**
 * Service Foreground avec MediaSession pour les contrôles média Android
 */
class RadioForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.Atom2Universe.app.radio.START"
        const val ACTION_UPDATE = "com.Atom2Universe.app.radio.UPDATE"
        const val ACTION_STOP = "com.Atom2Universe.app.radio.STOP"
        const val ACTION_PLAY = "com.Atom2Universe.app.radio.PLAY"
        const val ACTION_PAUSE = "com.Atom2Universe.app.radio.PAUSE"
        const val ACTION_RECORD = "com.Atom2Universe.app.radio.RECORD"
        const val ACTION_STOP_RECORD = "com.Atom2Universe.app.radio.STOP_RECORD"
        const val ACTION_DISMISS = "com.Atom2Universe.app.radio.DISMISS"

        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "RadioForegroundService"
    }

    private var mediaSession: MediaSessionCompat? = null
    // Flag atomique pour éviter les race conditions pendant le release
    @Volatile
    private var isReleasing = false
    private val mediaSessionLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        // Gère les media buttons via MediaButtonReceiver
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_START -> {
                // Premier démarrage - utilise startForeground
                // Vérifier permission notification sur Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted, cannot start foreground service")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val notification = buildMediaNotification()
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE -> {
                // Mise à jour - utilise NotificationManager directement (plus léger)
                val notification = buildMediaNotification()
                val manager = getSystemService(NotificationManager::class.java)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
            ACTION_STOP -> {
                mediaSession?.isActive = false
                RadioPlaybackHolder.stopFromService(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_DISMISS -> {
                mediaSession?.isActive = false
                RadioPlaybackHolder.stopFromService(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY -> {
                RadioPlaybackHolder.resume(this)
            }
            ACTION_PAUSE -> {
                RadioPlaybackHolder.pause(this)
            }
            ACTION_RECORD -> {
                RadioPlaybackHolder.setRecordingState(this, true)
                // Note: L'enregistrement réel est géré dans RadioActivity via Recorder
                // Ici on met juste à jour l'état et la notification
            }
            ACTION_STOP_RECORD -> {
                RadioPlaybackHolder.setRecordingState(this, false)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Utiliser un flag atomique et synchronisation pour éviter les race conditions
        // entre isActive = false et release()
        synchronized(mediaSessionLock) {
            isReleasing = true
            mediaSession?.let { session ->
                session.isActive = false
                session.release()
            }
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun setupMediaSession() {
        synchronized(mediaSessionLock) {
            isReleasing = false
            mediaSession = MediaSessionCompat(this, TAG).apply {
                // Note: setFlags() deprecated - modern MediaSession handles buttons automatically
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        // Vérifier le flag atomique avant de traiter le callback
                        if (isReleasing) return
                        RadioPlaybackHolder.resume(this@RadioForegroundService)
                        updateNotification()
                    }

                    override fun onPause() {
                        // Vérifier le flag atomique avant de traiter le callback
                        if (isReleasing) return
                        RadioPlaybackHolder.pause(this@RadioForegroundService)
                        updateNotification()
                    }

                    override fun onStop() {
                        // Vérifier le flag atomique avant de traiter le callback
                        if (isReleasing) return
                        RadioPlaybackHolder.stop(this@RadioForegroundService)
                    }
                })

                isActive = true
            }
        }

        updateMediaSessionMetadata()
        updateMediaSessionPlaybackState()
    }

    private fun updateMediaSessionMetadata() {
        val station = RadioPlaybackHolder.getCurrentStation()
        val artist = RadioPlaybackHolder.getCurrentArtist()
        val title = RadioPlaybackHolder.getCurrentTitle()

        // Bug 4.27: Définir metadata appropriée pour stream live (durée indéfinie)
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title ?: station?.name ?: getString(R.string.radio_title))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist ?: station?.name ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, station?.name ?: getString(R.string.radio_title))
            // Durée indéfinie pour les streams live (-1 indique un stream live)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            // Marquer comme stream live
            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, "Live Radio")
            .build()

        mediaSession?.setMetadata(metadata)
    }

    private fun updateMediaSessionPlaybackState() {
        val isPlaying = RadioPlaybackHolder.isPlaying()
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        // Bug 4.27: Position indéfinie pour les streams live
        // PLAYBACK_POSITION_UNKNOWN indique que la position n'est pas applicable
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
                // Note: Pas de ACTION_SEEK_TO car les streams live ne supportent pas le seek
            )
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    /**
     * Bug 4.30: Logique simplifiée de mise à jour de notification
     *
     * Trois états possibles:
     * 1. Lecture active ou enregistrement -> Foreground obligatoire (service ne peut pas être tué)
     * 2. Pause (pas de lecture mais station sélectionnée) -> Notification persistante mais pas foreground
     * 3. Stop complet -> Pas de notification (géré par ACTION_STOP/DISMISS)
     */
    private fun updateNotification() {
        updateMediaSessionMetadata()
        updateMediaSessionPlaybackState()
        val notification = buildMediaNotification()

        val isActive = RadioPlaybackHolder.isPlaying() || RadioPlaybackHolder.isCurrentlyRecording()

        if (isActive) {
            // Cas 1: Lecture/enregistrement actif -> maintenir en foreground
            startForeground(NOTIFICATION_ID, notification)
        } else {
            // Cas 2: Pause -> détacher du foreground mais garder la notification
            // STOP_FOREGROUND_DETACH: le service peut être tué mais la notification reste
            stopForeground(STOP_FOREGROUND_DETACH)

            // Mettre à jour la notification manuellement
            if (hasNotificationPermission()) {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /**
     * Vérifie si la permission de notification est accordée
     */
    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.radio_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.radio_notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Data class pour capturer un snapshot atomique de l'état (Bug 4.19)
     */
    private data class NotificationStateSnapshot(
        val station: RadioStation?,
        val isPlaying: Boolean,
        val isRecording: Boolean,
        val artist: String?,
        val title: String?
    )

    /**
     * Capture un snapshot atomique de l'état pour la notification (Bug 4.19)
     */
    private fun captureStateSnapshot(): NotificationStateSnapshot {
        return NotificationStateSnapshot(
            station = RadioPlaybackHolder.getCurrentStation(),
            isPlaying = RadioPlaybackHolder.isPlaying(),
            isRecording = RadioPlaybackHolder.isCurrentlyRecording(),
            artist = RadioPlaybackHolder.getCurrentArtist(),
            title = RadioPlaybackHolder.getCurrentTitle()
        )
    }

    private fun buildMediaNotification(): Notification {
        updateMediaSessionMetadata()
        updateMediaSessionPlaybackState()

        // Bug 4.19: Capturer un snapshot atomique de l'état
        val snapshot = captureStateSnapshot()
        val station = snapshot.station
        val isPlaying = snapshot.isPlaying
        val isRecording = snapshot.isRecording
        val artist = snapshot.artist
        val title = snapshot.title

        Log.d(TAG, "buildMediaNotification: station=${station?.name}, isPlaying=$isPlaying, artist=$artist, title=$title")

        // Intent pour ouvrir RadioActivity
        val openIntent = Intent(this, RadioActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Play/Pause
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                getString(R.string.audio_action_pause),
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                getString(R.string.audio_action_play),
                createPendingIntent(ACTION_PLAY)
            )
        }

        // Action Stop
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_delete,
            getString(R.string.audio_action_stop),
            createPendingIntent(ACTION_STOP)
        )

        // Action Record
        val recordAction = if (isRecording) {
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.radio_action_stop_record),
                createPendingIntent(ACTION_STOP_RECORD)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_btn_speak_now,
                getString(R.string.radio_action_record),
                createPendingIntent(ACTION_RECORD)
            )
        }

        // Titre et sous-titre
        val contentTitle = title ?: station?.name ?: getString(R.string.radio_notification_title)
        val contentText = buildString {
            if (!artist.isNullOrBlank() && artist != title) {
                append(artist)
            } else if (station != null && title != station.name) {
                append(station.name)
            }
            if (isRecording) {
                if (isNotEmpty()) append(" • ")
                append("⏺ ${getString(R.string.radio_player_status_recording)}")
            }
        }.ifBlank { station?.name ?: getString(R.string.radio_title) }

        val dismissAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.audio_action_dismiss),
            createPendingIntent(ACTION_DISMISS)
        )

        val shouldBeForeground = isPlaying || isRecording

        // Construction de la notification MediaStyle
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(shouldBeForeground)
            .setAutoCancel(!shouldBeForeground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            // Actions dans l'ordre: Play/Pause, Stop, Record
            .addAction(playPauseAction)
            .addAction(stopAction)
            .addAction(recordAction)
            // Style MediaStyle avec les 3 boutons compacts
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Affiche les 3 actions en vue compacte
            )
            .setColor(ContextCompat.getColor(this, R.color.media_player_background))

        if (!shouldBeForeground) {
            builder.addAction(dismissAction)
        }

        return builder.build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RadioForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
