package com.Atom2Universe.app.news

import android.content.Context
import org.json.JSONObject

object NewsPreferences {

    private const val PREFS_NAME      = "news_prefs"
    private const val KEY_HIDDEN_IDS  = "hidden_article_ids"
    private const val KEY_BANNED      = "banned_words"
    private const val KEY_SOURCES     = "enabled_sources"
    private const val KEY_QUERY       = "last_query"
    private const val HIDDEN_TTL_MS   = 72L * 3_600_000L  // 72 h

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Sources ───────────────────────────────────────────────────────────────

    fun getEnabledSourceIds(context: Context): Set<String> {
        val stored = prefs(context).getStringSet(KEY_SOURCES, null)
        return stored ?: NewsSource.DEFAULT_SOURCES.filter { it.enabledByDefault }.map { it.id }.toSet()
    }

    fun setEnabledSourceIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SOURCES, ids).apply()
    }

    // ── Banned words ──────────────────────────────────────────────────────────

    fun getBannedWords(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_BANNED, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun setBannedWords(context: Context, words: List<String>) {
        prefs(context).edit().putString(KEY_BANNED, words.joinToString(",")).apply()
    }

    // ── Hidden article IDs (JSON map id→timestamp) ────────────────────────────

    fun getHiddenIds(context: Context): Map<String, Long> {
        val raw = prefs(context).getString(KEY_HIDDEN_IDS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val now  = System.currentTimeMillis()
            buildMap {
                json.keys().forEach { key ->
                    val ts = json.getLong(key)
                    if (now - ts < HIDDEN_TTL_MS) put(key, ts)
                }
            }
        } catch (_: Exception) { emptyMap() }
    }

    fun addHiddenId(context: Context, id: String) {
        val map = getHiddenIds(context).toMutableMap()
        map[id] = System.currentTimeMillis()
        saveHiddenIds(context, map)
    }

    fun removeHiddenId(context: Context, id: String) {
        val map = getHiddenIds(context).toMutableMap()
        map.remove(id)
        saveHiddenIds(context, map)
    }

    fun clearHiddenIds(context: Context) {
        prefs(context).edit().remove(KEY_HIDDEN_IDS).apply()
    }

    private fun saveHiddenIds(context: Context, map: Map<String, Long>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs(context).edit().putString(KEY_HIDDEN_IDS, json.toString()).apply()
    }

    // ── Search query ──────────────────────────────────────────────────────────

    fun getLastQuery(context: Context): String =
        prefs(context).getString(KEY_QUERY, "") ?: ""

    fun setLastQuery(context: Context, query: String) {
        prefs(context).edit().putString(KEY_QUERY, query).apply()
    }
}
