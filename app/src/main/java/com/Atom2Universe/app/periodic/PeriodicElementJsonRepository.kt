package com.Atom2Universe.app.periodic

import android.content.Context
import org.json.JSONObject

data class ElementJsonData(
    val phase: String?,
    val appearance: String?,
    val density: Double?,
    val melt: Double?,
    val boil: Double?,
    val discoveredBy: String?,
    val namedBy: String?,
    val electronConfiguration: String?,
    val electronegativityPauling: Double?,
    val block: String?,
    val summary: String?,
    val shells: List<Int>
)

object PeriodicElementJsonRepository {

    private var data: Map<Int, ElementJsonData>? = null

    fun load(context: Context) {
        if (data != null) return
        val json = context.assets.open("Elements/periodic_table.json").bufferedReader().readText()
        val elements = JSONObject(json).getJSONArray("elements")
        val map = mutableMapOf<Int, ElementJsonData>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            map[el.getInt("number")] = ElementJsonData(
                phase = el.optNullableString("phase"),
                appearance = el.optNullableString("appearance"),
                density = el.optNullableDouble("density"),
                melt = el.optNullableDouble("melt"),
                boil = el.optNullableDouble("boil"),
                discoveredBy = el.optNullableString("discovered_by"),
                namedBy = el.optNullableString("named_by"),
                electronConfiguration = el.optNullableString("electron_configuration"),
                electronegativityPauling = el.optNullableDouble("electronegativity_pauling"),
                block = el.optNullableString("block"),
                summary = el.optNullableString("summary"),
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
