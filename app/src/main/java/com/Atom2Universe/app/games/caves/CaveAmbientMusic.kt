package com.Atom2Universe.app.games.caves

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Musique d'ambiance pour Cave World.
 *
 * Cycle indéfini :
 *   silence aléatoire (3–8 min) → fade in (30 s) → plein volume (3–6 min) → fade out (30 s) → …
 *
 * Le point de départ dans le fichier est choisi au hasard à chaque épisode musical,
 * en s'assurant qu'il reste assez de piste pour le fade in + plateau + fade out.
 */
class CaveAmbientMusic(private val context: Context, private val scope: CoroutineScope) {

    companion object {
        private const val ASSET_PATH     = "Sounds/00 - Kevin MacLeod - Ancient Winds.mp3"
        private const val FILE_MS        = 3_480_000L   // ~58 min — durée estimée du fichier
        private const val FADE_MS        = 30_000L      // durée fade in / fade out
        private const val FADE_STEPS     = 60           // pas de fade (toutes les 500 ms)
        private const val PLAY_MIN_MS    = 4 * 60_000L  // durée musicale min (fades inclus)
        private const val PLAY_MAX_MS    = 7 * 60_000L  // durée musicale max
        private const val SILENCE_MIN_MS = 3 * 60_000L  // silence min entre deux épisodes
        private const val SILENCE_MAX_MS = 8 * 60_000L  // silence max
    }

    private var cycleJob: Job? = null

    /** Démarre (ou redémarre) le cycle. Annule tout cycle en cours. */
    fun start() {
        cycleJob?.cancel()
        cycleJob = scope.launch {
            // Petite pause initiale avant la toute première musique
            delay(Random.nextLong(20_000L, 60_000L))
            while (isActive) {
                playEpisode()
                if (!isActive) break
                delay(Random.nextLong(SILENCE_MIN_MS, SILENCE_MAX_MS))
            }
        }
    }

    /** Met en pause (coupe immédiatement l'audio, le cycle reprendra au prochain [start]). */
    fun pause() {
        cycleJob?.cancel()
        cycleJob = null
    }

    /** Reprend depuis le début du cycle (silence → musique). */
    fun resume() = start()

    /** Libère toutes les ressources. Appeler depuis onDestroy. */
    fun destroy() {
        cycleJob?.cancel()
    }

    // ── Lecture d'un épisode musical ─────────────────────────────────────────

    private suspend fun playEpisode() {
        val mp = MediaPlayer()
        try {
            // Chargement depuis les assets
            val afd = context.assets.openFd(ASSET_PATH)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setVolume(0f, 0f)

            withContext(Dispatchers.IO) { mp.prepare() }
            coroutineContext.ensureActive()

            // Position aléatoire, assez loin de la fin pour tenir tout l'épisode
            val playMs   = Random.nextLong(PLAY_MIN_MS, PLAY_MAX_MS)
            val maxStart = (FILE_MS - playMs - FADE_MS).coerceAtLeast(0L)
            val startMs  = Random.nextLong(0L, maxStart.coerceAtLeast(1L))

            @Suppress("DEPRECATION")
            mp.seekTo(startMs.toInt())   // toInt() OK : ~35 min max, suffisant sur le fichier
            mp.start()

            // Fade in
            val stepMs = FADE_MS / FADE_STEPS
            for (step in 1..FADE_STEPS) {
                val vol = step.toFloat() / FADE_STEPS
                mp.setVolume(vol, vol)
                delay(stepMs)
            }

            // Plateau volume maximum
            val holdMs = (playMs - FADE_MS * 2L).coerceAtLeast(0L)
            delay(holdMs)

            // Fade out
            for (step in FADE_STEPS downTo 1) {
                val vol = step.toFloat() / FADE_STEPS
                mp.setVolume(vol, vol)
                delay(stepMs)
            }

        } finally {
            // Toujours libérer le MediaPlayer, même en cas d'annulation de la coroutine
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
    }
}
