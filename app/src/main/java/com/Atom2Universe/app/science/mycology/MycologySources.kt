package com.Atom2Universe.app.science.mycology

import android.content.Context
import org.json.JSONObject

/** Les sources de l'atlas, lues dans `assets/science/mycology/sources.json` : elles ne vivent que dans « À propos ». */
object MycologySources {
    enum class Kind { OFFICIAL, JOURNAL, SOCIETY, ENCYCLOPEDIA }

    class Source(val id: String, val kind: Kind, val citation: String, val url: String, val species: Set<String>) {
        val general: Boolean get() = "*" in species
    }

    private var cache: List<Source>? = null

    fun load(context: Context): List<Source> = cache ?: runCatching {
        val text = context.assets.open("science/mycology/sources.json").bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("sources")
        List(array.length()) { index ->
            val o = array.getJSONObject(index)
            val species = o.getJSONArray("species")
            Source(
                id = o.getString("id"),
                kind = Kind.valueOf(o.getString("kind").uppercase()),
                citation = o.getString("citation"),
                url = o.getString("url"),
                species = List(species.length()) { species.getString(it) }.toSet()
            )
        }
    }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) cache = it }

    /** Les références d'une espèce : les siennes, puis celles qui valent pour toutes. */
    fun forSpecies(context: Context, id: String): List<Source> =
        load(context).filter { id in it.species || it.general }
}
