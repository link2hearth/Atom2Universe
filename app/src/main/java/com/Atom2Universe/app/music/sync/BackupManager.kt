package com.Atom2Universe.app.music.sync

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.Atom2Universe.app.music.AlbumFavoritesManager
import com.Atom2Universe.app.music.ArtistCustomizationManager
import com.Atom2Universe.app.music.MusicFavoritesManager
import com.Atom2Universe.app.music.MusicPlaylistManager
import com.Atom2Universe.app.music.MusicPreferences
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.data.PlayCountEntry
import com.Atom2Universe.app.music.sync.algorithm.LyricsMerger
import com.Atom2Universe.app.music.sync.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Manager for full backup and restore operations to Google Drive.
 *
 * Backup includes:
 * - Play counts (absolute values)
 * - Track favorites
 * - Album favorites
 * - Artist customizations (favorites, colors, images)
 * - Playlists
 * - User preferences
 * - Lyrics
 *
 * Only the "primary device" uploads the full backup during daily sync.
 * Any device can restore from the backup.
 */
object BackupManager {

    private const val TAG = "BackupManager"

    // Prefix for artist image files on Drive
    private const val ARTIST_IMAGE_PREFIX = "artist_img_"

    /**
     * Result of a backup operation.
     */
    sealed class BackupResult {
        object Success : BackupResult()
        object NotSignedIn : BackupResult()
        object NotPrimaryDevice : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    /**
     * Result of a restore operation.
     */
    sealed class RestoreResult {
        data class Success(val summary: RestoreSummary) : RestoreResult()
        object NotSignedIn : RestoreResult()
        object NoBackupFound : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    /**
     * Summary of what was restored.
     */
    data class RestoreSummary(
        val playCountsRestored: Int,
        val trackFavoritesRestored: Int,
        val albumFavoritesRestored: Int,
        val artistCustomizationsRestored: Int,
        val playlistsRestored: Int,
        val artistImagesRestored: Int,
        val lyricsRestored: Int,
        val preferencesRestored: Boolean
    )

    /**
     * Checks if this device is the primary device for backups.
     */
    suspend fun isPrimaryDevice(context: Context): Boolean {
        val googleSignInManager = GoogleSignInManager(context)
        if (!googleSignInManager.isSignedIn()) return false

        val account = googleSignInManager.getSignedInAccount() ?: return false
        val driveClient = GoogleDriveAppDataClient(context, account)

        val manifestJson = driveClient.readJsonFile("sync_manifest.json") ?: return false
        val manifest = try {
            SyncManifest.fromJson(JSONObject(manifestJson))
        } catch (_: Exception) {
            return false
        }

        val deviceId = DeviceIdentity.getDeviceId(context)
        return manifest.primaryDeviceId == deviceId
    }

    /**
     * Sets this device as the primary device for backups.
     */
    suspend fun setPrimaryDevice(context: Context, isPrimary: Boolean): Boolean {
        val googleSignInManager = GoogleSignInManager(context)
        if (!googleSignInManager.isSignedIn()) return false

        val account = googleSignInManager.getSignedInAccount() ?: return false
        val driveClient = GoogleDriveAppDataClient(context, account)

        val manifestJson = driveClient.readJsonFile("sync_manifest.json")
        val manifest = if (manifestJson != null) {
            try {
                SyncManifest.fromJson(JSONObject(manifestJson))
            } catch (_: Exception) {
                SyncManifest()
            }
        } else {
            SyncManifest()
        }

        val deviceId = DeviceIdentity.getDeviceId(context)
        val updatedManifest = manifest.copy(
            primaryDeviceId = if (isPrimary) deviceId else null
        )

        return driveClient.writeJsonFile("sync_manifest.json", updatedManifest.toJson().toString())
    }

