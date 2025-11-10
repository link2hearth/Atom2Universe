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
                val normalizedPayload = try {
                    normalizePayloadLocked(payload)
                } catch (error: JSONException) {
                    Log.w(TAG, "Unable to normalize save payload, persisting raw data", error)
                    payload
                }
                createAutoBackupLocked(normalizedPayload)
                writeToFile(normalizedPayload)
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
                    outputStream.fd.sync()
                }
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
        private const val NATIVE_SAVE_ENVELOPE_SCHEMA = "atom2univers.save.v2"
        private val RESERVED_ENVELOPE_KEYS = setOf(
            "schema",
            "version",
            "updatedAt",
            "lastSave"
        )
        private val ENVELOPE_CLICKER_KEYS = listOf(
            "clicker",
            "primary",
            "game",
            "state",
            "payload",
            "data",
            "save",
            "slot",
            "main"
        )
    }

    private fun normalizePayloadLocked(payload: String): String {
        val parsedPayload = parseJsonObject(payload) ?: return payload
        val now = System.currentTimeMillis()
        val existingEnvelope = readFromFile()?.let { parseJsonObject(it) }

        val normalized = when {
            isEnvelopePayload(parsedPayload) -> mergeEnvelope(parsedPayload, existingEnvelope, now)
            isArcadeProgressPayload(parsedPayload) -> mergeArcadeProgressPayload(parsedPayload, existingEnvelope, now)
            else -> buildEnvelope(parsedPayload, existingEnvelope, now)
        }

        return normalized?.toString() ?: payload
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        return try {
            val token = JSONTokener(raw).nextValue()
            token as? JSONObject
        } catch (error: JSONException) {
            null
        }
    }

    private fun isEnvelopePayload(payload: JSONObject): Boolean {
        val schema = payload.optString("schema")
        if (schema.equals(NATIVE_SAVE_ENVELOPE_SCHEMA, ignoreCase = true)) {
            return true
        }
        val version = payload.optInt("version", -1)
        if (version >= 2 && payload.has("clicker")) {
            return true
        }
        return false
    }

    private fun mergeEnvelope(
        payload: JSONObject,
        existing: JSONObject?,
        now: Long
    ): JSONObject? {
        val result = cloneJsonObject(payload)
        if (result == null) {
            return null
        }

        result.put("schema", NATIVE_SAVE_ENVELOPE_SCHEMA)
        result.put("version", 2)
        result.put("updatedAt", now)

        val clickerState = extractClickerState(result) ?: extractClickerState(existing)
        if (clickerState != null) {
            result.put("clicker", cloneJsonValue(clickerState))
        }

        val resolvedLastSave = extractTimestamp(clickerState ?: result, now)
        result.put("lastSave", resolvedLastSave)

        mergeEnvelopeSections(result, existing)

        val mergedMeta = mergeMetaSection(
            existing?.optJSONObject("meta"),
            result.optJSONObject("meta"),
            resolvedLastSave,
            now
        )
        result.put("meta", mergedMeta)

        return result
    }

    private fun buildEnvelope(
        payload: JSONObject,
        existing: JSONObject?,
        now: Long
    ): JSONObject? {
        val envelope = JSONObject()
        envelope.put("schema", NATIVE_SAVE_ENVELOPE_SCHEMA)
        envelope.put("version", 2)

        val clickerState = cloneJsonObject(payload) ?: return null
        envelope.put("clicker", clickerState)

        val resolvedLastSave = extractTimestamp(payload, now)
        envelope.put("lastSave", resolvedLastSave)
        envelope.put("updatedAt", now)

        if (payload.has("arcadeProgress")) {
            envelope.put("arcadeProgress", cloneJsonValue(payload.get("arcadeProgress")))
        }

        mergeEnvelopeSections(envelope, existing)

        val mergedMeta = mergeMetaSection(
            existing?.optJSONObject("meta"),
            envelope.optJSONObject("meta"),
            resolvedLastSave,
            now
        )
        envelope.put("meta", mergedMeta)

        return envelope
    }

    private fun mergeArcadeProgressPayload(
        payload: JSONObject,
        existing: JSONObject?,
        now: Long
    ): JSONObject? {
        val baseEnvelope = when {
            existing == null -> null
            isEnvelopePayload(existing) -> cloneJsonObject(existing)
            else -> buildEnvelope(existing, null, now)
        } ?: JSONObject()

        if (!baseEnvelope.has("schema")) {
            baseEnvelope.put("schema", NATIVE_SAVE_ENVELOPE_SCHEMA)
        }
        baseEnvelope.put("version", 2)
        baseEnvelope.put("updatedAt", now)

        val previousMeta = baseEnvelope.optJSONObject("meta")
        val normalizedProgress = normalizeArcadeProgressPayload(payload)
        baseEnvelope.put("arcadeProgress", normalizedProgress)

        val existingLastSave = extractTimestamp(existing, 0)
        val arcadeLastSave = if (existingLastSave > 0) existingLastSave else extractTimestamp(payload, now)
        baseEnvelope.put("lastSave", arcadeLastSave)

        val mergedMeta = mergeMetaSection(previousMeta, null, arcadeLastSave, now)
        baseEnvelope.put("meta", mergedMeta)

        return baseEnvelope
    }

    private fun normalizeArcadeProgressPayload(payload: JSONObject): JSONObject {
        val normalized = JSONObject()
        val version = payload.optInt("version", 1)
        normalized.put("version", if (version > 0) version else 1)

        val rawEntries = payload.optJSONObject("entries")
        val entries = when {
            rawEntries != null -> cloneJsonObject(rawEntries) ?: JSONObject()
            else -> JSONObject()
        }
        normalized.put("entries", entries)

        listOf("updatedAt", "lastSave").forEach { key ->
            val value = payload.opt(key)
            if (value != null && value != JSONObject.NULL) {
                normalized.put(key, value)
            }
        }

        return normalized
    }

    private fun isArcadeProgressPayload(payload: JSONObject): Boolean {
        if (!payload.has("entries")) {
            return false
        }
        if (payload.has("schema") || payload.has("clicker")) {
            return false
        }
        val entries = payload.opt("entries")
        if (entries !is JSONObject) {
            return false
        }

        val allowedKeys = setOf("version", "entries", "updatedAt", "lastSave")
        val iterator = payload.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (!allowedKeys.contains(key)) {
                return false
            }
        }

        return true
    }

    private fun mergeEnvelopeSections(target: JSONObject, existing: JSONObject?) {
        if (existing == null) {
            return
        }
        val iterator = existing.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (RESERVED_ENVELOPE_KEYS.contains(key) || key == "meta" || key == "clicker") {
                continue
            }
            if (!target.has(key)) {
                target.put(key, cloneJsonValue(existing.get(key)))
            }
        }
    }

    private fun mergeMetaSection(
        existing: JSONObject?,
        incoming: JSONObject?,
        lastSave: Long,
        updatedAt: Long
    ): JSONObject {
        val result = JSONObject()
        if (existing != null) {
            val iterator = existing.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                result.put(key, cloneJsonValue(existing.get(key)))
            }
        }
        if (incoming != null) {
            val iterator = incoming.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                result.put(key, cloneJsonValue(incoming.get(key)))
            }
        }
        result.put("lastSave", lastSave)
        result.put("updatedAt", updatedAt)
        return result
    }

    private fun extractClickerState(envelope: JSONObject?): JSONObject? {
        if (envelope == null) {
            return null
        }
        ENVELOPE_CLICKER_KEYS.forEach { key ->
            val direct = envelope.optJSONObject(key)
            if (isSerializedClickerState(direct)) {
                return direct
            }
            val raw = envelope.opt(key)
            if (raw is String) {
                val parsed = parseJsonObject(raw)
                if (isSerializedClickerState(parsed)) {
                    return parsed
                }
            } else if (raw is JSONObject && isSerializedClickerState(raw)) {
                return raw
            }
        }
        return null
    }

    private fun isSerializedClickerState(candidate: JSONObject?): Boolean {
        if (candidate == null) {
            return false
        }
        if (!candidate.has("atoms") || !candidate.has("lifetime")) {
            return false
        }
        if (!candidate.has("perClick") && !candidate.has("perSecond")) {
            return false
        }
        return true
    }

    private fun extractTimestamp(source: JSONObject?, fallback: Long): Long {
        if (source == null) {
            return fallback
        }
        val candidates = listOf("lastSave", "savedAt", "saved_at", "timestamp", "updatedAt")
        for (key in candidates) {
            val value = source.opt(key)
            val numeric = valueToLong(value)
            if (numeric != null && numeric > 0) {
                return numeric
            }
        }
        val meta = source.optJSONObject("meta")
        if (meta != null) {
            val nested = extractTimestamp(meta, 0)
            if (nested > 0) {
                return nested
            }
        }
        return fallback
    }

    private fun valueToLong(value: Any?): Long? {
        return when (value) {
            null -> null
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun cloneJsonObject(source: JSONObject?): JSONObject? {
        if (source == null) {
            return null
        }
        return try {
            JSONObject(source.toString())
        } catch (error: JSONException) {
            null
        }
    }

    private fun cloneJsonValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is JSONObject -> cloneJsonObject(value)
            is JSONArray -> try {
                JSONArray(value.toString())
            } catch (error: JSONException) {
                null
            }
            else -> value
        }
    }
}
