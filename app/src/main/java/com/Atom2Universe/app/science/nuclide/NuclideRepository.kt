package com.Atom2Universe.app.science.nuclide

import android.content.Context
import com.Atom2Universe.app.periodic.PeriodicElementDescriptionProvider
import com.Atom2Universe.app.periodic.getPeriodicElements
import org.json.JSONObject

object NuclideRepository {

    // Écrit depuis un thread IO, lu depuis le thread principal.
    @Volatile
    private var nuclides: List<Nuclide>? = null

    @Synchronized
    fun load(context: Context) {
        if (nuclides != null) return
        val json = context.assets.open("nuclides/nuclides.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("nuclides")
        val list = mutableListOf<Nuclide>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val modes = o.getJSONArray("decayModes").let { dm ->
                (0 until dm.length()).map {
                    val m = dm.getJSONObject(it)
                    DecayMode(m.getString("mode"), m.optNullableString("percent")?.toDoubleOrNull())
                }
            }
            list.add(Nuclide(
                Z = o.getInt("Z"),
                N = o.getInt("N"),
                A = o.getInt("A"),
                symbol = o.getString("symbol"),
                stable = o.getBoolean("stable"),
                halfLifeValue = o.optNullableString("halfLife"),
                halfLifeUnit = o.optNullableString("halfLifeUnit"),
                halfLifeOperator = o.optNullableString("halfLifeOperator"),
                decayModes = modes,
                spin = o.optNullableString("spin"),
                bindingEnergyPerNucleon = o.getDouble("bindingEnergyPerNucleon")
            ))
        }
        nuclides = list
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().ifEmpty { null }

    fun getAll(): List<Nuclide> = nuclides ?: emptyList()

    private val elementByZ by lazy { getPeriodicElements().associateBy { it.atomicNumber } }

    fun getElementName(context: Context, Z: Int): String {
        val element = elementByZ[Z] ?: return ""
        return PeriodicElementDescriptionProvider(context).getName(element)
    }
}
