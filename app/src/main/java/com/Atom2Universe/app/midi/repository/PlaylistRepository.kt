package com.Atom2Universe.app.midi.repository

import android.content.Context
import androidx.room.withTransaction
import com.Atom2Universe.app.midi.data.MidiDatabase
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.data.Playlist
import com.Atom2Universe.app.midi.data.PlaylistTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Repository pour gérer les opérations sur les playlists
 * Encapsule l'accès au DAO et fournit une API haut niveau
 */
class PlaylistRepository(context: Context) {

    private val database = MidiDatabase.getInstance(context)
    private val playlistDao = database.playlistDao()

    /**
     * Récupère toutes les playlists (Flow pour observation reactive)
     */
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    /**
     * Récupère une playlist par son ID
     */
    @Suppress("unused")
    suspend fun getPlaylistById(playlistId: Long): Playlist? {
        return withContext(Dispatchers.IO) {
            playlistDao.getPlaylistById(playlistId)
        }
    }

    /**
     * Récupère tous les tracks d'une playlist
     */
    fun getPlaylistTracks(playlistId: Long): Flow<List<MidiTrack>> {
        return playlistDao.getPlaylistTracks(playlistId)
    }

    /**
     * Récupère le nombre de tracks dans une playlist
     */
    suspend fun getPlaylistTrackCount(playlistId: Long): Int {
        return withContext(Dispatchers.IO) {
            playlistDao.getPlaylistTrackCount(playlistId)
        }
    }

