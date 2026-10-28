package com.Atom2Universe.app.music

import android.content.Context
import android.net.Uri
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.model.Album
import com.Atom2Universe.app.music.model.Artist
import com.Atom2Universe.app.music.model.Folder
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

object MusicLibrary {

    // Lock pour synchroniser les opérations sur la bibliothèque
    private val libraryLock = Any()

    @Volatile
    private var allTracks: List<MusicTrack> = emptyList()
    @Volatile
    private var artists: List<Artist> = emptyList()
    @Volatile
    private var albumArtists: List<Artist> = emptyList()  // Organisé par Album Artist
    @Volatile
    private var allAlbums: List<Album> = emptyList()       // Tous les albums
    @Volatile
    private var folderTree: Folder? = null                 // Arborescence des dossiers

    // Ordre dans lequel les albums des artistes sont actuellement triés (null = à retrier)
    @Volatile
    private var albumsSortedFor: AlbumSortOrder? = null

    // Albums dont l'année a déjà été lue dans les tags depuis la dernière organisation
    private val yearCheckedAlbums = ConcurrentHashMap.newKeySet<String>()

    // Index chemin/URI -> album pour les pochettes, reconstruit après chaque changement
    private class ArtworkIndex(
        val byPath: Map<String, Album>,
        val byUriWithoutPath: Map<Uri, Album>,
        val byUri: Map<Uri, Album>
    )
    @Volatile
    private var artworkIndex: ArtworkIndex? = null

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val LEADING_NUMBER = Regex("^(\\d+)")

    // Clés de tri des noms (albums, artistes, dossiers) calculées une seule fois
    private val sortKeyCache = ConcurrentHashMap<String, String>()
    private val artistSortKeyCache = ConcurrentHashMap<String, String>()

    // Strings de fallback localisées
    private var unknownArtist: String = "Unknown Artist"
    private var unknownAlbum: String = "Unknown Album"

    fun init(context: Context) {
        unknownArtist = context.getString(R.string.music_unknown_artist)
        unknownAlbum = context.getString(R.string.music_unknown_album)
    }

    // Options de tri pour les albums
    enum class AlbumSortOrder {
        NAME_ASC,      // A-Z
        NAME_DESC,     // Z-A
        YEAR_ASC,      // Plus ancien d'abord
        YEAR_DESC      // Plus récent d'abord
    }

    var currentAlbumSortOrder = AlbumSortOrder.NAME_ASC
        private set

    /**
     * Removes diacritics (accents) from a string for sorting purposes.
     * e.g., "Tété" -> "Tete", "Björk" -> "Bjork"
     */
    fun removeAccents(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace(COMBINING_MARKS, "")
    }

    private fun sortKey(text: String): String =
        sortKeyCache.getOrPut(text) { removeAccents(text).lowercase() }

    private fun artistSortKey(name: String): String =
        artistSortKeyCache.getOrPut(name) { getArtistSortName(name).lowercase() }

    /** Tri stable par nom sans accents, clé calculée une fois par élément. */
    private inline fun <T> sortByAccentlessText(list: MutableList<T>, crossinline text: (T) -> String) {
        if (list.size < 2) return
        val sorted = list.map { it to removeAccents(text(it)).lowercase() }.sortedBy { it.second }
        for (i in sorted.indices) list[i] = sorted[i].first
    }

    /**
     * Returns the sort name for an artist:
     * - Ignores "The " prefix (e.g., "The Rolling Stones" -> "Rolling Stones")
     * - Removes accents (e.g., "Tété" -> "Tete")
     */
    fun getArtistSortName(name: String): String {
        val withoutThe = if (name.lowercase().startsWith("the ")) {
            name.substring(4)
        } else {
            name
        }
        return removeAccents(withoutThe)
    }

    suspend fun setTracks(tracks: List<MusicTrack>) {
        allTracks = tracks
        organizeLibrary()
    }

    /**
     * Restaure la bibliothèque depuis un cache pré-organisé.
     * Évite d'appeler organizeLibrary() qui est coûteux avec beaucoup de pistes.
     */
    fun restoreFromCache(cached: MusicLibraryCache.CachedLibrary) {
        allTracks = cached.allTracks
        artists = cached.artists
        albumArtists = cached.albumArtists
        allAlbums = sortedByName(cached.allAlbums)
        folderTree = cached.folderTree
        currentAlbumSortOrder = cached.sortOrder
        // Le premier setAlbumSortOrder() retrie une fois, ensuite seulement si l'ordre change
        albumsSortedFor = null
        artworkIndex = null
        yearCheckedAlbums.clear()
    }

