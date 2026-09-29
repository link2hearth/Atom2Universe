package com.Atom2Universe.app.periodic

import android.content.Context
import org.json.JSONObject

/**
 * Valeurs numériques et noms propres de periodic_table.json. Les textes en anglais de ce fichier
 * (apparence, résumé) ne sont pas lus : ils viennent des fichiers localisés.
 */
data class ElementJsonData(
    val phase: String?,
    val density: Double?,
    val melt: Double?,
    val boil: Double?,
    val discoveredBy: String?,
    val namedBy: String?,
    val electronConfiguration: String?,
    val electronegativityPauling: Double?,
    val block: String?,
    val shells: List<Int>
)

object PeriodicElementJsonRepository {

    // Écrit depuis un thread IO, lu depuis le thread principal.
    @Volatile
    private var data: Map<Int, ElementJsonData>? = null

    val isLoaded: Boolean get() = data != null

    @Synchronized
    fun load(context: Context) {
        if (data != null) return
        val json = context.assets.open("Elements/periodic_table.json").bufferedReader().use { it.readText() }
        val elements = JSONObject(json).getJSONArray("elements")
        val map = mutableMapOf<Int, ElementJsonData>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            map[el.getInt("number")] = ElementJsonData(
                phase = el.optNullableString("phase"),
                density = el.optNullableDouble("density"),
                melt = el.optNullableDouble("melt"),
                boil = el.optNullableDouble("boil"),
                discoveredBy = el.optNullableString("discovered_by"),
                namedBy = el.optNullableString("named_by"),
                electronConfiguration = el.optNullableString("electron_configuration"),
                electronegativityPauling = el.optNullableDouble("electronegativity_pauling"),
                block = el.optNullableString("block"),
                shells = el.optJSONArray("shells")?.let { arr ->
                    (0 until arr.length()).map { arr.getInt(it) }
                } ?: emptyList()
            )
        }
        data = map
    }

    fun get(atomicNumber: Int): ElementJsonData? = data?.get(atomicNumber)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifEmpty { null }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key, Double.NaN).takeIf { !it.isNaN() }
}
