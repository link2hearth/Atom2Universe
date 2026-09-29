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
                (0 until dm.length()).map { dm.getString(it) }
            }
            // « 1.248e9 a » : mantisse et unité séparées pour pouvoir les localiser.
            val halfLife = if (o.isNull("halfLife")) null else o.optString("halfLife").trim().ifEmpty { null }
            val value = halfLife?.substringBefore(' ')
            val unit = halfLife?.substringAfter(' ', "")?.ifEmpty { null }
            list.add(Nuclide(
                Z = o.getInt("Z"),
                N = o.getInt("N"),
                A = o.getInt("A"),
                symbol = o.getString("symbol"),
                stable = o.getBoolean("stable"),
                halfLifeValue = value,
                halfLifeUnit = unit,
                decayModes = modes,
                spin = normalizeSpin(o.optString("spin", "")),
                bindingEnergyPerNucleon = o.getDouble("bindingEnergyPerNucleon")
            ))
        }
        nuclides = list
    }

    /**
     * Les données mélangent « 1/2+ », « +1/2 » et « +1/2+ » (parité en tête, parfois doublée).
     * On ramène tout à la notation usuelle J^π : « +0 » → « 0+ », « +1/2+ » → « 1/2+ ».
     */
    internal fun normalizeSpin(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty() || s == "?") return null
        val lead = s.first()
        if (lead != '+' && lead != '-') return s
        val rest = s.substring(1)
        if (rest.isEmpty()) return null
        return if (rest.last() == '+' || rest.last() == '-') rest else rest + lead
    }

    fun getAll(): List<Nuclide> = nuclides ?: emptyList()

    private val elementByZ by lazy { getPeriodicElements().associateBy { it.atomicNumber } }

    fun getElementName(context: Context, Z: Int): String {
        val element = elementByZ[Z] ?: return ""
        return PeriodicElementDescriptionProvider(context).getName(element)
    }
}