    /**
     * Checks if a backup exists on Google Drive.
     */
    suspend fun checkBackupExists(context: Context): BackupManifest? {
        val googleSignInManager = GoogleSignInManager(context)
        if (!googleSignInManager.isSignedIn()) return null

        val account = googleSignInManager.getSignedInAccount() ?: return null
        val driveClient = GoogleDriveAppDataClient(context, account)

        val manifestJson = driveClient.readJsonFile(BackupManifest.FILENAME) ?: return null
        return try {
            BackupManifest.fromJson(JSONObject(manifestJson))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing backup manifest", e)
            null
        }
    }

    /**
     * Performs a full backup to Google Drive.
     * Should only be called from the primary device.
     */
    suspend fun performBackup(context: Context): BackupResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting backup...")

        val googleSignInManager = GoogleSignInManager(context)
        if (!googleSignInManager.isSignedIn()) {
            return@withContext BackupResult.NotSignedIn
        }

        val account = googleSignInManager.getSignedInAccount()
            ?: return@withContext BackupResult.NotSignedIn

        try {
            val driveClient = GoogleDriveAppDataClient(context, account)

            // Collect all data
            val playCountsData = collectPlayCounts(context)
            val albumFavoritesData = collectAlbumFavorites()
            val artistCustomizationsData = collectArtistCustomizations()
            val playlistsData = collectPlaylists()
            val preferencesData = collectPreferences(context)
            val artistImages = collectArtistImages()

            // Upload JSON files
            driveClient.writeJsonFile(
                PlayCountsBackupFile.FILENAME,
                playCountsData.toJson().toString()
            )
            Log.d(TAG, "Uploaded play counts: ${playCountsData.counts.size}")

            driveClient.writeJsonFile(
                AlbumFavoritesBackupFile.FILENAME,
                albumFavoritesData.toJson().toString()
            )
            Log.d(TAG, "Uploaded album favorites: ${albumFavoritesData.favorites.size}")

            driveClient.writeJsonFile(
                ArtistCustomizationsBackupFile.FILENAME,
                artistCustomizationsData.toJson().toString()
            )
            Log.d(TAG, "Uploaded artist customizations: ${artistCustomizationsData.customizations.size}")

            driveClient.writeJsonFile(
                PlaylistsBackupFile.FILENAME,
                playlistsData.toJson().toString()
            )
            Log.d(TAG, "Uploaded playlists: ${playlistsData.playlists.size}")

            driveClient.writeJsonFile(
                PreferencesBackupFile.FILENAME,
                preferencesData.toJson().toString()
            )
            Log.d(TAG, "Uploaded preferences")

            // Upload artist images
            val uploadedImages = uploadArtistImages(driveClient, artistImages)
            Log.d(TAG, "Uploaded ${uploadedImages.size} artist images")

            // Create and upload backup manifest
            val deviceId = DeviceIdentity.getDeviceId(context)
            val db = MusicDatabase.getInstance(context)
            val metadata = db.syncMetadataDao().get()

            // Get track favorites count
            val trackFavoritesCount = MusicFavoritesManager.getFavoritesCount()

            // Get lyrics count
            val lyricsCount = LyricsMerger.getLocalLyrics(context).size

            val backupManifest = BackupManifest(
                version = 1,
                createdAt = System.currentTimeMillis(),
                deviceId = deviceId,
                deviceName = metadata?.deviceName ?: Build.MODEL ?: "Android Device",
                contents = BackupContents(
                    playCountsCount = playCountsData.counts.size,
                    trackFavoritesCount = trackFavoritesCount,
                    albumFavoritesCount = albumFavoritesData.favorites.size,
                    artistCustomizationsCount = artistCustomizationsData.customizations.size,
                    playlistsCount = playlistsData.playlists.size,
                    artistImagesCount = uploadedImages.size,
                    lyricsCount = lyricsCount,
                    hasPreferences = true
                )
            )

            driveClient.writeJsonFile(
                BackupManifest.FILENAME,
                backupManifest.toJson().toString()
            )

            // Update sync manifest with backup timestamp
            updateSyncManifestBackupTimestamp(driveClient)

            Log.d(TAG, "Backup completed successfully")
            BackupResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            BackupResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Performs a full restore from Google Drive.
     */
    suspend fun performRestore(context: Context): RestoreResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting restore...")

        val googleSignInManager = GoogleSignInManager(context)
        if (!googleSignInManager.isSignedIn()) {
            return@withContext RestoreResult.NotSignedIn
        }

        val account = googleSignInManager.getSignedInAccount()
            ?: return@withContext RestoreResult.NotSignedIn

        try {
            val driveClient = GoogleDriveAppDataClient(context, account)

            // Check if backup exists
            val manifestJson = driveClient.readJsonFile(BackupManifest.FILENAME)
            if (manifestJson == null) {
                Log.d(TAG, "No backup found")
                return@withContext RestoreResult.NoBackupFound
            }

            // Download and restore each data type
            var playCountsRestored = 0
            var trackFavoritesRestored = 0
            var albumFavoritesRestored = 0
            var artistCustomizationsRestored = 0
            var playlistsRestored = 0
            var lyricsRestored = 0
            var preferencesRestored = false

            // Restore play counts
            val playCountsJson = driveClient.readJsonFile(PlayCountsBackupFile.FILENAME)
            if (playCountsJson != null) {
                val playCountsFile = PlayCountsBackupFile.fromJson(JSONObject(playCountsJson))
                playCountsRestored = restorePlayCounts(context, playCountsFile)
                Log.d(TAG, "Restored $playCountsRestored play counts")
            }

            // Restore album favorites
            val albumFavoritesJson = driveClient.readJsonFile(AlbumFavoritesBackupFile.FILENAME)
            if (albumFavoritesJson != null) {
                val albumFavoritesFile = AlbumFavoritesBackupFile.fromJson(JSONObject(albumFavoritesJson))
                albumFavoritesRestored = restoreAlbumFavorites(context, albumFavoritesFile)
                Log.d(TAG, "Restored $albumFavoritesRestored album favorites")
            }

            // Restore artist images FIRST (before customizations, so we can link them)
            val artistImagesRestored = restoreArtistImages(driveClient)
            Log.d(TAG, "Restored $artistImagesRestored artist images")

            // Restore artist customizations (will link to downloaded images)
            val artistCustomJson = driveClient.readJsonFile(ArtistCustomizationsBackupFile.FILENAME)
            if (artistCustomJson != null) {
                val artistCustomFile = ArtistCustomizationsBackupFile.fromJson(JSONObject(artistCustomJson))
                artistCustomizationsRestored = restoreArtistCustomizations(context, artistCustomFile)
                Log.d(TAG, "Restored $artistCustomizationsRestored artist customizations")
            }

            // Restore playlists
            val playlistsJson = driveClient.readJsonFile(PlaylistsBackupFile.FILENAME)
            if (playlistsJson != null) {
                val playlistsFile = PlaylistsBackupFile.fromJson(JSONObject(playlistsJson))
                playlistsRestored = restorePlaylists(playlistsFile)
                Log.d(TAG, "Restored $playlistsRestored playlists")
            }

            // Restore preferences
            val preferencesJson = driveClient.readJsonFile(PreferencesBackupFile.FILENAME)
            if (preferencesJson != null) {
                val preferencesFile = PreferencesBackupFile.fromJson(JSONObject(preferencesJson))
                preferencesRestored = restorePreferences(context, preferencesFile)
                Log.d(TAG, "Restored preferences: $preferencesRestored")
            }

            // Restore track favorites from favorites.json
            val favoritesJson = driveClient.readJsonFile(FavoritesSyncFile.FILENAME)
            if (favoritesJson != null) {
                val favoritesFile = FavoritesSyncFile.fromJson(JSONObject(favoritesJson))
                trackFavoritesRestored = restoreTrackFavorites(context, favoritesFile)
                Log.d(TAG, "Restored $trackFavoritesRestored track favorites")
            }

            // Restore lyrics from lyrics.json
            val lyricsJson = driveClient.readJsonFile(LyricsSyncFile.FILENAME)
            if (lyricsJson != null) {
                val lyricsFile = LyricsSyncFile.fromJson(JSONObject(lyricsJson))
                lyricsRestored = restoreLyrics(context, lyricsFile)
                Log.d(TAG, "Restored $lyricsRestored lyrics")
            }

            // Invalidate caches to force reload
            AlbumFavoritesManager.invalidateCache()
            ArtistCustomizationManager.invalidateCache()
            MusicFavoritesManager.invalidateCache()
            MusicPlaylistManager.invalidateCache()

            Log.d(TAG, "Restore completed successfully")
            RestoreResult.Success(
                RestoreSummary(
                    playCountsRestored = playCountsRestored,
                    trackFavoritesRestored = trackFavoritesRestored,
                    albumFavoritesRestored = albumFavoritesRestored,
                    artistCustomizationsRestored = artistCustomizationsRestored,
                    playlistsRestored = playlistsRestored,
                    artistImagesRestored = artistImagesRestored,
                    lyricsRestored = lyricsRestored,
                    preferencesRestored = preferencesRestored
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreResult.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== Collection Methods ====================

    private suspend fun collectPlayCounts(context: Context): PlayCountsBackupFile {
        val db = MusicDatabase.getInstance(context)
        val entries = db.playCountDao().getAllWithPlayCount()

        return PlayCountsBackupFile(
            counts = entries.map { entry ->
                PlayCountBackupEntry(
                    key = entry.metadataKey,
                    artist = entry.artist,
                    title = entry.title,
                    album = entry.album,
                    playCount = entry.playCount,
                    lastPlayed = entry.lastPlayed
                )
            }
        )
    }

    private fun collectAlbumFavorites(): AlbumFavoritesBackupFile {
        return AlbumFavoritesBackupFile(
            favorites = AlbumFavoritesManager.getFavorites().map { (artist, album) ->
                AlbumFavoriteBackupEntry(
                    artistName = artist,
                    albumName = album
                )
            }
        )
    }

    private fun collectArtistCustomizations(): ArtistCustomizationsBackupFile {
        val customizations = mutableListOf<ArtistCustomizationBackupEntry>()

        // Get all favorite artists
        val favoriteArtists = ArtistCustomizationManager.getFavoriteArtistNames()

        for (artistName in favoriteArtists) {
            val custom = ArtistCustomizationManager.getCustomization(artistName)
            if (custom != null) {
                customizations.add(
                    ArtistCustomizationBackupEntry(
                        artistName = custom.artistName,
                        isFavorite = custom.isFavorite,
                        addedToFavoritesAt = if (custom.addedToFavoritesAt > 0) {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                                .format(java.util.Date(custom.addedToFavoritesAt))
                        } else null,
                        color = custom.color,
                        imageKey = custom.iconPath?.let { generateArtistImageKey(artistName) }
                    )
                )
            }
        }

        return ArtistCustomizationsBackupFile(customizations = customizations)
    }

    private fun collectPlaylists(): PlaylistsBackupFile {
        val playlists = MusicPlaylistManager.getPlaylists()

        return PlaylistsBackupFile(
            playlists = playlists.map { playlist ->
                PlaylistBackupEntry(
                    id = playlist.id,
                    name = playlist.name,
                    createdAt = playlist.createdAt,
                    tracks = playlist.tracks.map { track ->
                        PlaylistTrackBackupEntry(
                            title = track.title,
                            artist = track.artist,
                            album = track.album
                        )
                    }
                )
            }
        )
    }

    private fun collectPreferences(context: Context): PreferencesBackupFile {
        val prefs = MusicPreferences.getInstance(context)

        return PreferencesBackupFile(
            preferences = mapOf(
                "albumSortOrder" to prefs.albumSortOrder.ordinal,
                "showPlayCount" to prefs.showPlayCount,
                "autoFetchLyrics" to prefs.autoFetchLyrics,
                "artistDisplayMode" to prefs.artistDisplayMode.ordinal,
                "artistTileColumns" to prefs.artistTileColumns,
                "albumDisplayMode" to prefs.albumDisplayMode.ordinal,
                "albumTileColumns" to prefs.albumTileColumns,
                "trackDisplayMode" to prefs.trackDisplayMode.ordinal,
                "rootDisplayMode" to prefs.rootDisplayMode.ordinal,
                "rootOptionOrder" to prefs.rootOptionOrder.joinToString("|")
            )
        )
    }

    private fun collectArtistImages(): Map<String, File> {
        val images = mutableMapOf<String, File>()
        val favoriteArtists = ArtistCustomizationManager.getFavoriteArtistNames()

        for (artistName in favoriteArtists) {
            val iconPath = ArtistCustomizationManager.getArtistIcon(artistName)
            if (iconPath != null) {
                val file = File(iconPath)
                if (file.exists()) {
                    val key = generateArtistImageKey(artistName)
                    images[key] = file
                }
            }
        }

        return images
    }

    private suspend fun uploadArtistImages(
        driveClient: GoogleDriveAppDataClient,
        images: Map<String, File>
    ): List<String> = withContext(Dispatchers.IO) {
        val uploaded = mutableListOf<String>()

        for ((key, file) in images) {
            try {
                val bytes = file.readBytes()
                val filename = "$ARTIST_IMAGE_PREFIX$key.jpg"
                if (driveClient.writeBinaryFile(filename, bytes)) {
                    uploaded.add(key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading artist image: $key", e)
            }
        }

        uploaded
    }

    // ==================== Restore Methods ====================

    private suspend fun restorePlayCounts(
        context: Context,
        data: PlayCountsBackupFile
    ): Int = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        val playCountDao = db.playCountDao()
        var restoredCount = 0

        for (entry in data.counts) {
            try {
                val existing = playCountDao.getByKey(entry.key)
                if (existing != null) {
                    // Use MAX rule: never decrease play count
                    if (entry.playCount > existing.playCount) {
                        playCountDao.updatePlayCountMax(
                            entry.key,
                            entry.playCount,
                            System.currentTimeMillis()
                        )
                        restoredCount++
                    }
                } else {
                    // Insert new entry
                    playCountDao.upsert(
                        PlayCountEntry(
                            metadataKey = entry.key,
                            title = entry.title,
                            artist = entry.artist,
                            album = entry.album,
                            playCount = entry.playCount,
                            earnedPlayCount = 0, // Don't count restored plays as "earned" on this device
                            lastPlayed = entry.lastPlayed,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    restoredCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring play count for ${entry.key}", e)
            }
        }

        restoredCount
    }

    private suspend fun restoreAlbumFavorites(
        context: Context,
        data: AlbumFavoritesBackupFile
    ): Int = withContext(Dispatchers.IO) {
        AlbumFavoritesManager.loadFavorites(context)
        var restoredCount = 0

        for (entry in data.favorites) {
            if (!AlbumFavoritesManager.isFavorite(entry.artistName, entry.albumName)) {
                AlbumFavoritesManager.addToFavorites(entry.artistName, entry.albumName)
                restoredCount++
            }
        }

        restoredCount
    }

    private suspend fun restoreArtistCustomizations(
        context: Context,
        data: ArtistCustomizationsBackupFile
    ): Int = withContext(Dispatchers.IO) {
        ArtistCustomizationManager.loadCustomizations(context)
        var restoredCount = 0

        // Get the directory where artist images were restored
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val imagesDir = File(musicDir, ".a2u_artist_images")

        for (entry in data.customizations) {
            try {
                val existing = ArtistCustomizationManager.getCustomization(entry.artistName)

                // Restore favorite status
                if (entry.isFavorite && !ArtistCustomizationManager.isArtistFavorite(entry.artistName)) {
                    ArtistCustomizationManager.addArtistToFavorites(entry.artistName)
                    restoredCount++
                }

                // Restore color if not already set
                if (entry.color != null && existing?.color == null) {
                    ArtistCustomizationManager.setArtistColor(entry.artistName, entry.color)
                }

                // Restore image path if not already set and image was downloaded
                if (entry.imageKey != null && existing?.iconPath == null) {
                    val imageFile = File(imagesDir, "${entry.imageKey}.jpg")
                    if (imageFile.exists()) {
                        ArtistCustomizationManager.setArtistIcon(entry.artistName, imageFile.absolutePath)
                        Log.d(TAG, "Restored artist image for ${entry.artistName}: ${imageFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring artist customization for ${entry.artistName}", e)
            }
        }

        restoredCount
    }

    private suspend fun restorePlaylists(
        data: PlaylistsBackupFile
    ): Int = withContext(Dispatchers.IO) {
        MusicPlaylistManager.loadPlaylists()
        var restoredCount = 0
        val existingPlaylists = MusicPlaylistManager.getPlaylists()
        val existingIds = existingPlaylists.map { it.id }.toSet()

        for (playlist in data.playlists) {
            try {
                // Only restore if playlist doesn't already exist
                if (playlist.id !in existingIds) {
                    // Create playlist with its original ID is not possible through the manager,
                    // so we create a new one with the same name
                    val existingNames = existingPlaylists.map { it.name.lowercase() }
                    if (playlist.name.lowercase() !in existingNames) {
                        MusicPlaylistManager.createPlaylist(playlist.name)
                        restoredCount++
                        // Note: tracks would need to be added separately by matching metadata
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring playlist ${playlist.name}", e)
            }
        }

        restoredCount
    }

    private fun restorePreferences(context: Context, data: PreferencesBackupFile): Boolean {
        try {
            val prefs = MusicPreferences.getInstance(context)

            data.preferences["albumSortOrder"]?.let {
                if (it is Number) {
                    val ordinal = it.toInt()
                    prefs.albumSortOrder = com.Atom2Universe.app.music.MusicLibrary.AlbumSortOrder.entries.getOrElse(ordinal) {
                        com.Atom2Universe.app.music.MusicLibrary.AlbumSortOrder.NAME_ASC
                    }
                }
            }

            data.preferences["showPlayCount"]?.let {
                if (it is Boolean) prefs.showPlayCount = it
            }

            data.preferences["autoFetchLyrics"]?.let {
                if (it is Boolean) prefs.autoFetchLyrics = it
            }

            data.preferences["artistDisplayMode"]?.let {
                if (it is Number) {
                    val ordinal = it.toInt()
                    prefs.artistDisplayMode = com.Atom2Universe.app.music.ArtistDisplayMode.entries.getOrElse(ordinal) {
                        com.Atom2Universe.app.music.ArtistDisplayMode.LIST
                    }
                }
            }

            data.preferences["artistTileColumns"]?.let {
                if (it is Number) prefs.artistTileColumns = it.toInt()
            }

            data.preferences["albumDisplayMode"]?.let {
                if (it is Number) {
                    val ordinal = it.toInt()
                    prefs.albumDisplayMode = com.Atom2Universe.app.music.AlbumDisplayMode.entries.getOrElse(ordinal) {
                        com.Atom2Universe.app.music.AlbumDisplayMode.LIST
                    }
                }
            }

            data.preferences["albumTileColumns"]?.let {
                if (it is Number) prefs.albumTileColumns = it.toInt()
            }

            data.preferences["trackDisplayMode"]?.let {
                if (it is Number) {
                    val ordinal = it.toInt()
                    prefs.trackDisplayMode = com.Atom2Universe.app.music.TrackDisplayMode.entries.getOrElse(ordinal) {
                        com.Atom2Universe.app.music.TrackDisplayMode.LIST
                    }
                }
            }

            data.preferences["rootDisplayMode"]?.let {
                if (it is Number) {
                    val ordinal = it.toInt()
                    prefs.rootDisplayMode = com.Atom2Universe.app.music.RootDisplayMode.entries.getOrElse(ordinal) {
                        com.Atom2Universe.app.music.RootDisplayMode.LIST
                    }
                }
            }

            data.preferences["rootOptionOrder"]?.let {
                if (it is String && it.isNotBlank()) {
                    prefs.rootOptionOrder = it.split("|").filter { s -> s.isNotBlank() }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring preferences", e)
            return false
        }
    }

    private suspend fun restoreArtistImages(
        driveClient: GoogleDriveAppDataClient
    ): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0

        // Get list of artist image files on Drive
        val imageFiles = driveClient.listFilesWithPrefix(ARTIST_IMAGE_PREFIX)

        // Get the directory for artist images
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val imagesDir = File(musicDir, ".a2u_artist_images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        for (filename in imageFiles) {
            try {
                val bytes = driveClient.readBinaryFile(filename) ?: continue

                // Extract artist key from filename
                val key = filename
                    .removePrefix(ARTIST_IMAGE_PREFIX)
                    .removeSuffix(".jpg")

                // Save to local file
                val localFile = File(imagesDir, "$key.jpg")
                localFile.writeBytes(bytes)

                // Note: We can't automatically link the image to the artist
                // because we need the original artist name, not just the key.
                // The linkage will be restored via artist_customizations.json

                restoredCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring artist image: $filename", e)
            }
        }

        restoredCount
    }

    private suspend fun restoreTrackFavorites(
        context: Context,
        data: FavoritesSyncFile
    ): Int = withContext(Dispatchers.IO) {
        MusicFavoritesManager.loadFavorites(context)
        var restoredCount = 0

        for (favorite in data.favorites) {
            // Skip removed favorites
            if (favorite.removedAt != null) continue

            try {
                MusicFavoritesManager.addFavoriteByMetadata(
                    context,
                    favorite.artist,
                    favorite.title,
                    favorite.album,
                    favorite.addedAt
                )
                restoredCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring track favorite: ${favorite.title}", e)
            }
        }

        restoredCount
    }

    private suspend fun restoreLyrics(
        context: Context,
        data: LyricsSyncFile
    ): Int = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        val lyricsDao = db.lyricsDao()
        var restoredCount = 0

        for (entry in data.lyrics) {
            try {
                val existing = lyricsDao.getByKey(entry.key)
                if (existing == null) {
                    // Insert new lyrics
                    lyricsDao.insert(
                        com.Atom2Universe.app.music.lyrics.data.LyricsEntity(
                            metadataKey = entry.key,
                            trackId = 0, // Will be matched by metadata key
                            lyrics = entry.lyrics,
                            source = entry.source,
                            language = null, // SyncLyricsEntry doesn't have language
                            isSynced = entry.isSynced,
                            fetchedAt = System.currentTimeMillis(),
                            lastModified = entry.modifiedAt,
                            isSyncedToFile = false
                        )
                    )
                    restoredCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring lyrics for ${entry.key}", e)
            }
        }

        restoredCount
    }

    // ==================== Helper Methods ====================

    private fun generateArtistImageKey(artistName: String): String {
        val normalized = artistName.lowercase().trim()
        val md5 = MessageDigest.getInstance("MD5")
        val digest = md5.digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private suspend fun updateSyncManifestBackupTimestamp(driveClient: GoogleDriveAppDataClient) {
        val manifestJson = driveClient.readJsonFile("sync_manifest.json")
        val manifest = if (manifestJson != null) {
            try {
                SyncManifest.fromJson(JSONObject(manifestJson))
            } catch (_: Exception) {
                SyncManifest()
            }
        } else {
            SyncManifest()
        }

        val updated = manifest.copy(backupLastModified = System.currentTimeMillis())
        driveClient.writeJsonFile("sync_manifest.json", updated.toJson().toString())
    }
}
