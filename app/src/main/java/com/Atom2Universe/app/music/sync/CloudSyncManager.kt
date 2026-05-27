package com.Atom2Universe.app.music.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.Atom2Universe.app.music.AlbumFavoritesManager
import com.Atom2Universe.app.music.ArtistCustomizationManager
import com.Atom2Universe.app.music.MusicFavoritesManager
import com.Atom2Universe.app.music.MusicPlaylistManager
import com.Atom2Universe.app.stats.sync.StatsSyncManager
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.sync.algorithm.AlbumFavoritesMerger
import com.Atom2Universe.app.music.sync.algorithm.ArtistFavoritesMerger
import com.Atom2Universe.app.music.sync.algorithm.FavoritesMerger
import com.Atom2Universe.app.music.sync.algorithm.LyricsMerger
import com.Atom2Universe.app.music.sync.algorithm.PlayCountMerger
import com.Atom2Universe.app.music.sync.algorithm.PlaylistsMerger
import com.Atom2Universe.app.music.sync.data.SyncMetadata
import com.Atom2Universe.app.music.sync.data.SyncMetadataDao
import com.Atom2Universe.app.music.sync.data.SyncPlayCountDelta
import com.Atom2Universe.app.music.sync.data.SyncPlayCountDeltaDao
import com.Atom2Universe.app.music.sync.model.AlbumFavoritesSyncFile
import com.Atom2Universe.app.music.sync.model.ArtistFavoritesSyncFile
import com.Atom2Universe.app.music.sync.model.DeviceInfo
import com.Atom2Universe.app.music.sync.model.EqPresetsSyncFile
import com.Atom2Universe.app.music.sync.model.FavoritesSyncFile
import com.Atom2Universe.app.music.sync.model.LyricsSyncFile
import com.Atom2Universe.app.music.sync.model.PlaylistsSyncFile
import com.Atom2Universe.app.music.sync.model.SyncAlbumFavoriteEntry
import com.Atom2Universe.app.music.sync.model.SyncArtistFavoriteEntry
import com.Atom2Universe.app.music.sync.model.SyncFavoriteEntry
import com.Atom2Universe.app.music.sync.model.SyncLyricsEntry
import com.Atom2Universe.app.music.sync.model.SyncEqPreset
import com.Atom2Universe.app.music.sync.model.SyncPlaylistEntry
import com.Atom2Universe.app.music.sync.model.PlayCountDelta
import com.Atom2Universe.app.music.sync.model.PlayCountDeltaFile
import com.Atom2Universe.app.music.sync.model.SyncManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Main coordinator for cloud synchronization.
 *
 * Responsibilities:
 * - Manage sync state (enabled, last sync time, device ID)
 * - Track local changes (play count deltas, favorites, lyrics)
 * - Coordinate sync with Google Drive
 * - Schedule nightly sync via WorkManager
 */
