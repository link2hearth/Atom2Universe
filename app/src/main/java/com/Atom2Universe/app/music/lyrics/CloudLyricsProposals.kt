package com.Atom2Universe.app.music.lyrics

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Les paroles venues du cloud qu'on n'a pas affichées parce que le MP3 de cet
 * appareil avait déjà les siennes (« le fichier gagne »).
 *
 * Plutôt que de les jeter, on les garde ici pour les proposer dans l'édition des
 * paroles, à côté des résultats de recherche : l'utilisateur voit les deux versions
 * et choisit. Seules les paroles différentes de celles du fichier sont gardées, donc
 * le fichier reste petit.
 */
object CloudLyricsProposals {

    private const val TAG = "CloudLyricsProposals"
    private const val FILENAME = "cloud_lyrics_proposals.json"

    data class Proposal(val lyrics: String, val isSynced: Boolean, val modifiedAt: Long)

    private val lock = Any()
    private var cache: MutableMap<String, Proposal>? = null

    /**
     * Garde ces paroles du cloud pour ce titre, sauf si elles sont identiques à celles
     * du fichier ou plus anciennes qu'une proposition déjà gardée.
     */
    fun offer(context: Context, key: String, cloudLyrics: String, isSynced: Boolean, modifiedAt: Long, fileLyrics: String?) {
        if (cloudLyrics.isBlank()) return
        if (fileLyrics != null && cloudLyrics.trim() == fileLyrics.trim()) {
            remove(context, key)
            return
        }
        synchronized(lock) {
            val map = load(context)
            val existing = map[key]
            if (existing != null && existing.modifiedAt >= modifiedAt) return
            map[key] = Proposal(cloudLyrics, isSynced, modifiedAt)
            save(context, map)
        }
    }

    fun get(context: Context, key: String): Proposal? = synchronized(lock) { load(context)[key] }

    /** L'utilisateur a tranché pour ce titre : la proposition n'a plus lieu d'être. */
    fun remove(context: Context, key: String) {
        synchronized(lock) {
            val map = load(context)
            if (map.remove(key) != null) save(context, map)
        }
    }

    private fun load(context: Context): MutableMap<String, Proposal> {
        cache?.let { return it }
        val map = mutableMapOf<String, Proposal>()
        try {
            val file = File(context.filesDir, FILENAME)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                json.keys().forEach { key ->
                    val obj = json.optJSONObject(key) ?: return@forEach
                    map[key] = Proposal(
                        lyrics = obj.optString("lyrics", ""),
                        isSynced = obj.optBoolean("isSynced", false),
                        modifiedAt = obj.optLong("modifiedAt", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading proposals", e)
        }
        cache = map
        return map
    }

    private fun save(context: Context, map: Map<String, Proposal>) {
        try {
            val json = JSONObject()
            map.forEach { (key, p) ->
                json.put(key, JSONObject().apply {
                    put("lyrics", p.lyrics)
                    put("isSynced", p.isSynced)
                    put("modifiedAt", p.modifiedAt)
                })
            }
            File(context.filesDir, FILENAME).writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving proposals", e)
        }
    }
}
