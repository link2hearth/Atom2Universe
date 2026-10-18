package com.Atom2Universe.app.science.parentes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoryChapter(val node: String, val cousin: String, val fork: String, val art: String,
    val title: String, val body: String, val caption: String, val sources: List<String>)
data class LineageStory(val id: String, val endpoint: String, val title: String,
    val subtitle: String, val chapters: List<StoryChapter>)
data class ParentesStories(val stories: List<LineageStory>, val sources: List<LifeSource>) {
    companion object {
        @Volatile private var cached: ParentesStories? = null
        private fun JSONArray.strings() = List(length()) { getString(it) }

        @Synchronized fun load(context: Context, tree: LifeTree): ParentesStories {
            cached?.let { return it }
            val data = JSONObject(context.assets.open("parentes/stories.json").bufferedReader().use { it.readText() })
            require(data.getInt("schema") == 1)
            val entries = data.getJSONArray("stories")
            val stories = List(entries.length()) { i ->
                val s = entries.getJSONObject(i)
                val chapters = s.getJSONArray("steps")
                LineageStory(s.getString("id"), s.getString("endpoint"), s.getString("title"), s.getString("subtitle"),
                    List(chapters.length()) { k ->
                        val c = chapters.getJSONObject(k)
                        StoryChapter(c.getString("node"), c.getString("cousin"), c.getString("fork"), c.getString("art"),
                            c.getString("title"), c.getString("body"), c.getString("caption"), c.getJSONArray("sources").strings())
                    })
            }
            stories.forEach { story ->
                require(story.chapters.isNotEmpty() && tree.nodes.getValue(story.endpoint).species)
                require(story.chapters.last().node == story.endpoint)
                val path = tree.ancestors(story.endpoint)
                story.chapters.forEach { c ->
                    require(c.node in path && tree.nodes.getValue(c.cousin).species && c.cousin != story.endpoint)
                    require(tree.lca(story.endpoint, c.cousin) == c.fork)
                }
                story.chapters.zipWithNext().forEach { (a,b) -> require(a.node in tree.ancestors(b.node).drop(1)) }
            }
            val refs = data.getJSONArray("sources")
            val sources = List(refs.length()) { i ->
                val s = refs.getJSONObject(i)
                LifeSource(s.getString("id"), s.getString("citation"), s.getString("url"), s.getString("license"), s.getString("license_url"))
            }
            return ParentesStories(stories, sources).also { cached = it }
        }
    }
}
