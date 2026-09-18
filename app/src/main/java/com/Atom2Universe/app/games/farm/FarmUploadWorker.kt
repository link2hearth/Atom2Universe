package com.Atom2Universe.app.games.farm

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Publishes the farm once the network is there.
 *
 * WorkManager holds the job while the device is offline, keeps it if Android kills the app or the
 * phone restarts, and runs it as soon as a connection comes back. The decision itself stays in
 * [FarmSyncManager]: this class only says whether an attempt is worth making again.
 *
 * Only a failure is. "Someone published while you played" is an answer, not an outage - retrying
 * on it would spin forever, when the real answer is to wait for the player's choice at the next
 * opening.
 */
class FarmUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = FarmSyncManager.uploadPending(applicationContext)
        Log.d(TAG, "Attempt ${runAttemptCount + 1}: $outcome")
        return when (outcome) {
            // After the last attempt the farm simply stays unsent, and the next closing of the game
            // queues a fresh job: giving up here never loses the session.
            FarmSyncManager.UploadOutcome.FAILED ->
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        private const val TAG = "FarmUploadWorker"

        /** With a 30 s backoff doubling each time, about two hours of trying while connected. */
        private const val MAX_RETRIES = 8
    }
}