    /**
     * Crée une nouvelle playlist
     * @param name Nom de la playlist
     * @return ID de la playlist créée
     */
    suspend fun createPlaylist(name: String): Long {
        return withContext(Dispatchers.IO) {
            val playlist = Playlist(
                name = name,
                dateCreated = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(playlist)
        }
    }

    /**
     * Renomme une playlist
     */
    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
            val updated = playlist.copy(
                name = newName,
                dateModified = System.currentTimeMillis()
            )
            playlistDao.updatePlaylist(updated)
        }
    }

    /**
     * Supprime une playlist
     */
    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
            playlistDao.deletePlaylist(playlist)
        }
    }

    /**
     * Ajoute un track à une playlist (à la fin)
     * @return true si ajouté, false si déjà présent
     * Utilise une transaction pour éviter les race conditions sur getMaxPosition
     */
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                // Vérifie si le track n'est pas déjà dans la playlist
                if (playlistDao.isTrackInPlaylist(playlistId, trackId)) {
                    return@withTransaction false
                }

                // Récupère la position maximale et ajoute à la fin (atomique dans la transaction)
                val maxPosition = playlistDao.getMaxPosition(playlistId)
                val playlistTrack = PlaylistTrack(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = maxPosition + 1
                )

                playlistDao.addTrackToPlaylist(playlistTrack)

                // Met à jour la date de modification de la playlist
                val playlist = playlistDao.getPlaylistById(playlistId)
                playlist?.let {
                    playlistDao.updatePlaylist(it.copy(dateModified = System.currentTimeMillis()))
                }

                true
            }
        }
    }

    /**
     * Retire un track d'une playlist
     */
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        withContext(Dispatchers.IO) {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)

            // Met à jour la date de modification
            val playlist = playlistDao.getPlaylistById(playlistId)
            playlist?.let {
                playlistDao.updatePlaylist(it.copy(dateModified = System.currentTimeMillis()))
            }
        }
    }

    /**
     * Réordonne un track dans une playlist
     * @param trackId ID du track à déplacer
     * @param newPosition Nouvelle position (0-indexed)
     */
    @Suppress("unused")
    suspend fun reorderTrack(playlistId: Long, trackId: Long, newPosition: Int) {
        withContext(Dispatchers.IO) {
            playlistDao.updateTrackPosition(playlistId, trackId, newPosition)

            // Met à jour la date de modification
            val playlist = playlistDao.getPlaylistById(playlistId)
            playlist?.let {
                playlistDao.updatePlaylist(it.copy(dateModified = System.currentTimeMillis()))
            }
        }
    }

    /**
     * Vide une playlist (retire tous les tracks)
     */
    suspend fun clearPlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            playlistDao.clearPlaylist(playlistId)

            // Met à jour la date de modification
            val playlist = playlistDao.getPlaylistById(playlistId)
            playlist?.let {
                playlistDao.updatePlaylist(it.copy(dateModified = System.currentTimeMillis()))
            }
        }
    }

    /**
     * Vérifie si un track est dans une playlist
     */
    @Suppress("unused")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            playlistDao.isTrackInPlaylist(playlistId, trackId)
        }
    }

    // ==================== FAVORIS ====================

    /**
     * Récupère ou crée la playlist Favoris
     * @return ID de la playlist Favoris
     */
    @Suppress("unused")
    suspend fun getOrCreateFavoritesPlaylist(): Long {
        return withContext(Dispatchers.IO) {
            getOrCreateFavoritesPlaylistInternal()
        }
    }

    /**
     * Version interne pour être utilisée dans les transactions
     */
    private suspend fun getOrCreateFavoritesPlaylistInternal(): Long {
        val existing = playlistDao.getPlaylistByName(FAVORITES_PLAYLIST_NAME)
        return if (existing != null) {
            existing.id
        } else {
            val playlist = Playlist(
                name = FAVORITES_PLAYLIST_NAME,
                dateCreated = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(playlist)
        }
    }

    /**
     * Récupère l'ID de la playlist Favoris (null si inexistante)
     */
    suspend fun getFavoritesPlaylistId(): Long? {
        return withContext(Dispatchers.IO) {
            playlistDao.getPlaylistByName(FAVORITES_PLAYLIST_NAME)?.id
        }
    }

    /**
     * Récupère les IDs des tracks favoris en Flow (réactif)
     * Se met à jour automatiquement quand la playlist Favoris change
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun getFavoriteTrackIds(): Flow<List<Long>> {
        return allPlaylists.flatMapLatest { playlists ->
            val favoritesPlaylist = playlists.find { it.name == FAVORITES_PLAYLIST_NAME }
            if (favoritesPlaylist != null) {
                playlistDao.getPlaylistTrackIds(favoritesPlaylist.id)
            } else {
                flow { emit(emptyList()) }
            }
        }
    }

    /**
     * Ajoute ou retire un track des favoris
     * @return true si ajouté aux favoris, false si retiré
     * Utilise une transaction pour éviter les race conditions
     */
    suspend fun toggleFavorite(trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val favoritesId = getOrCreateFavoritesPlaylistInternal()
                val isCurrentlyFavorite = playlistDao.isTrackInPlaylist(favoritesId, trackId)

                if (isCurrentlyFavorite) {
                    playlistDao.removeTrackFromPlaylist(favoritesId, trackId)
                    false
                } else {
                    val maxPosition = playlistDao.getMaxPosition(favoritesId)
                    val playlistTrack = PlaylistTrack(
                        playlistId = favoritesId,
                        trackId = trackId,
                        position = maxPosition + 1
                    )
                    playlistDao.addTrackToPlaylist(playlistTrack)
                    true
                }
            }
        }
    }

    /**
     * Vérifie si un track est dans les favoris
     */
    suspend fun isTrackFavorite(trackId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val favoritesId = getFavoritesPlaylistId() ?: return@withContext false
            playlistDao.isTrackInPlaylist(favoritesId, trackId)
        }
    }

    /**
     * Récupère tous les tracks favoris (réactif)
     * Se met à jour automatiquement quand la playlist Favoris change
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun getFavoriteTracks(): Flow<List<MidiTrack>> {
        return allPlaylists.flatMapLatest { playlists ->
            val favoritesPlaylist = playlists.find { it.name == FAVORITES_PLAYLIST_NAME }
            if (favoritesPlaylist != null) {
                playlistDao.getPlaylistTracks(favoritesPlaylist.id)
            } else {
                flow { emit(emptyList()) }
            }
        }
    }

    companion object {
        const val FAVORITES_PLAYLIST_NAME = "⭐ Favoris"
    }
}
