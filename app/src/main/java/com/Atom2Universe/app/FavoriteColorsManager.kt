package com.Atom2Universe.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestionnaire des couleurs favorites
 * Stocke les combinaisons couleur + mode de texte dans SharedPreferences
 */
class FavoriteColorsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Représente une couleur favorite avec son mode de texte
     */
    data class FavoriteColor(
        val colorHex: String,
        val textColorMode: String
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("colorHex", colorHex)
                put("textColorMode", textColorMode)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): FavoriteColor {
                return FavoriteColor(
                    colorHex = json.getString("colorHex"),
                    textColorMode = json.optString("textColorMode", "auto")
                )
            }
        }
    }

    /**
     * Récupère la liste des couleurs favorites
     */
    fun getFavorites(): List<FavoriteColor> {
        val jsonString = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            (0 until jsonArray.length()).map { i ->
                FavoriteColor.fromJson(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Ajoute une couleur aux favoris
     * @return true si ajoutée, false si déjà existante
     */
    fun addFavorite(colorHex: String, textColorMode: String): Boolean {
        val favorites = getFavorites().toMutableList()

        // Vérifier si la combinaison existe déjà
        val exists = favorites.any {
            it.colorHex.equals(colorHex, ignoreCase = true) && it.textColorMode == textColorMode
        }
        if (exists) return false

        // Ajouter au début de la liste
        favorites.add(0, FavoriteColor(colorHex.uppercase(), textColorMode))

        // Limiter à MAX_FAVORITES
        while (favorites.size > MAX_FAVORITES) {
            favorites.removeAt(favorites.size - 1)
        }

        saveFavorites(favorites)
        return true
    }

    /**
     * Supprime une couleur des favoris
     */
    fun removeFavorite(colorHex: String, textColorMode: String) {
        val favorites = getFavorites().toMutableList()
        favorites.removeAll {
            it.colorHex.equals(colorHex, ignoreCase = true) && it.textColorMode == textColorMode
        }
        saveFavorites(favorites)
    }

    /**
     * Vérifie si une couleur est dans les favoris
     */
    fun isFavorite(colorHex: String, textColorMode: String): Boolean {
        return getFavorites().any {
            it.colorHex.equals(colorHex, ignoreCase = true) && it.textColorMode == textColorMode
        }
    }

    private fun saveFavorites(favorites: List<FavoriteColor>) {
        val jsonArray = JSONArray()
        favorites.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "notes_favorite_colors"
        private const val KEY_FAVORITES = "favorites"
        private const val MAX_FAVORITES = 16 // Maximum de favoris
    }
}
