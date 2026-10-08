package com.Atom2Universe.app.midi.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.Atom2Universe.app.midi.data.MidiDatabase
import com.Atom2Universe.app.midi.data.MidiTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository pour gérer les opérations sur les tracks MIDI
 * Encapsule l'accès au DAO et fournit une API haut niveau
 */
class MidiRepository(private val context: Context) {

    private val database = MidiDatabase.getInstance(context)
    private val midiTrackDao = database.midiTrackDao()

    /**
     * Récupère tous les tracks (Flow pour observation reactive)
     */
    val allTracks: Flow<List<MidiTrack>> = midiTrackDao.getAllTracks()

    /**
     * Récupère tous les artistes uniques
     */
    val allArtists: Flow<List<String>> = midiTrackDao.getAllArtists()

    /**
     * Récupère les artistes avec statistiques (nombre de titres/albums)
     */
    suspend fun getArtistsWithStats(): List<com.Atom2Universe.app.midi.data.ArtistItem> {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getArtistsWithStats().map {
                com.Atom2Universe.app.midi.data.ArtistItem(
                    name = it.artist,
                    trackCount = it.trackCount,
                    albumCount = it.albumCount
                )
            }
        }
    }

    /**
     * Récupère les albums d'un artiste avec statistiques
     */
    suspend fun getAlbumsWithStats(artist: String): List<com.Atom2Universe.app.midi.data.AlbumItem> {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getAlbumsWithStats(artist).map {
                com.Atom2Universe.app.midi.data.AlbumItem(
                    artist = it.artist,
                    album = it.album,
                    trackCount = it.trackCount
                )
            }
        }
    }

    /**
     * Récupère les tracks d'un artiste donné (Flow)
     */
    fun getTracksByArtist(artist: String): Flow<List<MidiTrack>> {
        return midiTrackDao.getTracksByArtist(artist)
    }

    /**
     * Récupère les tracks d'un artiste donné (Direct, suspend)
     */
    suspend fun getTracksByArtistDirect(artist: String): List<MidiTrack> {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getTracksByArtistDirect(artist)
        }
    }

    /**
     * Récupère tous les tracks (Direct, suspend - pour shuffle library)
     */
    suspend fun getAllTracksDirect(): List<MidiTrack> {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getAllTracksDirect()
        }
    }

    /**
     * Récupère les albums d'un artiste
     */
    fun getAlbumsByArtist(artist: String): Flow<List<String>> {
        return midiTrackDao.getAlbumsByArtist(artist)
    }

    /**
     * Récupère les tracks d'un album
     */
    fun getTracksByAlbum(artist: String, album: String): Flow<List<MidiTrack>> {
        return midiTrackDao.getTracksByAlbum(artist, album)
    }

    /**
     * Récupère un track par son ID
     */
    suspend fun getTrackById(trackId: Long): MidiTrack? {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getTrackById(trackId)
        }
    }

    /**
     * Récupère un track par son chemin de fichier (pour détecter doublons)
     */
    suspend fun getTrackByFilePath(filePath: String): MidiTrack? {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getTrackByFilePath(filePath)
        }
    }

    /**
     * Ajoute un nouveau track à la bibliothèque
     */
    suspend fun addTrack(track: MidiTrack): Long {
        return withContext(Dispatchers.IO) {
            midiTrackDao.insertTrack(track)
        }
    }

    /**
     * Ajoute plusieurs tracks d'un coup (bulk insert)
     */
    suspend fun addTracks(tracks: List<MidiTrack>) {
        withContext(Dispatchers.IO) {
            midiTrackDao.insertTracks(tracks)
        }
    }

    /**
     * Met à jour un track existant
     */
    suspend fun updateTrack(track: MidiTrack) {
        withContext(Dispatchers.IO) {
            midiTrackDao.updateTrack(track)
        }
    }

    /**
     * Supprime un track
     */
    suspend fun deleteTrack(trackId: Long) {
        withContext(Dispatchers.IO) {
            midiTrackDao.deleteTrack(trackId)
        }
    }

    /**
     * Supprime tous les tracks de la bibliothèque
     */
    suspend fun deleteAllTracks() {
        withContext(Dispatchers.IO) {
            midiTrackDao.deleteAllTracks()
        }
    }

    /**
     * Efface toute la bibliothèque (alias pour deleteAllTracks)
     */
    suspend fun clearAllTracks() {
        deleteAllTracks()
    }

    /**
     * Supprime les doublons (garde le plus récent de chaque filePath)
     * @return Nombre de doublons supprimés
     */
    suspend fun removeDuplicates(): Int {
        return withContext(Dispatchers.IO) {
            midiTrackDao.removeDuplicates()
        }
    }

    /**
     * Récupère tous les chemins de fichiers existants (pour scan incrémental)
     */
    suspend fun getAllFilePaths(): Set<String> {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getAllFilePaths().toSet()
        }
    }

    /**
     * Supprime les tracks dont le fichier n'existe plus
     * Découpe en lots de 900 pour respecter la limite SQLite de paramètres bind
     */
    suspend fun deleteTracksByFilePaths(filePaths: List<String>) {
        withContext(Dispatchers.IO) {
            filePaths.chunked(900).forEach { batch ->
                midiTrackDao.deleteTracksByFilePaths(batch)
            }
        }
    }

    /**
     * Compte le nombre total de tracks
     */
    suspend fun getTrackCount(): Int {
        return withContext(Dispatchers.IO) {
            midiTrackDao.getTrackCount()
        }
    }

    /**
     * Scanne un dossier MIDI et ajoute tous les fichiers trouvés
     * @param folderUri URI du dossier à scanner
     * @return Nombre de fichiers MIDI trouvés et ajoutés
     */
    suspend fun scanMidiFolder(folderUri: Uri): Int {
        return withContext(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext 0

            val midiFiles = mutableListOf<MidiTrack>()
            scanRecursive(documentFile, midiFiles)

            if (midiFiles.isNotEmpty()) {
                midiTrackDao.insertTracks(midiFiles)
            }

            midiFiles.size
        }
    }

    /**
     * Scan récursif d'un dossier pour trouver tous les fichiers MIDI
     */
    private fun scanRecursive(folder: DocumentFile, results: MutableList<MidiTrack>) {
        folder.listFiles().forEach { file ->
            when {
                file.isDirectory -> scanRecursive(file, results)
                file.isFile && isMidiFile(file.name) -> {
                    parseMidiFile(file)?.let { results.add(it) }
                }
            }
        }
    }

    /**
     * Vérifie si un fichier est un fichier MIDI (.mid ou .midi)
     */
    private fun isMidiFile(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.endsWith(".mid") || lower.endsWith(".midi")
    }

    /**
     * Parse un fichier MIDI et crée un MidiTrack
     * Format attendu du nom: "Artist - Album - Title.mid"
     */
    private fun parseMidiFile(file: DocumentFile): MidiTrack? {
        val uri = file.uri
        val displayName = file.name ?: return null
        val size = file.length()

        // Retire l'extension (insensible à la casse)
        val nameWithoutExt = displayName.replace(Regex("\\.midi?$", RegexOption.IGNORE_CASE), "")

        // Parse le nom selon les patterns supportés
        val parts = nameWithoutExt.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        val (artist, album, title) = when (parts.size) {
            3 -> Triple(parts[0], parts[1], parts[2])
            2 -> Triple(parts[0], parts[0], parts[1])
            1 -> Triple("Unknown Artist", "Unknown Album", parts[0])
            else -> Triple("Unknown Artist", "Unknown Album", nameWithoutExt)
        }

        return MidiTrack(
            title = title,
            artist = artist,
            album = album,
            filePath = uri.toString(),
            duration = 0L, // TODO: Parse MIDI file pour obtenir durée réelle
            dateAdded = System.currentTimeMillis(),
            fileSize = size
        )
    }
}