    /**
     * Sauvegarde l'état actuel de la bibliothèque dans le cache.
     */
    suspend fun saveToCache(context: Context) {
        MusicLibraryCache.saveToCache(
            context = context,
            allTracks = allTracks,
            artists = artists,
            albumArtists = albumArtists,
            allAlbums = allAlbums,
            folderTree = folderTree,
            sortOrder = currentAlbumSortOrder
        )
    }

    fun getAllTracks(): List<MusicTrack> = allTracks

    fun getArtists(): List<Artist> = artists

    fun getAlbumArtists(): List<Artist> = albumArtists

    fun getAllAlbums(): List<Album> = allAlbums

    /** Resolve the same album for artwork in artist views and playback, including compilations. */
    internal fun getArtworkAlbum(track: MusicTrack): Album? {
        val index = artworkIndex ?: synchronized(libraryLock) {
            artworkIndex ?: buildArtworkIndex().also { artworkIndex = it }
        }
        val path = track.filePath
        val candidate = if (!path.isNullOrBlank()) {
            index.byPath[path] ?: index.byUriWithoutPath[track.uri]
        } else {
            index.byUri[track.uri]
        }
        return candidate?.takeIf { it.name.equals(track.album, ignoreCase = true) }
    }

    private fun buildArtworkIndex(): ArtworkIndex {
        val byPath = HashMap<String, Album>()
        val byUriWithoutPath = HashMap<Uri, Album>()
        val byUri = HashMap<Uri, Album>()
        for (album in allAlbums) {
            for (candidate in album.tracks) {
                val candidatePath = candidate.filePath
                if (!candidatePath.isNullOrBlank()) {
                    byPath.putIfAbsent(candidatePath, album)
                } else {
                    byUriWithoutPath.putIfAbsent(candidate.uri, album)
                }
                byUri.putIfAbsent(candidate.uri, album)
            }
        }
        return ArtworkIndex(byPath, byUriWithoutPath, byUri)
    }

    fun getFolderTree(): Folder? = folderTree

    /**
     * Finds a folder by its path in the folder tree.
     * @param path The absolute path of the folder to find
     * @return The folder at the given path, or null if not found
     */
    fun getFolderAtPath(path: String): Folder? {
        val root = folderTree ?: return null
        if (path.isEmpty() || path == root.path) return root

        fun findInFolder(folder: Folder): Folder? {
            if (folder.path == path) return folder
            for (subfolder in folder.subfolders) {
                val found = findInFolder(subfolder)
                if (found != null) return found
            }
            return null
        }

        return findInFolder(root)
    }

    /**
     * Returns the total number of folders that contain at least one track (directly or in subfolders).
     */
    fun getTotalFolderCount(): Int {
        val root = folderTree ?: return 0
        // Count folders that have tracks directly or have subfolders with tracks
        fun countFolders(folder: Folder): Int {
            var subfolderCount = 0
            for (subfolder in folder.subfolders) {
                subfolderCount += countFolders(subfolder)
            }
            val hasTracksInTree = folder.tracks.isNotEmpty() || subfolderCount > 0
            return if (hasTracksInTree) {
                1 + subfolderCount
            } else {
                0
            }
        }
        return countFolders(root)
    }

    fun getArtist(name: String): Artist? = artists.find { it.name.equals(name, ignoreCase = true) }

    fun getAlbumArtist(name: String): Artist? = albumArtists.find { it.name.equals(name, ignoreCase = true) }

    fun getAlbum(artistName: String, albumName: String): Album? {
        return getArtist(artistName)?.albums?.find { it.name == albumName }
    }

    @Suppress("unused")
    fun getTracksForAlbum(artistName: String, albumName: String): List<MusicTrack> {
        return getAlbum(artistName, albumName)?.tracks ?: emptyList()
    }

    @Suppress("unused")
    fun getAlbumsForArtist(artistName: String): List<Album> {
        return getArtist(artistName)?.albums ?: emptyList()
    }

