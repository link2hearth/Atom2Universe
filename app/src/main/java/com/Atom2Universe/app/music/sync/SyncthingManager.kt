package com.Atom2Universe.app.music.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.Atom2Universe.app.music.MusicPlayCountManager
import com.Atom2Universe.app.music.data.ListenEvent
import com.Atom2Universe.app.music.data.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Synchronisation des listen_events via un dossier partagé par Syncthing.
 *
 * Protocole :
 *   - Chaque appareil écrit [syncFolder]/a2u_events_[deviceId].json
 *   - Syncthing synchronise ce dossier entre les appareils
 *   - À chaque export on importe aussi les fichiers des autres appareils
 *   - Déduplication par UUID → aucun conflit possible
 *
 * L'utilisateur configure le dossier dans les paramètres. Ce dossier doit
 * correspondre au dossier Syncthing configuré sur tous les appareils.
 * Le NAS est un nœud Syncthing comme les autres.
 */
object SyncthingManager {

    private const val TAG = "SyncthingManager"
    private const val PREFS_NAME = "a2u_syncthing"
    private const val KEY_SYNC_FOLDER = "sync_folder_path"
    private const val KEY_LAST_EXPORT_AT = "last_export_at"
    private const val KEY_LAST_IMPORT_AT = "last_import_at"
    private const val KEY_LAST_IMPORT_COUNT = "last_import_count"
    private const val WORK_NAME = "a2u_syncthing_sync"
    private const val FILE_PREFIX = "a2u_events_"
    private const val FORMAT_VERSION = 1

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    // ==================== Configuration ====================

    fun isConfigured(context: Context): Boolean {
        val folder = getSyncFolder(context)
        return folder != null && folder.exists()
    }

    fun getSyncFolder(context: Context): File? {
        val path = prefs(context).getString(KEY_SYNC_FOLDER, null) ?: return null
        return File(path)
    }

    fun setSyncFolder(context: Context, path: String) {
        prefs(context).edit().putString(KEY_SYNC_FOLDER, path.trim()).apply()
        Log.i(TAG, "Sync folder set: $path")
    }

    fun getLastExportAt(context: Context): Long = prefs(context).getLong(KEY_LAST_EXPORT_AT, 0)
    fun getLastImportAt(context: Context): Long = prefs(context).getLong(KEY_LAST_IMPORT_AT, 0)
    fun getLastImportCount(context: Context): Int = prefs(context).getInt(KEY_LAST_IMPORT_COUNT, 0)

    // ==================== Scheduling ====================

    /**
     * Planifie un export/import différé de 2 minutes.
     * Appeler après chaque modification locale (écoute, favori, etc.).
     * Si déjà planifié, remet le timer à zéro (débounce).
     */
    fun scheduleExport() {
        val ctx = appContext ?: return
        val request = OneTimeWorkRequestBuilder<SyncExportWorker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d(TAG, "Sync export scheduled (2 min debounce)")
    }

