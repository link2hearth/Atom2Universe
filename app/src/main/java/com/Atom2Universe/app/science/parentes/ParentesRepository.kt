package com.Atom2Universe.app.science.parentes

import android.content.Context
import android.content.res.Configuration
import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.content.ComponentCallbacks2
import android.os.SystemClock
import android.util.Log

data class GroupArticle(val id: String, val key: String, val sources: List<String>)
data class LifeSource(val id: String, val citation: String, val url: String, val license: String, val licenseUrl: String)
class ParentesRepository private constructor(
    val tree: LifeTree, val version: String, val taxonomy: String, val retrieved: String,
    val groups: Map<String, GroupArticle>, val sources: List<LifeSource>,
    val notes: Map<String, SpeciesNote>,
    val labels: Map<String, SpeciesLabel>,
    private val appContext: Context
) {
    private val resourceIds = ConcurrentHashMap<String, Int>()
    private fun resource(key: String): Int = resourceIds.getOrPut(key) {
        appContext.resources.getIdentifier(key, "string", appContext.packageName).also { require(it != 0) { "Missing $key" } }
    }
    fun text(context: Context, key: String) = context.getString(resource(key))
    val overview: TreeScene by lazy { tree.atlas(tree.root) }
    val overviewPositions: Map<String, TreeLayout.RadialPoint> by lazy { TreeLayout.radial(overview) }
    private val searchTerms: Map<String, String> by lazy {
        fun localized(language: String) = appContext.createConfigurationContext(Configuration(appContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
        val en = localized("en"); val fr = localized("fr")
        tree.nodes.values.filter { it.species || tree.isBrowsable(it.id) }.associate { n ->
            val key = groups[n.id]?.key ?: n.labelKey
            n.id to LifeTree.normalize(listOf(n.scientific, n.id,
                if (key.isNotEmpty()) en.getString(resource(key)) else "",
                if (key.isNotEmpty()) fr.getString(resource(key)) else "",
                (labels[n.id]?.aliases ?: notes[n.id]?.aliases).orEmpty().joinToString(" ")).joinToString(" "))
        }
    }
    fun name(context: Context, id: String): String {
        val node = tree.nodes.getValue(id)
        val key = groups[id]?.key ?: node.labelKey
        return when {
            key.isNotEmpty() -> text(context, key)
            node.scientific.isNotEmpty() -> node.scientific
            else -> context.getString(R.string.pt_junction)
        }
    }
    fun search(query: String, speciesOnly: Boolean): List<LifeNode> {
        val terms = LifeTree.normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return tree.nodes.values.filter { n ->
            (n.species || (!speciesOnly && tree.isBrowsable(n.id))) &&
                terms.all { it in searchTerms.getValue(n.id) }
        }
    }

    companion object {
        private fun JSONArray.strings() = (0 until length()).map { getString(it) }
        @Volatile private var cached: ParentesRepository? = null
        private var callbacksRegistered = false

        @Synchronized
        fun load(context: Context): ParentesRepository {
            fun json(file: String) = JSONObject(context.assets.open("parentes/$file").bufferedReader().use { it.readText() })
            cached?.let { return it }
            val started = SystemClock.elapsedRealtime()
            val archive = LifeArchive.read(context.assets.open("parentes/runtime.bin"))
            val content = json("content.json")
            val groupArray = content.getJSONArray("groups")
            val groups = (0 until groupArray.length()).map { i ->
                val g = groupArray.getJSONObject(i)
                GroupArticle(g.getString("id"), g.getString("key"), g.getJSONArray("sources").strings())
            }.associateBy { it.id }
            val sourceArray = content.getJSONArray("sources")
            val sources = (0 until sourceArray.length()).map { i ->
                val s = sourceArray.getJSONObject(i)
                LifeSource(s.getString("id"), s.getString("citation"), s.getString("url"),
                    s.getString("license"), s.getString("license_url"))
            }
            val tree = LifeTree(archive.nodes, archive.root, groups.keys)
            val result = ParentesRepository(tree, archive.version, archive.taxonomy, archive.retrieved,
                groups, sources, archive.notes, archive.labels, context.applicationContext)
            // Prepare the first geometry and displayed resource IDs off the UI thread.
            result.overviewPositions
            archive.nodes.values.filter { it.labelKey.isNotEmpty() }.forEach { result.resource(it.labelKey) }
            groups.values.forEach { result.resource(it.key) }
            if (!callbacksRegistered) {
                context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
                    override fun onConfigurationChanged(newConfig: Configuration) = Unit
                    override fun onLowMemory() { cached = null }
                    override fun onTrimMemory(level: Int) {
                        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) cached = null
                    }
                })
                callbacksRegistered = true
            }
            cached = result
            Log.d("ParentesLoad", "Atlas + overview prepared in ${SystemClock.elapsedRealtime() - started} ms")
            return result
        }
    }
}