object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    private const val WORK_NAME = "a2u_cloud_sync"
    private const val WORK_NAME_DEBOUNCED = "a2u_debounced_sync"
    private const val SYNC_HOUR = 3  // 3 AM

    // Instant sync configuration
    private const val DEBOUNCE_DELAY_MINUTES = 10L  // 10 minutes debounce
    private const val STARTUP_SYNC_THRESHOLD_MS = 3600_000L  // 1 hour since last sync

    private lateinit var appContext: Context
    private lateinit var syncMetadataDao: SyncMetadataDao
    private lateinit var syncDeltaDao: SyncPlayCountDeltaDao

    private var isInitialized = false
    // Sync state
    @Volatile
    private var isSyncInProgress = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Initializes the CloudSyncManager.
     * Call this at app startup.
     */
    suspend fun init(context: Context) {
        if (isInitialized) return

        appContext = context.applicationContext
        val db = MusicDatabase.getInstance(appContext)
        syncMetadataDao = db.syncMetadataDao()
        syncDeltaDao = db.syncPlayCountDeltaDao()

        // Ensure device is registered
        ensureDeviceRegistered()

        isInitialized = true
        Log.d(TAG, "CloudSyncManager initialized")
    }

    /**
     * Ensures the device has a unique ID in the database.
     */
    private suspend fun ensureDeviceRegistered() {
        val existing = syncMetadataDao.get()
        if (existing == null) {
            val deviceId = UUID.randomUUID().toString()
            val deviceName = Build.MODEL ?: "Android Device"
            syncMetadataDao.insert(
                SyncMetadata(
                    id = 1,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    lastSyncTimestamp = 0,
                    syncEnabled = false
                )
            )
            Log.d(TAG, "Registered device: $deviceName ($deviceId)")
        }
    }

    /**
     * Checks if sync is enabled and user is signed in.
     */
    suspend fun isSyncEnabled(): Boolean {
        if (!isInitialized) return false
        val googleSignInManager = GoogleSignInManager(appContext)
        return googleSignInManager.isSignedIn() &&
                (syncMetadataDao.isSyncEnabled() == true)
    }

    /**
     * Enables or disables sync.
     * When enabling for the first time, creates baseline deltas for all existing play counts.
     */
    suspend fun setSyncEnabled(enabled: Boolean) {
        val wasEnabled = syncMetadataDao.isSyncEnabled() == true
        val lastSync = syncMetadataDao.getLastSyncTimestamp() ?: 0

        syncMetadataDao.setSyncEnabled(enabled)

        if (enabled) {
            // If enabling sync and we've never synced before, create baseline deltas
            // for all existing play counts so they can be uploaded
            if (!wasEnabled && lastSync == 0L) {
                createBaselineDeltas()
            }
            scheduleNightlySync()
        } else {
            cancelScheduledSync()
            cancelDebouncedSync()
        }
        Log.d(TAG, "Sync enabled: $enabled (wasEnabled: $wasEnabled, lastSync: $lastSync)")
    }

    /**
     * Creates baseline deltas for all existing play counts.
     * Called ONCE when sync is enabled for the first time to ensure
     * existing plays can be synced to other devices.
     *
     * IMPORTANT: Utilise earnedPlayCount (écoutes sur cet appareil) et NON playCount
     * (qui inclut les imports POPM). Cela évite le doublement quand un MP3 avec POPM
     * est copié sur un nouvel appareil.
     *
     * NOTE: Cette méthode vérifie le flag baselineDeltasCreated pour s'assurer
     * qu'elle n'est appelée qu'UNE SEULE FOIS. Sinon, les deltas seraient recréés
     * à chaque sync (après deleteAll()), causant une multiplication des compteurs.
     */
    private suspend fun createBaselineDeltas() = withContext(Dispatchers.IO) {
        try {
            // Vérifier si les baseline deltas ont déjà été créés
            if (syncMetadataDao.areBaselineDeltasCreated() == true) {
                Log.d(TAG, "Baseline deltas already created, skipping")
                return@withContext
            }

            val db = MusicDatabase.getInstance(appContext)
            val playCountDao = db.playCountDao()
            // Utilise getAllWithEarnedPlayCount pour n'inclure QUE les écoutes locales
            val existingCounts = playCountDao.getAllWithEarnedPlayCount()

            if (existingCounts.isEmpty()) {
                // Même si pas de deltas à créer, marquer comme fait pour éviter rappels inutiles
                syncMetadataDao.markBaselineDeltasCreated()
                return@withContext
            }

            var createdCount = 0
            for (entry in existingCounts) {
                val existingDelta = syncDeltaDao.getByKey(entry.metadataKey)
                if (existingDelta == null && entry.earnedPlayCount > 0) {
                    syncDeltaDao.insert(
                        SyncPlayCountDelta(
                            metadataKey = entry.metadataKey,
                            title = entry.title,
                            artist = entry.artist,
                            album = entry.album,
                            delta = entry.earnedPlayCount,  // Utilise earnedPlayCount, pas playCount
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    createdCount++
                }
            }

            // Marquer les baseline deltas comme créés pour ne plus les recréer
            syncMetadataDao.markBaselineDeltasCreated()
            Log.d(TAG, "Created baseline deltas for $createdCount tracks (using earnedPlayCount)")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating baseline deltas", e)
        }
    }

    /**
     * Gets the last sync timestamp.
     */
    suspend fun getLastSyncTimestamp(): Long {
        return syncMetadataDao.getLastSyncTimestamp() ?: 0
    }

    /**
     * Gets the device ID.
     */
    suspend fun getDeviceId(): String {
        if (!isInitialized) {
            return UUID.randomUUID().toString()
        }

        val metadata = syncMetadataDao.get()
        if (metadata == null) {
            val deviceId = UUID.randomUUID().toString()
            val deviceName = Build.MODEL ?: "Android Device"
            syncMetadataDao.insert(
                SyncMetadata(
                    id = 1,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    lastSyncTimestamp = 0,
                    syncEnabled = false
                )
            )
            return deviceId
        }

        if (metadata.deviceId.isBlank()) {
            val deviceId = UUID.randomUUID().toString()
            syncMetadataDao.insert(metadata.copy(deviceId = deviceId))
            return deviceId
        }

        return metadata.deviceId
    }

    /**
     * Schedules the nightly sync job.
     */
    fun scheduleNightlySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(calculateDelayToNight(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        Log.d(TAG, "Scheduled nightly sync")
    }

    /**
     * Cancels the scheduled sync job.
     */
    private fun cancelScheduledSync() {
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "Cancelled scheduled sync")
    }

    /**
     * Calculates delay until next sync time (3 AM).
     */
    private fun calculateDelayToNight(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, SYNC_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * No-op : les play counts sont désormais tracés via listen_events (SyncthingManager).
     * Conservé pour compatibilité des appelants existants.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun recordPlayCountDelta(metadataKey: String, track: MusicTrack) {
        // Les écoutes sont maintenant dans listen_events → sync via SyncthingManager
    }

    // ==================== Instant Sync Methods ====================

    /**
     * Triggers a debounced sync after data changes.
     * Call this after any data modification (play count, favorites, lyrics, playlists, etc.)
     *
     * Uses WorkManager to schedule a sync in DEBOUNCE_DELAY_MINUTES.
     * If called again before the delay expires, the existing work is replaced (timer resets).
     *
     * WorkManager ensures the sync runs even if:
     * - The app is in the background
     * - The screen is off
     * - The device is in doze mode (will run when constraints are met)
     */
    fun triggerDebouncedSync() {
        if (!isInitialized) {
            Log.d(TAG, "triggerDebouncedSync: not initialized, skipping")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(DEBOUNCE_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME_DEBOUNCED)
            .build()

        // REPLACE policy: cancels any pending debounced sync and schedules a new one
        // This creates the "debounce" effect - each new change resets the timer
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME_DEBOUNCED,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        Log.d(TAG, "Debounced sync scheduled in $DEBOUNCE_DELAY_MINUTES minutes (WorkManager)")

        // Planifie aussi l'export Syncthing (même débounce)
        SyncthingManager.scheduleExport()
    }

    /**
     * Performs a sync at app startup if conditions are met.
     * Call this when MusicPlayerActivity starts.
     *
     * Conditions for startup sync:
     * - CloudSyncManager is initialized
     * - Sync is enabled and user is signed in
     * - Last sync was more than STARTUP_SYNC_THRESHOLD_MS ago
     *
     * Uses WorkManager for immediate execution with network constraint.
     */
    suspend fun syncOnStartup() {
        if (!isInitialized) {
            Log.d(TAG, "syncOnStartup: not initialized, skipping")
            return
        }

        // Check if sync is enabled
        if (!isSyncEnabled()) {
            Log.d(TAG, "syncOnStartup: sync not enabled, skipping")
            return
        }

        // Check time since last sync
        val lastSync = getLastSyncTimestamp()
        val timeSinceLastSync = System.currentTimeMillis() - lastSync

        if (timeSinceLastSync < STARTUP_SYNC_THRESHOLD_MS) {
            Log.d(TAG, "syncOnStartup: last sync was ${timeSinceLastSync / 60000} min ago, skipping (threshold: ${STARTUP_SYNC_THRESHOLD_MS / 60000} min)")
            return
        }

        // Schedule immediate sync via WorkManager (respects network constraint)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag("startup_sync")
            .build()

        WorkManager.getInstance(appContext).enqueue(syncRequest)
        Log.d(TAG, "Startup sync scheduled (last sync was ${timeSinceLastSync / 60000} min ago)")
    }

    /**
     * Cancels any pending debounced sync.
     * Call this when sync is disabled or user signs out.
     */
    fun cancelDebouncedSync() {
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME_DEBOUNCED)
        Log.d(TAG, "Debounced sync cancelled")
    }

    // ==================== End Instant Sync Methods ====================

    /**
     * Performs immediate sync.
     */
    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync...")

        if (!isInitialized) {
            return@withContext SyncResult.NotInitialized
        }

        // Prevent concurrent syncs
        if (isSyncInProgress) {
            Log.d(TAG, "Sync already in progress, skipping")
            return@withContext SyncResult.Error("Sync already in progress")
        }

        val googleSignInManager = GoogleSignInManager(appContext)
        if (!googleSignInManager.isSignedIn()) {
            return@withContext SyncResult.NotSignedIn
        }

        val account = googleSignInManager.getSignedInAccount()
            ?: return@withContext SyncResult.NotSignedIn

        isSyncInProgress = true
        try {
            val client = GoogleDriveAppDataClient(appContext, account)

            // Phase 1: Download
            downloadManifest(client)
            val cloudDeltas = downloadNewDeltas(client)
            val cloudFavorites = downloadFavorites(client)
            val cloudLyrics = downloadLyrics(client)
            val cloudEqPresets = downloadEqPresets(client)
            val cloudPlaylists = downloadPlaylists(client)
            val cloudAlbumFavorites = downloadAlbumFavorites(client)
            val cloudArtistFavorites = downloadArtistFavorites(client)

            // Phase 2: Merge
            mergePlayCounts(cloudDeltas)
            mergeFavorites(cloudFavorites)
            mergeLyrics(cloudLyrics)
            mergeEqPresets(cloudEqPresets)
            mergePlaylists(cloudPlaylists)
            mergeAlbumFavorites(cloudAlbumFavorites)
            mergeArtistFavorites(cloudArtistFavorites)

            // Phase 2.5: Download and merge artist images
            val artistImagesDownloaded = downloadAndMergeArtistImages(client)
            Log.d(TAG, "Artist images downloaded: $artistImagesDownloaded")

            // Phase 3: Upload
            // If no deltas but we have LOCAL play counts, create baseline deltas now
            // Note: on utilise countWithEarnedPlayCount pour ignorer les imports POPM
            if (syncDeltaDao.count() == 0) {
                val db = MusicDatabase.getInstance(appContext)
                val playCountDao = db.playCountDao()
                if (playCountDao.countWithEarnedPlayCount() > 0) {
                    createBaselineDeltas()
                }
            }

            uploadLocalDeltas(client)
            uploadFavorites(client)
            uploadLyrics(client)
            uploadEqPresets(client)
            uploadPlaylists(client)
            uploadAlbumFavorites(client)
            uploadArtistFavorites(client)

            // Upload artist images (for favorite artists with icons)
            val artistImagesUploaded = uploadArtistImages(client)
            Log.d(TAG, "Artist images uploaded: $artistImagesUploaded")

            updateManifest(client)

            // Phase 3.5: Sync stats (usage sessions)
            try {
                val statsResult = StatsSyncManager.syncStats()
                Log.d(TAG, "Stats sync: ${statsResult.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Stats sync failed (non-critical)", e)
                // Continue sync even if stats fail
            }

            // Phase 4: Backup (if primary device)
            performBackupIfPrimary()

            // Phase 5: Cleanup (weekly)
            val calendar = Calendar.getInstance()
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                cleanupOldDeltaFiles(client)
            }

            // Update sync timestamp
            syncMetadataDao.updateLastSyncTimestamp(System.currentTimeMillis())

            Log.d(TAG, "Sync completed successfully")
            SyncResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult.Error(e.message ?: "Unknown error")
        } finally {
            isSyncInProgress = false
        }
    }

    /**
     * Downloads the sync manifest from Google Drive.
     */
    private suspend fun downloadManifest(client: GoogleDriveAppDataClient): SyncManifest {
        val json = client.readJsonFile("sync_manifest.json")
        return if (json != null) {
            try {
                SyncManifest.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing manifest, creating new one", e)
                SyncManifest()
            }
        } else {
            SyncManifest()
        }
    }

    /**
     * Downloads ALL play count delta files from other devices.
     *
     * Important:
     * - Downloads ALL files, not just new ones since last sync
     * - Excludes delta files from THIS device (only merges OTHER devices' data)
     * - The merge algorithm is idempotent: earnedPlayCount + total_cloud_deltas
     *   This prevents double-counting when re-downloading the same files.
     */
    private suspend fun downloadNewDeltas(client: GoogleDriveAppDataClient): List<PlayCountDeltaFile> {
        val currentDeviceId = getDeviceId()
        val deltaFiles = client.listDeltaFiles()
        val allDeltas = mutableListOf<PlayCountDeltaFile>()

        for (filename in deltaFiles) {
            try {
                val content = client.readJsonFile(filename) ?: continue
                val deltaFile = PlayCountDeltaFile.fromJson(JSONObject(content))

                // Skip our own delta files - we only want OTHER devices' deltas
                if (deltaFile.deviceId == currentDeviceId) continue

                // Download ALL delta files from other devices (no timestamp filter)
                // The merge algorithm uses earnedPlayCount + cloud_total which is idempotent
                allDeltas.add(deltaFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing delta file: $filename", e)
            }
        }

        Log.d(TAG, "Downloaded ${allDeltas.size} delta files from other devices")
        return allDeltas
    }

    /**
     * Downloads favorites file if newer than local.
     */
    private suspend fun downloadFavorites(client: GoogleDriveAppDataClient): FavoritesSyncFile {
        val json = client.readJsonFile(FavoritesSyncFile.FILENAME)
        return if (json != null) {
            try {
                FavoritesSyncFile.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing favorites", e)
                FavoritesSyncFile.empty()
            }
        } else {
            FavoritesSyncFile.empty()
        }
    }

    /**
     * Downloads lyrics file if newer than local.
     */
    private suspend fun downloadLyrics(client: GoogleDriveAppDataClient): LyricsSyncFile {
        val json = client.readJsonFile(LyricsSyncFile.FILENAME)
        return if (json != null) {
            try {
                LyricsSyncFile.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing lyrics", e)
                LyricsSyncFile.empty()
            }
        } else {
            LyricsSyncFile.empty()
        }
    }

    /**
     * Merges cloud play counts with local data.
     */
    private suspend fun mergePlayCounts(cloudDeltas: List<PlayCountDeltaFile>) {
        if (cloudDeltas.isEmpty()) return
        PlayCountMerger.merge(appContext, cloudDeltas)
    }

    /**
     * Merges cloud favorites with local data.
     */
    private suspend fun mergeFavorites(cloudFavorites: FavoritesSyncFile) {
        if (cloudFavorites.favorites.isEmpty()) return
        FavoritesMerger.merge(appContext, cloudFavorites)
    }

    /**
     * Merges cloud lyrics with local data.
     */
    private suspend fun mergeLyrics(cloudLyrics: LyricsSyncFile) {
        if (cloudLyrics.lyrics.isEmpty()) return
        LyricsMerger.merge(appContext, cloudLyrics)
    }

    // ==================== Playlists Sync ====================

    /**
     * Downloads playlists sync file.
     */
    private suspend fun downloadPlaylists(client: GoogleDriveAppDataClient): PlaylistsSyncFile {
        val json = client.readJsonFile(PlaylistsSyncFile.FILENAME)
        return if (json != null) {
            try {
                PlaylistsSyncFile.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing playlists", e)
                PlaylistsSyncFile.empty()
            }
        } else {
            PlaylistsSyncFile.empty()
        }
    }

    /**
     * Merges cloud playlists with local data.
     */
    private suspend fun mergePlaylists(cloudPlaylists: PlaylistsSyncFile) {
        if (cloudPlaylists.playlists.isEmpty()) return
        PlaylistsMerger.merge(appContext, cloudPlaylists)
    }

    /**
     * Uploads local playlists to Google Drive.
     * Downloads existing cloud data first and merges with local (last-write-wins).
     */
    private suspend fun uploadPlaylists(client: GoogleDriveAppDataClient) {
        val localPlaylists = MusicPlaylistManager.getAllPlaylistsForSync()
        if (localPlaylists.isEmpty() && MusicPlaylistManager.getPlaylistsCount() == 0) {
            Log.d(TAG, "No playlists to upload")
            return
        }

        // Download existing cloud playlists
        val cloudPlaylistsMap = mutableMapOf<String, SyncPlaylistEntry>()
        try {
            val cloudJson = client.readJsonFile(PlaylistsSyncFile.FILENAME)
            if (cloudJson != null) {
                val cloudFile = PlaylistsSyncFile.fromJson(JSONObject(cloudJson))
                for (entry in cloudFile.playlists) {
                    cloudPlaylistsMap[entry.id] = entry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read existing playlists from cloud", e)
        }

        // Merge: local takes precedence if timestamps are newer
        for (localEntry in localPlaylists) {
            val cloudEntry = cloudPlaylistsMap[localEntry.id]
            if (cloudEntry == null) {
                cloudPlaylistsMap[localEntry.id] = localEntry
            } else {
                val localTs = localEntry.getLastModifiedTimestamp()
                val cloudTs = cloudEntry.getLastModifiedTimestamp()
                if (localTs >= cloudTs) {
                    cloudPlaylistsMap[localEntry.id] = localEntry
                }
            }
        }

        // Upload merged data
        val syncFile = PlaylistsSyncFile(
            version = 1,
            lastModified = System.currentTimeMillis(),
            playlists = cloudPlaylistsMap.values.toList()
        )

        val success = client.writeJsonFile(PlaylistsSyncFile.FILENAME, syncFile.toJson().toString())
        if (success == true) {
            MusicPlaylistManager.clearDeletedPlaylistsCache()
            Log.d(TAG, "Uploaded ${cloudPlaylistsMap.size} playlists")
        }
    }

    // ==================== Album Favorites Sync ====================

    /**
     * Downloads album favorites sync file.
     */
    private suspend fun downloadAlbumFavorites(client: GoogleDriveAppDataClient): AlbumFavoritesSyncFile {
        val json = client.readJsonFile(AlbumFavoritesSyncFile.FILENAME)
        return if (json != null) {
            try {
                AlbumFavoritesSyncFile.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing album favorites", e)
                AlbumFavoritesSyncFile.empty()
            }
        } else {
            AlbumFavoritesSyncFile.empty()
        }
    }

    /**
     * Merges cloud album favorites with local data.
     */
    private suspend fun mergeAlbumFavorites(cloudFavorites: AlbumFavoritesSyncFile) {
        if (cloudFavorites.favorites.isEmpty()) return
        AlbumFavoritesMerger.merge(appContext, cloudFavorites)
    }

    /**
     * Uploads local album favorites to Google Drive.
     * Downloads existing cloud data first and merges with local (last-write-wins).
     */
    private suspend fun uploadAlbumFavorites(client: GoogleDriveAppDataClient) {
        val localFavorites = AlbumFavoritesManager.getAllFavoritesForSync()
        if (localFavorites.isEmpty() && AlbumFavoritesManager.getFavoritesCount() == 0) {
            Log.d(TAG, "No album favorites to upload")
            return
        }

        // Download existing cloud favorites
        val cloudFavoritesMap = mutableMapOf<String, SyncAlbumFavoriteEntry>()
        try {
            val cloudJson = client.readJsonFile(AlbumFavoritesSyncFile.FILENAME)
            if (cloudJson != null) {
                val cloudFile = AlbumFavoritesSyncFile.fromJson(JSONObject(cloudJson))
                for (entry in cloudFile.favorites) {
                    cloudFavoritesMap[entry.key] = entry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read existing album favorites from cloud", e)
        }

        // Merge: local takes precedence if timestamps are newer
        for (localEntry in localFavorites) {
            val cloudEntry = cloudFavoritesMap[localEntry.key]
            if (cloudEntry == null) {
                cloudFavoritesMap[localEntry.key] = localEntry
            } else {
                val localTs = localEntry.getLastModifiedTimestamp()
                val cloudTs = cloudEntry.getLastModifiedTimestamp()
                if (localTs >= cloudTs) {
                    cloudFavoritesMap[localEntry.key] = localEntry
                }
            }
        }

        // Upload merged data
        val syncFile = AlbumFavoritesSyncFile(
            version = 1,
            lastModified = System.currentTimeMillis(),
            favorites = cloudFavoritesMap.values.toList()
        )

        val success = client.writeJsonFile(AlbumFavoritesSyncFile.FILENAME, syncFile.toJson().toString())
        if (success == true) {
            AlbumFavoritesManager.clearDeletedFavoritesCache()
            Log.d(TAG, "Uploaded ${cloudFavoritesMap.size} album favorites")
        }
    }

    // ==================== Artist Favorites Sync ====================

    /**
     * Downloads artist favorites sync file.
     */
    private suspend fun downloadArtistFavorites(client: GoogleDriveAppDataClient): ArtistFavoritesSyncFile {
        val json = client.readJsonFile(ArtistFavoritesSyncFile.FILENAME)
        return if (json != null) {
            try {
                ArtistFavoritesSyncFile.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing artist favorites", e)
                ArtistFavoritesSyncFile.empty()
            }
        } else {
            ArtistFavoritesSyncFile.empty()
        }
    }

    /**
     * Merges cloud artist favorites with local data.
     */
    private suspend fun mergeArtistFavorites(cloudFavorites: ArtistFavoritesSyncFile) {
        if (cloudFavorites.favorites.isEmpty()) return
        ArtistFavoritesMerger.merge(appContext, cloudFavorites)
    }

    /**
     * Uploads local artist favorites to Google Drive.
     * Downloads existing cloud data first and merges with local (last-write-wins).
     */
    private suspend fun uploadArtistFavorites(client: GoogleDriveAppDataClient) {
        val localFavorites = ArtistCustomizationManager.getAllFavoritesForSync()
        if (localFavorites.isEmpty() && ArtistCustomizationManager.getFavoriteArtistsCount() == 0) {
            Log.d(TAG, "No artist favorites to upload")
            return
        }

        // Download existing cloud favorites
        val cloudFavoritesMap = mutableMapOf<String, SyncArtistFavoriteEntry>()
        try {
            val cloudJson = client.readJsonFile(ArtistFavoritesSyncFile.FILENAME)
            if (cloudJson != null) {
                val cloudFile = ArtistFavoritesSyncFile.fromJson(JSONObject(cloudJson))
                for (entry in cloudFile.favorites) {
                    cloudFavoritesMap[entry.key] = entry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read existing artist favorites from cloud", e)
        }

        // Merge: local takes precedence if timestamps are newer
        for (localEntry in localFavorites) {
            val cloudEntry = cloudFavoritesMap[localEntry.key]
            if (cloudEntry == null) {
                cloudFavoritesMap[localEntry.key] = localEntry
            } else {
                val localTs = localEntry.getLastModifiedTimestamp()
                val cloudTs = cloudEntry.getLastModifiedTimestamp()
                if (localTs >= cloudTs) {
                    cloudFavoritesMap[localEntry.key] = localEntry
                }
            }
        }

        // Upload merged data
        val syncFile = ArtistFavoritesSyncFile(
            version = 1,
            lastModified = System.currentTimeMillis(),
            favorites = cloudFavoritesMap.values.toList()
        )

        val success = client.writeJsonFile(ArtistFavoritesSyncFile.FILENAME, syncFile.toJson().toString())
        if (success == true) {
            ArtistCustomizationManager.clearDeletedFavoritesCache()
            Log.d(TAG, "Uploaded ${cloudFavoritesMap.size} artist favorites")
        }
    }

    /**
     * Uploads local deltas to Google Drive.
     * IMPORTANT: If a delta file for today already exists, MERGES with it instead of overwriting.
     * This prevents data loss when syncing multiple times per day.
     */
    private suspend fun uploadLocalDeltas(client: GoogleDriveAppDataClient) {
        val localDeltas = syncDeltaDao.getAll()
        if (localDeltas.isEmpty()) return

        val deviceId = getDeviceId()
        val today = dateFormat.format(Date())
        val filename = PlayCountDeltaFile.generateFilename(deviceId, today)

        // Read existing file for today (if any) to MERGE instead of overwrite
        val existingDeltas = mutableMapOf<String, PlayCountDelta>()
        try {
            val existingContent = client.readJsonFile(filename)
            if (existingContent != null) {
                val existingFile = PlayCountDeltaFile.fromJson(JSONObject(existingContent))
                // Only merge if it's from the same device (safety check)
                if (existingFile.deviceId == deviceId) {
                    for (delta in existingFile.deltas) {
                        existingDeltas[delta.key] = delta
                    }
                    Log.d(TAG, "Found existing delta file for today with ${existingDeltas.size} entries, will merge")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read existing delta file (may not exist yet)", e)
        }

        // Merge local deltas with existing ones (ADD the counts)
        for (localDelta in localDeltas) {
            val existing = existingDeltas[localDelta.metadataKey]
            if (existing != null) {
                // Track already exists in today's file - ADD the deltas
                existingDeltas[localDelta.metadataKey] = PlayCountDelta(
                    key = localDelta.metadataKey,
                    artist = localDelta.artist,
                    title = localDelta.title,
                    album = localDelta.album,
                    delta = existing.delta + localDelta.delta  // SUM!
                )
            } else {
                // New track for today
                existingDeltas[localDelta.metadataKey] = PlayCountDelta(
                    key = localDelta.metadataKey,
                    artist = localDelta.artist,
                    title = localDelta.title,
                    album = localDelta.album,
                    delta = localDelta.delta
                )
            }
        }

        val deltaFile = PlayCountDeltaFile(
            deviceId = deviceId,
            date = today,
            createdAt = System.currentTimeMillis(),
            deltas = existingDeltas.values.toList()
        )

        val success = client.writeJsonFile(filename, deltaFile.toJson().toString())
        if (success == true) {
            syncDeltaDao.deleteAll()
            syncMetadataDao.updateLastUploadedDeltaDate(today)
            Log.d(TAG, "Uploaded ${existingDeltas.size} deltas (merged with existing)")
        } else {
            Log.e(TAG, "Failed to upload deltas")
        }
    }

    /**
     * Uploads favorites to Google Drive.
     * Merges local favorites (including deletions) with cloud data to preserve
     * all soft-delete entries and their timestamps for proper conflict resolution.
     */
    private suspend fun uploadFavorites(client: GoogleDriveAppDataClient) {
        val localFavorites = FavoritesMerger.getLocalFavorites(appContext)

        // Download existing cloud favorites to merge
        val cloudJson = client.readJsonFile(FavoritesSyncFile.FILENAME)
        val cloudFavorites = if (cloudJson != null) {
            try {
                FavoritesSyncFile.fromJson(JSONObject(cloudJson)).favorites
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Merge: local takes precedence, but preserve cloud entries not in local
        val mergedMap = mutableMapOf<String, SyncFavoriteEntry>()

        // Start with cloud favorites
        for (cloudEntry in cloudFavorites) {
            mergedMap[cloudEntry.key] = cloudEntry
        }

        // Override/add with local favorites (including soft-deleted ones)
        for (localEntry in localFavorites) {
            val existing = mergedMap[localEntry.key]
            if (existing == null) {
                // New entry from local
                mergedMap[localEntry.key] = localEntry
            } else {
                // Conflict: use the one with the most recent timestamp
                val localTimestamp = localEntry.getLastModifiedTimestamp()
                val cloudTimestamp = existing.getLastModifiedTimestamp()

                if (localTimestamp >= cloudTimestamp) {
                    mergedMap[localEntry.key] = localEntry
                }
            }
        }

        val mergedFavorites = mergedMap.values.toList()

        if (mergedFavorites.isEmpty()) {
            Log.d(TAG, "No favorites to upload")
            return
        }

        val syncFile = FavoritesSyncFile(
            version = 2,
            lastModified = System.currentTimeMillis(),
            favorites = mergedFavorites
        )

        val success = client.writeJsonFile(FavoritesSyncFile.FILENAME, syncFile.toJson().toString())
        if (success == true) {
            // Clear the deleted favorites cache since they've been uploaded
            MusicFavoritesManager.clearDeletedFavoritesCache()
            Log.d(TAG, "Uploaded ${mergedFavorites.size} favorites (merged with cloud)")
        } else {
            Log.e(TAG, "Failed to upload favorites")
        }
    }

    /**
     * Uploads lyrics to Google Drive.
     * Merges local lyrics (including deletions) with cloud data for proper sync.
     */
    private suspend fun uploadLyrics(client: GoogleDriveAppDataClient) {
        val localLyrics = LyricsMerger.getLocalLyrics(appContext)

        // Download existing cloud lyrics to merge
        val cloudJson = client.readJsonFile(LyricsSyncFile.FILENAME)
        val cloudLyrics = if (cloudJson != null) {
            try {
                LyricsSyncFile.fromJson(JSONObject(cloudJson)).lyrics
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Merge: local takes precedence, but preserve cloud entries not in local
        val mergedMap = mutableMapOf<String, SyncLyricsEntry>()

        // Start with cloud lyrics
        for (cloudEntry in cloudLyrics) {
            mergedMap[cloudEntry.key] = cloudEntry
        }

        // Override/add with local lyrics (including soft-deleted ones)
        for (localEntry in localLyrics) {
            val existing = mergedMap[localEntry.key]
            if (existing == null) {
                mergedMap[localEntry.key] = localEntry
            } else {
                // Conflict: use the one with the most recent timestamp
                val localTimestamp = localEntry.getLastModifiedTimestamp()
                val cloudTimestamp = existing.getLastModifiedTimestamp()

                if (localTimestamp >= cloudTimestamp) {
                    mergedMap[localEntry.key] = localEntry
                }
            }
        }

        val mergedLyrics = mergedMap.values.toList()

        if (mergedLyrics.isEmpty()) {
            Log.d(TAG, "No lyrics to upload")
            return
        }

        val syncFile = LyricsSyncFile(
            version = 1,
            lastModified = System.currentTimeMillis(),
            lyrics = mergedLyrics
        )

        val success = client.writeJsonFile(LyricsSyncFile.FILENAME, syncFile.toJson().toString())
        if (success == true) {
            // Clear the deleted lyrics cache since they've been uploaded
            LyricsMerger.clearDeletedCache(appContext)
            Log.d(TAG, "Uploaded ${mergedLyrics.size} lyrics entries (merged with cloud)")
        } else {
            Log.e(TAG, "Failed to upload lyrics")
        }
    }

    // ==================== EQ PRESETS SYNC ====================

    private const val DELETED_EQ_PRESETS_FILENAME = "deleted_eq_presets.json"
    private val deletedEqPresetsCache = mutableMapOf<String, SyncEqPreset>()
    private var isDeletedEqCacheLoaded = false

    /**
     * Downloads EQ presets file from Google Drive.
     */
    private suspend fun downloadEqPresets(client: GoogleDriveAppDataClient): EqPresetsSyncFile {
        val json = client.readJsonFile(EqPresetsSyncFile.FILENAME)
        return if (json != null) {
            try {
                EqPresetsSyncFile.fromJson(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing EQ presets", e)
                EqPresetsSyncFile()
            }
        } else {
            EqPresetsSyncFile()
        }
    }

    /**
     * Merges cloud EQ presets with local data.
     * Only custom presets are synced (not system presets).
     * Supports soft-delete for proper cross-device sync.
     */
    private suspend fun mergeEqPresets(cloudPresets: EqPresetsSyncFile) {
        if (cloudPresets.presets.isEmpty()) return

        loadDeletedEqPresetsCache()

        val db = MusicDatabase.getInstance(appContext)
        val eqPresetDao = db.eqPresetDao()

        var addedCount = 0
        var updatedCount = 0
        var removedCount = 0

        for (cloudPreset in cloudPresets.presets) {
            val localPreset = eqPresetDao.getPresetByName(cloudPreset.name)
            val localDeletedPreset = deletedEqPresetsCache[cloudPreset.name]

            // Determine local timestamp
            val localTimestamp = when {
                localPreset != null -> localPreset.createdAt
                localDeletedPreset != null -> localDeletedPreset.deletedAt ?: 0
                else -> 0L
            }

            val cloudTimestamp = cloudPreset.getLastModifiedTimestamp()

            if (cloudTimestamp > localTimestamp) {
                // Cloud is newer
                if (cloudPreset.isActive()) {
                    // Cloud has active preset - add or update locally
                    if (localPreset == null) {
                        val newPreset = com.Atom2Universe.app.music.equalizer.data.EqPreset(
                            name = cloudPreset.name,
                            isSystemPreset = false,
                            band32Hz = cloudPreset.band32Hz,
                            band64Hz = cloudPreset.band64Hz,
                            band125Hz = cloudPreset.band125Hz,
                            band250Hz = cloudPreset.band250Hz,
                            band500Hz = cloudPreset.band500Hz,
                            band1kHz = cloudPreset.band1kHz,
                            band2kHz = cloudPreset.band2kHz,
                            band4kHz = cloudPreset.band4kHz,
                            band8kHz = cloudPreset.band8kHz,
                            band16kHz = cloudPreset.band16kHz,
                            bassBoostStrength = cloudPreset.bassBoostStrength,
                            virtualizerStrength = cloudPreset.virtualizerStrength,
                            createdAt = cloudPreset.createdAt
                        )
                        eqPresetDao.insertPreset(newPreset)
                        deletedEqPresetsCache.remove(cloudPreset.name)
                        addedCount++
                        Log.d(TAG, "Added EQ preset from cloud: ${cloudPreset.name}")
                    } else if (!localPreset.isSystemPreset) {
                        val updatedPreset = localPreset.copy(
                            band32Hz = cloudPreset.band32Hz,
                            band64Hz = cloudPreset.band64Hz,
                            band125Hz = cloudPreset.band125Hz,
                            band250Hz = cloudPreset.band250Hz,
                            band500Hz = cloudPreset.band500Hz,
                            band1kHz = cloudPreset.band1kHz,
                            band2kHz = cloudPreset.band2kHz,
                            band4kHz = cloudPreset.band4kHz,
                            band8kHz = cloudPreset.band8kHz,
                            band16kHz = cloudPreset.band16kHz,
                            bassBoostStrength = cloudPreset.bassBoostStrength,
                            virtualizerStrength = cloudPreset.virtualizerStrength
                        )
                        eqPresetDao.updatePreset(updatedPreset)
                        updatedCount++
                        Log.d(TAG, "Updated EQ preset from cloud: ${cloudPreset.name}")
                    }
                } else {
                    // Cloud has deleted preset - remove locally
                    if (localPreset != null && !localPreset.isSystemPreset) {
                        eqPresetDao.deletePreset(localPreset)
                        removedCount++
                        Log.d(TAG, "Removed EQ preset from cloud: ${cloudPreset.name}")
                    }
                    deletedEqPresetsCache.remove(cloudPreset.name)
                }
            }
        }

        saveDeletedEqPresetsCache()
        Log.d(TAG, "Merged EQ presets: added $addedCount, updated $updatedCount, removed $removedCount")
    }

    /**
     * Uploads custom EQ presets to Google Drive.
     * Only user-created presets are synced (not system presets).
     * Merges with cloud data to preserve soft-delete entries.
     */
    private suspend fun uploadEqPresets(client: GoogleDriveAppDataClient) {
        loadDeletedEqPresetsCache()

        val db = MusicDatabase.getInstance(appContext)
        val eqPresetDao = db.eqPresetDao()

        // Get active custom presets
        val customPresets = eqPresetDao.getUserPresets()

        val localPresets = mutableListOf<SyncEqPreset>()

        // Add active presets
        for (preset in customPresets) {
            localPresets.add(SyncEqPreset(
                name = preset.name,
                band32Hz = preset.band32Hz,
                band64Hz = preset.band64Hz,
                band125Hz = preset.band125Hz,
                band250Hz = preset.band250Hz,
                band500Hz = preset.band500Hz,
                band1kHz = preset.band1kHz,
                band2kHz = preset.band2kHz,
                band4kHz = preset.band4kHz,
                band8kHz = preset.band8kHz,
                band16kHz = preset.band16kHz,
                bassBoostStrength = preset.bassBoostStrength,
                virtualizerStrength = preset.virtualizerStrength,
                createdAt = preset.createdAt,
                updatedAt = preset.createdAt,
                deletedAt = null
            ))
        }

        // Add deleted presets (soft-delete)
        for ((_, deletedPreset) in deletedEqPresetsCache) {
            localPresets.add(deletedPreset)
        }

        // Download existing cloud presets to merge
        val cloudJson = client.readJsonFile(EqPresetsSyncFile.FILENAME)
        val cloudPresets = if (cloudJson != null) {
            try {
                EqPresetsSyncFile.fromJson(JSONObject(cloudJson)).presets
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Merge: local takes precedence
        val mergedMap = mutableMapOf<String, SyncEqPreset>()

        for (cloudPreset in cloudPresets) {
            mergedMap[cloudPreset.name] = cloudPreset
        }

        for (localPreset in localPresets) {
            val existing = mergedMap[localPreset.name]
            if (existing == null) {
                mergedMap[localPreset.name] = localPreset
            } else {
                val localTimestamp = localPreset.getLastModifiedTimestamp()
                val cloudTimestamp = existing.getLastModifiedTimestamp()

                if (localTimestamp >= cloudTimestamp) {
                    mergedMap[localPreset.name] = localPreset
                }
            }
        }

        val mergedPresets = mergedMap.values.toList()

        if (mergedPresets.isEmpty()) {
            Log.d(TAG, "No EQ presets to upload")
            return
        }

        val syncFile = EqPresetsSyncFile(
            version = 1,
            lastModified = System.currentTimeMillis(),
            presets = mergedPresets
        )

        val success = client.writeJsonFile(EqPresetsSyncFile.FILENAME, syncFile.toJson().toString())
        if (success == true) {
            deletedEqPresetsCache.clear()
            saveDeletedEqPresetsCache()
            Log.d(TAG, "Uploaded ${mergedPresets.size} EQ presets (merged with cloud)")
        } else {
            Log.e(TAG, "Failed to upload EQ presets")
        }
    }

    /**
     * Tracks an EQ preset deletion for cloud sync.
     * Call this when an EQ preset is deleted locally.
     */
    fun trackEqPresetDeletion(preset: com.Atom2Universe.app.music.equalizer.data.EqPreset) {
        loadDeletedEqPresetsCache()

        deletedEqPresetsCache[preset.name] = SyncEqPreset(
            name = preset.name,
            band32Hz = preset.band32Hz,
            band64Hz = preset.band64Hz,
            band125Hz = preset.band125Hz,
            band250Hz = preset.band250Hz,
            band500Hz = preset.band500Hz,
            band1kHz = preset.band1kHz,
            band2kHz = preset.band2kHz,
            band4kHz = preset.band4kHz,
            band8kHz = preset.band8kHz,
            band16kHz = preset.band16kHz,
            bassBoostStrength = preset.bassBoostStrength,
            virtualizerStrength = preset.virtualizerStrength,
            createdAt = preset.createdAt,
            updatedAt = preset.createdAt,
            deletedAt = System.currentTimeMillis()
        )

        saveDeletedEqPresetsCache()
        Log.d(TAG, "Tracked EQ preset deletion: ${preset.name}")
    }

    // ==================== Deleted EQ Presets Cache Persistence ====================

    private fun loadDeletedEqPresetsCache() {
        if (isDeletedEqCacheLoaded) return

        try {
            val file = java.io.File(appContext.filesDir, DELETED_EQ_PRESETS_FILENAME)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                val array = json.optJSONArray("deletedPresets") ?: org.json.JSONArray()

                for (i in 0 until array.length()) {
                    val entry = array.getJSONObject(i)
                    val name = entry.optString("name", "")
                    if (name.isNotEmpty()) {
                        deletedEqPresetsCache[name] = SyncEqPreset.fromJson(entry)
                    }
                }
                Log.d(TAG, "Loaded ${deletedEqPresetsCache.size} deleted EQ presets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading deleted EQ presets cache", e)
        }

        isDeletedEqCacheLoaded = true
    }

    private fun saveDeletedEqPresetsCache() {
        try {
            val json = JSONObject().apply {
                put("deletedPresets", org.json.JSONArray().apply {
                    deletedEqPresetsCache.values.forEach { preset ->
                        put(preset.toJson())
                    }
                })
            }

            val file = java.io.File(appContext.filesDir, DELETED_EQ_PRESETS_FILENAME)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving deleted EQ presets cache", e)
        }
    }

    // ==================== ARTIST IMAGES SYNC ====================

    private const val ARTIST_IMAGE_PREFIX = "artist_img_"

    /**
     * Downloads and merges artist images from Google Drive.
     * Downloads images that exist on Drive but not locally.
     */
    private suspend fun downloadAndMergeArtistImages(client: GoogleDriveAppDataClient): Int {
        var downloadedCount = 0

        try {
            // List all artist image files on Drive
            val driveImageFiles = client.listFilesWithPrefix(ARTIST_IMAGE_PREFIX)
            if (driveImageFiles.isEmpty()) {
                Log.d(TAG, "No artist images on Drive")
                return 0
            }

            // Get the local images directory
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC
            )
            val imagesDir = java.io.File(musicDir, ".a2u_artist_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Download images we don't have locally
            for (filename in driveImageFiles) {
                try {
                    val key = filename
                        .removePrefix(ARTIST_IMAGE_PREFIX)
                        .removeSuffix(".jpg")

                    val localFile = java.io.File(imagesDir, "$key.jpg")

                    // Skip if we already have this image locally
                    if (localFile.exists()) continue

                    // Download from Drive
                    val bytes = client.readBinaryFile(filename)
                    if (bytes != null && bytes.isNotEmpty()) {
                        localFile.writeBytes(bytes)
                        downloadedCount++
                        Log.d(TAG, "Downloaded artist image: $filename")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading artist image: $filename", e)
                }
            }

            // Now link the downloaded images to artists via customizations
            linkDownloadedImagesToArtists(client, imagesDir)

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading artist images", e)
        }

        Log.d(TAG, "Downloaded $downloadedCount artist images")
        return downloadedCount
    }

    /**
     * Links downloaded artist images to their corresponding artists.
     * Uses the artist_customizations.json backup file to find the mapping.
     */
    private suspend fun linkDownloadedImagesToArtists(
        client: GoogleDriveAppDataClient,
        imagesDir: java.io.File
    ) {
        try {
            // Read artist customizations backup to get the imageKey -> artistName mapping
            val customizationsJson = client.readJsonFile(
                com.Atom2Universe.app.music.sync.model.ArtistCustomizationsBackupFile.FILENAME
            ) ?: return

            val customizationsFile = com.Atom2Universe.app.music.sync.model.ArtistCustomizationsBackupFile
                .fromJson(JSONObject(customizationsJson))

            // Load local customizations
            ArtistCustomizationManager.loadCustomizations(appContext)

            for (entry in customizationsFile.customizations) {
                if (entry.imageKey != null) {
                    val existing = ArtistCustomizationManager.getCustomization(entry.artistName)

                    // Only set if local doesn't have an image yet
                    if (existing?.iconPath == null) {
                        val imageFile = java.io.File(imagesDir, "${entry.imageKey}.jpg")
                        if (imageFile.exists()) {
                            ArtistCustomizationManager.setArtistIcon(
                                entry.artistName,
                                imageFile.absolutePath
                            )
                            Log.d(TAG, "Linked artist image: ${entry.artistName} -> ${imageFile.absolutePath}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error linking artist images", e)
        }
    }

    /**
     * Uploads local artist images to Google Drive.
     * Only uploads images for favorite artists that aren't already on Drive.
     */
    private suspend fun uploadArtistImages(client: GoogleDriveAppDataClient): Int {
        var uploadedCount = 0

        try {
            // Load customizations
            ArtistCustomizationManager.loadCustomizations(appContext)

            // Get all artists with custom icons (not just favorites, but any with icons)
            val artistsWithIcons = mutableListOf<Pair<String, String>>() // artistName, iconPath

            // Check favorites first
            val favoriteArtists = ArtistCustomizationManager.getFavoriteArtistNames()
            for (artistName in favoriteArtists) {
                val iconPath = ArtistCustomizationManager.getArtistIcon(artistName)
                if (iconPath != null) {
                    artistsWithIcons.add(artistName to iconPath)
                }
            }

            if (artistsWithIcons.isEmpty()) {
                Log.d(TAG, "No artist images to upload")
                return 0
            }

            // Get list of images already on Drive
            val existingDriveImages = client.listFilesWithPrefix(ARTIST_IMAGE_PREFIX).toSet()

            for ((artistName, iconPath) in artistsWithIcons) {
                try {
                    val key = generateArtistImageKey(artistName)
                    val filename = "$ARTIST_IMAGE_PREFIX$key.jpg"

                    // Skip if already on Drive
                    if (existingDriveImages.contains(filename)) continue

                    // Read local file
                    val file = java.io.File(iconPath)
                    if (!file.exists()) continue

                    val bytes = file.readBytes()
                    if (client.writeBinaryFile(filename, bytes)) {
                        uploadedCount++
                        Log.d(TAG, "Uploaded artist image for: $artistName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading artist image for: $artistName", e)
                }
            }

            // Also update artist_customizations.json with imageKeys
            updateArtistCustomizationsBackup(client)

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading artist images", e)
        }

        Log.d(TAG, "Uploaded $uploadedCount artist images")
        return uploadedCount
    }

    /**
     * Generates a unique key for an artist image based on the artist name.
     */
    private fun generateArtistImageKey(artistName: String): String {
        val normalized = artistName.lowercase().trim()
        val md5 = java.security.MessageDigest.getInstance("MD5")
        val digest = md5.digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Updates the artist_customizations.json backup file with imageKeys for uploaded images.
     */
    private suspend fun updateArtistCustomizationsBackup(client: GoogleDriveAppDataClient) {
        try {
            val customizations = mutableListOf<com.Atom2Universe.app.music.sync.model.ArtistCustomizationBackupEntry>()

            val favoriteArtists = ArtistCustomizationManager.getFavoriteArtistNames()
            for (artistName in favoriteArtists) {
                val custom = ArtistCustomizationManager.getCustomization(artistName) ?: continue

                customizations.add(
                    com.Atom2Universe.app.music.sync.model.ArtistCustomizationBackupEntry(
                        artistName = custom.artistName,
                        isFavorite = custom.isFavorite,
                        addedToFavoritesAt = if (custom.addedToFavoritesAt > 0) {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                .format(Date(custom.addedToFavoritesAt))
                        } else null,
                        color = custom.color,
                        imageKey = custom.iconPath?.let { generateArtistImageKey(artistName) }
                    )
                )
            }

            val backupFile = com.Atom2Universe.app.music.sync.model.ArtistCustomizationsBackupFile(
                customizations = customizations
            )

            client.writeJsonFile(
                com.Atom2Universe.app.music.sync.model.ArtistCustomizationsBackupFile.FILENAME,
                backupFile.toJson().toString()
            )
            Log.d(TAG, "Updated artist customizations backup with ${customizations.size} entries")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating artist customizations backup", e)
        }
    }

    /**
     * Updates the sync manifest on Google Drive.
     */
    private suspend fun updateManifest(client: GoogleDriveAppDataClient) {
        val existingJson = client.readJsonFile("sync_manifest.json")
        val existing = if (existingJson != null) {
            try {
                SyncManifest.fromJson(JSONObject(existingJson))
            } catch (_: Exception) {
                SyncManifest()
            }
        } else {
            SyncManifest()
        }

        val deviceId = getDeviceId()
        val metadata = syncMetadataDao.get()

        val updatedDevices = existing.devices.toMutableMap()
        updatedDevices[deviceId] = DeviceInfo(
            name = metadata?.deviceName ?: "Unknown",
            lastSeen = System.currentTimeMillis(),
            lastDeltaDate = metadata?.lastUploadedDeltaDate
        )

        val updatedManifest = existing.copy(
            lastSyncTimestamp = System.currentTimeMillis(),
            devices = updatedDevices
        )

        client.writeJsonFile("sync_manifest.json", updatedManifest.toJson().toString())
        Log.d(TAG, "Updated manifest")
    }

    /**
     * Intelligently cleans up old delta files.
     *
     * Instead of blindly deleting files older than X days, this method:
     * 1. Reads the manifest to find all "active" devices (seen in last 60 days)
     * 2. Finds the MINIMUM lastSeen timestamp among active devices
     * 3. Only deletes delta files whose createdAt < minimum lastSeen
     *
     * This ensures we NEVER delete a delta file that hasn't been merged by all devices.
     * A device offline for 59 days can still catch up when it reconnects.
     *
     * Fallback: If no devices found or error, uses 60 days as safety threshold.
     */
    private suspend fun cleanupOldDeltaFiles(client: GoogleDriveAppDataClient) {
        try {
            // Read manifest to get device info
            val manifestJson = client.readJsonFile("sync_manifest.json")
            val manifest = if (manifestJson != null) {
                try {
                    SyncManifest.fromJson(JSONObject(manifestJson))
                } catch (_: Exception) {
                    null
                }
            } else null

            val now = System.currentTimeMillis()
            val sixtyDaysMs = 60L * 24 * 60 * 60 * 1000

            // Find active devices (seen in last 60 days)
            val activeDevices = manifest?.devices?.filter { (_, info) ->
                (now - info.lastSeen) < sixtyDaysMs
            } ?: emptyMap()

            if (activeDevices.isEmpty()) {
                // No active devices found - use fallback (60 days)
                Log.d(TAG, "No active devices in manifest, using 60-day fallback")
                val deletedCount = client.deleteOldFiles("playcounts_device_", 60)
                if (deletedCount > 0) {
                    Log.d(TAG, "Cleaned up $deletedCount old delta files (fallback)")
                }
                return
            }

            // Find the MINIMUM lastSeen among active devices
            // This is the safe threshold - all active devices have synced since this time
            val minLastSeen = activeDevices.values.minOfOrNull { it.lastSeen } ?: 0L

            if (minLastSeen == 0L) {
                Log.d(TAG, "Could not determine safe cleanup threshold, skipping cleanup")
                return
            }

            // List all delta files and check each one
            val deltaFiles = client.listDeltaFiles()
            var deletedCount = 0

            for (filename in deltaFiles) {
                try {
                    val content = client.readJsonFile(filename) ?: continue
                    val deltaFile = PlayCountDeltaFile.fromJson(JSONObject(content))

                    // Only delete if createdAt < minLastSeen (all active devices have merged it)
                    if (deltaFile.createdAt < minLastSeen) {
                        val deleted = client.deleteFile(filename)
                        if (deleted) {
                            deletedCount++
                            Log.d(TAG, "Deleted delta file: $filename (createdAt=${deltaFile.createdAt} < minLastSeen=$minLastSeen)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking delta file $filename", e)
                }
            }

            if (deletedCount > 0) {
                Log.d(TAG, "Smart cleanup: deleted $deletedCount delta files (all devices have merged them)")
            } else {
                Log.d(TAG, "Smart cleanup: no files to delete (some devices may not have synced yet)")
            }

            // Also log active device status for debugging
            Log.d(TAG, "Active devices (${activeDevices.size}): ${activeDevices.keys.joinToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Error during smart cleanup", e)
            // Fallback to simple cleanup on error
            val deletedCount = client.deleteOldFiles("playcounts_device_", 60)
            if (deletedCount > 0) {
                Log.d(TAG, "Fallback cleanup: deleted $deletedCount old delta files")
            }
        }
    }

    /**
     * Performs a full backup if this device is the primary device.
     * Called during daily sync.
     */
    private suspend fun performBackupIfPrimary() {
        try {
            val isPrimary = BackupManager.isPrimaryDevice(appContext)
            if (isPrimary) {
                Log.d(TAG, "This device is primary, performing backup...")
                when (val result = BackupManager.performBackup(appContext)) {
                    is BackupManager.BackupResult.Success -> {
                        Log.d(TAG, "Backup completed successfully")
                    }
                    is BackupManager.BackupResult.Error -> {
                        Log.e(TAG, "Backup failed: ${result.message}")
                    }
                    else -> {
                        Log.d(TAG, "Backup skipped: $result")
                    }
                }
            } else {
                Log.d(TAG, "Not primary device, skipping backup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during backup check", e)
        }
    }

    /**
     * Checks if this device is set as the primary device for backups.
     */
    suspend fun isPrimaryDevice(): Boolean {
        if (!isInitialized) return false
        return BackupManager.isPrimaryDevice(appContext)
    }

    /**
     * Resets play counts after the multiplication bug.
     *
     * This function:
     * 1. Deletes ALL delta files on Google Drive (from all devices)
     * 2. Resets local playCount to earnedPlayCount (the real local plays)
     * 3. Clears local delta table
     * 4. Resets baselineDeltasCreated flag to allow fresh baseline creation
     *
     * IMPORTANT: Run this on ALL devices that were affected by the bug,
     * starting with the PRIMARY device.
     *
     * @return Result with counts of what was reset
     */
    suspend fun resetPlayCountsAfterBug(): PlayCountResetResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting play count reset after multiplication bug...")

        if (!isInitialized) {
            return@withContext PlayCountResetResult(
                success = false,
                errorMessage = "CloudSyncManager not initialized"
            )
        }

        val googleSignInManager = GoogleSignInManager(appContext)
        if (!googleSignInManager.isSignedIn()) {
            return@withContext PlayCountResetResult(
                success = false,
                errorMessage = "Not signed in to Google"
            )
        }

        val account = googleSignInManager.getSignedInAccount()
            ?: return@withContext PlayCountResetResult(
                success = false,
                errorMessage = "Could not get Google account"
            )

        try {
            val driveClient = GoogleDriveAppDataClient(appContext, account)
            val db = MusicDatabase.getInstance(appContext)
            val playCountDao = db.playCountDao()

            // Step 1: Delete all delta files on Google Drive
            val deltaFiles = driveClient.listDeltaFiles()
            var deletedCloudFiles = 0
            for (filename in deltaFiles) {
                if (driveClient.deleteFile(filename)) {
                    deletedCloudFiles++
                }
            }
            Log.d(TAG, "Deleted $deletedCloudFiles delta files from Google Drive")

            // Step 2: Reset local playCount to earnedPlayCount
            val beforeCount = playCountDao.countWithPlayCount()
            playCountDao.resetPlayCountsToEarned()
            val resetCount = beforeCount // All entries were potentially affected
            Log.d(TAG, "Reset $resetCount play count entries to earnedPlayCount")

            // Step 3: Clear local delta table
            val localDeltaCount = syncDeltaDao.count()
            syncDeltaDao.deleteAll()
            Log.d(TAG, "Cleared $localDeltaCount local deltas")

            // Step 4: Reset baselineDeltasCreated flag
            syncMetadataDao.resetBaselineDeltasCreated()
            Log.d(TAG, "Reset baselineDeltasCreated flag")

            // Step 5: Update POPM tags in files to match new counts
            // (This will be done progressively by MusicPopmSyncManager)

            PlayCountResetResult(
                success = true,
                deletedCloudFiles = deletedCloudFiles,
                resetPlayCounts = resetCount,
                clearedLocalDeltas = localDeltaCount
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during play count reset", e)
            PlayCountResetResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Sets this device as the primary device for backups.
     */
    suspend fun setPrimaryDevice(isPrimary: Boolean): Boolean {
        if (!isInitialized) return false
        return BackupManager.setPrimaryDevice(appContext, isPrimary)
    }

    /**
     * Deletes ALL cloud data from Google Drive appDataFolder.
     * This is a destructive, irreversible operation.
     *
     * @return DeleteCloudDataResult with success status and file count
     */
    suspend fun deleteAllCloudData(): DeleteCloudDataResult = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                return@withContext DeleteCloudDataResult(
                    success = false,
                    errorMessage = "CloudSyncManager not initialized"
                )
            }

            val signInManager = GoogleSignInManager(appContext)
            val account = signInManager.getSignedInAccount()
            if (account == null) {
                return@withContext DeleteCloudDataResult(
                    success = false,
                    errorMessage = "Not signed in"
                )
            }

            val client = GoogleDriveAppDataClient(appContext, account)
            val deletedCount = client.deleteAllFiles()

            if (deletedCount < 0) {
                return@withContext DeleteCloudDataResult(
                    success = false,
                    errorMessage = "Failed to delete files"
                )
            }

            // Also clear local sync deltas since cloud is wiped
            syncDeltaDao.deleteAll()

            // Reset last sync timestamp
            syncMetadataDao.updateLastSyncTimestamp(0)

            Log.d(TAG, "Deleted all cloud data: $deletedCount files")

            DeleteCloudDataResult(
                success = true,
                deletedFilesCount = deletedCount
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all cloud data", e)
            DeleteCloudDataResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
}

/**
 * Result of a sync operation.
 */
sealed class SyncResult {
    object Success : SyncResult()
    object NotInitialized : SyncResult()
    object NotSignedIn : SyncResult()
    object NotEnabled : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Result of the play count reset operation.
 */
data class PlayCountResetResult(
    val success: Boolean,
    val deletedCloudFiles: Int = 0,
    val resetPlayCounts: Int = 0,
    val clearedLocalDeltas: Int = 0,
    val errorMessage: String? = null
)

/**
 * Result of deleting all cloud data.
 */
data class DeleteCloudDataResult(
    val success: Boolean,
    val deletedFilesCount: Int = 0,
    val errorMessage: String? = null
)