    /**
     * Planifie un export/import immédiat (sans contrainte réseau).
     * Utilisé au démarrage de l'app pour récupérer les changements des autres appareils.
     */
    fun scheduleImmediateSync() {
        val ctx = appContext ?: return
        val request = OneTimeWorkRequestBuilder<SyncExportWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "${WORK_NAME}_startup",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    // ==================== Export ====================

    /**
     * Exporte tous les listen_events locaux vers [syncFolder]/a2u_events_[deviceId].json.
     * Syncthing propagera le fichier aux autres appareils automatiquement.
     */
    suspend fun exportEvents(context: Context) = withContext(Dispatchers.IO) {
        val folder = getSyncFolder(context) ?: run {
            Log.d(TAG, "No sync folder configured, skipping export")
            return@withContext
        }

        if (!folder.exists() && !folder.mkdirs()) {
            Log.w(TAG, "Cannot create sync folder: ${folder.absolutePath}")
            return@withContext
        }

        val deviceId = DeviceIdentity.getDeviceId(context)
        val events = MusicDatabase.getInstance(context)
            .listenEventDao()
            .getLocalEventsSince(deviceId, 0L)

        val eventsArray = JSONArray()
        for (e in events) {
            eventsArray.put(JSONObject().apply {
                put("uuid", e.uuid)
                put("trackKey", e.trackKey)
                put("deviceId", e.deviceId)
                put("listenedAt", e.listenedAt)
                put("durationListenedMs", e.durationListenedMs)
                put("trackDurationMs", e.trackDurationMs)
                put("title", e.title)
                put("artist", e.artist)
                put("album", e.album)
                put("isMigrated", e.isMigrated)
            })
        }

        val payload = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("deviceId", deviceId)
            put("exportedAt", System.currentTimeMillis())
            put("events", eventsArray)
        }

        val file = File(folder, "$FILE_PREFIX$deviceId.json")
        file.writeText(payload.toString(2))

        prefs(context).edit().putLong(KEY_LAST_EXPORT_AT, System.currentTimeMillis()).apply()
        Log.i(TAG, "Exported ${events.size} events → ${file.name}")
    }

    // ==================== Import ====================

    /**
     * Importe les événements de tous les autres appareils depuis le dossier sync.
     * La déduplication par UUID est garantie par IGNORE en base.
     * @return nombre de nouveaux événements insérés
     */
    suspend fun importEvents(context: Context): Int = withContext(Dispatchers.IO) {
        val folder = getSyncFolder(context) ?: return@withContext 0
        if (!folder.exists()) return@withContext 0

        val deviceId = DeviceIdentity.getDeviceId(context)
        val listenEventDao = MusicDatabase.getInstance(context).listenEventDao()

        val otherFiles = folder.listFiles { f ->
            f.name.startsWith(FILE_PREFIX)
                    && f.name.endsWith(".json")
                    && !f.name.contains(deviceId)
        } ?: return@withContext 0

        var totalImported = 0

        for (file in otherFiles) {
            try {
                val root = JSONObject(file.readText())
                if (root.optInt("formatVersion", 0) != FORMAT_VERSION) {
                    Log.w(TAG, "Unknown format in ${file.name}, skipping")
                    continue
                }

                val arr = root.getJSONArray("events")
                val incoming = ArrayList<ListenEvent>(arr.length())
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    incoming.add(
                        ListenEvent(
                            uuid = e.getString("uuid"),
                            trackKey = e.getString("trackKey"),
                            deviceId = e.getString("deviceId"),
                            listenedAt = e.getLong("listenedAt"),
                            durationListenedMs = e.getLong("durationListenedMs"),
                            trackDurationMs = e.getLong("trackDurationMs"),
                            title = e.getString("title"),
                            artist = e.getString("artist"),
                            album = e.getString("album"),
                            isMigrated = e.optBoolean("isMigrated", false)
                        )
                    )
                }

                // Filtrer les UUIDs déjà présents
                val known = listenEventDao.getExistingUuids(incoming.map { it.uuid }).toHashSet()
                val newEvents = incoming.filter { it.uuid !in known }

                if (newEvents.isNotEmpty()) {
                    if (MusicPlayCountManager.isInitialized()) {
                        MusicPlayCountManager.insertRemoteEvents(newEvents)
                    } else {
                        listenEventDao.insertAll(newEvents)
                    }
                    totalImported += newEvents.size
                    Log.i(TAG, "Imported ${newEvents.size} new events from ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading ${file.name}", e)
            }
        }

        if (totalImported > 0 || otherFiles.isNotEmpty()) {
            val now = System.currentTimeMillis()
            prefs(context).edit()
                .putLong(KEY_LAST_IMPORT_AT, now)
                .putInt(KEY_LAST_IMPORT_COUNT, totalImported)
                .apply()
            Log.i(TAG, "Import done: $totalImported new events from ${otherFiles.size} device file(s)")
        }

        totalImported
    }

    // ==================== Helpers ====================

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
