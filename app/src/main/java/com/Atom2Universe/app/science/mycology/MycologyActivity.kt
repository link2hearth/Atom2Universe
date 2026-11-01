package com.Atom2Universe.app.science.mycology

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceModule
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceUi
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.parentes.ParentesRepository
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * L'atlas des champignons : des sosies comparés côte à côte (d'après les confusions réellement observées
 * en France), une grille d'espèces, et celles dont le classement a changé. Chaque fiche montre le champignon
 * en trois vues dessinées en code, puis ses chapitres. Les sources n'apparaissent que dans « À propos ».
 */
class MycologyActivity : ThemedActivity() {
    private enum class Tab(val title: Int) { LOOKALIKES(R.string.myco_tab_lookalikes), SPECIES(R.string.myco_tab_species), CHANGED(R.string.myco_tab_changed) }

    private sealed class Sheet {
        data class Species(val id: String) : Sheet()
        data class Group(val id: String) : Sheet()
        object About : Sheet()
    }

    private val palette by lazy { SciencePalette(this) }
    private val ui by lazy { ScienceUi(this, palette) }
    private val hue by lazy { ScienceModule.MYCOLOGY.color(palette) }
    private val prefs by lazy { getSharedPreferences("mycology", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabs: ScienceUi.TabRow
    private var tab = Tab.LOOKALIKES
    // Les choix de l'écran Espèces : plusieurs statuts, plusieurs critères, gardés d'une ouverture à l'autre.
    private val statuses = HashSet<FungusStatus>()
    private val criteria = HashSet<String>()
    private var criteriaOpen = true
    private var mode = PlateMode.SIDE
    private var marks = true

    // La fiche ouverte : une seule fenêtre plein écran pour toute la navigation entre fiches.
    private val fiche by lazy {
        ScienceFiche(this, palette, R.string.myco_back, R.string.myco_close) { current = null; history.clear() }
    }
    private var current: Sheet? = null
    private var lifeIndex: MycologyLifeIndex? = null
    private var lifeRepo: ParentesRepository? = null
    /** La place des liens de la fiche ouverte, remplie sur place quand l'arbre arrive. */
    private var treeSlot: Pair<String, LinearLayout>? = null
    private val history = ArrayList<Sheet>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "mycology")) {
            finish()
            return
        }
        tab = Tab.entries.firstOrNull { it.name == (savedInstanceState?.getString("tab") ?: prefs.getString("tab", null)) } ?: Tab.LOOKALIKES
        prefs.getStringSet("statuses", null)?.forEach { name -> FungusStatus.entries.firstOrNull { it.name == name }?.let { statuses.add(it) } }
        criteria.addAll(FungusCriteria.known(prefs.getStringSet("criteria", null) ?: emptySet()))
        criteriaOpen = prefs.getBoolean("criteria_open", true)
        mode = PlateMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: PlateMode.SIDE
        marks = prefs.getBoolean("marks", true)
        buildUi()
        render()
        savedInstanceState?.getString("sheet")?.let { restoreSheet(it) }
        if (savedInstanceState == null) intent.getStringExtra(EXTRA_SPECIES_ID)?.let { id -> content.post { open(Sheet.Species(id), push = false) } }
        loadTree()
        val y = savedInstanceState?.getInt("scroll") ?: 0
        scroll.post { scroll.scrollTo(0, y) }
    }

    /** L'arbre se charge à part : le lien « Arbre du vivant » apparaît dès qu'il est prêt, sans refaire la fiche. */
    private fun loadTree() {
        val latin = FungusCatalog.species.associate { it.id to getString(it.latin) }
        lifecycleScope.launch {
            val (repo, index) = runCatching {
                withContext(Dispatchers.Default) { ParentesRepository.load(applicationContext).let { it to MycologyLifeIndex.of(it.tree, latin) } }
            }.getOrNull() ?: return@launch
            lifeRepo = repo
            lifeIndex = index
            val slot = treeSlot
            treeSlot = null
            if (slot != null && (current as? Sheet.Species)?.id == slot.first) classification(slot.second, slot.first)
        }
    }

    // ------------------------------------------------------------------ écran principal

    private fun buildUi() {
        val header = ui.header(getString(R.string.myco_title), ScienceModule.MYCOLOGY, onBack = { finish() }, onHome = {
            fiche.dismiss()
            tab = Tab.LOOKALIKES
            render()
            scroll.scrollTo(0, 0)
            scroll.announceForAccessibility(getString(R.string.science_return_to_module_start))
        }, actions = listOf(ScienceUi.HeaderAction(R.drawable.ic_sci_help, R.string.myco_about) { open(Sheet.About) }))

        val column = ui.column()
        // L'avertissement et le numéro d'urgence restent visibles : c'est une question de sécurité.
        val warning = ui.line().apply {
            background = palette.shape(ColorUtils.blendARGB(palette.surface, DANGER, 0.10f), 12f)
            setPadding(ui.dp(10), ui.dp(6), ui.dp(8), ui.dp(6))
        }
        warning.addView(ImageView(this).apply { setImageResource(R.drawable.ic_info); imageTintList = android.content.res.ColorStateList.valueOf(DANGER) },
            LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
        warning.addView(ui.text(getString(R.string.myco_warning), 12.5f), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(8); marginEnd = ui.dp(8) })
        warning.addView(ui.pill(getString(R.string.myco_urgent), DANGER).apply { isClickable = true; isFocusable = true; setOnClickListener { showUrgent() } })
        column.addView(warning, LinearLayout.LayoutParams(-1, -2).apply { setMargins(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(4)) })

        tabs = ui.tabs(Tab.entries.map { getString(it.title) }, tab.ordinal) { index ->
            val next = Tab.entries[index]
            if (tab != next) { tab = next; tabs.select(index); render(); scroll.scrollTo(0, 0) }
        }
        column.addView(tabs.view)
        content = ui.column().apply { setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(24)) }
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(content) }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(ui.screen(header, column, maxWidthDp = 1100))
    }

    private fun render() {
        content.removeAllViews()
        when (tab) {
            Tab.LOOKALIKES -> renderLookalikes()
            Tab.SPECIES -> renderSpecies()
            Tab.CHANGED -> renderChanged()
        }
    }

    private fun contentWidth() = min(resources.displayMetrics.widthPixels, ui.dp(1100)) - ui.dp(32)

    private fun renderLookalikes() {
        FungusCatalog.groups.forEach { group ->
            val members = group.members.mapNotNull { FungusCatalog.get(it) }
            val card = ui.card { open(Sheet.Group(group.id)) }.apply { contentDescription = getString(group.title) }
            val head = ui.line()
            head.addView(ui.text(getString(group.title), 17f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(ui.pill(getString(group.evidence), palette.outline, small = true))
            card.addView(head)
            val plates = ui.line().apply { gravity = Gravity.TOP }
            val looks = members.map { it.look }
            members.forEach { sp ->
                val cell = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL }
                cell.addView(plate(sp.look, PlateMode.SIDE, compact = true, shared = looks), LinearLayout.LayoutParams(-1, ui.dp(150)))
                cell.addView(ui.text(getString(sp.name), 12f).apply {
                    gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(2) })
                cell.addView(statusChip(sp.status, small = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(3) })
                plates.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(2); marginEnd = ui.dp(2) })
            }
            card.addView(plates, ui.full())
            content.addView(card, ui.full())
        }
    }

    private fun sortedSpecies(): List<FungusSpecies> {
        val collator = Collator.getInstance(resources.configuration.locales[0])
        return FungusCatalog.species.sortedWith(compareBy(collator) { getString(it.name) })
    }

    /**
     * L'écran Espèces : les statuts (plusieurs à la fois, « Tous » les efface), puis des critères pour retrouver
     * un champignon d'après ce qu'on a vu, et la grille des tuiles. Une puce touchée change la grille sans
     * reconstruire l'écran, donc sans faire sauter le défilement.
     */
    private fun renderSpecies() {
        val results = ui.column()
        val statusChips = ArrayList<Pair<FungusStatus?, TextView>>()
        val criterionChips = ArrayList<Pair<String, TextView>>()
        val toggle = ui.action("") {}
        val clear = ui.action(getString(R.string.myco_criteria_clear)) {}
        val panel = ui.column().apply { visibility = if (criteriaOpen) View.VISIBLE else View.GONE }

        fun refresh() {
            statusChips.forEach { (status, chip) -> ui.styleChip(chip, if (status == null) statuses.isEmpty() else status in statuses) }
            criterionChips.forEach { (id, chip) -> ui.styleChip(chip, id in criteria) }
            val base = if (criteria.isEmpty()) getString(R.string.myco_criteria) else getString(R.string.myco_criteria_count, criteria.size)
            toggle.text = base + if (criteriaOpen) " ▴" else " ▾"
            clear.visibility = if (criteria.isEmpty()) View.GONE else View.VISIBLE
            fillResults(results)
        }

        content.addView(ui.chipRow((listOf<FungusStatus?>(null) + FungusStatus.entries).map { status ->
            ui.chip(getString(status?.label ?: R.string.myco_filter_all), false) {
                if (status == null) statuses.clear() else if (!statuses.remove(status)) statuses.add(status)
                refresh()
            }.also { statusChips.add(status to it) }
        }), ui.full(4))

        toggle.setOnClickListener {
            criteriaOpen = !criteriaOpen
            panel.visibility = if (criteriaOpen) View.VISIBLE else View.GONE
            refresh()
        }
        clear.setOnClickListener { criteria.clear(); refresh() }
        val head = ui.line()
        head.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(clear, LinearLayout.LayoutParams(-2, -2))
        content.addView(head, ui.full(10))

        for (group in CriterionGroup.entries) {
            val offered = FungusCriteria.offered[group] ?: continue
            panel.addView(ui.text(getString(group.title), 12f, secondary = true, bold = true), ui.full(10))
            panel.addView(ui.flow(offered.map { c ->
                ui.chip(getString(c.label, *c.labelArgs.toTypedArray()), false) {
                    if (!criteria.remove(c.id)) criteria.add(c.id)
                    refresh()
                }.also { criterionChips.add(c.id to it) }
            }), ui.full(4))
        }
        content.addView(panel, ui.full(0))
        content.addView(results, ui.full(6))
        refresh()
    }

    private fun fillResults(results: LinearLayout) {
        results.removeAllViews()
        val list = sortedSpecies().filter {
            (statuses.isEmpty() || it.status in statuses) &&
                (criteria.isEmpty() || (it.diagram == FungusDiagramKind.MUSHROOM && FungusCriteria.matches(it.look, criteria)))
        }
        results.addView(ui.note(resources.getQuantityString(R.plurals.myco_species_count, list.size, list.size)), ui.full())
        val tiles = list.map { sp ->
            val tile = ui.tile(1.1f) { open(Sheet.Species(sp.id)) }
            val thumb: View = if (sp.diagram == FungusDiagramKind.MUSHROOM) plate(sp.look, PlateMode.SIDE, compact = true)
                else FungusDiagramView(this, sp.diagram, sp.id)
            tile.image.addView(thumb, FrameLayout.LayoutParams(-1, -1))
            tile.title.text = getString(sp.name)
            tile.subtitle.text = getString(sp.latin)
            tile.badge(statusChip(sp.status, small = true))
            if (sp.saleBanned) tile.badge(ui.pill(getString(R.string.myco_sale_banned), 0xFF5B5B5B.toInt(), small = true))
            tile.view.contentDescription = getString(sp.name)
            tile.view
        }
        results.addView(ui.grid(tiles, ui.columns(contentWidth())), ui.full(6))
    }

    private fun renderChanged() {
        FungusCatalog.changes.forEach { change ->
            val sp = FungusCatalog.get(change.speciesId) ?: return@forEach
            val card = ui.card { open(Sheet.Species(sp.id)) }
            val line = ui.line().apply { gravity = Gravity.TOP }
            line.addView(plate(sp.look, PlateMode.SIDE, compact = true), LinearLayout.LayoutParams(ui.dp(84), ui.dp(112)))
            val col = ui.column()
            col.addView(ui.text(getString(sp.name), 17f, bold = true))
            col.addView(ui.latin(getString(sp.latin), 12.5f))
            val states = ui.line().apply { setPadding(0, ui.dp(6), 0, ui.dp(4)) }
            // L'ancien statut est barré et estompé : il n'a plus cours.
            states.addView(statusChip(change.before).apply { paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG; alpha = .55f })
            states.addView(ui.text("→", 16f, bold = true), LinearLayout.LayoutParams(-2, -2).apply { setMargins(ui.dp(8), 0, ui.dp(8), 0) })
            states.addView(statusChip(change.now))
            col.addView(states)
            col.addView(ui.text(change.years.joinToString(" · "), 12.5f, bold = true).apply { setTextColor(palette.ink(hue)) })
            col.addView(ui.text(getString(change.story), 13.5f), ui.full(4))
            line.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
            card.addView(line)
            content.addView(card, ui.full())
        }
    }

    // ------------------------------------------------------------------ fenêtres

    private fun open(sheet: Sheet, push: Boolean = true) {
        val previous = current
        if (previous != null && push && previous != sheet) history.add(previous)
        if (previous == null) history.clear()
        current = sheet
        val onBack: (() -> Unit)? = if (history.isNotEmpty()) ::back else null
        val found = when (sheet) {
            is Sheet.Species -> FungusCatalog.get(sheet.id)?.also { showSpecies(it, onBack) } != null
            is Sheet.Group -> FungusCatalog.groups.firstOrNull { it.id == sheet.id }?.also { fiche.show(getString(it.title), null, null, groupBody(it), onBack) } != null
            Sheet.About -> { fiche.show(getString(R.string.myco_about_title), null, null, aboutBody(), onBack); true }
        }
        if (!found) current = previous
    }

    private fun back() {
        val previous = history.removeLastOrNull() ?: return
        open(previous, push = false)
    }

    private fun restoreSheet(key: String) {
        val sheet = when {
            key == "about" -> Sheet.About
            key.startsWith("s:") -> Sheet.Species(key.removePrefix("s:"))
            key.startsWith("g:") -> Sheet.Group(key.removePrefix("g:"))
            else -> return
        }
        content.post { open(sheet, push = false) }
    }

    // ------------------------------------------------------------------ une espèce

    private fun topView(look: FungusLook) = look.capShape == CapShape.MOREL || look.capShape == CapShape.BRAIN || look.capShape == CapShape.BALL

    /** Le texte et la liste des planches se lisent hors du fil de l'interface ; la fiche s'ouvre ensuite. */
    private fun showSpecies(sp: FungusSpecies, onBack: (() -> Unit)?) {
        val sheet = current
        val language = resources.configuration.locales[0].language
        lifecycleScope.launch {
            val (article, herbarium) = withContext(Dispatchers.IO) {
                runCatching { MycologyArticleRepository.load(applicationContext, sp.id, language) }.getOrNull() to
                    runCatching { FungusHerbarium.hasPlate(applicationContext, sp.id) }.getOrDefault(false)
            }
            // Une autre fiche a été demandée entre-temps : celle-ci n'est plus voulue.
            if (current == sheet) showSpecies(sp, onBack, article, herbarium)
        }
    }

    /**
     * Une espèce en trois morceaux : l'identité et les liens, le dessin avec ses vues, puis les chapitres. Sur un
     * écran large le dessin a toute la hauteur à gauche et ne bouge pas ; sinon tout se suit dans une colonne.
     */
    private fun showSpecies(sp: FungusSpecies, onBack: (() -> Unit)?, article: MycologyArticle?, herbarium: Boolean) {
        val top = ui.column()
        val badges = mutableListOf<View>(statusChip(sp.status))
        if (sp.saleBanned) badges.add(ui.pill(getString(R.string.myco_sale_banned), 0xFF5B5B5B.toInt()))
        top.addView(ui.identity(getString(sp.latin), badges))
        getString(sp.altNames).takeIf { it.isNotBlank() }?.let { top.addView(ui.note(it), ui.full(4)) }
        val slot = ui.column()
        top.addView(slot)
        if (lifeIndex != null) classification(slot, sp.id) else treeSlot = sp.id to slot

        val media = ui.column()
        val drawing = ui.column()
        val frame: View = if (sp.diagram == FungusDiagramKind.MUSHROOM) {
            val plateView = plate(sp.look, mode, compact = false)
            plateView.showMarks = marks
            drawing.addView(modeBar(listOf(plateView), if (topView(sp.look)) R.string.myco_view_top else R.string.myco_view_under), ui.full(0))
            ui.hero(plateView, 1.1f, 480)
        } else {
            drawing.addView(ui.note(getString(R.string.myco_supplement_illustration)), ui.full(4))
            ui.hero(FungusDiagramView(this, sp.diagram, sp.id), 1.1f, 480)
        }
        drawing.addView(frame, if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(8) } else ui.full())
        if (herbarium) {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = getString(R.string.myco_herbarium_accessibility, getString(sp.name))
            }
            val photo = ui.hero(image, 1.1f, 480)
            val toggle = ui.action(getString(R.string.myco_show_drawing)) {}
            var showingDrawing = false
            toggle.setOnClickListener {
                showingDrawing = !showingDrawing
                photo.visibility = if (showingDrawing) View.GONE else View.VISIBLE
                drawing.visibility = if (showingDrawing) View.VISIBLE else View.GONE
                toggle.text = getString(if (showingDrawing) R.string.myco_show_herbarium else R.string.myco_show_drawing)
            }
            media.addView(toggle, ui.wrap(0))
            media.addView(photo, if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(8) } else ui.full())
            drawing.visibility = View.GONE
            media.addView(drawing, if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { FungusHerbarium.decode(applicationContext, sp.id) } }
                    .onSuccess { image.setImageBitmap(it) }
            }
        } else {
            media.addView(drawing, if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
        }

        val body = ui.column()
        if (article?.verified == false) body.addView(ui.note(getString(R.string.myco_article_pending)), ui.full(8))
        if (article != null && article.sections.isNotEmpty()) {
            article.sections.forEach { section -> body.addView(ui.chapter(section.title, hue, ui.paragraph(section.text)), ui.full(12)) }
        } else {
            val traits = ui.column()
            (article?.traits?.takeIf { it.isNotEmpty() } ?: resources.getStringArray(sp.traits).toList())
                .forEach { traits.addView(bullet(it), ui.full(4)) }
            body.addView(ui.chapter(getString(R.string.myco_sec_traits), hue, traits), ui.full(12))
        }
        // Le chapitre du danger prend la couleur du statut.
        body.addView(ui.chapter(getString(R.string.myco_sec_danger), sp.status.color,
            ui.paragraph(article?.safetyNote?.takeIf { it.isNotBlank() } ?: getString(sp.note))), ui.full(12))
        if (sp.history != 0) body.addView(ui.chapter(getString(R.string.myco_sec_history), palette.accent, ui.paragraph(getString(sp.history))), ui.full(12))

        val groups = FungusCatalog.groupsOf(sp.id)
        if (groups.isNotEmpty()) {
            body.addView(ui.section(getString(R.string.myco_sec_lookalikes)), ui.full(22))
            groups.forEach { group ->
                val others = group.members.filter { it != sp.id }.mapNotNull { FungusCatalog.get(it) }
                body.addView(tileStrip(others), ui.full(8))
                body.addView(ui.action(getString(group.title)) { open(Sheet.Group(group.id)) }, ui.wrap(8))
            }
        }
        article?.related?.filter { FungusCatalog.get(it) != null && groups.none { g -> it in g.members } }?.takeIf { it.isNotEmpty() }?.let { ids ->
            body.addView(ui.section(getString(R.string.science_see_also)), ui.full(22))
            body.addView(tileStrip(ids.mapNotNull { FungusCatalog.get(it) }), ui.full(8))
        }
        if (article != null && article.sources.isNotEmpty()) {
            val sources = ui.column()
            article.sources.forEach { source -> sources.addView(ui.link(source.title, source.url), ui.full(2)) }
            body.addView(ui.chapter(getString(R.string.myco_sec_references), palette.secondary, sources), ui.full(16))
        }
        fiche.show(getString(sp.name), top, media, body, onBack)
    }

    /** D'autres champignons en tuiles qu'on fait défiler de côté ; chacune ouvre sa fiche. */
    private fun tileStrip(species: List<FungusSpecies>): View {
        val row = ui.line().apply { gravity = Gravity.TOP }
        species.forEach { other ->
            val tile = ui.tile(1.1f) { open(Sheet.Species(other.id)) }
            val thumb: View = if (other.diagram == FungusDiagramKind.MUSHROOM) plate(other.look, PlateMode.SIDE, compact = true)
                else FungusDiagramView(this, other.diagram, other.id)
            tile.image.addView(thumb, FrameLayout.LayoutParams(-1, -1))
            tile.title.text = getString(other.name)
            tile.subtitle.text = getString(other.latin)
            tile.badge(statusChip(other.status, small = true))
            tile.view.contentDescription = getString(other.name)
            row.addView(tile.view, LinearLayout.LayoutParams(ui.dp(150), -2).apply { marginEnd = ui.dp(10) })
        }
        return ui.scrollRow(row)
    }

    private fun classification(slot: LinearLayout, speciesId: String) {
        val node = lifeIndex?.forSpecies(speciesId) ?: return
        val repo = lifeRepo ?: return
        slot.addView(ui.chipRow(listOf(ui.moduleChip(ScienceModule.TREE, {
            if (ScienceNavigation.isModuleLink(intent) && intent.getStringExtra(EXTRA_LIFE_NODE) == node) finish()
            else startActivity(Intent(this, ParentesActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                .putExtra(ParentesActivity.EXTRA_NODE_ID, node))
        }))), ui.full(12))
        slot.addView(ui.note(repo.tree.ancestors(node).filter { it in repo.groups }.reversed()
            .joinToString(getString(R.string.pt_separator)) { repo.name(this, it) }), ui.full(6))
    }

    // ------------------------------------------------------------------ un groupe de sosies

    private fun groupBody(group: FungusGroup): LinearLayout {
        val body = ui.column()
        body.addView(ui.pill(getString(group.evidence), palette.outline, small = true), LinearLayout.LayoutParams(-2, -2))
        val members = group.members.mapNotNull { FungusCatalog.get(it) }
        val looks = members.map { it.look }
        val avail = min(resources.displayMetrics.widthPixels, ui.dp(680)) - ui.dp(40)
        val cellW = (avail / members.size).coerceIn(ui.dp(150), ui.dp(300))
        val plates = ArrayList<FungusPlateView>()
        val row = ui.line().apply { gravity = Gravity.TOP }
        members.forEach { sp ->
            val col = ui.column().apply { isClickable = true; setOnClickListener { open(Sheet.Species(sp.id)) } }
            val view = plate(sp.look, mode, compact = false, shared = looks)
            view.showMarks = false
            plates.add(view)
            col.addView(ui.hero(view, 1.3f, 300), LinearLayout.LayoutParams(cellW, -2))
            col.addView(ui.text(getString(sp.name), 14f, bold = true).apply { gravity = Gravity.CENTER_HORIZONTAL }, LinearLayout.LayoutParams(cellW, -2).apply { topMargin = ui.dp(4) })
            col.addView(statusChip(sp.status), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.dp(2) })
            row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(8) })
        }
        body.addView(modeBar(plates, if (members.all { topView(it.look) }) R.string.myco_view_top else R.string.myco_view_under, withMarks = false), ui.full(10))
        body.addView(ui.scrollRow(row), ui.full(8))
        val points = ui.column()
        resources.getStringArray(group.points).forEach { points.addView(bullet(it), ui.full(4)) }
        body.addView(ui.chapter(getString(R.string.myco_sec_differences), hue, points), ui.full(18))
        return body
    }

    // ------------------------------------------------------------------ À propos, sources, urgence

    private fun aboutBody(): LinearLayout {
        val body = ui.column()
        val intro = ui.column()
        listOf(R.string.myco_about_intro, R.string.myco_about_lookalikes, R.string.myco_about_method,
            R.string.myco_about_drawings, R.string.myco_about_supplement)
            .forEachIndexed { index, text -> intro.addView(ui.paragraph(getString(text)), ui.full(if (index == 0) 0 else 10)) }
        body.addView(intro)
        body.addView(ui.chapter(getString(R.string.myco_about_rules_title), hue, ui.paragraph(getString(R.string.myco_about_rules))), ui.full(16))
        body.addView(ui.action(getString(R.string.myco_urgent)) { showUrgent() }, ui.wrap(14))
        body.addView(ui.paragraph(getString(R.string.science_navigation_help)), ui.full(16))

        body.addView(ui.section(getString(R.string.myco_about_sources)), ui.full(22))
        val sources = MycologySources.load(this)
        if (sources.isEmpty()) {
            body.addView(ui.text(getString(R.string.myco_sources_error), 14f), ui.full(6))
        } else {
            listOf(
                MycologySources.Kind.OFFICIAL to R.string.myco_sources_official,
                MycologySources.Kind.JOURNAL to R.string.myco_sources_journals,
                MycologySources.Kind.SOCIETY to R.string.myco_sources_society,
                MycologySources.Kind.ENCYCLOPEDIA to R.string.myco_sources_encyclopedia,
                MycologySources.Kind.TAXONOMY to R.string.myco_sources_taxonomy
            ).forEach { (kind, title) ->
                val list = ui.column()
                sources.filter { it.kind == kind }.forEach { list.addView(ui.link(it.citation, it.url), ui.full(4)) }
                if (kind == MycologySources.Kind.ENCYCLOPEDIA) list.addView(ui.note(getString(R.string.myco_sources_secondary)), ui.full(8))
                body.addView(ui.chapter(getString(title), palette.secondary, list), ui.full(12))
            }
            body.addView(ui.action(getString(R.string.myco_sources_by_species)) { showSpeciesSources() }, ui.wrap(16))
        }
        return body
    }

    private fun showSpeciesSources() {
        val list = sortedSpecies()
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this).setTitle(R.string.myco_sources_by_species)
            .setItems(list.map { getString(it.name) }.toTypedArray()) { _, index ->
                val sp = list[index]
                val body = ui.column().apply { setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8)) }
                body.addView(ui.latin(getString(sp.latin), 14f))
                MycologySources.forSpecies(this, sp.id).forEach { body.addView(ui.link(it.citation, it.url), ui.full(8)) }
                com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this).setTitle(sp.name).setView(ScrollView(this).apply { addView(body) })
                    .setPositiveButton(R.string.myco_close, null).show()
            }.setNegativeButton(R.string.myco_close, null).show()
    }

    private fun showUrgent() {
        val body = ui.column().apply { setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8)) }
        body.addView(ui.note(getString(R.string.myco_urgent_hint)))
        listOf(
            R.string.myco_urgent_fr to R.string.myco_tel_fr,
            R.string.myco_urgent_fr_vital to R.string.myco_tel_fr_vital,
            R.string.myco_urgent_be to R.string.myco_tel_be,
            R.string.myco_urgent_ch to R.string.myco_tel_ch,
            R.string.myco_urgent_eu to R.string.myco_tel_eu
        ).forEach { (name, tel) ->
            val number = getString(tel)
            val line = ui.line().apply {
                setPadding(0, ui.dp(12), 0, ui.dp(12)); isClickable = true; isFocusable = true
                background = ui.ripple(null)
                setOnClickListener { dial(number) }
            }
            line.addView(ui.text(getString(name)), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(ui.text(number, 19f, bold = true).apply { setTextColor(palette.ink(palette.accent)) })
            body.addView(line, ui.full(2))
        }
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this).setTitle(R.string.myco_urgent_title).setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.myco_close, null).show()
    }

    private fun dial(number: String) {
        try { startActivity(Intent(Intent.ACTION_DIAL, "tel:${number.filter { it.isDigit() }}".toUri())) }
        catch (_: ActivityNotFoundException) { /* pas de téléphone : rien ne se passe */ }
    }

    // ------------------------------------------------------------------ morceaux d'interface

    private fun plate(look: FungusLook, plateMode: PlateMode, compact: Boolean, shared: List<FungusLook>? = null) =
        FungusPlateView(this).also {
            it.look = look; it.mode = plateMode; it.compact = compact; it.sharedScaleWith = shared
        }

    /** Les trois vues d'une planche (et les repères) en puces : la vue choisie vaut pour toutes les planches. */
    private fun modeBar(plates: List<FungusPlateView>, underLabel: Int, withMarks: Boolean = true): View {
        val chips = ArrayList<Pair<PlateMode, TextView>>()
        val views = ArrayList<View>()
        PlateMode.entries.forEach { m ->
            val chip = ui.chip(getString(if (m == PlateMode.UNDER) underLabel else m.label), m == mode) {}
            chip.setOnClickListener {
                mode = m
                plates.forEach { p -> p.mode = m }
                chips.forEach { (mm, c) -> ui.styleChip(c, mm == mode) }
            }
            chips.add(m to chip); views.add(chip)
        }
        if (withMarks) {
            val marksChip = ui.chip(getString(R.string.myco_marks), marks) {}
            marksChip.setOnClickListener {
                marks = !marks
                plates.forEach { p -> p.showMarks = marks }
                ui.styleChip(marksChip, marks)
            }
            views.add(marksChip)
        }
        return ui.chipRow(views)
    }

    private fun statusChip(status: FungusStatus, small: Boolean = false) = ui.pill(getString(status.label), status.color, small)

    private fun bullet(text: String) = ui.line().apply {
        gravity = Gravity.TOP
        addView(ui.text("•", 15f, secondary = true), LinearLayout.LayoutParams(ui.dp(16), -2))
        addView(ui.text(text), LinearLayout.LayoutParams(0, -2, 1f))
    }

    // ------------------------------------------------------------------ cycle de vie

    override fun onPause() {
        prefs.edit().putString("tab", tab.name).putString("mode", mode.name).putBoolean("marks", marks)
            .putStringSet("statuses", statuses.mapTo(HashSet()) { it.name }).putStringSet("criteria", HashSet(criteria))
            .putBoolean("criteria_open", criteriaOpen).apply()
        super.onPause()
    }

    override fun onDestroy() { fiche.dismiss(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", tab.name)
        outState.putInt("scroll", scroll.scrollY)
        outState.putString("sheet", when (val c = current) {
            is Sheet.Species -> "s:${c.id}"
            is Sheet.Group -> "g:${c.id}"
            Sheet.About -> "about"
            null -> null
        })
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_LIFE_NODE = "mycology_life_node"
        const val EXTRA_SPECIES_ID = "mycology_species_id"
        private const val DANGER = 0xFFC62828.toInt()
    }
}
