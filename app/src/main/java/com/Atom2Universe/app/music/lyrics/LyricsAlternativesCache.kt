package com.Atom2Universe.app.music.lyrics

import com.Atom2Universe.app.music.lyrics.api.AlternativeLyrics
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache mémoire des résultats alternatifs de paroles retournés par les APIs.
 *
 * Utilisé pour :
 * - Conserver les alternatives disponibles pendant ~10 minutes
 * - Permettre à LyricsBottomSheet de proposer la navigation entre résultats
 *
 * Le cache est keyed par trackId (MediaStore). Les entrées expirent après 10 minutes.
 */
object LyricsAlternativesCache {

    private const val TTL_MS = 10 * 60 * 1000L // 10 minutes

    private data class CacheEntry(
        val alternatives: List<AlternativeLyrics>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired() = System.currentTimeMillis() - timestamp > TTL_MS
    }

    private val cache = ConcurrentHashMap<Long, CacheEntry>()

    /**
     * Stocke les alternatives pour un track. Ne fait rien si la liste est vide.
     */
    fun put(trackId: Long, alternatives: List<AlternativeLyrics>) {
        if (alternatives.isNotEmpty()) {
            cache[trackId] = CacheEntry(alternatives)
        }
    }

    /**
     * Récupère les alternatives pour un track, ou null si expiré / absent.
     */
    fun get(trackId: Long): List<AlternativeLyrics>? {
        val entry = cache[trackId] ?: return null
        if (entry.isExpired()) {
            cache.remove(trackId)
            return null
        }
        return entry.alternatives
    }

    /**
     * Supprime les alternatives d'un track (ex : après sauvegarde).
     */
    fun clear(trackId: Long) {
        cache.remove(trackId)
    }
}
