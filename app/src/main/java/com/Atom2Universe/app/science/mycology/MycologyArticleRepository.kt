package com.Atom2Universe.app.science.mycology

import android.content.Context
import org.json.JSONObject

internal data class MycologyArticleSection(val title: String, val text: String)
internal data class MycologyArticleSource(val title: String, val url: String)
internal data class MycologyArticle(
    val verified: Boolean,
    val sections: List<MycologyArticleSection>,
    val traits: List<String>,
    val safetyNote: String,
    val sources: List<MycologyArticleSource>,
    val related: List<String>
)

/** Species articles live in language-specific assets, like botany and bestiary articles. */
internal object MycologyArticleRepository {
    fun load(context: Context, id: String, language: String): MycologyArticle {
        require(id.matches(Regex("[a-z0-9_]+")))
        val locale = if (language == "fr") "fr" else "en"
        val path = "science/mycology/encyclopedia/$locale/$id.json"
        val root = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        require(root.getString("id") == id) { "Wrong species in $path" }
        val sections = root.getJSONArray("sections").let { array ->
            (0 until array.length()).map { index ->
                val section = array.getJSONObject(index)
                MycologyArticleSection(section.getString("title"), section.getString("text"))
            }
        }
        val traits = root.optJSONArray("traits")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        }.orEmpty()
        val sources = root.getJSONArray("sources").let { array ->
            (0 until array.length()).map { index ->
                val source = array.getJSONObject(index)
                MycologyArticleSource(source.getString("title"), source.getString("url"))
            }
        }
        val related = root.optJSONArray("related")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        }.orEmpty()
        return MycologyArticle(root.optString("reviewStatus") == "verified", sections, traits,
            root.optString("safetyNote"), sources, related)
    }
}
