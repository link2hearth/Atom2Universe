package com.Atom2Universe.app.science.botany

import android.content.Context
import org.json.JSONObject

internal data class BotanyArticleSection(val title: String, val text: String)
internal data class BotanyArticleSource(val title: String, val url: String)
internal data class BotanyArticle(
    val sections: List<BotanyArticleSection>,
    val sources: List<BotanyArticleSource>,
    val related: List<String>
)

/** Bilingual, source-linked plant articles kept with the bestiary's other offline encyclopaedia data. */
internal object BotanyEncyclopediaRepository {
    fun load(context: Context, id: String, language: String): BotanyArticle {
        val locale = if (language == "fr") "fr" else "en"
        val path = "botany/encyclopedia/$locale/$id.json"
        val root = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        val sectionJson = root.getJSONArray("sections")
        val sections = (0 until sectionJson.length()).map { index ->
            val section = sectionJson.getJSONObject(index)
            BotanyArticleSection(section.getString("title"), section.getString("text"))
        }
        val sourceJson = root.getJSONArray("sources")
        val sources = (0 until sourceJson.length()).map { index ->
            val source = sourceJson.getJSONObject(index)
            BotanyArticleSource(source.getString("title"), source.getString("url"))
        }
        val relatedJson = root.optJSONArray("related")
        val related = if (relatedJson == null) emptyList() else
            (0 until relatedJson.length()).map { relatedJson.getString(it) }
        return BotanyArticle(sections, sources, related)
    }
}
