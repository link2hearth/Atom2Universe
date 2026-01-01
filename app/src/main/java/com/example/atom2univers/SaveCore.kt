package com.example.atom2univers

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class SaveCore(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val saveLock = Any()

    fun get(key: String?): String? {
        if (key.isNullOrBlank()) {
            return null
        }
        synchronized(saveLock) {
            val payload = loadJsonObjectLocked() ?: return null
            val value = payload.opt(key)
            if (value == null || value == JSONObject.NULL) {
                return null
            }
            return when (value) {
                is JSONObject -> value.toString()
                is JSONArray -> value.toString()
                else -> value.toString()
            }
        }
    }

    fun set(key: String?, value: String?): Boolean {
        if (key.isNullOrBlank()) {
            return false
        }
        synchronized(saveLock) {
            val payload = loadJsonObjectLocked() ?: JSONObject()
            if (value == null) {
                payload.remove(key)
            } else {
                payload.put(key, parseJsonValue(value))
            }
            return persistJsonLocked(payload)
        }
    }

    fun getAll(): String? {
        return export()
    }

    fun mergeSave(saveBlob: String?): Boolean {
        if (saveBlob.isNullOrBlank()) {
            return false
        }
        val incoming = parseJsonObject(saveBlob) ?: return false
        synchronized(saveLock) {
            val payload = loadJsonObjectLocked() ?: JSONObject()
            val iterator = incoming.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                payload.put(key, incoming.get(key))
            }
            return persistJsonLocked(payload)
        }
    }

    fun export(): String? {
        synchronized(saveLock) {
            return preferences.getString(KEY_BLOB, null)
        }
    }

    fun import(blob: String?): Boolean {
        synchronized(saveLock) {
            if (blob.isNullOrBlank()) {
                clearLocked()
                return true
            }
            val parsed = parseJsonObject(blob) ?: return false
            if (isArcadeProgressPayload(parsed)) {
                val payload = loadJsonObjectLocked() ?: JSONObject()
                payload.put("arcadeProgress", parsed)
                return persistJsonLocked(payload)
            }
            return persistJsonLocked(parsed)
        }
    }

    fun transaction(action: SaveCoreTransaction.() -> Unit): String? {
        synchronized(saveLock) {
            val payload = loadJsonObjectLocked() ?: JSONObject()
            val transaction = SaveCoreTransaction(payload)
            transaction.action()
            persistJsonLocked(payload)
            return payload.toString()
        }
    }

    fun migrateFromLegacy(legacyLoader: () -> String?): Boolean {
        synchronized(saveLock) {
            if (preferences.getBoolean(KEY_MIGRATED, false)) {
                return false
            }
        }
        val legacy = legacyLoader()
        val imported = if (!legacy.isNullOrBlank()) {
            import(legacy)
        } else {
            false
        }
        preferences.edit(commit = true) {
            putBoolean(KEY_MIGRATED, true)
        }
        return imported
    }

    private fun clearLocked() {
        preferences.edit(commit = true) {
            remove(KEY_BLOB)
        }
    }

    private fun loadJsonObjectLocked(): JSONObject? {
        val raw = preferences.getString(KEY_BLOB, null) ?: return null
        return parseJsonObject(raw)
    }

    private fun persistJsonLocked(payload: JSONObject): Boolean {
        preferences.edit(commit = true) {
            putString(KEY_BLOB, payload.toString())
        }
        return true
    }

    private fun parseJsonValue(raw: String): Any {
        return try {
            JSONTokener(raw).nextValue()
        } catch (error: JSONException) {
            raw
        }
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        return try {
            val token = JSONTokener(raw).nextValue()
            token as? JSONObject
        } catch (error: JSONException) {
            null
        }
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

    companion object {
        private const val PREF_NAME = "atom2univers_save_core"
        private const val KEY_BLOB = "save_core_blob"
        private const val KEY_MIGRATED = "save_core_migrated"
    }
}

class SaveCoreTransaction(private val payload: JSONObject) {

    fun get(key: String?): Any? {
        if (key.isNullOrBlank()) {
            return null
        }
        return payload.opt(key)
    }

    fun set(key: String?, value: Any?): Boolean {
        if (key.isNullOrBlank()) {
            return false
        }
        if (value == null || value == JSONObject.NULL) {
            payload.remove(key)
            return true
        }
        payload.put(key, value)
        return true
    }

    fun remove(key: String?): Boolean {
        if (key.isNullOrBlank()) {
            return false
        }
        payload.remove(key)
        return true
    }

    fun mergeSave(saveBlob: String?): Boolean {
        if (saveBlob.isNullOrBlank()) {
            return false
        }
        val incoming = try {
            val token = JSONTokener(saveBlob).nextValue()
            token as? JSONObject
        } catch (error: JSONException) {
            null
        } ?: return false
        val iterator = incoming.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            payload.put(key, incoming.get(key))
        }
        return true
    }
}
