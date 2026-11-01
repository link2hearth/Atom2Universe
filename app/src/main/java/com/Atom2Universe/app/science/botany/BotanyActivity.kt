package com.Atom2Universe.app.science.botany

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.GridGap
import com.Atom2Universe.app.science.PlantGroupKnowledge
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceModule
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceUi
import com.Atom2Universe.app.science.parentes.ParentesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * L'atlas botanique : une grille de tuiles qu'on filtre (recherche, collections, critères), des comparaisons,
 * les familles, des leçons, et le catalogue de référence de l'arbre du vivant. Les fiches partagent une seule
 * fenêtre plein écran, avec son historique ; l'image de la plante est en tête, le texte en chapitres.
 */
class BotanyActivity : ThemedActivity() {
    private enum class Tab(val title: Int) {
        EXPLORE(R.string.bot_explore), COMPARE(R.string.bot_compare), FAMILIES(R.string.bot_families), LEARN(R.string.bot_learn), LIFE(R.string.bot_life_catalogue)
    }
    private data class Facet(val key: String, val title: Int, val options: List<Pair<String, Int>>, val present: (PlantSpecies) -> Set<String>)
    private val palette by lazy { SciencePalette(this) }
    private val ui by lazy { ScienceUi(this, palette) }
    private val hue by lazy { ScienceModule.BOTANY.color(palette) }
    private val prefs by lazy { getSharedPreferences("botany", MODE_PRIVATE) }
    private val selected = linkedMapOf<String, MutableSet<String>>()
    private val expanded = mutableSetOf<String>()
    private var tab = Tab.EXPLORE
    private var search = ""
    private var sort = PlantSort.NAME
    private var filtersOpen = false
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var pane: FrameLayout
    /** La grille de l'explorateur recycle ses tuiles : les commandes sont la première ligne, les résultats suivent. */
    private lateinit var list: RecyclerView
    private lateinit var gridLayout: GridLayoutManager
    /** Les liens vers l'arbre de la fiche ouverte, remplis sur place quand l'arbre finit de se charger. */
    private var lifeSlot: Pair<String, () -> Unit>? = null
    private lateinit var tabs: ScienceUi.TabRow
    private var current: String? = null
    private val history = mutableListOf<String>()
    private val fiche by lazy { ScienceFiche(this, palette, R.string.bot_back, R.string.bot_close) { current = null; history.clear() } }
    private var life: BotanyLifeCatalogue? = null
    private var lifeLoading = false
    private var lifeFailed = false
    private var lifeSearch = ""
    private var lifeFamily = ""
    private var lifeBranch = ""
    private var lifeLatinSort = false
    private var lifeLimit = 40
    private var lifeOrder: Pair<Boolean, List<BotanyLifeIndex.Entry>>? = null
    private val catalog = BotanyCatalog
    private val monthIds = listOf(R.string.bot_month_1, R.string.bot_month_2, R.string.bot_month_3, R.string.bot_month_4,
        R.string.bot_month_5, R.string.bot_month_6, R.string.bot_month_7, R.string.bot_month_8, R.string.bot_month_9, R.string.bot_month_10, R.string.bot_month_11, R.string.bot_month_12)
    private val monthShort = listOf(R.string.bot_month_short_1, R.string.bot_month_short_2, R.string.bot_month_short_3, R.string.bot_month_short_4,
        R.string.bot_month_short_5, R.string.bot_month_short_6, R.string.bot_month_short_7, R.string.bot_month_short_8, R.string.bot_month_short_9, R.string.bot_month_short_10, R.string.bot_month_short_11, R.string.bot_month_short_12)
    private val facets by lazy {
        listOf(
            Facet("collection", R.string.bot_collections, catalog.collections.map { it.id to it.title }) { it.collectionIds },
            Facet("section", R.string.bot_explore, catalog.sections.map { it.id to it.title }) { it.sectionIds },
            Facet("family", R.string.bot_family, catalog.families.map { it.id to it.name }) { setOf(it.familyId) },
            Facet("leaf", R.string.bot_leaf_shape, LeafShape.entries.map { it.name to it.label }) { p -> p.leafShapes.map { it.name }.toSet() },
            Facet("margin", R.string.bot_leaf_margin, LeafMargin.entries.map { it.name to it.label }) { p -> p.leafMargins.map { it.name }.toSet() },
            Facet("arrangement", R.string.bot_leaf_arrangement, LeafArrangement.entries.map { it.name to it.label }) { p -> p.leafArrangements.map { it.name }.toSet() },
            Facet("color", R.string.bot_flower_color, FlowerColor.entries.map { it.name to it.label }) { p -> p.flowerColors.map { it.name }.toSet() },
            Facet("flower", R.string.bot_flower_shape, FlowerShape.entries.map { it.name to it.label }) { setOf(it.look.flower.name) },
            Facet("fruit", R.string.bot_fruit_shape, FruitShape.entries.map { it.name to it.label }) { setOf(it.look.fruit.name) },
            Facet("habitat", R.string.bot_habitat, Habitat.entries.map { it.name to it.label }) { p -> p.habitats.map { it.name }.toSet() },
            Facet("month", R.string.bot_month, monthIds.mapIndexed { i, r -> (i + 1).toString() to r }) { p -> p.flowering.map { it.toString() }.toSet() },
            Facet("foliage", R.string.bot_foliage, listOf("true" to R.string.bot_evergreen, "false" to R.string.bot_deciduous)) { if (it.variablePersistence) setOf("true", "false") else setOf(it.evergreen.toString()) }
        )
    }
    private val english by lazy { createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.ENGLISH) }).resources }
    private val french by lazy { createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.FRENCH) }).resources }
    private val searchIndex by lazy {
        catalog.species.associate { p ->
            val family = catalog.family(p.familyId)
            p.id to listOf(english, french).flatMap { r ->
                listOf(r.getString(p.name), r.getString(p.aliases), r.getString(p.latin), r.getString(family.name),
                    if (family.aliases == 0) "" else r.getString(family.aliases)) +
                    catalog.collections.filter { it.id in p.collectionIds }.map { r.getString(it.title) }
            }.joinToString(" ").let { PlantQuery.normalize(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "botany")) {
            finish()
            return
        }
        tab = Tab.entries.firstOrNull { it.name == (savedInstanceState?.getString("tab") ?: prefs.getString("tab", null)) } ?: Tab.EXPLORE
        search = savedInstanceState?.getString("search") ?: prefs.getString("search", "").orEmpty()
        sort = PlantSort.entries.firstOrNull { it.name == (savedInstanceState?.getString("sort") ?: prefs.getString("sort", null)) } ?: PlantSort.NAME
        filtersOpen = savedInstanceState?.getBoolean("filters") ?: prefs.getBoolean("filters", false)
        for (f in facets) {
            val ids = savedInstanceState?.getStringArrayList("facet_${f.key}")?.toSet() ?: prefs.getStringSet("facet_${f.key}", emptySet()).orEmpty()
            selected[f.key] = ids.filterTo(mutableSetOf()) { id -> f.options.any { it.first == id } && catalog.species.any { id in f.present(it) } }
        }
        expanded.addAll(savedInstanceState?.getStringArrayList("expanded") ?: prefs.getStringSet("expanded", emptySet()).orEmpty())
        lifeSearch = savedInstanceState?.getString("life_search").orEmpty()
        lifeFamily = savedInstanceState?.getString("life_family").orEmpty()
        lifeBranch = savedInstanceState?.getString("life_branch").orEmpty()
        lifeLatinSort = savedInstanceState?.getBoolean("life_latin_sort") ?: false
        lifeLimit = savedInstanceState?.getInt("life_limit") ?: 40
        buildUi(); render()
        loadLife()
        savedInstanceState?.getStringArrayList("history")?.let { history.addAll(it) }
        val sheet = if (savedInstanceState != null) savedInstanceState.getString("sheet")
            else intent.getStringExtra(EXTRA_LIFE_NODE)?.let { "life:$it" } ?: intent.getStringExtra(EXTRA_SPECIES_ID)?.let { "species:$it" }
        sheet?.let { key -> content.post { open(key, false) } }
        val y = savedInstanceState?.getInt("scroll") ?: 0
        scroll.post { scroll.scrollTo(0, y) }
        savedInstanceState?.let { BundleCompat.getParcelable(it, "list", Parcelable::class.java) }?.let { list.layoutManager?.onRestoreInstanceState(it) }
    }

    private fun buildUi() {
        val header = ui.header(getString(R.string.bot_title), ScienceModule.BOTANY, onBack = { finish() }, onHome = {
            fiche.dismiss(); tab = Tab.EXPLORE; tabs.select(tab.ordinal); render(); toTop()
        }, actions = listOf(ScienceUi.HeaderAction(R.drawable.ic_sci_help, R.string.bot_about) { open("about") }))
        val column = ui.column()
        tabs = ui.tabs(Tab.entries.map { getString(it.title) }, tab.ordinal) { index ->
            val next = Tab.entries[index]
            if (next != tab) { tab = next; tabs.select(index); render(); toTop() }
        }
        column.addView(tabs.view)
        content = ui.column().apply { setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(24)) }
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(content, FrameLayout.LayoutParams(-1, -2)) }
        val gap = ui.dp(10)
        gridLayout = GridLayoutManager(this, 2)
        list = RecyclerView(this).apply {
            layoutManager = gridLayout; itemAnimator = null
            setPadding(ui.dp(16) - gap / 2, ui.dp(4), ui.dp(16) - gap / 2, ui.dp(24)); clipToPadding = false
            addItemDecoration(GridGap(gap))
            recycledViewPool.setMaxRecycledViews(ROW_SPECIES, 24)
            addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) {
                    val columns = max(2, (right - left - v.paddingLeft - v.paddingRight) / ui.dp(168))
                    if (columns != gridLayout.spanCount) { gridLayout.spanCount = columns; adapter?.notifyDataSetChanged() }
                }
            }
        }
        pane = FrameLayout(this)
        pane.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        pane.addView(list, FrameLayout.LayoutParams(-1, -1))
        column.addView(pane, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(ui.screen(header, column, maxWidthDp = 1100))
    }

    private fun toTop() { scroll.scrollTo(0, 0); list.scrollToPosition(0) }

    /** Largeur du contenu qui défile, pour ranger des tuiles en colonnes. */
    private fun contentWidth() = (if (pane.width > 0) pane.width else min(resources.displayMetrics.widthPixels, ui.dp(1100))) - ui.dp(32)

    private fun render() {
        content.removeAllViews()
        val listed = tab == Tab.EXPLORE
        scroll.visibility = if (listed) View.GONE else View.VISIBLE
        list.visibility = if (listed) View.VISIBLE else View.GONE
        if (!listed) list.adapter = null
        when (tab) {
            Tab.EXPLORE -> explore()
            Tab.LIFE -> lifeBrowser()
            Tab.COMPARE -> {
                content.addView(ui.note(getString(R.string.bot_compare_note)), ui.full())
                catalog.groups.forEach { g -> content.addView(groupCard(g), ui.full()) }
            }
            Tab.FAMILIES -> {
                content.addView(ui.note(getString(R.string.bot_port_note)), ui.full())
                val tiles = catalog.families.sortedBy { getString(it.name) }.mapNotNull { f ->
                    val members = catalog.species.filter { it.familyId == f.id }
                    if (members.isEmpty()) null else plantTile(members.first(), PlantView.LEAF, getString(f.name), getString(f.description)) { open("family:${f.id}") }
                }
                content.addView(ui.grid(tiles, ui.columns(contentWidth())), ui.full(10))
            }
            Tab.LEARN -> {
                content.addView(ui.note(getString(R.string.bot_learn_intro)), ui.full())
                val tiles = BotanyLessons.all.mapNotNull { lesson ->
                    catalog.get(lesson.specimen)?.let { p -> plantTile(p, lesson.view, getString(lesson.title), getString(p.name)) { open("lesson:${lesson.id}") } }
                }
                content.addView(ui.grid(tiles, ui.columns(contentWidth())), ui.full(10))
            }
        }
    }

    /** Une tuile de plante : son dessin (ou sa planche d'herbier) en haut, puis [title] et [subtitle]. */
    private fun plantTile(p: PlantSpecies, view: PlantView, title: String, subtitle: String?, onClick: () -> Unit): View {
        val tile = ui.tile(1f, onClick)
        tile.image.addView(PlantPlateView(this, p, view, true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, FrameLayout.LayoutParams(-1, -1))
        tile.title.text = title
        if (subtitle == null) tile.subtitle.visibility = View.GONE else tile.subtitle.text = subtitle
        tile.view.contentDescription = title
        return tile.view
    }

    private fun explore() {
        val header = ui.column()
        val rowsAdapter = ExploreAdapter(header)
        val controls = mutableListOf<Pair<TextView, Pair<String, String>>>()
        val toggle = ui.action(getString(R.string.bot_filters)) {}
        val filterPanel = ui.column().apply { visibility = if (filtersOpen) View.VISIBLE else View.GONE }
        var pendingRefresh: Runnable? = null
        fun refresh() {
            pendingRefresh?.let { list.removeCallbacks(it) }; pendingRefresh = null
            controls.forEach { (chip, key) -> ui.styleChip(chip, key.second in selected[key.first].orEmpty()) }
            val count = selected.values.sumOf { it.size }
            toggle.text = if (count == 0) getString(R.string.bot_filters) else getString(R.string.bot_filters_count, count)
            val q = query()
            val found = PlantQuery.sorted(catalog.species.filter { q.matches(it, searchIndex[it.id].orEmpty(), normalized = true) }, sort, resources.configuration.locales[0],
                { getString(it.name) }, { getString(it.latin) }, { getString(catalog.family(it.familyId).name) })
            val rows = ArrayList<ExploreRow>()
            rows += ExploreRow.Note(resources.getQuantityString(R.plurals.bot_species_count, found.size, found.size), 13f, true, 8, live = true)
            catalog.collections.filter { it.id in q.collections }.forEach { rows += ExploreRow.Note(getString(it.description), 13f, true, 4) }
            if (q.months.isNotEmpty()) rows += ExploreRow.Note(getString(R.string.bot_month_scope), 12f, true, 4)
            if (found.isEmpty()) rows += ExploreRow.Note(getString(R.string.bot_empty), 15f, false, 8)
            found.mapTo(rows) { ExploreRow.Species(it) }
            rowsAdapter.submit(rows)
        }
        val searchBox = ui.searchField(getString(R.string.bot_search), search) { text ->
            search = text; pendingRefresh?.let { list.removeCallbacks(it) }
            pendingRefresh = Runnable { if (list.adapter === rowsAdapter) refresh() }.also { list.postDelayed(it, 140) }
        }
        header.addView(searchBox.view, ui.full(4))
        header.addView(ui.text(getString(R.string.bot_collections), 13f, bold = true), ui.full(12))
        header.addView(ui.flow(catalog.collections.map { collection ->
            ui.chip(getString(collection.title), collection.id in selected["collection"].orEmpty()) { flip("collection", collection.id); refresh() }
                .also { controls += it to ("collection" to collection.id) }
        }), ui.full(6))
        header.addView(ui.note(getString(R.string.bot_collection_help)), ui.full(4))
        header.addView(ui.flow(catalog.sections.map { section ->
            ui.chip(getString(section.title), section.id in selected["section"].orEmpty()) { flip("section", section.id); refresh() }
                .also { controls += it to ("section" to section.id) }
        }), ui.full(10))
        header.addView(ui.note(getString(R.string.bot_port_note)), ui.full(4))
        val options = ui.line()
        options.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        options.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        options.addView(ui.action(getString(R.string.bot_reset)) {
            selected.values.forEach { it.clear() }; search = ""; searchBox.edit.setText(""); refresh()
        }, LinearLayout.LayoutParams(-2, -2))
        header.addView(options, ui.full(10))
        var filtersBuilt = false
        fun buildFilters() {
            if (filtersBuilt) return
            filtersBuilt = true
            filterPanel.addView(ui.note(getString(R.string.bot_filter_help)), ui.full())
            for (f in facets.filter { it.key != "section" && it.key != "collection" }) {
                val offered = f.options.filter { (id, _) -> catalog.species.any { id in f.present(it) } }
                if (offered.isEmpty()) continue
                val flow = ui.flow(emptyList()).apply { visibility = if (f.key in expanded) View.VISIBLE else View.GONE }
                fun populate() {
                    if (flow.childCount > 0) return
                    for ((id, res) in offered) {
                        val chip = ui.chip(getString(res), id in selected[f.key].orEmpty()) { flip(f.key, id); refresh() }
                        controls += chip to (f.key to id); flow.addView(chip, ViewGroup.LayoutParams(-2, -2))
                    }
                }
                val head = ui.action(getString(f.title), if (f.key in expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more) {}
                head.setOnClickListener {
                    if (!expanded.remove(f.key)) expanded.add(f.key)
                    if (f.key in expanded) populate()
                    flow.visibility = if (f.key in expanded) View.VISIBLE else View.GONE
                    head.setCompoundDrawablesRelativeWithIntrinsicBounds(if (f.key in expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0, 0, 0)
                }
                filterPanel.addView(head, ui.wrap(8))
                if (f.key in expanded) populate()
                filterPanel.addView(flow, ui.full(4))
            }
        }
        toggle.setOnClickListener {
            filtersOpen = !filtersOpen
            if (filtersOpen) buildFilters()
            filterPanel.visibility = if (filtersOpen) View.VISIBLE else View.GONE
        }
        if (filtersOpen) buildFilters()
        header.addView(filterPanel, ui.full(0))
        val sortRow = ui.line()
        sortRow.addView(ui.text(getString(R.string.bot_sort), 13f), LinearLayout.LayoutParams(-2, -2))
        val sortTitles = listOf(R.string.bot_sort_name, R.string.bot_sort_latin, R.string.bot_sort_family, R.string.bot_sort_height, R.string.bot_sort_flowering)
        sortRow.addView(Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(this@BotanyActivity, android.R.layout.simple_spinner_item, sortTitles.map { getString(it) }) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getView(position, convertView, parent) as TextView).apply { setTextColor(palette.text); textSize = 14f }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getDropDownView(position, convertView, parent) as TextView).apply { setTextColor(palette.text); setBackgroundColor(palette.raised); setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14)) }
            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            contentDescription = getString(R.string.bot_sort); setSelection(sort.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { val next = PlantSort.entries[position]; if (next != sort) { sort = next; refresh() } }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, LinearLayout.LayoutParams(-2, ui.dp(44)).apply { marginStart = ui.dp(8) })
        header.addView(sortRow, ui.full(6))
        gridLayout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (rowsAdapter.getItemViewType(position) == ROW_SPECIES) 1 else gridLayout.spanCount
        }
        list.adapter = rowsAdapter
        refresh()
    }

    private fun flip(key: String, id: String) { val set = selected.getOrPut(key) { mutableSetOf() }; if (!set.remove(id)) set.add(id) }
    private inline fun <reified E : Enum<E>> enums(key: String) = enumValues<E>().filterTo(mutableSetOf()) { it.name in selected[key].orEmpty() }
    private fun query() = PlantQuery(search, selected["section"].orEmpty(), selected["family"].orEmpty(),
        enums("leaf"), enums("margin"), enums("arrangement"), enums("color"), enums("flower"), enums("fruit"), enums("habitat"),
        selected["month"].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(), selected["foliage"]?.singleOrNull()?.toBooleanStrictOrNull(),
        collections = selected["collection"].orEmpty())

    private sealed class ExploreRow {
        class Note(val text: String, val size: Float, val secondary: Boolean, val top: Int, val live: Boolean = false) : ExploreRow()
        class Species(val plant: PlantSpecies) : ExploreRow()
    }

    /** La même tuile que partout, rebranchée sur une autre espèce quand elle revient à l'écran. */
    private inner class SpeciesHolder(val tile: ScienceUi.Tile) : RecyclerView.ViewHolder(tile.view) {
        fun bind(p: PlantSpecies) {
            tile.image.removeAllViews()
            tile.image.addView(PlantPlateView(this@BotanyActivity, p, PlantView.HABIT, true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, FrameLayout.LayoutParams(-1, -1))
            tile.title.text = getString(p.name); tile.subtitle.text = getString(p.latin)
            tile.view.contentDescription = getString(p.name)
            tile.view.setOnClickListener { open("species:${p.id}") }
        }
    }

    private inner class ExploreAdapter(private val header: LinearLayout) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<ExploreRow> = emptyList()
        /** Seules les lignes de résultat changent : la ligne des commandes garde ses vues, le focus et le clavier. */
        fun submit(next: List<ExploreRow>) {
            val old = rows.size; rows = next
            if (old > 0) notifyItemRangeRemoved(1, old)
            if (next.isNotEmpty()) notifyItemRangeInserted(1, next.size)
        }
        override fun getItemCount() = 1 + rows.size
        override fun getItemViewType(position: Int) = if (position == 0) ROW_HEADER else if (rows[position - 1] is ExploreRow.Species) ROW_SPECIES else ROW_NOTE
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            ROW_HEADER -> {
                (header.parent as? ViewGroup)?.removeView(header)
                header.layoutParams = RecyclerView.LayoutParams(-1, -2)
                // Une seule ligne à état, jamais recyclée : les mêmes commandes reviennent quand on y revient.
                object : RecyclerView.ViewHolder(header) {}.apply { setIsRecyclable(false) }
            }
            ROW_NOTE -> object : RecyclerView.ViewHolder(ui.text("", 13f)) {}
            else -> {
                val tile = ui.tile(1f) {}
                tile.view.isClickable = true; tile.view.isFocusable = true
                tile.view.layoutParams = RecyclerView.LayoutParams(-1, -2)
                SpeciesHolder(tile)
            }
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = if (position == 0) null else rows[position - 1]) {
                is ExploreRow.Note -> (holder.itemView as TextView).apply {
                    text = row.text; textSize = row.size; setTextColor(if (row.secondary) palette.secondary else palette.text)
                    accessibilityLiveRegion = if (row.live) View.ACCESSIBILITY_LIVE_REGION_POLITE else View.ACCESSIBILITY_LIVE_REGION_NONE
                    layoutParams = RecyclerView.LayoutParams(-1, -2).apply { topMargin = ui.dp(row.top) }
                }
                is ExploreRow.Species -> (holder as SpeciesHolder).bind(row.plant)
                null -> Unit
            }
        }
    }

    private fun groupCard(g: PlantGroup): View {
        val box = ui.card { open("group:${g.id}") }
        box.addView(ui.text(getString(g.title), 17f, bold = true))
        val plates = ui.line().apply { gravity = Gravity.TOP }
        g.members.take(4).forEach { id ->
            catalog.get(id)?.let { p ->
                val col = ui.column()
                col.addView(PlantPlateView(this, p, PlantView.LEAF, true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(-1, ui.dp(112)))
                col.addView(ui.text(getString(p.name), 12f).apply { gravity = Gravity.CENTER }, ui.full(3))
                plates.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(4) })
            }
        }
        box.addView(plates, ui.full()); return box
    }

    // ------------------------------------------------------------------ les fiches

    private fun open(key: String, push: Boolean = true) {
        val previous = current
        if (push && previous != null && previous != key) history.add(previous)
        current = key
        val back: (() -> Unit)? = if (history.isEmpty()) null else ({ open(history.removeAt(history.lastIndex), false) })
        when {
            key == "about" -> showAbout(back)
            key.startsWith("life:") -> showLife(key.substringAfter(':'), back)
            key.startsWith("species:") -> catalog.get(key.substringAfter(':'))?.let { showSpecies(it, back) }
            key.startsWith("group:") -> catalog.groups.firstOrNull { it.id == key.substringAfter(':') }?.let { showGroup(it, back) }
            key.startsWith("family:") -> catalog.families.firstOrNull { it.id == key.substringAfter(':') }?.let { showFamily(it, back) }
            key.startsWith("taxon:") -> showTaxon(key.substringAfter(':'), back)
            key.startsWith("lesson:") -> BotanyLessons.all.firstOrNull { it.id == key.substringAfter(':') }?.let { lesson ->
                val p = catalog.get(lesson.specimen) ?: return
                val body = ui.column()
                body.addView(ui.chapter(getString(p.name), hue, ui.paragraph(getString(lesson.body))), ui.full(4))
                body.addView(ui.section(getString(R.string.science_see_also)), ui.full(22))
                body.addView(tileStrip(listOf(p)), ui.full(8))
                fiche.show(getString(lesson.title), null, media(p), body, back)
            }
        }
    }

    private fun persistence(p: PlantSpecies) = if (p.variablePersistence) R.string.bot_persistence_variable
        else if (p.evergreen) R.string.bot_evergreen else R.string.bot_deciduous

    private fun showSpecies(p: PlantSpecies, back: (() -> Unit)?) {
        val article = runCatching {
            BotanyEncyclopediaRepository.load(this, p.id, resources.configuration.locales[0].language)
        }.getOrNull()
        // En tête : le nom latin, les autres noms, la famille, puis les liens vers l'arbre (quand il est prêt).
        val top = ui.column()
        top.addView(ui.identity(getString(p.latin), listOf(ui.chip(getString(catalog.family(p.familyId).name), false) { open("family:${p.familyId}") })))
        getString(p.aliases).takeIf { it.isNotBlank() }?.let { top.addView(ui.note(it), ui.full(4)) }
        val treeSlot = ui.column()
        top.addView(treeSlot)
        whenLifeLoaded { data ->
            data.index.forIllustrated(p.id)?.let { entry ->
                val links = mutableListOf<View>(ui.moduleChip(ScienceModule.TREE, { openTree(entry.nodeId) }))
                entry.lineage.reversed().filter { PlantGroupKnowledge.hasArticle(data.repository.tree.nodes.getValue(it).scientific) }.forEach { ancestor ->
                    links += ui.chip(data.repository.name(this, ancestor), false) { open("taxon:$ancestor") }
                }
                treeSlot.addView(ui.chipRow(links), ui.full(12))
            }
        }

        val body = ui.column()
        if (article != null) {
            article.sections.forEach { section -> body.addView(ui.chapter(section.title, hue, ui.paragraph(section.text)), ui.full(12)) }
        } else {
            body.addView(ui.chapter(getString(R.string.bot_traits), hue, ui.paragraph(getString(p.description))), ui.full(12))
            val ecology = ui.column()
            ecology.addView(ui.text(p.habitats.joinToString(" · ") { getString(it.label) }))
            ecology.addView(ui.text(getString(p.distribution), 14f), ui.full(4))
            body.addView(ui.chapter(getString(R.string.bot_ecology), hue, ecology), ui.full(12))
        }
        val size = ui.column()
        size.addView(ui.text(height(p), 16f))
        size.addView(ui.text(getString(persistence(p)), 14f), ui.full(4))
        body.addView(ui.chapter(getString(R.string.bot_height), hue, size), ui.full(12))
        if (p.flowering.isNotEmpty() || p.fruiting.isNotEmpty()) {
            val agenda = ui.column()
            agenda.addView(calendar(p))
            agenda.addView(ui.note(getString(p.phenologyNote.takeIf { it != 0 } ?: R.string.bot_calendar_note)), ui.full(8))
            body.addView(ui.chapter(getString(R.string.bot_calendar), hue, agenda), ui.full(12))
        }
        body.addView(ui.note(catalog.collections.filter { it.id in p.collectionIds }.joinToString(" · ") { getString(it.title) }), ui.full(10))
        val relatedIds = article?.related.orEmpty().filter { catalog.get(it) != null }
        if (relatedIds.isNotEmpty()) {
            body.addView(ui.section(getString(R.string.science_see_also)), ui.full(22))
            body.addView(tileStrip(relatedIds.mapNotNull { catalog.get(it) }), ui.full(8))
        }
        val groups = catalog.groups.filter { p.id in it.members }
        if (groups.isNotEmpty()) {
            body.addView(ui.section(getString(R.string.bot_related)), ui.full(22))
            body.addView(ui.flow(groups.map { g -> ui.action(getString(g.title)) { open("group:${g.id}") } }), ui.full(8))
        }
        val references = ui.column()
        if (article != null) article.sources.forEach { source -> references.addView(ui.link(source.title, source.url), ui.full(2)) }
        else speciesReferences(references, p)
        body.addView(ui.chapter(getString(R.string.bot_references), palette.secondary, references), ui.full(16))
        fiche.show(getString(p.name), top, media(p), body, back)
    }

    private fun speciesImage(p: PlantSpecies): ImageView {
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.bot_herbarium_accessibility, getString(p.name))
        }
        // Sans planche vérifiée, le dessin du port tiré des caractères remplace le cadre vide.
        val herbarium = PlantHerbarium.hasPlate(p.id)
        if (!herbarium) image.contentDescription = getString(R.string.bot_plate_accessibility, getString(p.name), getString(PlantView.HABIT.label), getString(p.captions.getValue(PlantView.HABIT)))
        lifecycleScope.launch {
            val key = PlantImageKey(p.id, PlantView.HABIT, 768, false, herbarium)
            runCatching { PlantImageStore.shared.load(p, key) }.onSuccess { bitmap -> image.setImageBitmap(bitmap) }
        }
        return image
    }

    /** L'image de la plante en tête de fiche : à gauche sur un écran large, sinon avant les chapitres. */
    private fun media(p: PlantSpecies): View = ui.column().apply {
        val hero = ui.hero(speciesImage(p), 1f).apply {
            background = GradientDrawable().apply { setColor(PAPER); cornerRadius = com.Atom2Universe.app.AppearanceStyle.corner(this@BotanyActivity, 18f) }
        }
        addView(hero, if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else ui.full())
        addView(ui.note(getString(R.string.bot_herbarium_note)), ui.full(6))
    }

    /** D'autres plantes en tuiles qu'on fait défiler de côté ; chacune ouvre sa fiche. */
    private fun tileStrip(species: List<PlantSpecies>): View {
        val row = ui.line().apply { gravity = Gravity.TOP }
        species.forEach { p ->
            row.addView(plantTile(p, PlantView.HABIT, getString(p.name), getString(p.latin)) { open("species:${p.id}") },
                LinearLayout.LayoutParams(ui.dp(150), -2).apply { marginEnd = ui.dp(10) })
        }
        return ui.scrollRow(row)
    }

    private fun showGroup(g: PlantGroup, back: (() -> Unit)?) {
        val body = ui.column()
        body.addView(ui.note(getString(R.string.bot_compare_note)), ui.full(4))
        val r = ui.line().apply { gravity = Gravity.TOP }
        val cellWidth = ui.dp(225)
        g.members.mapNotNull { catalog.get(it) }.forEach { p ->
            val col = ui.column().apply { isClickable = true; isFocusable = true; setOnClickListener { open("species:${p.id}") } }
            col.addView(ui.hero(speciesImage(p), 1.1f, 225).apply {
                background = GradientDrawable().apply { setColor(PAPER); cornerRadius = com.Atom2Universe.app.AppearanceStyle.corner(this@BotanyActivity, 14f) }
            }, LinearLayout.LayoutParams(cellWidth, -2))
            col.addView(ui.text(getString(p.name), 15f, bold = true), ui.full(6))
            col.addView(ui.latin(getString(p.latin), 12.5f), ui.full(2))
            r.addView(col, LinearLayout.LayoutParams(cellWidth, -2).apply { marginEnd = ui.dp(10) })
        }
        body.addView(ui.scrollRow(r), ui.full())
        body.addView(ui.chapter(getString(R.string.bot_compare), hue, ui.paragraph(getString(g.differences))), ui.full(16))
        body.addView(ui.note(getString(R.string.bot_herbarium_note)), ui.full(8))
        fiche.show(getString(g.title), null, null, body, back)
    }

    private fun showFamily(f: PlantFamily, back: (() -> Unit)?) {
        val body = ui.column()
        PlantGroupKnowledge.append(this, body, palette, f.id)
        val treeSlot = ui.column()
        body.addView(treeSlot, ui.full(0))
        whenLifeLoaded { data ->
            data.repository.tree.nodes.values.firstOrNull { it.scientific == f.id }?.let { node ->
                treeSlot.addView(ui.chipRow(listOf(
                    ui.moduleChip(ScienceModule.TREE, { openTree(node.id) }),
                    ui.chip(getString(R.string.plant_group_group_members), false) { open("taxon:${node.id}") }
                )), ui.full(12))
            }
        }
        val members = catalog.species.filter { it.familyId == f.id }
        body.addView(ui.section(resources.getQuantityString(R.plurals.bot_species_count, members.size, members.size)), ui.full(22))
        val columns = ui.columns(min(resources.displayMetrics.widthPixels, ui.dp(680)) - ui.dp(40))
        members.groupBy { getString(it.latin).substringBefore(' ') }.toSortedMap().forEach { (genus, plants) ->
            body.addView(ui.text(getString(R.string.bot_genus, genus), 15f, bold = true), ui.full(14))
            body.addView(ui.grid(plants.map { p -> plantTile(p, PlantView.HABIT, getString(p.name), getString(p.latin)) { open("species:${p.id}") } }, columns), ui.full(8))
        }
        fiche.show(getString(f.name), null, null, body, back)
    }

    /** Remplit une partie de la fiche ouverte maintenant, ou sur place quand l'arbre est chargé, sans refaire la fiche. */
    private fun whenLifeLoaded(fill: (BotanyLifeCatalogue) -> Unit) {
        val data = life
        if (data != null) fill(data) else lifeSlot = current?.let { key -> key to { life?.let(fill); Unit } }
    }

    private fun calendar(p: PlantSpecies): View {
        val box = ui.column()
        for ((title, months) in listOf(R.string.bot_bloom to p.flowering, R.string.bot_fruiting to p.fruiting)) {
            box.addView(ui.text(getString(title), 13f, bold = true), ui.full(6))
            // Deux rangées de six, pour que les noms des mois restent lisibles sur un téléphone avec de grandes polices.
            repeat(2) { half ->
                val r = ui.line()
                repeat(6) { j ->
                    val m = half * 6 + j; val active = m + 1 in months
                    r.addView(ui.text(getString(monthShort[m]), 12f, bold = active).apply {
                        gravity = Gravity.CENTER; setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(8))
                        background = if (active) palette.shape(palette.accent, 10f) else palette.shape(palette.raised, 10f)
                        setTextColor(if (active) palette.onAccent else palette.secondary)
                        contentDescription = getString(R.string.bot_calendar_cell, getString(title), getString(monthIds[m]), getString(if (active) R.string.bot_present else R.string.bot_absent))
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(3) })
                }
                box.addView(r, ui.full(3))
            }
        }
        return box
    }

    private fun showAbout(back: (() -> Unit)?) {
        val body = ui.column()
        body.addView(ui.paragraph(getString(R.string.bot_about_text)), ui.full(4))
        body.addView(ui.paragraph(getString(R.string.bot_about_scope)), ui.full(10))
        body.addView(ui.paragraph(getString(R.string.science_navigation_help)), ui.full(10))
        val references = ui.column()
        catalog.sources.forEach { s -> references.addView(ui.link(getString(s.title), s.url), ui.full(2)) }
        body.addView(ui.chapter(getString(R.string.bot_references), palette.secondary, references), ui.full(16))
        body.addView(ui.section(getString(R.string.bot_sources_species)), ui.full(22))
        appendWhenReached(body, catalog.species.size) { i ->
            val p = catalog.species[i]
            val latin = getString(p.latin)
            body.addView(ui.text(getString(p.name), 15f, bold = true), ui.full(12))
            body.addView(ui.link(getString(R.string.bot_lookup_powo, latin), "https://powo.science.kew.org/results?q=" + android.net.Uri.encode(latin)), ui.full(2))
            speciesReferences(body, p)
        }
        fiche.show(getString(R.string.bot_about), null, null, body, back)
    }

    /**
     * Construit une longue liste par morceaux : le suivant s'ajoute quand la fin du précédent arrive à l'écran,
     * pour que la fiche s'ouvre sans créer des milliers de vues d'un coup. La page entièrement déroulée est la même.
     */
    private fun appendWhenReached(body: LinearLayout, count: Int, chunk: Int = 24, build: (Int) -> Unit) {
        var next = 0
        val end = object : View(this) {
            private val visible = android.graphics.Rect()
            private val listener = ViewTreeObserver.OnScrollChangedListener { grow() }
            fun grow() {
                if (next >= count || !isAttachedToWindow || !getLocalVisibleRect(visible)) return
                body.removeView(this)
                repeat(minOf(chunk, count - next)) { build(next++) }
                if (next < count) body.addView(this, LinearLayout.LayoutParams(-1, 1))
            }
            override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnScrollChangedListener(listener); post { grow() } }
            override fun onDetachedFromWindow() { if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(listener); super.onDetachedFromWindow() }
        }
        repeat(minOf(chunk, count)) { build(next++) }
        if (next < count) body.addView(end, LinearLayout.LayoutParams(-1, 1))
    }

    private fun speciesReferences(body: LinearLayout, p: PlantSpecies) {
        val pages = BotanyReferences.pages(p.id)
        if (pages.isEmpty()) catalog.sources.filter { it.id in p.sourceIds }.forEach { source ->
            body.addView(ui.link(getString(source.title), source.url), ui.full(2))
        }
        pages.forEach { url ->
            body.addView(ui.link(getString(R.string.bot_taxon_reference_domain, getString(p.latin), android.net.Uri.parse(url).host.orEmpty()), url), ui.full(2))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        val key = intent.getStringExtra(EXTRA_LIFE_NODE)?.let { "life:$it" }
            ?: intent.getStringExtra(EXTRA_SPECIES_ID)?.let { "species:$it" }
        key?.let { open(it) }
    }

    private fun loadLife() {
        if (lifeLoading || life != null) return
        lifeLoading = true; lifeFailed = false
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.Default) { runCatching { BotanyLifeCatalogue.load(applicationContext) } }
            lifeLoading = false
            loaded.onSuccess { life = it }.onFailure {
                lifeFailed = true; android.util.Log.e("BotanyLife", "Plant reference catalogue load failed", it)
            }
            if (tab == Tab.LIFE) render()
            val slot = lifeSlot; lifeSlot = null
            slot?.takeIf { it.first == current }?.second?.invoke()
            // Seules les pages qui attendaient l'arbre sont refaites.
            current?.takeIf { it.startsWith("life:") || it.startsWith("taxon:") }?.let { open(it, false) }
        }
    }

    private fun lifeBrowser() {
        content.addView(ui.note(getString(R.string.bot_life_intro)), ui.full())
        val data = life
        if (data == null) {
            content.addView(ui.text(getString(if (lifeFailed) R.string.bot_life_error else R.string.bot_life_loading)), ui.full())
            if (lifeFailed) content.addView(ui.action(getString(R.string.bot_life_retry)) { loadLife(); render() }, ui.wrap())
            return
        }
        val repo = data.repository
        val results = ui.column()
        // Trié une fois par ordre ; chaque recherche ne fait ensuite que filtrer cette liste.
        fun ordered(): List<BotanyLifeIndex.Entry> {
            lifeOrder?.takeIf { it.first == lifeLatinSort }?.let { return it.second }
            val collator = java.text.Collator.getInstance(resources.configuration.locales[0])
            val sorted = data.index.entries.map { it to collator.getCollationKey(if (lifeLatinSort) it.scientific else repo.name(this, it.nodeId)) }
                .sortedWith { a, b -> a.second.compareTo(b.second).takeIf { it != 0 } ?: a.first.nodeId.compareTo(b.first.nodeId) }.map { it.first }
            lifeOrder = lifeLatinSort to sorted
            return sorted
        }
        var pendingRefresh: Runnable? = null
        fun refresh() {
            pendingRefresh?.let { results.removeCallbacks(it) }; pendingRefresh = null
            results.removeAllViews()
            val words = PlantQuery.normalize(lifeSearch).split(WHITESPACE).filter { it.isNotEmpty() }
            val found = ordered().filter { entry ->
                (lifeFamily.isEmpty() || entry.family == lifeFamily) && (lifeBranch.isEmpty() || lifeBranch in entry.lineage) &&
                    words.all { it in data.searchTerms.getValue(entry.nodeId) }
            }
            results.addView(ui.note(getString(R.string.bot_life_count, found.size)), ui.full())
            for (entry in found.take(lifeLimit)) {
                val name = repo.name(this, entry.nodeId)
                val status = getString(if (entry.illustratedId != null) R.string.bot_life_illustrated else R.string.bot_life_reference)
                val line = ui.line().apply {
                    background = ui.ripple(null); isClickable = true; isFocusable = true
                    setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10))
                    setOnClickListener { open("life:${entry.nodeId}") }
                    contentDescription = getString(R.string.bot_life_row, name, entry.scientific, status)
                }
                val texts = ui.column()
                texts.addView(ui.text(name, 15f, bold = true))
                texts.addView(ui.latin(entry.scientific, 13f), ui.full(1))
                line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(ui.note(status))
                results.addView(line, ui.full(2))
                results.addView(ui.divider(), LinearLayout.LayoutParams(-1, 1))
            }
            if (found.size > lifeLimit) results.addView(ui.action(getString(R.string.bot_life_more)) { lifeLimit += 40; refresh() }, ui.wrap(10))
        }
        content.addView(ui.searchField(getString(R.string.bot_life_search), lifeSearch) { text ->
            lifeSearch = text; lifeLimit = 40; pendingRefresh?.let { results.removeCallbacks(it) }
            pendingRefresh = Runnable { if (results.isAttachedToWindow) refresh() }.also { results.postDelayed(it, 140) }
        }.view, ui.full())
        fun selector(title: Int, items: List<Pair<String, String>>, selected: () -> String, change: (String) -> Unit) {
            content.addView(ui.text(getString(title), 13f, bold = true), ui.full())
            content.addView(Spinner(this).apply {
                adapter = ArrayAdapter(this@BotanyActivity, android.R.layout.simple_spinner_dropdown_item, items.map { it.second })
                setSelection(items.indexOfFirst { it.first == selected() }.coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    // Le sélecteur signale aussi son choix initial : seul un vrai changement rafraîchit la liste.
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val next = items[position].first
                        if (next != selected()) { change(next); refresh() }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }, ui.full(2))
        }
        val all = "" to getString(R.string.bot_life_all)
        selector(R.string.bot_family, listOf(all) + data.index.entries.mapNotNull { it.family }.distinct().sorted().map { it to it }, { lifeFamily }) { lifeLimit = 40; lifeFamily = it }
        val branches = repo.groups.keys.filter { it != BotanyLifeIndex.GREEN_ROOT && data.index.isPlant(it) }
            .map { it to repo.name(this, it) }.sortedBy { it.second }
        selector(R.string.bot_life_branch, listOf(all) + branches, { lifeBranch }) { lifeLimit = 40; lifeBranch = it }
        content.addView(CheckBox(this).apply {
            text = getString(R.string.bot_life_sort_latin); setTextColor(palette.text); isChecked = lifeLatinSort
            setOnCheckedChangeListener { _, checked -> lifeLatinSort = checked; refresh() }
        }, ui.full())
        content.addView(results, ui.full()); refresh()
    }

    private fun showLife(id: String, back: (() -> Unit)?) {
        val data = life
        if (data == null) {
            val body = ui.column(); body.addView(ui.text(getString(if (lifeFailed) R.string.bot_life_error else R.string.bot_life_loading)), ui.full())
            if (lifeFailed) body.addView(ui.action(getString(R.string.bot_life_retry)) { loadLife(); showLife(id, back) }, ui.wrap())
            fiche.show(getString(R.string.bot_life_reference), null, null, body, back); return
        }
        val entry = data.index.forNode(id)
        if (entry == null) { Toast.makeText(this, R.string.bot_life_missing, Toast.LENGTH_LONG).show(); fiche.dismiss(); return }
        entry.illustratedId?.let { catalog.get(it) }?.let { showSpecies(it, back); return }
        val repo = data.repository
        val top = ui.column()
        top.addView(ui.identity(entry.scientific, links = listOf(ui.moduleChip(ScienceModule.TREE, { openTree(id) }))))
        val body = ui.column()
        body.addView(ui.note(getString(R.string.bot_life_reference_note)), ui.full(4))
        repo.notes[id]?.let { note -> body.addView(ui.chapter(getString(R.string.bot_life_notice), hue, ui.paragraph(repo.text(this, note.key))), ui.full(12)) }
        val lineage = ui.column()
        lineage.addView(ui.flow(entry.lineage.reversed().filter { repo.tree.nodes.getValue(it).scientific.isNotBlank() || it in repo.groups }.map { ancestor ->
            ui.chip(repo.name(this, ancestor), false) {
                if (PlantGroupKnowledge.hasArticle(repo.tree.nodes.getValue(ancestor).scientific)) open("taxon:$ancestor") else openTree(ancestor)
            }
        }))
        entry.family?.let { family -> lineage.addView(ui.text(getString(R.string.bot_life_family_value, family), 14f), ui.full(10)) }
        body.addView(ui.chapter(getString(R.string.bot_life_classification), hue, lineage), ui.full(12))
        entry.lineage.firstOrNull { it in repo.groups }?.let { group ->
            body.addView(ui.chapter(getString(R.string.bot_life_group_traits), hue, ui.paragraph(repo.text(this, repo.groups.getValue(group).key + "_body"))), ui.full(12))
        }
        val sources = ui.column()
        sources.addView(ui.note(getString(R.string.bot_life_provenance, repo.version, repo.taxonomy, repo.retrieved)))
        sources.addView(ui.link(getString(R.string.bot_life_source_tree), "https://tree.opentreeoflife.org/opentree/${repo.version}@$id"), ui.full(6))
        val qid = repo.notes[id]?.qid ?: repo.labels[id]?.qid
        if (qid?.matches(Regex("Q[0-9]+")) == true) sources.addView(ui.link(getString(R.string.bot_life_source_notice), "https://www.wikidata.org/wiki/$qid"), ui.full(2))
        sources.addView(ui.link(getString(R.string.bot_life_source_kew), "https://powo.science.kew.org/results?q=${android.net.Uri.encode(entry.scientific)}"), ui.full(2))
        body.addView(ui.chapter(getString(R.string.bot_sources_species), palette.secondary, sources), ui.full(16))
        fiche.show(repo.name(this, id), top, null, body, back)
    }

    private fun openTree(id: String) {
        startActivity(Intent(this, ParentesActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
            .putExtra(ParentesActivity.EXTRA_NODE_ID, id))
    }

    private fun showTaxon(id: String, back: (() -> Unit)?) {
        val repo = life?.repository
        if (repo == null) {
            val body = ui.column(); body.addView(ui.text(getString(if (lifeFailed) R.string.bot_life_error else R.string.bot_life_loading)), ui.full())
            if (lifeFailed) body.addView(ui.action(getString(R.string.bot_life_retry)) { loadLife(); showTaxon(id, back) }, ui.wrap())
            fiche.show(getString(R.string.bot_life_reference), null, null, body, back); return
        }
        val node = repo.tree.nodes[id] ?: return
        val top = ui.column()
        top.addView(ui.identity(node.scientific.takeIf { it.isNotBlank() }, links = listOf(ui.moduleChip(ScienceModule.TREE, { openTree(id) }))))
        val body = ui.column()
        PlantGroupKnowledge.append(this, body, palette, node.scientific)
        val ancestors = repo.tree.ancestors(id).drop(1).filter { PlantGroupKnowledge.hasArticle(repo.tree.nodes.getValue(it).scientific) }.reversed()
        if (ancestors.isNotEmpty()) {
            body.addView(ui.chapter(getString(R.string.bot_life_classification), hue,
                ui.flow(ancestors.map { ancestor -> ui.chip(repo.name(this, ancestor), false) { open("taxon:$ancestor") } })), ui.full(12))
        }
        // On suit les arêtes de l'archive : jamais de classement reconstruit d'après les étiquettes d'ordre.
        val children = repo.tree.children[id].orEmpty()
        if (children.isNotEmpty()) {
            body.addView(ui.chapter(getString(R.string.plant_group_group_members), hue, ui.flow(children.map { child ->
                ui.chip(repo.name(this, child), false) {
                    if (PlantGroupKnowledge.hasArticle(repo.tree.nodes.getValue(child).scientific)) open("taxon:$child") else openTree(child)
                }
            })), ui.full(12))
        }
        fiche.show(repo.name(this, id), top, null, body, back)
    }

    companion object {
        const val EXTRA_LIFE_NODE = "botany_life_node"
        const val EXTRA_SPECIES_ID = "botany_species_id"
        private const val ROW_HEADER = 0
        private const val ROW_NOTE = 1
        private const val ROW_SPECIES = 2
        /** Le papier des planches d'herbier. */
        private const val PAPER = 0xFFF4F0E3.toInt()
        private val WHITESPACE = Regex("\\s+")
    }

    private fun height(p: PlantSpecies): String {
        if (p.heightMaxCm < 200) return getString(R.string.bot_height_cm, p.heightMinCm, p.heightMaxCm)
        val n = NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply { maximumFractionDigits = 1 }
        return getString(R.string.bot_height_m, n.format(p.heightMinCm / 100f), n.format(p.heightMaxCm / 100f))
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("life_search", lifeSearch); out.putString("life_family", lifeFamily); out.putString("life_branch", lifeBranch)
        out.putBoolean("life_latin_sort", lifeLatinSort); out.putInt("life_limit", lifeLimit)
        out.putString("tab", tab.name); out.putString("search", search); out.putString("sort", sort.name)
        out.putBoolean("filters", filtersOpen); out.putString("sheet", current)
        out.putStringArrayList("history", ArrayList(history)); out.putStringArrayList("expanded", ArrayList(expanded)); out.putInt("scroll", scroll.scrollY)
        if (tab == Tab.EXPLORE) out.putParcelable("list", list.layoutManager?.onSaveInstanceState())
        selected.forEach { (key, value) -> out.putStringArrayList("facet_$key", ArrayList(value)) }
    }
    override fun onPause() {
        super.onPause()
        prefs.edit().apply {
            putString("tab", tab.name); putString("search", search); putString("sort", sort.name)
            putBoolean("filters", filtersOpen); putStringSet("expanded", expanded.toSet())
            selected.forEach { (key, value) -> putStringSet("facet_$key", value.toSet()) }
        }.apply()
    }
    override fun onDestroy() { fiche.dismiss(); super.onDestroy() }
}