    /**
     * Bug fix: Utilise synchronized(libraryLock) pour éviter les race conditions
     * lors du tri concurrent avec d'autres threads qui itèrent sur les albums.
     */
    fun setAlbumSortOrder(sortOrder: AlbumSortOrder) {
        synchronized(libraryLock) {
            currentAlbumSortOrder = sortOrder
            // Déjà trié dans cet ordre : rien à faire (appelé à chaque retour sur l'écran)
            if (albumsSortedFor == sortOrder) return
            resortArtistAlbumsLocked()
        }
    }

    /** Retrie les albums de chaque artiste ; à appeler sous libraryLock. */
    private fun resortArtistAlbumsLocked() {
        val order = currentAlbumSortOrder
        artists.forEach { artist ->
            sortAlbums(artist.albums, order)
        }
        albumArtists.forEach { artist ->
            sortAlbums(artist.albums, order)
        }
        // Tous les albums ne suit pas ce réglage : toujours par nom, voir sortedByName
        albumsSortedFor = order
    }

    /**
     * Ordre de la section Tous les albums : toujours alphabétique, quel que soit le réglage
     * de tri (celui-ci ne concerne que les albums d'un artiste). La barre alphabétique de
     * cette section compte sur cet ordre.
     */
    private fun sortedByName(albums: Collection<Album>): List<Album> =
        albums.sortedBy { sortKey(it.name) }

    private fun sortAlbums(albums: MutableList<Album>, order: AlbumSortOrder) {
        when (order) {
            AlbumSortOrder.NAME_ASC -> albums.sortBy { sortKey(it.name) }
            AlbumSortOrder.NAME_DESC -> albums.sortByDescending { sortKey(it.name) }
            AlbumSortOrder.YEAR_ASC -> albums.sortWith(
                compareBy<Album> { it.year ?: Int.MAX_VALUE }.thenBy { sortKey(it.name) }
            )
            AlbumSortOrder.YEAR_DESC -> albums.sortWith(
                compareByDescending<Album> { it.year ?: Int.MIN_VALUE }.thenBy { sortKey(it.name) }
            )
        }
    }

