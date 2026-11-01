package com.Atom2Universe.app.science.bestiary

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.GridGap
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceModule
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceUi
import com.Atom2Universe.app.science.parentes.LifeTree
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.parentes.ParentesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

/**
 * Le bestiaire : une grille de tuiles illustrées qu'on filtre par groupe ou par nom, et la fiche de chaque animal
 * en chapitres, avec le lien vers l'arbre du vivant en tête et d'autres animaux du même groupe en bas. Les fiches
 * s'enchaînent dans la même fenêtre, la flèche revient à la précédente.
 */
class BestiaryActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private val ui by lazy { ScienceUi(this, palette) }
    private val hue by lazy { ScienceModule.BESTIARY.color(palette) }
    private var repository: ParentesRepository? = null
    private var index: BestiaryLifeIndex? = null
    private var entries: List<BestiaryEntry>? = null
    private var selected: String? = null
    private val history = ArrayList<String>()
    private var pendingNode: String? = null
    private var query = ""
    private var group = ""
    private var keepScroll = true
    private var opening: Job? = null
    // La place des liens de la fiche ouverte, remplie quand l'arbre finit de se charger.
    private var pendingClassification: Pair<String, LinearLayout>? = null
    private lateinit var grid: RecyclerView
    private lateinit var layout: GridLayoutManager
    private lateinit var count: TextView
    private lateinit var treeStatus: LinearLayout
    private val groupChips = ArrayList<Pair<String, TextView>>()
    private val adapter = TileAdapter()
    private val searchHandler = Handler(Looper.getMainLooper())
    private val applySearch = Runnable { renderList() }
    private val fiche by lazy {
        ScienceFiche(this, palette, R.string.be_back, R.string.be_close) { selected = null; history.clear(); pendingClassification = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "bestiary")) {
            finish()
            return
        }
        query = savedInstanceState?.getString("query").orEmpty()
        group = savedInstanceState?.getString("group").orEmpty()
        selected = if (savedInstanceState != null) savedInstanceState.getString("species") else intent.getStringExtra(EXTRA_SPECIES_ID)
        savedInstanceState?.getStringArrayList("history")?.let { history.addAll(it) }
        pendingNode = if (savedInstanceState != null) savedInstanceState.getString("node") else intent.getStringExtra(EXTRA_LIFE_NODE)
        buildUi()
        savedInstanceState?.let { androidx.core.os.BundleCompat.getParcelable(it, "grid", android.os.Parcelable::class.java) }
            ?.let { layout.onRestoreInstanceState(it) }
        loadEntries()
        loadTree()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fiche.isShowing) fiche.dismiss() else finish() }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNode = intent.getStringExtra(EXTRA_LIFE_NODE)
        intent.getStringExtra(EXTRA_SPECIES_ID)?.let { id ->
            if (entries == null) selected = id else AnimalCatalog.byId[id]?.let { history.clear(); open(it, push = false) }
        }
        applyLink()
    }

    // ------------------------------------------------------------------ l'écran

    private fun buildUi() {
        val header = ui.header(getString(R.string.be_title), ScienceModule.BESTIARY, onBack = { finish() }, onHome = {
            fiche.dismiss(); query = ""; group = ""; pendingNode = null; keepScroll = false
            intent.removeExtra(EXTRA_SPECIES_ID); intent.removeExtra(EXTRA_LIFE_NODE)
            recreate()
        }, actions = listOf(ScienceUi.HeaderAction(R.drawable.ic_sci_help, R.string.science_help) { help() }))

        val content = ui.column()
        val controls = ui.column().apply { setPadding(ui.dp(16), ui.dp(6), ui.dp(16), 0) }
        controls.addView(ui.searchField(getString(R.string.be_search), query) { text ->
            query = text
            searchHandler.removeCallbacks(applySearch); searchHandler.postDelayed(applySearch, SEARCH_DELAY_MS)
        }.view, ui.full(0))
        groupChips.clear()
        (listOf("" to R.string.be_all) + GROUPS.map { it to groupTitle(it) }).forEach { (id, title) ->
            groupChips += id to ui.chip(getString(title), group == id) {
                if (group != id) { group = id; restyleGroups(); renderList() }
            }
        }
        controls.addView(ui.chipRow(groupChips.map { it.second }), ui.full(10))
        count = ui.note("")
        controls.addView(count, ui.full(10))
        treeStatus = ui.column()
        controls.addView(treeStatus)
        content.addView(controls)

        // La grille : autant de colonnes que la largeur le permet, des tuiles d'au moins 160 dp.
        val gap = ui.dp(10)
        layout = GridLayoutManager(this, 2)
        grid = RecyclerView(this).apply {
            layoutManager = layout
            adapter = this@BestiaryActivity.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(ui.dp(16) - gap / 2, ui.dp(4), ui.dp(16) - gap / 2, ui.dp(16))
            addItemDecoration(GridGap(gap))
            addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) {
                    val columns = max(2, (right - left - v.paddingLeft - v.paddingRight) / ui.dp(168))
                    if (columns != layout.spanCount) { layout.spanCount = columns; this@BestiaryActivity.adapter.notifyDataSetChanged() }
                }
            }
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(ui.screen(header, content, maxWidthDp = 1100))
    }

    private fun restyleGroups() { groupChips.forEach { (id, chip) -> ui.styleChip(chip, group == id) } }

    private fun renderList() {
        searchHandler.removeCallbacks(applySearch)
        val all = entries ?: return
        val terms = LifeTree.normalize(query).split(SPACES).filter { it.isNotBlank() }
        val shown = all.filter { entry ->
            (group.isEmpty() || group == entry.animal.group) && terms.all { it in entry.searchKey }
        }
        count.text = getString(if (shown.isEmpty()) R.string.be_empty else R.string.be_count, shown.size, all.size)
        adapter.items = shown
        adapter.notifyDataSetChanged()
    }

    /** Chaque tuile montre la planche, le nom et le nom latin ; la grille réutilise les vues. */
    private inner class TileAdapter : RecyclerView.Adapter<TileAdapter.Holder>() {
        var items: List<BestiaryEntry> = emptyList()
        inner class Holder(val tile: ScienceUi.Tile, val plate: AnimalPlateView) : RecyclerView.ViewHolder(tile.view)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val tile = ui.tile(.75f)
            val plate = AnimalPlateView(this@BestiaryActivity, thumbnail = true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            tile.image.addView(plate, FrameLayout.LayoutParams(-1, -1))
            tile.view.layoutParams = RecyclerView.LayoutParams(-1, -2)
            tile.view.isClickable = true; tile.view.isFocusable = true
            return Holder(tile, plate)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.plate.variant = entry.animal.id
            holder.tile.title.text = entry.name
            holder.tile.subtitle.text = entry.animal.scientific
            holder.tile.view.setOnClickListener { open(entry.animal) }
        }
    }

    private fun help() {
        val body = ui.column()
        body.addView(ui.paragraph(getString(R.string.be_intro)), ui.full(4))
        body.addView(ui.paragraph(getString(R.string.be_plate_note)), ui.full(14))
        body.addView(ui.paragraph(getString(R.string.science_navigation_help)), ui.full(14))
        fiche.show(getString(R.string.be_title), null, null, body)
    }

    // ------------------------------------------------------------------ les données

    private fun loadEntries() {
        val locale = resources.configuration.locales[0]
        lifecycleScope.launch {
            entries = withContext(Dispatchers.Default) { AnimalCatalog.entries(locale) }
            renderList()
            selected?.let { AnimalCatalog.byId[it]?.let { animal -> open(animal, push = false) } }
            applyLink()
        }
    }

    private fun loadTree() {
        treeStatus.removeAllViews()
        treeStatus.addView(ui.note(getString(R.string.be_tree_loading)), ui.full(4))
        lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.Default) {
                    val repo = ParentesRepository.load(applicationContext)
                    repo to BestiaryLifeIndex.of(repo.tree)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            treeStatus.removeAllViews()
            if (loaded == null) {
                treeStatus.addView(ui.action(getString(R.string.be_tree_retry)) { loadTree() }, ui.wrap(4))
                return@launch
            }
            repository = loaded.first
            index = loaded.second
            pendingClassification?.let { (id, block) -> pendingClassification = null; addClassification(block, id) }
            applyLink()
        }
    }

    private fun applyLink() {
        val node = pendingNode ?: return
        val loaded = index ?: return
        if (entries == null) return
        pendingNode = null
        val animal = loaded.forNode(node)?.let { AnimalCatalog.byId[it] }
        if (animal == null) Toast.makeText(this, R.string.be_unavailable, Toast.LENGTH_LONG).show()
        else if (animal.id != selected) { history.clear(); open(animal, push = false) }
    }

    // ------------------------------------------------------------------ la fiche

    /** Ouvre la fiche ; avec [push], la fiche ouverte jusque-là s'ajoute à l'historique de la flèche. */
    private fun open(animal: AnimalSpecies, push: Boolean = true) {
        val previous = selected
        if (push && previous != null && previous != animal.id) history.add(previous)
        selected = animal.id
        opening?.cancel()
        val language = language()
        opening = lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                try {
                    AnimalEncyclopediaRepository.load(applicationContext, animal.id, language)
                } catch (_: IOException) {
                    null
                } catch (_: JSONException) {
                    null
                }
            }
            if (selected == animal.id) show(animal, profile)
        }
    }

    private fun back() {
        val previous = history.removeLastOrNull()?.let { AnimalCatalog.byId[it] } ?: return
        open(previous, push = false)
    }

    private fun show(animal: AnimalSpecies, profile: EncyclopediaProfile?) {
        val name = AnimalCatalog.name(animal, language())
        val profileSections = profile?.sections
        // En tête : le nom latin, le groupe, puis les liens (remplis quand l'arbre est prêt).
        val top = ui.column()
        top.addView(ui.identity(animal.scientific, listOf(ui.pill(getString(groupTitle(animal.group)), palette.categorySurface(hue)))))
        val links = ui.column()
        top.addView(links)
        pendingClassification = null
        if (index != null) addClassification(links, animal.id) else pendingClassification = animal.id to links

        val plate = AnimalPlateView(this).apply {
            variant = animal.id
            contentDescription = profileSections?.get("identification")?.let {
                getString(R.string.be_plate_accessibility, name, it)
            } ?: getString(R.string.be_plate_accessibility_name, name)
        }
        val media = ui.column()
        media.addView(ui.hero(plate, .75f), if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else ui.full())

        val body = ui.column()
        if (profileSections == null) {
            body.addView(ui.text(getString(R.string.be_encyclopedia_coming)), ui.full())
        } else {
            listOf(
                R.string.be_overview to profileSections["overview"],
                R.string.be_identification to profileSections["identification"],
                R.string.be_habitat to profileSections["habitat"],
                R.string.be_diet to profileSections["diet"],
                R.string.be_behavior to profileSections["behavior"],
                R.string.be_reproduction to profileSections["reproduction"],
                R.string.be_ecology to profileSections["ecology"],
                R.string.be_conservation to profileSections["conservation"]
            ).forEach { (title, text) ->
                if (text == null) return@forEach
                body.addView(ui.chapter(getString(title), hue, ui.paragraph(text)), ui.full(12))
            }
        }
        sameGroup(animal)?.let { body.addView(it, ui.full(20)) }
        val references = profile?.references.orEmpty()
        if (references.isNotEmpty()) {
            val sources = ui.column()
            sources.addView(ui.note(getString(R.string.be_source_note)))
            references.forEach { reference -> sources.addView(ui.link(reference.title, reference.url), ui.full(2)) }
            body.addView(ui.chapter(getString(R.string.be_sources), palette.secondary, sources), ui.full(12))
        }
        fiche.show(name, top, media, body, if (history.isEmpty()) null else ::back)
    }

    /** D'autres animaux du même groupe, voisins dans l'ordre alphabétique, en tuiles qu'on fait défiler de côté. */
    private fun sameGroup(animal: AnimalSpecies): View? {
        val all = entries ?: return null
        val peers = all.filter { it.animal.group == animal.group }
        val at = peers.indexOfFirst { it.animal.id == animal.id }
        if (peers.size < 2 || at < 0) return null
        val picked = (1..SEE_ALSO).mapNotNull { offset ->
            val index = (at + if (offset % 2 == 1) (offset + 1) / 2 else -(offset / 2)).mod(peers.size)
            peers[index].takeIf { it.animal.id != animal.id }
        }.distinctBy { it.animal.id }
        val row = ui.line().apply { gravity = android.view.Gravity.TOP }
        picked.forEach { entry ->
            val tile = ui.tile(.75f) { open(entry.animal) }
            tile.image.addView(AnimalPlateView(this, thumbnail = true).apply {
                variant = entry.animal.id; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
            tile.title.text = entry.name; tile.subtitle.text = entry.animal.scientific
            tile.view.contentDescription = entry.name
            row.addView(tile.view, LinearLayout.LayoutParams(ui.dp(150), -2).apply { marginEnd = ui.dp(10) })
        }
        val block = ui.column()
        block.addView(ui.section(getString(R.string.science_see_also)))
        block.addView(ui.scrollRow(row), ui.full(8))
        return block
    }

    /** Le lien vers l'arbre du vivant et la lignée de l'animal, en tête de sa fiche. */
    private fun addClassification(block: LinearLayout, animalId: String) {
        val node = index?.forSpecies(animalId) ?: return
        val repo = repository ?: return
        block.addView(ui.chipRow(listOf(ui.moduleChip(ScienceModule.TREE, { openTree(node) }))), ui.full(12))
        block.addView(ui.note(repo.tree.ancestors(node).filter { it in repo.groups }.reversed()
            .joinToString(getString(R.string.pt_separator)) { repo.name(this, it) }), ui.full(6))
    }

    private fun openTree(node: String) {
        if (ScienceNavigation.isModuleLink(intent) && intent.getStringExtra(EXTRA_LIFE_NODE) == node) finish()
        else startActivity(Intent(this, ParentesActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
            .putExtra(ParentesActivity.EXTRA_NODE_ID, node))
    }

    private fun language() = resources.configuration.locales[0].language

    private fun groupTitle(id: String) = when (id) {
        "mammals" -> R.string.be_mammals
        "birds" -> R.string.be_birds
        "reptiles" -> R.string.be_reptiles
        "amphibians" -> R.string.be_amphibians
        "fish" -> R.string.be_fish
        else -> R.string.be_invertebrates
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("query", query); outState.putString("group", group)
        outState.putString("species", selected); outState.putString("node", pendingNode)
        outState.putStringArrayList("history", ArrayList(history))
        if (keepScroll && ::layout.isInitialized) outState.putParcelable("grid", layout.onSaveInstanceState())
    }
    override fun onDestroy() { searchHandler.removeCallbacks(applySearch); fiche.dismiss(); super.onDestroy() }

    companion object {
        const val EXTRA_LIFE_NODE = "bestiary_life_node"
        const val EXTRA_SPECIES_ID = "bestiary_species_id"
        private const val SEARCH_DELAY_MS = 150L
        private const val SEE_ALSO = 8
        private val SPACES = Regex("\\s+")
        // L'ordre des groupes tel qu'il est montré depuis le début du catalogue.
        private val GROUPS = listOf("birds", "fish", "invertebrates", "mammals", "amphibians", "reptiles")
    }
}
