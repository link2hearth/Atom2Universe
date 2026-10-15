package com.Atom2Universe.app.science.bestiary

import android.content.Context
import org.json.JSONObject

internal data class EncyclopediaReference(val url: String, val title: String)

internal data class EncyclopediaProfile(
    val sections: Map<String, String>,
    val references: List<EncyclopediaReference>
)

internal object AnimalEncyclopediaRepository {
    fun load(context: Context, id: String, language: String): EncyclopediaProfile {
        val locale = if (language == "fr") "fr" else "en"
        val json = context.assets.open("science/bestiary/encyclopedia/$locale/$id.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        val sectionJson = root.getJSONObject("sections")
        val sections = buildMap {
            val keys = sectionJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, sectionJson.getString(key))
            }
        }
        val sourceJson = root.getJSONArray("sources")
        val references = (0 until sourceJson.length()).map { index ->
            val source = sourceJson.getJSONObject(index)
            EncyclopediaReference(source.getString("url"), source.getString("title"))
        }
        return EncyclopediaProfile(sections, references)
    }
}
