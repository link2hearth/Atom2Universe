package com.Atom2Universe.app.music.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker for nightly cloud sync.
 *
 * Scheduled to run once per day around 3 AM when:
 * - Device is connected to network
 * - Battery is not low
 *
 * The worker will:
 * 1. Download deltas from other devices
 * 2. Merge with local data
 * 3. Upload local changes
 * 4. Update sync manifest
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync worker...")

        // Syncthing export/import (source de vérité pour les listen_events)
        if (SyncthingManager.isConfigured(applicationContext)) {
            try {
                SyncthingManager.init(applicationContext)
                SyncthingManager.exportEvents(applicationContext)
                SyncthingManager.importEvents(applicationContext)
                Log.d(TAG, "Syncthing sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Syncthing sync failed", e)
            }
        }

        // Google Drive sync (conservé pour les autres données : favoris, playlists, paroles, EQ)
        CloudSyncManager.init(applicationContext)
        return when (val result = CloudSyncManager.syncNow()) {
            is SyncResult.Success -> {
                Log.d(TAG, "Cloud sync completed successfully")
                Result.success()
            }
            is SyncResult.NotInitialized -> {
                Log.w(TAG, "CloudSyncManager not initialized")
                Result.failure()
            }
            is SyncResult.NotSignedIn -> {
                Log.d(TAG, "User not signed in, skipping cloud sync")
                Result.success()
            }
            is SyncResult.NotEnabled -> {
                Log.d(TAG, "Cloud sync disabled, skipping")
                Result.success()
            }
            is SyncResult.Error -> {
                Log.e(TAG, "Cloud sync failed: ${result.message}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
