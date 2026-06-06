package com.Atom2Universe.app.science.nuclide

import android.content.Context
import com.Atom2Universe.app.periodic.PeriodicElementDescriptionProvider
import com.Atom2Universe.app.periodic.getPeriodicElements
import org.json.JSONObject

object NuclideRepository {

    private var nuclides: List<Nuclide>? = null
    private var byZN: Map<Pair<Int,Int>, Nuclide>? = null

    fun load(context: Context) {
        if (nuclides != null) return
        val json = context.assets.open("nuclides/nuclides.json").bufferedReader().readText()
        val arr = JSONObject(json).getJSONArray("nuclides")
        val list = mutableListOf<Nuclide>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val modes = o.getJSONArray("decayModes").let { dm ->
                (0 until dm.length()).map { dm.getString(it) }
            }
            list.add(Nuclide(
                Z = o.getInt("Z"),
                N = o.getInt("N"),
                A = o.getInt("A"),
                symbol = o.getString("symbol"),
                stable = o.getBoolean("stable"),
                halfLife = if (o.isNull("halfLife")) null else o.optString("halfLife"),
                decayModes = modes,
                spin = o.getString("spin"),
                bindingEnergyPerNucleon = o.getDouble("bindingEnergyPerNucleon")
            ))
        }
        nuclides = list
        byZN = list.associateBy { it.Z to it.N }
    }

    fun getAll(): List<Nuclide> = nuclides ?: emptyList()

    fun get(Z: Int, N: Int): Nuclide? = byZN?.get(Z to N)

    private val elementByZ by lazy { getPeriodicElements().associateBy { it.atomicNumber } }

    fun getElementName(context: Context, Z: Int): String {
        val element = elementByZ[Z] ?: return ""
        return PeriodicElementDescriptionProvider(context).getName(element)
    }

    val maxZ: Int get() = nuclides?.maxOfOrNull { it.Z } ?: 118
    val maxN: Int get() = nuclides?.maxOfOrNull { it.N } ?: 180
}
