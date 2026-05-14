package com.Atom2Universe.app.music.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker : exporte les listen_events locaux puis importe
 * ceux des autres appareils depuis le dossier Syncthing.
 * Planifié par SyncthingManager.scheduleExport() (débounce 2 min)
 * et SyncthingManager.scheduleImmediateSync() (démarrage).
 */
class SyncExportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncExportWorker"
    }

    override suspend fun doWork(): Result {
        if (!SyncthingManager.isConfigured(applicationContext)) {
            Log.d(TAG, "Sync folder not configured, nothing to do")
            return Result.success()
        }

        return try {
            SyncthingManager.init(applicationContext)
            SyncthingManager.exportEvents(applicationContext)
            SyncthingManager.importEvents(applicationContext)
            Log.d(TAG, "Syncthing export/import completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Syncthing export/import failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
