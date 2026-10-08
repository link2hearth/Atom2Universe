package com.Atom2Universe.app.midi.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) pour les opérations sur les Playlists
 */
@Dao
interface PlaylistDao {

    /**
     * Récupère toutes les playlists triées par nom (insensible à la casse)
     */
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    /**
     * Récupère une playlist par son ID
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    /**
     * Récupère une playlist par son nom
     */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): Playlist?

    /**
     * Récupère tous les IDs de tracks dans une playlist
     */
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getPlaylistTrackIds(playlistId: Long): Flow<List<Long>>

    /**
     * Récupère tous les tracks d'une playlist (avec jointure)
     * Triés par position dans la playlist
     */
    @Transaction
    @Query("""
        SELECT mt.* FROM midi_tracks mt
        INNER JOIN playlist_tracks pt ON mt.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getPlaylistTracks(playlistId: Long): Flow<List<MidiTrack>>

    /**
     * Récupère le nombre de tracks dans une playlist
     */
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getPlaylistTrackCount(playlistId: Long): Int

    /**
     * Insère une nouvelle playlist
     * @return L'ID de la playlist créée
     */
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    /**
     * Met à jour une playlist existante
     */
    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    /**
     * Supprime une playlist (les tracks associés seront supprimés via CASCADE)
     */
    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    /**
     * Ajoute un track à une playlist
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(playlistTrack: PlaylistTrack)

    /**
     * Retire un track d'une playlist
     */
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    /**
     * Met à jour la position d'un track dans une playlist
     */
    @Query("""
        UPDATE playlist_tracks
        SET position = :newPosition
        WHERE playlistId = :playlistId AND trackId = :trackId
    """)
    suspend fun updateTrackPosition(playlistId: Long, trackId: Long, newPosition: Int)

    /**
     * Récupère la position maximale dans une playlist
     * (utilisé pour ajouter un nouveau track à la fin)
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    /**
     * Supprime tous les tracks d'une playlist
     */
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    /**
     * Vérifie si un track est déjà dans une playlist
     */
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: Long): Boolean
}
