package com.Atom2Universe.app.notes.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class FavoriteColorsManager(context: Context) {

    data class FavoriteColor(val colorHex: String, val textColorMode: String = "auto")

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavorites(): List<FavoriteColor> {
        val json = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FavoriteColor(obj.getString("colorHex"), obj.optString("textColorMode", "auto"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addFavorite(colorHex: String, textColorMode: String = "auto") {
        val current = getFavorites().toMutableList()
        if (current.any { it.colorHex == colorHex }) return
        if (current.size >= MAX_FAVORITES) current.removeAt(0)
        current.add(FavoriteColor(colorHex, textColorMode))
        saveFavorites(current)
    }

    fun removeFavorite(colorHex: String) {
        val current = getFavorites().filter { it.colorHex != colorHex }
        saveFavorites(current)
    }

    fun isFavorite(colorHex: String) = getFavorites().any { it.colorHex == colorHex }

    private fun saveFavorites(favorites: List<FavoriteColor>) {
        val arr = JSONArray(favorites.map { JSONObject().apply {
            put("colorHex", it.colorHex); put("textColorMode", it.textColorMode)
        }})
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "notes_favorite_colors"
        private const val KEY_FAVORITES = "favorites"
        private const val MAX_FAVORITES = 16
    }
}