    /**
     * Extrait le numéro de piste effectif d'un track.
     * Priorité : tag trackNumber > numéro au début du nom de fichier > null
     */
    private fun getEffectiveTrackNumber(track: MusicTrack): Int? {
        // 1. Utiliser le tag trackNumber s'il existe
        track.trackNumber?.let { return it }

        // 2. Essayer d'extraire le numéro au début du nom de fichier
        val filePath = track.filePath ?: return null
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val match = LEADING_NUMBER.find(fileName)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Trie les pistes d'un album par numéro de disque, puis par numéro de piste, puis par titre.
     * Pour les albums multi-disques (ex: disque 1/2 et 2/2), affiche toutes les pistes
     * du disque 1 d'abord (1, 2, 3...) puis toutes les pistes du disque 2 (1, 2, 3...).
     */
    private class TrackSortKey(val track: MusicTrack, val disc: Int?, val number: Int?, val title: String)

    private fun sortTracksInAlbum(tracks: MutableList<MusicTrack>) {
        if (tracks.size < 2) return
        // Numéros et titres calculés une fois par piste, pas à chaque comparaison
        val keyed = tracks.map { TrackSortKey(it, it.discNumber, getEffectiveTrackNumber(it), it.title.lowercase()) }
        val sorted = keyed.sortedWith { a, b ->
            val discA = a.disc
            val discB = b.disc
            val numA = a.number
            val numB = b.number

            when {
                // Si les deux ont un numéro de disque différent, trier par disque
                discA != null && discB != null && discA != discB -> discA.compareTo(discB)
                // Sinon, trier par numéro de piste
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1  // a a un numéro, b non -> a en premier
                numB != null -> 1   // b a un numéro, a non -> b en premier
                // Aucun numéro, trier par titre
                else -> a.title.compareTo(b.title)
            }
        }
        for (i in sorted.indices) tracks[i] = sorted[i].track
    }

    private suspend fun organizeLibrary() = withContext(Dispatchers.Default) {
        // Construire toutes les structures dans des variables locales
        // puis faire un swap atomique à la fin pour la thread-safety
        val order = currentAlbumSortOrder
        val artistMap = mutableMapOf<String, Artist>()
        val albumArtistMap = mutableMapOf<String, Artist>()
        val albumsMap = mutableMapOf<String, Album>()  // Clé: "albumName|albumArtist" pour unicité

        // Maps internes pour lookup O(1) des albums par artiste (évite O(n²))
        val artistAlbumMaps = mutableMapOf<String, MutableMap<String, Album>>()
        val albumArtistAlbumMaps = mutableMapOf<String, MutableMap<String, Album>>()

        // Copie locale de allTracks pour éviter les modifications concurrentes
        val tracksSnapshot = allTracks.toList()

        for (track in tracksSnapshot) {
            val artistName = track.artist.ifBlank { unknownArtist }
            val albumName = track.album.ifBlank { unknownAlbum }

            // Album Artist: utilise uniquement si le tag ALBUM_ARTIST est renseigné
            // Sur Android < 11, le tag sera lu depuis les fichiers lors du deep scan (fixMissingAlbumArtists)
            val albumArtistName = track.albumArtist?.takeIf { it.isNotBlank() }

            // Clés normalisées (insensibles à la casse) pour le regroupement
            // "Rolling Stones" et "rolling stones" → même entrée dans la map
            val artistKey = artistName.lowercase()
            val albumKey = albumName.lowercase()
            val albumArtistKey = albumArtistName?.lowercase()

            // === Organisation par Artiste (existant) ===
            val artist = artistMap.getOrPut(artistKey) {
                artistAlbumMaps[artistKey] = mutableMapOf()
                Artist(name = artistName)  // Nom d'affichage = première occurrence
            }

            // Lookup O(1) au lieu de O(n)
            val artistAlbums = artistAlbumMaps[artistKey]!!
            var album = artistAlbums[albumKey]
            if (album == null) {
                album = Album(
                    id = track.id,
                    name = albumName,
                    artist = artist.name,
                    albumArtUri = track.albumArtUri,
                    year = track.year
                )
                artist.albums.add(album)
                artistAlbums[albumKey] = album
            } else {
                if (album.year == null && track.year != null) {
                    album.year = track.year
                }
            }
            album.tracks.add(track)
            artist.trackCount++

            // === Organisation par Album Artist ===
            // Ne créer un album artist que si albumArtistName est défini
            if (albumArtistKey != null) {
                val albumArtist = albumArtistMap.getOrPut(albumArtistKey) {
                    albumArtistAlbumMaps[albumArtistKey] = mutableMapOf()
                    Artist(name = albumArtistName!!)  // Nom d'affichage = première occurrence
                }

                // Lookup O(1) au lieu de O(n)
                val albumArtistAlbums = albumArtistAlbumMaps[albumArtistKey]!!
                var albumByArtist = albumArtistAlbums[albumKey]
                if (albumByArtist == null) {
                    albumByArtist = Album(
                        id = track.id,
                        name = albumName,
                        artist = albumArtist.name,
                        albumArtUri = track.albumArtUri,
                        year = track.year
                    )
                    albumArtist.albums.add(albumByArtist)
                    albumArtistAlbums[albumKey] = albumByArtist
                } else {
                    if (albumByArtist.year == null && track.year != null) {
                        albumByArtist.year = track.year
                    }
                }
                albumByArtist.tracks.add(track)
                albumArtist.trackCount++
            }

            // === Liste de tous les albums (par nom d'album + artiste) ===
            // Utilise albumArtist si disponible, sinon artist (pour la clé unique)
            val albumKeyArtist = albumArtistName ?: artistName
            val albumKeyArtistKey = albumArtistKey ?: artistKey
            val globalAlbumKey = "$albumKey|$albumKeyArtistKey"
            val globalAlbum = albumsMap.getOrPut(globalAlbumKey) {
                Album(
                    id = track.id,
                    name = albumName,
                    artist = albumKeyArtist,
                    albumArtUri = track.albumArtUri,
                    year = track.year
                )
            }
            if (globalAlbum.year == null && track.year != null) {
                globalAlbum.year = track.year
            }
            globalAlbum.tracks.add(track)
        }

        // Trier les pistes et albums pour les artistes
        artistMap.values.forEach { artist ->
            artist.albums.forEach { album ->
                sortTracksInAlbum(album.tracks)
            }
            sortAlbums(artist.albums, order)
        }

        // Trier les pistes et albums pour les album artists
        albumArtistMap.values.forEach { artist ->
            artist.albums.forEach { album ->
                sortTracksInAlbum(album.tracks)
            }
            sortAlbums(artist.albums, order)
        }

        // Trier les pistes pour tous les albums
        albumsMap.values.forEach { album ->
            sortTracksInAlbum(album.tracks)
        }

        // Construire les listes finales triées
        val newArtists = artistMap.values.sortedBy { artistSortKey(it.name) }
        val newAlbumArtists = albumArtistMap.values.sortedBy { artistSortKey(it.name) }
        val newAllAlbums = sortedByName(albumsMap.values)

        // Construire le folder tree
        val newFolderTree = buildFolderTreeInternal(tracksSnapshot)

        // Swap atomique de toutes les structures sous le lock
        synchronized(libraryLock) {
            artists = newArtists
            albumArtists = newAlbumArtists
            allAlbums = newAllAlbums
            folderTree = newFolderTree
            albumsSortedFor = order
            artworkIndex = null
            // Les années lues dans les tags étaient portées par les anciens albums
            yearCheckedAlbums.clear()
        }
    }

    /**
     * Builds a hierarchical folder tree from track file paths.
     * Only folders containing at least one audio file are included.
     * Version interne qui retourne le folder tree sans modifier l'état global.
     */
    private fun buildFolderTreeInternal(tracks: List<MusicTrack>): Folder {
        val rootFolder = Folder(path = "", name = "Root")
        val folderMap = mutableMapOf<String, Folder>()

        for (track in tracks) {
            val filePath = track.filePath ?: continue
            // Extract parent directory path - handle both Unix and Windows paths
            val parentPath = filePath.substringBeforeLast('/').substringBeforeLast('\\')
            if (parentPath.isEmpty() || parentPath == filePath) continue

            // Create intermediate folders if necessary
            var currentPath = ""
            var parentFolder = rootFolder
            val separator = if (filePath.contains('\\')) '\\' else '/'

            for (segment in parentPath.split('/', '\\').filter { it.isNotEmpty() }) {
                currentPath = if (currentPath.isEmpty()) {
                    // For Windows paths like "C:", keep just the segment
                    // For Unix paths, add leading slash
                    if (segment.endsWith(":")) segment else "/$segment"
                } else {
                    "$currentPath$separator$segment"
                }

                val existingFolder = folderMap[currentPath]
                if (existingFolder != null) {
                    parentFolder = existingFolder
                } else {
                    val newFolder = Folder(path = currentPath, name = segment)
                    folderMap[currentPath] = newFolder
                    parentFolder.subfolders.add(newFolder)
                    parentFolder = newFolder
                }
            }

            // Add the track to its parent folder
            parentFolder.tracks.add(track)
        }

        // Sort subfolders and tracks in each folder
        fun sortFolderContents(folder: Folder) {
            folder.subfolders.sortBy { sortKey(it.name) }
            sortByAccentlessText(folder.tracks) { it.title }
            folder.subfolders.forEach { sortFolderContents(it) }
        }
        sortFolderContents(rootFolder)

        return rootFolder
    }

    /**
     * Corrige les album artists manquants en lisant directement les tags ID3.
     * Sur Android < 11, MediaStore ne peut pas lire ALBUM_ARTIST, donc il faut le lire depuis les fichiers.
     * Appelé manuellement lors d'un refresh complet (bouton refresh).
     * Optimisé : traite seulement les premiers fichiers de chaque album pour éviter les scans longs.
     * @return true si des album artists ont été trouvés et que la bibliothèque a été réorganisée
     */
    suspend fun fixMissingAlbumArtists(progressCallback: ((Int, Int) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        // Sur Android 11+, MediaStore lit déjà ALBUM_ARTIST correctement
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return@withContext false
        }

        // Collecte les albums sans album artist et prend une piste représentative par album
        val albumsToCheck: List<Pair<String, MusicTrack>>  // Pair<albumName, representativeTrack>
        synchronized(libraryLock) {
            val albumMap = mutableMapOf<String, MusicTrack>()
            for (track in allTracks) {
                if (track.filePath.isNullOrBlank() || !track.albumArtist.isNullOrBlank()) continue

                val albumName = track.album
                // Prend la première piste de chaque album seulement
                if (!albumMap.containsKey(albumName)) {
                    albumMap[albumName] = track
                }
            }
            albumsToCheck = albumMap.toList()
        }

        if (albumsToCheck.isEmpty()) return@withContext false

        // Lit les album artists depuis les tags ID3 (un fichier par album seulement)
        val albumArtistByAlbumName = mutableMapOf<String, String>()  // albumName -> albumArtist

        var processed = 0
        for ((albumName, track) in albumsToCheck) {
            val albumArtistFromTags = MusicTagEditor.readAlbumArtistFromFile(track.filePath!!)
            if (albumArtistFromTags != null) {
                albumArtistByAlbumName[albumName] = albumArtistFromTags
            }

            processed++
            progressCallback?.invoke(processed, albumsToCheck.size)

            // Yield pour éviter de bloquer trop longtemps
            if (processed % 10 == 0) {
                yield()
            }
        }

        // Si aucun album artist trouvé, ne rien faire
        if (albumArtistByAlbumName.isEmpty()) return@withContext false

        // Met à jour toutes les pistes des albums concernés
        synchronized(libraryLock) {
            val updatedTracks = allTracks.map { track ->
                val albumArtist = albumArtistByAlbumName[track.album]
                if (albumArtist != null && track.albumArtist.isNullOrBlank()) {
                    track.copy(albumArtist = albumArtist)
                } else {
                    track
                }
            }
            allTracks = updatedTracks
        }

        // Réorganise complètement la bibliothèque avec les nouveaux album artists
        organizeLibrary()
        return@withContext true
    }

    /**
     * Corrige les années manquantes dans les albums en lisant directement les tags ID3.
     * MediaStore ne lit pas toujours correctement le champ année (notamment ID3v2.4 TDRC).
     * Lit un fichier par album sur un fil d'E/S, une seule fois par album tant que la
     * bibliothèque n'est pas réorganisée, puis retrie les albums si des années ont changé.
     */
    suspend fun fixMissingYears() = withContext(Dispatchers.IO) {
        // Collecte tous les albums sans année sous le lock
        val albumsWithoutYear: List<Album>
        synchronized(libraryLock) {
            val tempList = mutableListOf<Album>()
            artists.forEach { artist ->
                tempList.addAll(artist.albums.filter { it.year == null })
            }
            albumArtists.forEach { artist ->
                tempList.addAll(artist.albums.filter { it.year == null })
            }
            tempList.addAll(allAlbums.filter { it.year == null })
            albumsWithoutYear = tempList.toList()
        }

        if (albumsWithoutYear.isEmpty()) return@withContext

        // Collecte les années à mettre à jour (hors du lock pour éviter les I/O sous lock)
        val yearUpdates = mutableMapOf<String, Int>()  // albumKey -> year
        val processedAlbums = mutableSetOf<String>()

        for (album in albumsWithoutYear) {
            val albumKey = "${album.name}|${album.artist}"
            if (!processedAlbums.add(albumKey)) continue
            // Déjà lu sans succès depuis la dernière organisation
            if (!yearCheckedAlbums.add(albumKey)) continue
            ensureActive()

            // Trouve une piste avec un filePath
            val trackWithPath = album.tracks.firstOrNull { !it.filePath.isNullOrBlank() }
            if (trackWithPath == null) continue

            // Lit l'année directement depuis les tags ID3 (I/O)
            val yearFromTags = MusicTagEditor.readYearFromFile(trackWithPath.filePath!!)
            if (yearFromTags != null) {
                yearUpdates[albumKey] = yearFromTags
            }
        }

        // Applique toutes les mises à jour sous le lock
        if (yearUpdates.isNotEmpty()) {
            synchronized(libraryLock) {
                for ((albumKey, year) in yearUpdates) {
                    val parts = albumKey.split("|", limit = 2)
                    if (parts.size == 2) {
                        updateAlbumYearEverywhere(parts[0], parts[1], year)
                    }
                }
                // Les années ont changé : le tri par année doit être refait
                resortArtistAlbumsLocked()
            }
        }
    }

    /**
     * Met à jour l'année d'un album partout où il apparaît.
     */
    private fun updateAlbumYearEverywhere(albumName: String, artistName: String, year: Int) {
        // Met à jour dans la liste des artistes
        artists.forEach { artist ->
            artist.albums.filter { it.name == albumName }.forEach { it.year = year }
        }
        // Met à jour dans la liste des album artists
        albumArtists.forEach { artist ->
            artist.albums.filter { it.name == albumName && it.artist == artistName }.forEach { it.year = year }
        }
        // Met à jour dans tous les albums
        allAlbums.filter { it.name == albumName && it.artist == artistName }.forEach { it.year = year }
    }

    /**
     * Met à jour un track dans la bibliothèque.
     * Si l'artiste ou l'album change, réorganise la bibliothèque.
     * @return true si une réorganisation complète a été nécessaire
     */
    suspend fun updateTrack(oldTrack: MusicTrack, newTrack: MusicTrack): Boolean {
        val needsReorganization = oldTrack.artist != newTrack.artist ||
                oldTrack.album != newTrack.album ||
                oldTrack.albumArtist != newTrack.albumArtist

        // Remplace le track dans la liste
        allTracks = allTracks.map { if (it.id == oldTrack.id) newTrack else it }

        if (needsReorganization) {
            organizeLibrary()
            return true
        }

        // Mise à jour in-place dans les structures existantes
        updateTrackInPlace(oldTrack, newTrack)
        return false
    }

    /**
     * Met à jour plusieurs tracks dans la bibliothèque.
     * @return true si une réorganisation complète a été nécessaire
     */
    suspend fun updateTracks(updates: List<Pair<MusicTrack, MusicTrack>>): Boolean {
        if (updates.isEmpty()) return false

        val needsReorganization = updates.any { (oldTrack, newTrack) ->
            oldTrack.artist != newTrack.artist ||
                    oldTrack.album != newTrack.album ||
                    oldTrack.albumArtist != newTrack.albumArtist
        }

        // Crée une map des mises à jour pour un accès rapide
        val updateMap = updates.associate { (old, new) -> old.id to new }

        // Remplace tous les tracks dans la liste
        allTracks = allTracks.map { track ->
            updateMap[track.id] ?: track
        }

        if (needsReorganization) {
            organizeLibrary()
            return true
        }

        // Mise à jour in-place dans les structures existantes
        for ((oldTrack, newTrack) in updates) {
            updateTrackInPlace(oldTrack, newTrack)
        }
        return false
    }

    /**
     * Met à jour un track dans les structures Artist/Album sans réorganisation.
     * Bug fix: Met à jour dans artists, albumArtists ET allAlbums (pas de return prématuré).
     */
    private fun updateTrackInPlace(oldTrack: MusicTrack, newTrack: MusicTrack) {
        // Mise à jour dans la hiérarchie des artistes
        for (artist in artists) {
            for (album in artist.albums) {
                val index = album.tracks.indexOfFirst { it.id == oldTrack.id }
                if (index >= 0) {
                    album.tracks[index] = newTrack
                }
            }
        }

        // Mise à jour dans la hiérarchie des album artists
        for (artist in albumArtists) {
            for (album in artist.albums) {
                val index = album.tracks.indexOfFirst { it.id == oldTrack.id }
                if (index >= 0) {
                    album.tracks[index] = newTrack
                }
            }
        }

        // Mise à jour dans la liste globale des albums
        for (album in allAlbums) {
            val index = album.tracks.indexOfFirst { it.id == oldTrack.id }
            if (index >= 0) {
                album.tracks[index] = newTrack
            }
        }

        // Mise à jour dans le folder tree
        folderTree?.let { root ->
            updateTrackInFolderTree(root, oldTrack, newTrack)
        }

        // Chemin ou URI ont pu changer
        synchronized(libraryLock) { artworkIndex = null }
    }

    /**
     * Met à jour un track récursivement dans l'arborescence des dossiers.
     */
    private fun updateTrackInFolderTree(folder: Folder, oldTrack: MusicTrack, newTrack: MusicTrack) {
        val index = folder.tracks.indexOfFirst { it.id == oldTrack.id }
        if (index >= 0) {
            folder.tracks[index] = newTrack
        }
        for (subfolder in folder.subfolders) {
            updateTrackInFolderTree(subfolder, oldTrack, newTrack)
        }
    }

    /**
     * Trouve un track par son ID
     */
    fun getTrackById(trackId: Long): MusicTrack? {
        return allTracks.find { it.id == trackId }
    }

    /**
     * Trouve un track par son filePath.
     * Utile quand l'ID MediaStore a pu changer (après édition de tags).
     */
    fun getTrackByFilePath(filePath: String): MusicTrack? {
        return allTracks.find { it.filePath == filePath }
    }

    fun clear() {
        allTracks = emptyList()
        artists = emptyList()
        albumArtists = emptyList()
        allAlbums = emptyList()
        folderTree = null
        albumsSortedFor = null
        artworkIndex = null
        yearCheckedAlbums.clear()
    }

    fun getTotalTrackCount(): Int = allTracks.size

    fun getTotalArtistCount(): Int = artists.size

    fun getTotalAlbumArtistCount(): Int = albumArtists.size

    fun getTotalAlbumCount(): Int = allAlbums.size
}
