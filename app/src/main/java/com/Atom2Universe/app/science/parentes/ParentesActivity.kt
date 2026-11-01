package com.Atom2Universe.app.science.parentes

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.PlantGroupKnowledge
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceModule
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceUi
import com.Atom2Universe.app.science.bestiary.AnimalEncyclopediaRepository
import com.Atom2Universe.app.science.bestiary.BestiaryActivity
import com.Atom2Universe.app.science.bestiary.BestiaryLifeIndex
import com.Atom2Universe.app.science.botany.BotanyActivity
import com.Atom2Universe.app.science.botany.BotanyLifeIndex
import com.Atom2Universe.app.science.mycology.FungusCatalog
import com.Atom2Universe.app.science.mycology.MycologyActivity
import com.Atom2Universe.app.science.mycology.MycologyLifeIndex
import com.Atom2Universe.app.science.timeline.CosmicTimelineActivity
import com.Atom2Universe.app.science.timeline.LifeDate
import com.Atom2Universe.app.science.timeline.LifeTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Parentés : la carte de l'arbre du vivant, qu'on explore de branche en branche ou dans laquelle on compare deux
 * espèces. Tout ce qui se lit (détails d'un nœud, recherche, liste, explications) s'ouvre dans la fiche commune
 * des modules de science, avec ses liens vers le bestiaire, les plantes, les champignons et la chronologie.
 */
class ParentesActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private val ui by lazy { ScienceUi(this, palette) }
    private val hue by lazy { ScienceModule.TREE.color(palette) }
    private var repository: ParentesRepository? = null
    private val repo get() = requireNotNull(repository)
    private var bestiaryIndex: BestiaryLifeIndex? = null
    private var mycologyIndex: MycologyLifeIndex? = null
    private var focus = ""
    private var selected = ""
    private var first = ""
    private var second = ""
    private var comparing = false
    private var pendingNode: String? = null
    private var pendingFirst: String? = null
    private var pendingSecond: String? = null
    private var pendingDate: String? = null
    private var scene: TreeScene? = null
    private var renderJob: Job? = null
    private var overviewPrepared: ParentesTreeView.Prepared? = null
    private lateinit var header: ScienceUi.Header
    private lateinit var breadcrumb: LinearLayout
    private lateinit var selectors: LinearLayout
    private lateinit var chart: ParentesTreeView
    private lateinit var card: LinearLayout
    private lateinit var cardScroll: ScrollView
    private lateinit var status: TextView
    private lateinit var loading: LinearLayout
    private lateinit var modes: ScienceUi.TabRow
    private val storyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val a = result.data?.getStringExtra("first")
            val b = result.data?.getStringExtra("second")
            if (a != null && b != null && (repository == null ||
                (repo.tree.nodes[a]?.species == true && repo.tree.nodes[b]?.species == true))) {
                first = a; second = b; comparing = true
                if (repository != null) render()
            }
        }
    }
    // La fiche : une seule fenêtre pour les détails, la recherche, la liste et les explications, avec son historique.
    private val fiche by lazy { ScienceFiche(this, palette, R.string.pt_back, R.string.pt_close) { currentSheet = null; sheetHistory.clear() } }
    private var currentSheet: (() -> Unit)? = null
    private val sheetHistory = ArrayList<() -> Unit>()
    private var searchJob: Job? = null
    private val wide get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focus = savedInstanceState?.getString("focus").orEmpty()
        selected = savedInstanceState?.getString("selected").orEmpty()
        first = savedInstanceState?.getString("first").orEmpty()
        second = savedInstanceState?.getString("second").orEmpty()
        comparing = savedInstanceState?.getBoolean("comparing") ?: false
        if (savedInstanceState != null) {
            pendingNode = savedInstanceState.getString("pending_node")
            pendingFirst = savedInstanceState.getString("pending_first")
            pendingSecond = savedInstanceState.getString("pending_second")
            pendingDate = savedInstanceState.getString("pending_date")
        } else receiveLink(intent)
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        loadAtlas()
    }

    private fun loadAtlas() {
        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.Default) { runCatching {
                val atlas = ParentesRepository.load(applicationContext)
                // Les index des catalogues se construisent ici aussi, hors du fil de l'interface.
                Triple(atlas, BestiaryLifeIndex.of(atlas.tree),
                    MycologyLifeIndex(atlas.tree, FungusCatalog.species.associate { it.id to getString(it.latin) }))
            } }
            loaded.onSuccess { (result, bestiary, mycology) ->
                loading.visibility = View.GONE
                repository = result
                bestiaryIndex = bestiary
                mycologyIndex = mycology
                focus = focus.takeIf { it in repo.tree.nodes } ?: repo.tree.root
                selected = selected.takeIf { it in repo.tree.nodes } ?: focus
                fun defaultSpecies(name: String) = repo.tree.species.first { it.scientific == name }.id
                first = first.takeIf { repo.tree.nodes[it]?.species == true } ?: defaultSpecies("Apis mellifera")
                second = second.takeIf { repo.tree.nodes[it]?.species == true } ?: defaultSpecies("Bombus terrestris")
                val restoredSelection = selected
                render { if (restoredSelection in requireNotNull(scene).parents) select(restoredSelection) }
                applyPendingLink()
            }.onFailure { error ->
                android.util.Log.e("ParentesLoad", "Atlas load failed", error)
                status.text = getString(R.string.pt_error)
                loading.removeAllViews()
                loading.addView(ui.action(getString(R.string.pt_story_retry)) {
                    loading.removeAllViews(); loading.addView(ProgressBar(this@ParentesActivity)); loadAtlas()
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        updateBackDescription()
        receiveLink(intent)
        if (repository != null) applyPendingLink()
    }

    private fun receiveLink(intent: Intent) {
        pendingNode = intent.getStringExtra(EXTRA_NODE_ID)
        pendingFirst = intent.getStringExtra(EXTRA_FIRST_ID)
        pendingSecond = intent.getStringExtra(EXTRA_SECOND_ID)
        pendingDate = intent.getStringExtra(CosmicTimelineActivity.EXTRA_LIFE_ID)
    }

    private fun applyPendingLink() {
        val id = pendingNode ?: return
        val dating = LifeTimeline.get(pendingDate)
        val a = pendingFirst ?: dating?.comparison?.first
        val b = pendingSecond ?: dating?.comparison?.second
        pendingNode = null; pendingFirst = null; pendingSecond = null; pendingDate = null
        if (id !in repo.tree.nodes || (dating != null && (dating.nodeId != id || !LifeTimeline.matches(repo.tree, dating)))) {
            Toast.makeText(this, R.string.ct_life_link_unavailable, Toast.LENGTH_LONG).show(); return
        }
        fiche.dismiss()
        if (a != null && b != null && a != b && repo.tree.nodes[a]?.species == true &&
            repo.tree.nodes[b]?.species == true && repo.tree.lca(a, b) == id) {
            first = a; second = b; comparing = true; render()
        } else reveal(id)
    }

    // ------------------------------------------------------------------ l'écran

    private fun buildUi() {
        header = ui.header(getString(R.string.pt_short_title), ScienceModule.TREE, onBack = { navigateBack() }, onHome = ::returnToStart,
            actions = listOf(
                ScienceUi.HeaderAction(R.drawable.ic_search, R.string.pt_search_tree) { if (repository != null) picker(false) { reveal(it) } },
                ScienceUi.HeaderAction(R.drawable.ic_view_list, R.string.pt_list) { if (repository != null) accessibleList() },
                ScienceUi.HeaderAction(R.drawable.ic_sci_help, R.string.pt_read_title) { showReading() }
            ), subtitle = getString(R.string.pt_tree_subtitle))
        updateBackDescription()
        val column = ui.column().apply { setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(6)) }
        val mapColumn = if (wide) ui.column().apply { setPadding(0, 0, ui.dp(8), 0) } else column
        val toolsColumn = if (wide) ui.column().apply { setPadding(ui.dp(8), 0, 0, 0) } else column
        if (wide) {
            column.addView(ui.line().apply {
                gravity = Gravity.TOP
                addView(mapColumn, LinearLayout.LayoutParams(0, -1, 1f))
                addView(toolsColumn, LinearLayout.LayoutParams(ui.dp(300), -1))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        breadcrumb = ui.line().apply { setPadding(0, ui.dp(2), 0, ui.dp(2)) }
        mapColumn.addView(breadcrumb)
        selectors = ui.column()
        toolsColumn.addView(selectors)
        val mapArea = FrameLayout(this)
        chart = ParentesTreeView(this).apply { onSelect = { id ->
            if (!comparing && repo.tree.isBrowsable(id) && id != focus) explore(id)
            else select(id)
        } }
        mapArea.addView(chart, FrameLayout.LayoutParams(-1, -1))
        loading = ui.column().apply {
            gravity = Gravity.CENTER
            addView(ProgressBar(this@ParentesActivity))
            addView(ui.text(getString(R.string.pt_story_loading_map)))
        }
        mapArea.addView(loading, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val camera = ui.column().apply { background = palette.shape(palette.surface, 24f) }
        camera.addView(ui.icon(R.drawable.ic_ae_zoom_in, R.string.pt_zoom_in) { chart.zoomBy(1.6f) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        camera.addView(ui.icon(R.drawable.ic_ae_zoom_out, R.string.pt_zoom_out) { chart.zoomBy(1f / 1.6f) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        camera.addView(ui.icon(R.drawable.ic_ae_zoom_fit, R.string.pt_fit) { chart.fit() }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        mapArea.addView(camera, FrameLayout.LayoutParams(ui.dp(48), -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = ui.dp(8); rightMargin = ui.dp(4) })
        mapColumn.addView(mapArea, LinearLayout.LayoutParams(-1, 0, 1f))
        card = ui.card()
        cardScroll = ScrollView(this).apply { addView(card); visibility = View.GONE }
        toolsColumn.addView(cardScroll, (if (wide) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2)).apply { topMargin = ui.dp(6) })
        if (wide) toolsColumn.addView(View(this), LinearLayout.LayoutParams(-1, 0, 0.001f))
        status = ui.note(getString(R.string.pt_loading)).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, ui.dp(2)) }
        toolsColumn.addView(status)
        modes = ui.tabs(listOf(getString(R.string.pt_explore_tab), getString(R.string.pt_compare), getString(R.string.pt_trails_tab)), 0) { index ->
            if (repository == null) return@tabs
            when (index) {
                0 -> explore(repo.tree.root)
                1 -> { comparing = true; render() }
                else -> storyLauncher.launch(Intent(this, ParentesStoryActivity::class.java))
            }
        }
        toolsColumn.addView(modes.view, LinearLayout.LayoutParams(-1, -2))
        setContentView(ui.screen(header, column, maxWidthDp = 1400))
    }

    /** Les panneaux suivent tout de suite ; le dessin se prépare hors du fil de l'interface, puis [after] s'exécute. */
    private fun render(after: (() -> Unit)? = null) {
        val atlas = repo
        val compare = comparing; val target = focus; val a = first; val b = second
        selected = if (compare) atlas.tree.lca(a, b) else target
        chart.selected = selected
        refreshNavigation()
        modes.select(if (compare) 1 else 0)
        showSummary()
        val cached = if (!compare && target == atlas.tree.root) overviewPrepared else null
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val prepared = cached ?: withContext(Dispatchers.Default) {
                val current = if (compare) atlas.tree.compare(a, b) else if (target == atlas.tree.root) atlas.overview else atlas.tree.atlas(target)
                chart.prepare(current, atlas.tree, atlas.groups.keys, if (current === atlas.overview) atlas.overviewPositions else null) { id ->
                    when {
                        compare && id == current.common && atlas.tree.nodes.getValue(id).scientific.isEmpty() -> getString(R.string.pt_common_node)
                        atlas.tree.nodes.getValue(id).species || atlas.tree.isBrowsable(id) || compare -> atlas.name(this@ParentesActivity, id)
                        else -> ""
                    }
                }
            }
            if (prepared.scene === atlas.overview) overviewPrepared = prepared
            scene = prepared.scene
            chart.show(prepared)
            after?.invoke()
        }
    }

    private fun refreshNavigation() {
        breadcrumb.removeAllViews()
        selectors.removeAllViews()
        if (comparing) {
            for ((id, isFirst) in listOf(first to true, second to false)) {
                selectors.addView(ui.action(getString(if (isFirst) R.string.pt_a else R.string.pt_b, repo.name(this, id))) {
                    picker(true) { if (isFirst) first = it else second = it; render() }
                }, ui.wrap(4))
            }
        } else {
            if (focus != repo.tree.root) breadcrumb.addView(ui.icon(R.drawable.ic_chevron_left, R.string.pt_parent) {
                explore(repo.tree.displayParent(focus) ?: repo.tree.root)
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            breadcrumb.addView(ui.text(repo.name(this, focus), 16f, bold = true).apply {
                maxLines = 2; setOnClickListener { details(focus) }
                contentDescription = getString(R.string.pt_node_action, repo.name(this@ParentesActivity, focus))
                isClickable = true; isFocusable = true; background = ui.ripple(null)
                setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            breadcrumb.addView(ui.note(getString(R.string.pt_tip_count, repo.tree.descendants(focus).size)), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(8) })
            LifeTimeline.forNode(repo.tree, focus)?.let { date ->
                breadcrumb.addView(ui.moduleChip(ScienceModule.TIMELINE, { openTimeline(date) }))
            }
        }
        status.text = getString(if (comparing) R.string.pt_comparison_hint else R.string.pt_map_hint)
    }

    private fun explore(id: String, after: (() -> Unit)? = null) {
        comparing = false; focus = id; selected = id
        render(after)
    }
    private fun reveal(id: String) {
        if (repo.tree.nodes.getValue(id).species) {
            explore(repo.tree.displayParent(id) ?: repo.tree.root) { chart.post { chart.reveal(id) } }
            select(id)
        } else explore(id)
    }
    private fun select(id: String) { selected = id; chart.selected = id; showSummary() }
    private fun navigateBack() {
        when {
            fiche.isShowing -> fiche.dismiss()
            ScienceNavigation.isModuleLink(intent) -> finish()
            repository == null -> finish()
            comparing -> explore(repo.tree.root)
            selected != focus -> select(focus)
            focus != repo.tree.root -> explore(repo.tree.displayParent(focus) ?: repo.tree.root)
            else -> finish()
        }
    }

    private fun updateBackDescription() {
        header.back.contentDescription = getString(if (ScienceNavigation.isModuleLink(intent))
            R.string.science_back_to_previous_module else R.string.pt_back)
    }

    private fun returnToStart() {
        fiche.dismiss()
        pendingNode = null; pendingFirst = null; pendingSecond = null; pendingDate = null
        comparing = false
        // Un lien en attente est aussi annulé si l'atlas se charge encore.
        if (repository == null) {
            focus = ""; selected = ""
        } else {
            explore(repo.tree.root)
            header.back.announceForAccessibility(getString(R.string.science_return_to_module_start))
        }
    }

    /** Le résumé du nœud choisi, sous la carte : son nom, une ligne, et les liens vers les autres modules. */
    private fun showSummary() {
        card.removeAllViews()
        val show = comparing || selected != focus
        cardScroll.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        // Un aperçu borné, qui défile, laisse la place au dessin, y compris en paysage.
        if (!wide) cardScroll.layoutParams.height = ui.dp(150)
        if (comparing && selected == repo.tree.lca(first, second)) {
            card.addView(ui.text(getString(if (first == second) R.string.pt_same else R.string.pt_common_summary), 14f, bold = true))
            val links = mutableListOf<View>(ui.action(getString(R.string.pt_details)) { comparisonDetails() })
            if (first != second) LifeTimeline.forNode(repo.tree, selected)?.let { date -> links += ui.moduleChip(ScienceModule.TIMELINE, { openTimeline(date) }) }
            card.addView(ui.chipRow(links), ui.full(8))
        } else {
            val heading = ui.line()
            heading.addView(ui.text(repo.name(this, selected), 17f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(ui.action(getString(R.string.pt_details)) { details(selected) })
            card.addView(heading)
            card.addView(ui.text(if (repo.tree.nodes.getValue(selected).species) repo.tree.nodes.getValue(selected).scientific else article(selected), 14f).apply {
                maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
            }, ui.full(4))
            val links = moduleLinks(selected)
            if (links.isNotEmpty()) card.addView(ui.chipRow(links), ui.full(8))
        }
        cardScroll.post { cardScroll.scrollTo(0, 0) }
    }

    // ------------------------------------------------------------------ les fiches

    /** Montre une fiche ; celle d'avant va dans l'historique de la flèche, sauf quand on y revient ([push] faux). */
    private fun present(push: Boolean = true, build: (onBack: (() -> Unit)?) -> Unit) {
        val previous = currentSheet
        if (push && previous != null) sheetHistory.add(previous)
        val self: () -> Unit = { present(false, build) }
        currentSheet = self
        build(if (sheetHistory.isEmpty()) null else ({ sheetHistory.removeAt(sheetHistory.lastIndex)() }))
    }

    private fun showReading(): Unit = present { back ->
        val body = ui.column()
        body.addView(ui.paragraph(getString(R.string.science_navigation_help)), ui.full(4))
        ParentesReading.sections.forEach { section ->
            val text = ui.column()
            text.addView(ui.paragraph(getString(section.body)))
            text.addView(ui.link(android.net.Uri.parse(section.url).host.orEmpty(), section.url), ui.full(6))
            body.addView(ui.chapter(getString(section.title), hue, text), ui.full(12))
        }
        fiche.show(getString(R.string.pt_read_title), null, null, body, back)
    }

    private fun accessibleList(): Unit = present { back ->
        val body = ui.column()
        body.addView(ui.action(getString(R.string.pt_catalogue, repo.tree.species.size)) { picker(true) { reveal(it) } }, ui.wrap(0))
        body.addView(ui.note(getString(R.string.pt_list_intro)), ui.full(10))
        val entries = scene?.parents.orEmpty().keys.filter {
            repo.tree.nodes.getValue(it).species || repo.tree.isBrowsable(it) || comparing
        }
        entries.forEach { id ->
            val ancestor = repo.tree.displayParent(id)
            body.addView(nodeRow(if (ancestor == null) repo.name(this, id) else getString(R.string.pt_branch_format, repo.name(this, ancestor), repo.name(this, id)), null) {
                select(id); details(id)
            }, ui.full(2))
        }
        fiche.show(getString(R.string.pt_list), null, null, body, back)
    }

    /** Une ligne de liste cliquable : un nom, et un nom latin en dessous s'il y en a un. */
    private fun nodeRow(name: String, latin: String?, onClick: () -> Unit): View {
        val line = ui.line().apply {
            background = ui.ripple(null); isClickable = true; isFocusable = true
            setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10)); setOnClickListener { onClick() }
        }
        val texts = ui.column()
        texts.addView(ui.text(name, 15f, bold = true))
        latin?.takeIf { it.isNotBlank() && it != name }?.let { texts.addView(ui.latin(it, 13f), ui.full(1)) }
        line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return line
    }

    private fun article(id: String): String {
        PlantGroupKnowledge.description(this, repo.tree.nodes.getValue(id).scientific)?.let { return it }
        repo.groups[id]?.let { return repo.text(this, it.key + "_body") }
        repo.notes[id]?.let { return repo.text(this, it.key) }
        val n = repo.tree.nodes.getValue(id)
        if (!n.species) return getString(R.string.pt_node_body)
        val group = repo.tree.ancestors(id).firstOrNull { it in repo.groups } ?: repo.tree.root
        return getString(R.string.pt_species_body, repo.name(this, group))
    }

    /** La fiche d'un nœud : son identité et ses liens, puis ses chapitres et ce qu'on peut en faire sur la carte. */
    private fun details(id: String): Unit = present { back ->
        val node = repo.tree.nodes.getValue(id)
        val top = ui.column()
        top.addView(ui.identity(node.scientific.takeIf { it.isNotEmpty() }, links = moduleLinks(id) + datingLinks(id)))
        val body = ui.column()
        val description = ui.column()
        description.addView(ui.paragraph(article(id)))
        bestiaryIndex?.forNode(id)?.let { profile ->
            // La description de l'animal est le portrait de sa fiche encyclopédique, lu hors du fil de l'interface.
            val overview = ui.paragraph("").apply { visibility = View.GONE }
            description.addView(overview, ui.full(8))
            val language = resources.configuration.locales[0].language
            lifecycleScope.launch {
                val text = withContext(Dispatchers.IO) {
                    try { AnimalEncyclopediaRepository.load(applicationContext, profile, language).sections["overview"] }
                    catch (_: java.io.IOException) { null } catch (_: org.json.JSONException) { null }
                }
                if (!text.isNullOrBlank()) { overview.text = text; overview.visibility = View.VISIBLE }
            }
        }
        body.addView(ui.chapter(repo.name(this, id), hue, description), ui.full(4))
        val knowledge = ui.column()
        PlantGroupKnowledge.append(this, knowledge, palette, node.scientific, includeDescription = false)
        if (knowledge.childCount > 0) body.addView(knowledge, ui.full(8))

        val lineage = repo.tree.ancestors(id).drop(1).filter { it in repo.groups }.reversed()
        val position = ui.column()
        if (lineage.isNotEmpty()) position.addView(ui.flow(lineage.map { ancestor -> ui.chip(repo.name(this, ancestor), false) { details(ancestor) } }))
        val notes = mutableListOf(getString(if (node.support.any { '@' in it }) R.string.pt_support_study else R.string.pt_support_taxonomy))
        if (node.conflicts.isNotEmpty()) notes += getString(R.string.pt_conflicts)
        if (repo.tree.children[id].orEmpty().size > 2) notes += getString(R.string.pt_polytomy)
        if (!node.species) notes += getString(R.string.pt_count, repo.tree.descendants(id).size)
        position.addView(ui.note(notes.joinToString("\n")), ui.full(10))
        body.addView(ui.chapter(getString(R.string.pt_position_title), hue, position), ui.full(12))

        if (node.species) {
            val group = repo.tree.ancestors(id).firstOrNull { it in repo.groups }
            group?.let { body.addView(ui.chapter(repo.name(this, it), hue, ui.paragraph(repo.text(this, repo.groups.getValue(it).key + "_body"))), ui.full(12)) }
        }
        // Ce qu'on peut faire sur la carte : y aller, remonter, voir les membres, choisir pour la comparaison.
        val actions = mutableListOf<View>()
        repo.tree.displayParent(id)?.let { parent -> actions += ui.action(getString(R.string.pt_parent), R.drawable.ic_chevron_left) { fiche.dismiss(); explore(parent) } }
        if (!node.species) {
            actions += ui.action(getString(R.string.pt_open), R.drawable.ic_pt_tree) { fiche.dismiss(); explore(id) }
            actions += ui.action(getString(R.string.pt_members)) { picker(true, repo.tree.descendants(id).map { it.id }.toSet()) { details(it) } }
        } else {
            actions += ui.action(getString(R.string.pt_choose_a), R.drawable.ic_pt_compare) { fiche.dismiss(); first = id; comparing = true; render() }
            actions += ui.action(getString(R.string.pt_choose_b), R.drawable.ic_pt_compare) { fiche.dismiss(); second = id; comparing = true; render() }
        }
        body.addView(ui.flow(actions), ui.full(18))
        fiche.show(repo.name(this, id), top, null, body, back)
    }

    /** Les liens vers les fiches illustrées des autres modules pour ce nœud. */
    private fun moduleLinks(id: String): List<View> {
        val links = mutableListOf<View>()
        if (com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "bestiary")) bestiaryIndex?.forNode(id)?.let { species ->
            links += ui.moduleChip(ScienceModule.BESTIARY, {
                startActivity(Intent(this, BestiaryActivity::class.java)
                    .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(BestiaryActivity.EXTRA_SPECIES_ID, species)
                    .putExtra(BestiaryActivity.EXTRA_LIFE_NODE, id))
            })
        }
        if (com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "botany") &&
            repo.tree.nodes[id]?.species == true && BotanyLifeIndex.GREEN_ROOT in repo.tree.ancestors(id)) {
            links += ui.moduleChip(ScienceModule.BOTANY, {
                startActivity(Intent(this, BotanyActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(BotanyActivity.EXTRA_LIFE_NODE, id))
            })
        }
        if (com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "mycology")) mycologyIndex?.forNode(id)?.let { species ->
            links += ui.moduleChip(ScienceModule.MYCOLOGY, {
                startActivity(Intent(this, MycologyActivity::class.java)
                    .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(MycologyActivity.EXTRA_SPECIES_ID, species)
                    .putExtra(MycologyActivity.EXTRA_LIFE_NODE, id))
            })
        }
        return links
    }

    /** Le lien vers la chronologie : la date du nœud, sinon celle de son plus proche ancêtre daté. */
    private fun datingLinks(id: String): List<View> {
        val exact = LifeTimeline.forNode(repo.tree, id)
        if (exact != null) return listOf(ui.moduleChip(ScienceModule.TIMELINE, { openTimeline(exact) }, exact.dateLabel(this)))
        val ancestor = LifeTimeline.nearestAncestor(repo.tree, id) ?: return emptyList()
        return listOf(ui.moduleChip(ScienceModule.TIMELINE, { openTimeline(ancestor) }, getString(R.string.ct_life_open_ancestor, getString(ancestor.title))))
    }

    private fun comparisonDetails(): Unit = present { back ->
        val common = repo.tree.lca(first, second)
        val top = ui.column()
        top.addView(ui.identity(null, links = if (first != second) datingLinks(common) else emptyList()))
        val body = ui.column()
        body.addView(ui.note(getString(R.string.pt_comparison_limits)), ui.full(4))
        body.addView(ui.chapter(getString(R.string.pt_common_node), hue, ui.flow(listOf(ui.chip(repo.name(this, common), false) { details(common) }))), ui.full(12))
        for ((id, title) in listOf(first to R.string.pt_path_a, second to R.string.pt_path_b)) {
            val path = ui.column()
            repo.tree.pathTo(id, common).forEach { n -> path.addView(nodeRow(repo.name(this, n), repo.tree.nodes.getValue(n).scientific) { details(n) }, ui.full(2)) }
            body.addView(ui.chapter(getString(title), hue, path), ui.full(12))
        }
        fiche.show(getString(R.string.pt_compare), top, null, body, back)
    }

    private fun openTimeline(date: LifeDate) {
        val target = Intent(this, CosmicTimelineActivity::class.java)
            .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
            .putExtra(CosmicTimelineActivity.EXTRA_LIFE_ID, date.id)
        if (comparing && first != second && repo.tree.lca(first, second) == date.nodeId) {
            target.putExtra(EXTRA_FIRST_ID, first).putExtra(EXTRA_SECOND_ID, second)
        }
        startActivity(target)
    }

    /** La recherche d'un nœud : un champ, et la liste des résultats dans la fiche ; choisir ferme la fiche. */
    private fun picker(speciesOnly: Boolean, within: Set<String>? = null, chosen: (String) -> Unit): Unit = present { back ->
        val body = ui.column()
        val empty = ui.note(getString(R.string.pt_none))
        val results = ui.column()
        var found = emptyList<LifeNode>()
        fun update(query: String) {
            searchJob?.cancel()
            empty.text = getString(R.string.pt_story_loading_search)
            empty.visibility = View.VISIBLE
            searchJob = lifecycleScope.launch {
                found = withContext(Dispatchers.Default) {
                    val collator = java.text.Collator.getInstance(resources.configuration.locales[0])
                    val hits = repo.search(query, speciesOnly).filter { within == null || it.id in within }
                    ensureActive()
                    val named = hits.map { it to collator.getCollationKey(repo.name(this@ParentesActivity, it.id)) }
                    ensureActive()
                    named.sortedBy { it.second }.map { it.first }
                }
                results.removeAllViews()
                found.take(RESULTS).forEach { n ->
                    results.addView(nodeRow(repo.name(this@ParentesActivity, n.id), n.scientific) { fiche.dismiss(); chosen(n.id) }, ui.full(2))
                }
                if (found.size > RESULTS) results.addView(ui.note(getString(R.string.pt_tip_count, found.size)), ui.full(8))
                empty.text = getString(R.string.pt_none)
                empty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        body.addView(ui.searchField(getString(R.string.pt_search_hint), "") { update(it) }.view, ui.full(4))
        body.addView(empty, ui.full(10))
        body.addView(results, ui.full(4))
        update("")
        fiche.show(getString(if (speciesOnly) R.string.pt_choose else R.string.pt_search), null, null, body, back)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("focus", focus); outState.putString("selected", selected)
        outState.putString("first", first); outState.putString("second", second)
        outState.putBoolean("comparing", comparing)
        outState.putString("pending_node", pendingNode); outState.putString("pending_first", pendingFirst)
        outState.putString("pending_second", pendingSecond); outState.putString("pending_date", pendingDate)
    }
    override fun onDestroy() { searchJob?.cancel(); fiche.dismiss(); super.onDestroy() }

    companion object {
        const val EXTRA_NODE_ID = "life_node_id"
        const val EXTRA_FIRST_ID = "life_comparison_first"
        const val EXTRA_SECOND_ID = "life_comparison_second"
        /** Au plus tant de résultats de recherche affichés d'un coup. */
        private const val RESULTS = 200
    }
}
