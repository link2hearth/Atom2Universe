package com.Atom2Universe.app.midi.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) pour les opérations sur les MidiTracks
 */
@Dao
interface MidiTrackDao {

    /**
     * Récupère tous les tracks triés par titre (insensible à la casse)
     */
    @Query("SELECT * FROM midi_tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<MidiTrack>>

    /**
     * Récupère tous les tracks d'un artiste donné
     */
    @Query("SELECT * FROM midi_tracks WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByArtist(artist: String): Flow<List<MidiTrack>>

    /**
     * Récupère tous les tracks d'un artiste donné (version directe suspend)
     */
    @Query("SELECT * FROM midi_tracks WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    suspend fun getTracksByArtistDirect(artist: String): List<MidiTrack>

    /**
     * Récupère tous les tracks (version directe suspend pour shuffle library)
     */
    @Query("SELECT * FROM midi_tracks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllTracksDirect(): List<MidiTrack>

    /**
     * Récupère tous les tracks d'un album donné
     */
    @Query("SELECT * FROM midi_tracks WHERE artist = :artist AND album = :album ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByAlbum(artist: String, album: String): Flow<List<MidiTrack>>

    /**
     * Récupère un track par son ID
     */
    @Query("SELECT * FROM midi_tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): MidiTrack?

    /**
     * Récupère un track par son chemin de fichier
     */
    @Query("SELECT * FROM midi_tracks WHERE filePath = :filePath LIMIT 1")
    suspend fun getTrackByFilePath(filePath: String): MidiTrack?

    /**
     * Récupère tous les artistes uniques
     */
    @Query("SELECT DISTINCT artist FROM midi_tracks ORDER BY artist COLLATE NOCASE ASC")
    fun getAllArtists(): Flow<List<String>>

    /**
     * Récupère les artistes avec leurs statistiques
     */
    @Query("""
        SELECT
            artist,
            COUNT(DISTINCT id) as trackCount,
            COUNT(DISTINCT album) as albumCount
        FROM midi_tracks
        GROUP BY artist
        ORDER BY artist COLLATE NOCASE ASC
    """)
    suspend fun getArtistsWithStats(): List<ArtistStats>

    /**
     * Récupère les albums d'un artiste avec leurs statistiques
     */
    @Query("""
        SELECT
            artist,
            album,
            COUNT(*) as trackCount
        FROM midi_tracks
        WHERE artist = :artist
        GROUP BY album
        ORDER BY album COLLATE NOCASE ASC
    """)
    suspend fun getAlbumsWithStats(artist: String): List<AlbumStats>

    /**
     * Data class pour les statistiques d'artiste
     */
    data class ArtistStats(
        val artist: String,
        val trackCount: Int,
        val albumCount: Int
    )

    /**
     * Data class pour les statistiques d'album
     */
    data class AlbumStats(
        val artist: String,
        val album: String,
        val trackCount: Int
    )

    /**
     * Récupère tous les albums d'un artiste
     */
    @Query("SELECT DISTINCT album FROM midi_tracks WHERE artist = :artist ORDER BY album COLLATE NOCASE ASC")
    fun getAlbumsByArtist(artist: String): Flow<List<String>>

    /**
     * Insère un nouveau track (ou le remplace si existe déjà)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: MidiTrack): Long

    /**
     * Insère plusieurs tracks d'un coup
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<MidiTrack>)

    /**
     * Met à jour un track existant
     */
    @Update
    suspend fun updateTrack(track: MidiTrack)

    /**
     * Supprime un track par son ID
     */
    @Query("DELETE FROM midi_tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    /**
     * Supprime tous les tracks
     */
    @Query("DELETE FROM midi_tracks")
    suspend fun deleteAllTracks()

    /**
     * Compte le nombre total de tracks
     */
    @Query("SELECT COUNT(*) FROM midi_tracks")
    suspend fun getTrackCount(): Int

    /**
     * Supprime les doublons (garde le plus récent de chaque filePath)
     * Retourne le nombre de doublons supprimés
     */
    @Query("""
        DELETE FROM midi_tracks
        WHERE id NOT IN (
            SELECT MAX(id)
            FROM midi_tracks
            GROUP BY filePath
        )
    """)
    suspend fun removeDuplicates(): Int

    /**
     * Récupère tous les chemins de fichiers (pour scan incrémental)
     */
    @Query("SELECT filePath FROM midi_tracks")
    suspend fun getAllFilePaths(): List<String>

    /**
     * Supprime les tracks dont le filePath est dans la liste donnée
     */
    @Query("DELETE FROM midi_tracks WHERE filePath IN (:filePaths)")
    suspend fun deleteTracksByFilePaths(filePaths: List<String>)
}
