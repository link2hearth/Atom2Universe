package com.Atom2Universe.app.music.lyrics

import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackException
import com.Atom2Universe.app.music.MusicPlaybackHolder
import com.Atom2Universe.app.music.MusicPreferences
import com.Atom2Universe.app.music.lyrics.api.LyricsResult
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gestionnaire pour la récupération automatique des paroles.
 *
 * Quand activé, recherche et ajoute automatiquement les paroles
 * pour chaque morceau écouté (si elles n'existent pas déjà).
 *
 * Workflow:
 * 1. Vérifie si l'option est activée
 * 2. Vérifie si les paroles existent déjà (cache ou fichier)
 * 3. Si non, lance une recherche API en arrière-plan
 * 4. Si trouvé, sauvegarde automatiquement
 *
 * Tout se passe en arrière-plan sans bloquer la lecture.
 */
object LyricsAutoFetchManager {

    private const val TAG = "LyricsAutoFetch"
    private const val PREFETCH_THRESHOLD_MS = 20_000L // 20s avant la fin

    private lateinit var preferences: MusicPreferences
    private var isInitialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track l'ID du dernier morceau traité pour éviter les doublons
    @Volatile
    private var lastProcessedTrackId: Long = -1

    // Track l'ID du dernier morceau préchargé pour éviter les préchargements en double
    @Volatile
    private var lastPrefetchedTrackId: Long = -1

    // Listener sur la progression pour déclencher le préchargement 20s avant la fin
    private val progressListener = object : MusicPlaybackHolder.PlayerListener {
        override fun onProgressChanged(position: Long, duration: Long) {
            if (!isEnabled() || duration <= 0) return
            val remaining = duration - position
            if (remaining in 1..PREFETCH_THRESHOLD_MS) {
                prefetchNextTrackLyrics()
            }
        }
        override fun onTrackChanged(track: MusicTrack?) {
            // Reset du préchargement au changement de titre (nouveau cycle)
            lastPrefetchedTrackId = -1
        }
        override fun onPlaybackStateChanged(isPlaying: Boolean) {}
        override fun onError(error: PlaybackException) {}
        override fun onPlaylistChanged(playlist: List<MusicTrack>) {}
        override fun onPlayCountIncremented(track: MusicTrack, newCount: Long) {}
    }

    /**
     * Initialise le manager avec le contexte de l'application.
     */
    fun init(context: Context) {
        if (isInitialized) return

        preferences = MusicPreferences.getInstance(context.applicationContext)
        isInitialized = true

        MusicPlaybackHolder.addListener(progressListener)

        Log.d(TAG, "LyricsAutoFetchManager initialized (enabled: ${isEnabled()})")
    }

    /**
     * Vérifie si l'auto-fetch est activé.
     */
    fun isEnabled(): Boolean {
        return if (isInitialized) preferences.autoFetchLyrics else false
    }

    /**
     * Appelé quand un nouveau morceau commence à jouer.
     * Déclenche la recherche automatique si nécessaire.
     */
    fun onTrackChanged(track: MusicTrack?) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized")
            return
        }

        if (track == null) {
            return
        }

        // Éviter de traiter le même track plusieurs fois
        if (track.id == lastProcessedTrackId) {
            return
        }

        lastProcessedTrackId = track.id

        // Vérifier si l'auto-fetch est activé
        if (!isEnabled()) {
            Log.d(TAG, "Auto-fetch disabled, skipping: ${track.title}")
            return
        }

        // Lancer la recherche en arrière-plan
        scope.launch {
            processTrack(track)
        }
    }

    /**
     * Traite un morceau : vérifie si les paroles existent, sinon les recherche.
     */
    private suspend fun processTrack(track: MusicTrack) {
        try {
            Log.d(TAG, "Processing: ${track.title} by ${track.artist}")

            // Vérifier si les paroles existent déjà (cache ou fichier)
            val existingLyrics = LyricsManager.getLyrics(track)

            if (existingLyrics != null && existingLyrics.isNotBlank()) {
                Log.d(TAG, "✓ Lyrics already exist for: ${track.title}")
                return
            }

            // Pas de paroles, lancer la recherche en ligne
            Log.d(TAG, "→ Fetching lyrics online for: ${track.title}")

            val result = LyricsManager.fetchLyricsOnline(track)

            when (result) {
                is LyricsResult.Success -> {
                    Log.d(TAG, "✓ Found lyrics from ${result.source} for: ${track.title}")

                    // Sauvegarder automatiquement
                    val saved = LyricsManager.saveLyrics(track, result.lyrics, result.source)

                    if (saved) {
                        Log.d(TAG, "✓ Auto-saved lyrics for: ${track.title}")
                    } else {
                        Log.w(TAG, "✗ Failed to save lyrics for: ${track.title}")
                    }
                }
                is LyricsResult.NotFound -> {
                    Log.d(TAG, "✗ No lyrics found for: ${track.title}")
                }
                is LyricsResult.RateLimited -> {
                    Log.w(TAG, "⚠ Rate limited, skipping: ${track.title}")
                }
                is LyricsResult.Error -> {
                    Log.w(TAG, "✗ Error fetching lyrics for ${track.title}: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing track: ${track.title}", e)
        }
    }

    /**
     * Précharge les paroles du prochain titre en arrière-plan, si elles ne sont pas déjà
     * disponibles. Déclenché 20s avant la fin du titre en cours.
     */
    private fun prefetchNextTrackLyrics() {
        val nextTrack = MusicPlaybackHolder.getNextTrack() ?: return
        if (nextTrack.id == lastPrefetchedTrackId) return
        lastPrefetchedTrackId = nextTrack.id

        scope.launch {
            try {
                // Si les paroles existent déjà (cache ou fichier), rien à faire
                val existing = LyricsManager.getLyrics(nextTrack)
                if (existing != null) {
                    Log.d(TAG, "Prefetch skipped (already cached): ${nextTrack.title}")
                    return@launch
                }

                Log.d(TAG, "Prefetching lyrics for next track: ${nextTrack.title} by ${nextTrack.artist}")
                val result = LyricsManager.fetchLyricsOnline(nextTrack)

                when (result) {
                    is LyricsResult.Success -> Log.d(TAG, "✓ Prefetch success (${result.source}): ${nextTrack.title}")
                    is LyricsResult.NotFound -> Log.d(TAG, "✗ Prefetch: no lyrics found for: ${nextTrack.title}")
                    is LyricsResult.RateLimited -> Log.w(TAG, "⚠ Prefetch rate limited: ${nextTrack.title}")
                    is LyricsResult.Error -> Log.w(TAG, "✗ Prefetch error for ${nextTrack.title}: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prefetch error for next track: ${e.message}")
            }
        }
    }

    /**
     * Réinitialise le tracking du dernier morceau traité.
     * Utile pour forcer un re-traitement si nécessaire.
     */
    fun reset() {
        lastProcessedTrackId = -1
        lastPrefetchedTrackId = -1
        Log.d(TAG, "Reset last processed track")
    }
}
