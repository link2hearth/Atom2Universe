package com.Atom2Universe.app.music.lyrics.api

import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Repository pour la recherche de paroles avec cascade fallback.
 * Les URLs des APIs sont configurées par l'utilisateur.
 * Si une URL est vide, cette API est ignorée.
 */
class LyricsRepository(
    private val primaryConfig: LyricsApiConfig?,
    private val fallbackConfig: LyricsApiConfig?,
    private val timeoutMs: Long = 10000
) {

    companion object {
        private const val TAG = "LyricsRepository"
    }

    // Les clients sont créés uniquement si les URLs sont configurées
    private val primaryClient: GenericLyricsApiClient? =
        primaryConfig?.let { GenericLyricsApiClient(it, timeoutMs) }
    private val fallbackClient: GenericLyricsApiClient? =
        fallbackConfig?.let { GenericLyricsApiClient(it, timeoutMs) }

    /**
     * Vérifie si au moins une API est configurée
     */
    fun hasConfiguredApis(): Boolean = primaryClient != null || fallbackClient != null

    /**
     * Recherche des paroles avec fallback en cascade.
     * Retourne le premier résultat réussi ou NotFound si toutes échouent.
     * Si aucune API n'est configurée, retourne NotFound immédiatement.
     */
    suspend fun fetchLyrics(track: MusicTrack): LyricsResult = withContext(Dispatchers.IO) {
        if (!hasConfiguredApis()) {
            Log.d(TAG, "No lyrics APIs configured - skipping search")
            return@withContext LyricsResult.NotFound
        }

        Log.d(TAG, "Fetching lyrics for: '${track.title}' by '${track.artist}'")

        // Lire les métadonnées directement depuis les tags ID3 du fichier MP3 (priorité)
        val metadata = com.Atom2Universe.app.music.lyrics.LyricsUtils.getMetadataFromFile(track)
        val actualTitle = metadata?.title ?: track.title
        val actualArtist = metadata?.artist ?: track.artist

        val cleanTitle = com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(actualTitle)
        val cleanArtist = com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(actualArtist)
        val cleanAlbum = track.album.takeIf { it.isNotBlank() }
            ?.let { com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(it) }
            ?.takeIf { it.isNotBlank() }
        val cleanAlbumArtist = track.albumArtist
            ?.let { com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(it) }
            ?.takeIf { it.isNotBlank() && it != cleanArtist }

        if (metadata != null && (actualTitle != track.title || actualArtist != track.artist)) {
            Log.d(TAG, "Using ID3 tags: '$actualTitle' by '$actualArtist' (MediaStore had: '${track.title}' by '${track.artist}')")
        }
        Log.d(TAG, "Cleaned: '$cleanTitle' by '$cleanArtist' | album: '$cleanAlbum' | albumArtist: '$cleanAlbumArtist'")

        val trackDurationSeconds = track.duration / 1000

        // Mémoriser si on a eu des erreurs réseau/temporaires (pour ne pas stocker
        // un marqueur noLyricsFound si l'échec n'est pas définitif).
        var hadNetworkError = false

        // Essayer l'API principale avec cascade (avec 1 retry en cas d'erreur temporaire)
        if (primaryClient != null && primaryConfig != null) {
            Log.d(TAG, "Trying primary API (${primaryConfig.sourceLabel})...")
            var primaryResult = searchWithCascade(primaryClient, primaryConfig.sourceLabel, cleanTitle, cleanArtist, cleanAlbumArtist, cleanAlbum, trackDurationSeconds)

            // Retry une fois si erreur temporaire (timeout, erreur réseau)
            if (primaryResult is LyricsResult.Error) {
                Log.w(TAG, "✗ Primary API error, retrying in 3s... (${primaryResult.message})")
                delay(3000)
                primaryResult = searchWithCascade(primaryClient, primaryConfig.sourceLabel, cleanTitle, cleanArtist, cleanAlbumArtist, cleanAlbum, trackDurationSeconds)
            }

            when {
                primaryResult is LyricsResult.Success -> {
                    Log.d(TAG, "✓ Success from primary API")
                    return@withContext primaryResult
                }
                primaryResult is LyricsResult.RateLimited -> {
                    Log.w(TAG, "✗ Primary API rate limited, trying fallback")
                }
                primaryResult is LyricsResult.Error -> {
                    Log.w(TAG, "✗ Primary API failed after retry (${primaryResult.message}), trying fallback")
                    hadNetworkError = true
                }
            }
        }

        // Essayer l'API de secours avec cascade
        if (fallbackClient != null && fallbackConfig != null) {
            Log.d(TAG, "Trying fallback API (${fallbackConfig.sourceLabel})...")
            val result = searchWithCascade(fallbackClient, fallbackConfig.sourceLabel, cleanTitle, cleanArtist, cleanAlbumArtist, cleanAlbum, trackDurationSeconds)
            when {
                result is LyricsResult.Success -> {
                    Log.d(TAG, "✓ Success from fallback API")
                    return@withContext result
                }
                result is LyricsResult.Error -> {
                    hadNetworkError = true
                }
            }
        }

        Log.d(TAG, "All configured APIs failed for: '${track.title}' by '${track.artist}'")

        // Si on a eu des erreurs réseau (timeout, erreur HTTP transitoire) et aucune réponse
        // définitive "NotFound", retourner Error pour que LyricsManager ne stocke pas
        // le marqueur noLyricsFound — on réessaiera à la prochaine lecture automatique.
        if (hadNetworkError) {
            return@withContext LyricsResult.Error("API temporarily unavailable", "all_apis")
        }

        LyricsResult.NotFound
    }

    /**
     * Essaie plusieurs combinaisons de paramètres pour maximiser les chances de trouver des paroles.
     * Cascade du plus ciblé au plus spécialisé :
     *   1. titre + artiste (nettoyage de base : feat., live, remastered, etc.)
     *   2. titre + albumArtist (si différent de l'artiste)
     *   3. titre + artiste + album (recherche ciblée pour désambiguïsation)
     *   4. titre + artiste normalisés agressivement (supprime ALL parenthèses et symboles restants)
     *
     * Le try 4 n'est exécuté que si la forme normalisée diffère de la forme nettoyée (try 1).
     * Aucune tentative "titre seul" : trop imprécis, donne des résultats catastrophiques.
     *
     * IMPORTANT : si toutes les tentatives échouent avec Error (timeout, erreur réseau),
     * on retourne Error plutôt que NotFound pour ne pas stocker de marqueur noLyricsFound.
     */
    private suspend fun searchWithCascade(
        client: GenericLyricsApiClient,
        label: String,
        title: String,
        artist: String,
        albumArtist: String?,
        album: String?,
        trackDurationSeconds: Long
    ): LyricsResult {
        var lastError: LyricsResult.Error? = null

        // 1. titre + artiste — pas d'album dans l'URL (retourne tous les résultats),
        //    mais scoringAlbum permet quand même de scorer les correspondances d'album côté client.
        Log.d(TAG, "[$label] Try 1/4: title='$title' artist='$artist' (match dur=${trackDurationSeconds}s album scoring='$album')")
        val r1 = trySearch(client, label, title, artist, null, null, trackDurationSeconds, scoringAlbum = album, scoringAlbumArtist = albumArtist)
        if (r1 is LyricsResult.Success) return r1
        if (r1 is LyricsResult.RateLimited) return r1
        if (r1 is LyricsResult.Error) lastError = r1

        // 2. titre + albumArtist (si disponible et différent)
        if (!albumArtist.isNullOrBlank()) {
            Log.d(TAG, "[$label] Try 2/4: title='$title' albumArtist='$albumArtist'")
            val r2 = trySearch(client, label, title, albumArtist, null, null, trackDurationSeconds, scoringAlbum = album, scoringAlbumArtist = albumArtist)
            if (r2 is LyricsResult.Success) return r2
            if (r2 is LyricsResult.RateLimited) return r2
            if (r2 is LyricsResult.Error) lastError = r2
        }

        // 3. titre + artiste + album dans l'URL (filtre côté API pour désambiguïsation)
        if (!album.isNullOrBlank()) {
            Log.d(TAG, "[$label] Try 3/4: title='$title' artist='$artist' album='$album'")
            val r3 = trySearch(client, label, title, artist, album, null, trackDurationSeconds, scoringAlbum = album, scoringAlbumArtist = albumArtist)
            if (r3 is LyricsResult.Success) return r3
            if (r3 is LyricsResult.RateLimited) return r3
            if (r3 is LyricsResult.Error) lastError = r3
        }

        // 4. normalisation agressive : supprime les symboles (&, !, ...) et chiffres du titre et de l'artiste.
        //    Ex: "Angus & Julia Stone" → "Angus Julia Stone", "My Band! (Vol. 2)" → "My Band"
        //    Uniquement exécuté si le résultat diffère de la forme déjà nettoyée (try 1).
        //    Pas de scoringAlbum ici : le nom d'album peut lui aussi contenir des symboles.
        val normTitle = com.Atom2Universe.app.music.lyrics.LyricsUtils.normalizeForSearch(title)
        val normArtist = com.Atom2Universe.app.music.lyrics.LyricsUtils.normalizeForSearch(artist)
        if (normTitle != title || normArtist != artist) {
            Log.d(TAG, "[$label] Try 4/4: normTitle='$normTitle' normArtist='$normArtist'")
            val r4 = trySearch(client, label, normTitle, normArtist, null, null, trackDurationSeconds)
            if (r4 is LyricsResult.Success) return r4
            if (r4 is LyricsResult.RateLimited) return r4
            if (r4 is LyricsResult.Error) lastError = r4
        } else {
            Log.d(TAG, "[$label] Try 4/4 skipped: already in normalized form")
        }

        // Si toutes les tentatives ont échoué avec des erreurs réseau (et aucune
        // réponse propre "NotFound" de l'API), propager l'erreur pour éviter
        // de stocker un marqueur noLyricsFound sur un échec temporaire.
        return lastError ?: LyricsResult.NotFound
    }

    private suspend fun trySearch(
        client: GenericLyricsApiClient,
        label: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?,
        matchDurationSeconds: Long,
        scoringAlbum: String? = null,
        scoringAlbumArtist: String? = null
    ): LyricsResult {
        return try {
            client.searchLyrics(title, artist, album, durationSeconds, matchDurationSeconds, scoringAlbum, scoringAlbumArtist)
        } catch (e: Exception) {
            Log.e(TAG, "[$label] Error: ${e.message}")
            LyricsResult.Error(e.message ?: "Unknown error", label)
        }
    }
}
