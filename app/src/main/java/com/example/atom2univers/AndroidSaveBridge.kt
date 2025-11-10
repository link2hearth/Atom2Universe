package com.example.atom2univers

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.content.edit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.text.Charsets

class AndroidSaveBridge(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val saveDirectory: File = appContext.filesDir
    private val saveFile: File = File(saveDirectory, SAVE_FILE_NAME)
    private val saveLock = Any()
    private val backupDirectory: File = File(saveDirectory, BACKUP_DIRECTORY_NAME)

    @JavascriptInterface
    fun saveData(payload: String?) {
        synchronized(saveLock) {
            if (payload.isNullOrEmpty()) {
                clearDataInternal()
                return
            }

            try {
                createAutoBackupLocked(payload)
                writeToFile(payload)
                removeLegacyPreference()
            } catch (error: IOException) {
                Log.e(TAG, "Unable to persist save data", error)
            }
        }
    }

    @JavascriptInterface
    fun loadData(): String? {
        synchronized(saveLock) {
            val fileData = readFromFile()
            if (!fileData.isNullOrEmpty()) {
                if (isValidSavePayload(fileData)) {
                    return fileData
                }
                Log.w(TAG, "Primary save data is corrupted, attempting recovery")
                val restored = restoreLatestBackupLocked()
                if (!restored.isNullOrEmpty()) {
                    return restored
                }
                Log.e(TAG, "No valid backup available, clearing corrupted save data")
                clearDataInternal()
                return null
            }

            val legacyData = preferences.getString(KEY_SAVE, null)
            if (!legacyData.isNullOrEmpty()) {
                if (!isValidSavePayload(legacyData)) {
                    Log.w(TAG, "Ignoring invalid legacy save data")
                    removeLegacyPreference()
                    return null
                }
                try {
                    writeToFile(legacyData)
                    removeLegacyPreference()
                } catch (error: IOException) {
                    Log.w(TAG, "Unable to migrate legacy save data", error)
                }
                return legacyData
            }

            return null
        }
    }

    @JavascriptInterface
    fun clearData() {
        synchronized(saveLock) {
            clearDataInternal()
        }
    }

    @JavascriptInterface
    fun saveBackup(payload: String?, label: String?, source: String?): String? {
        if (payload.isNullOrEmpty()) {
            return null
        }
        val normalizedSource = when (source?.lowercase()) {
            "auto" -> "auto"
            else -> "manual"
        }
        synchronized(saveLock) {
            return try {
                val entry = writeBackupInternal(payload, label, normalizedSource)
                trimBackupDirectory()
                entry.put("source", normalizedSource)
                entry.toString()
            } catch (error: IOException) {
                Log.e(TAG, "Unable to create manual backup", error)
                null
            }
        }
    }

    @JavascriptInterface
    fun listBackups(): String {
        synchronized(saveLock) {
            val array = JSONArray()
            if (!backupDirectory.exists()) {
                return array.toString()
            }
            val files = backupDirectory.listFiles { file ->
                file.isFile && file.name.endsWith(".json", ignoreCase = true)
            } ?: return array.toString()

            files.sortedByDescending { it.lastModified() }
                .forEach { file ->
                    val metadata = readBackupMetadata(file)
                    if (metadata != null) {
                        array.put(metadata)
                    }
                }
            return array.toString()
        }
    }

    @JavascriptInterface
    fun loadBackup(id: String?): String? {
        if (id.isNullOrBlank()) {
            return null
        }
        synchronized(saveLock) {
            val file = File(backupDirectory, "$id.json")
            if (!file.exists()) {
                return null
            }
            return try {
                file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    val raw = reader.readText()
                    val parsed = JSONObject(raw)
                    parsed.optString("data", null)
                }
            } catch (error: IOException) {
                Log.e(TAG, "Unable to load backup $id", error)
                null
            } catch (error: JSONException) {
                Log.e(TAG, "Unable to parse backup $id", error)
                null
            }
        }
    }

    @JavascriptInterface
    fun deleteBackup(id: String?) {
        if (id.isNullOrBlank()) {
            return
        }
        synchronized(saveLock) {
            val file = File(backupDirectory, "$id.json")
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Unable to delete backup file ${file.name}")
            }
        }
    }

    private fun restoreLatestBackupLocked(): String? {
        if (!backupDirectory.exists()) {
            return null
        }
        val files = backupDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".json", ignoreCase = true)
        } ?: return null

        files.sortedByDescending { it.lastModified() }
            .forEach { file ->
                val payload = readBackupPayload(file)
                if (payload.isNullOrEmpty() || !isValidSavePayload(payload)) {
                    return@forEach
                }
                try {
                    writeToFile(payload)
                    Log.i(TAG, "Restored primary save data from backup ${file.name}")
                    return payload
                } catch (error: IOException) {
                    Log.e(TAG, "Unable to restore backup ${file.name}", error)
                }
            }

        return null
    }

    private fun readFromFile(): String? {
        if (!saveFile.exists()) {
            return null
        }
        return try {
            saveFile.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to read save file", error)
            null
        }
    }

    private fun readBackupPayload(file: File): String? {
        return try {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val raw = reader.readText()
                val parsed = JSONObject(raw)
                parsed.optString("data", null)
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to read backup payload from ${file.name}", error)
            null
        } catch (error: JSONException) {
            Log.e(TAG, "Unable to parse backup payload from ${file.name}", error)
            null
        }
    }

    @Throws(IOException::class)
    private fun writeToFile(payload: String) {
        if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
            throw IOException("Unable to create save directory: ${saveDirectory.absolutePath}")
        }

        val tempFile = File(saveDirectory, "$SAVE_FILE_NAME.tmp")

        try {
            FileOutputStream(tempFile).use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
                outputStream.fd.sync()
            }

            if (saveFile.exists() && !saveFile.delete()) {
                throw IOException("Unable to delete existing save file")
            }

            if (!tempFile.renameTo(saveFile)) {
                throw IOException("Unable to move temp save file into place")
            }
        } finally {
            if (tempFile.exists() && tempFile != saveFile) {
                tempFile.delete()
            }
        }
    }

    private fun isValidSavePayload(payload: String): Boolean {
        if (payload.isBlank()) {
            return false
        }
        return try {
            val token = JSONTokener(payload).nextValue()
            token is JSONObject || token is JSONArray
        } catch (error: JSONException) {
            false
        }
    }

    private fun clearDataInternal() {
        if (saveFile.exists() && !saveFile.delete()) {
            Log.w(TAG, "Unable to delete save file")
        }

        val tempFile = File(saveDirectory, "$SAVE_FILE_NAME.tmp")
        if (tempFile.exists() && !tempFile.delete()) {
            Log.w(TAG, "Unable to delete temporary save file")
        }

        removeLegacyPreference()
    }

    private fun createAutoBackupLocked(nextPayload: String) {
        if (!saveFile.exists()) {
            return
        }
        val current = readFromFile() ?: return
        if (current == nextPayload) {
            return
        }
        try {
            writeBackupInternal(current, null, "auto")
            trimBackupDirectory()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to create automatic backup", error)
        }
    }

    @Throws(IOException::class)
    private fun writeBackupInternal(payload: String, label: String?, source: String): JSONObject {
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw IOException("Unable to create backup directory: ${backupDirectory.absolutePath}")
        }

        val timestamp = System.currentTimeMillis()
        val id = "bk_$timestamp"
        val tempFile = File(backupDirectory, "$id.tmp")
        val backupFile = File(backupDirectory, "$id.json")

        val wrapper = JSONObject()
        wrapper.put("version", 1)
        wrapper.put("savedAt", timestamp)
        wrapper.put("source", source)
        wrapper.put("data", payload)
        if (!label.isNullOrBlank()) {
            wrapper.put("label", label.trim())
        }

        try {
            FileOutputStream(tempFile).use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(wrapper.toString())
                    writer.flush()
                }
                outputStream.fd.sync()
            }

            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Unable to delete existing backup file")
            }

            if (!tempFile.renameTo(backupFile)) {
                throw IOException("Unable to move temp backup file into place")
            }
        } finally {
            if (tempFile.exists() && tempFile != backupFile) {
                tempFile.delete()
            }
        }

        val entry = JSONObject()
        entry.put("id", id)
        entry.put("savedAt", timestamp)
        entry.put("size", backupFile.length())
        if (!label.isNullOrBlank()) {
            entry.put("label", label.trim())
        }
        entry.put("source", source)
        return entry
    }

    private fun trimBackupDirectory() {
        if (!backupDirectory.exists()) {
            return
        }
        val files = backupDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".json", ignoreCase = true)
        } ?: return

        if (files.size <= MAX_BACKUP_COUNT) {
            return
        }

        files.sortedByDescending { it.lastModified() }
            .drop(MAX_BACKUP_COUNT)
            .forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Unable to trim backup file ${file.name}")
                }
            }
    }

    private fun readBackupMetadata(file: File): JSONObject? {
        return try {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val raw = reader.readText()
                val parsed = JSONObject(raw)
                val savedAt = parsed.optLong("savedAt", file.lastModified())
                val source = parsed.optString("source", "manual")
                val label = parsed.optString("label", null)
                val entry = JSONObject()
                entry.put("id", file.name.removeSuffix(".json"))
                entry.put("savedAt", savedAt)
                entry.put("size", file.length())
                entry.put("source", source)
                if (!label.isNullOrBlank()) {
                    entry.put("label", label)
                }
                return entry
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to read backup metadata from ${file.name}", error)
            null
        } catch (error: JSONException) {
            Log.e(TAG, "Unable to parse backup metadata from ${file.name}", error)
            null
        }
    }

    private fun removeLegacyPreference() {
        preferences.edit(commit = true) {
            remove(KEY_SAVE)
        }
    }

    private companion object {
        private const val TAG = "AndroidSaveBridge"
        private const val PREF_NAME = "atom2univers_storage"
        private const val KEY_SAVE = "atom2univers_save"
        private const val SAVE_FILE_NAME = "atom2univers_save.json"
        private const val BACKUP_DIRECTORY_NAME = "atom2univers_backups"
        private const val MAX_BACKUP_COUNT = 8
    }
}
