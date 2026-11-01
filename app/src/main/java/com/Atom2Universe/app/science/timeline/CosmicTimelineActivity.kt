package com.Atom2Universe.app.science.timeline

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceModule
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceUi
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.solarsystem.SolarSystemActivity
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * La chronologie : la frise d'un chapitre (de l'Univers à l'histoire humaine), ses chapitres de lecture dans la
 * couleur de l'époque, ses événements en tuiles illustrées, et des fiches qui s'enchaînent dans la même fenêtre.
 */
class CosmicTimelineActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private val ui by lazy { ScienceUi(this, palette) }
    private val prefs by lazy { getSharedPreferences("cosmic_timeline", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var header: ScienceUi.Header
    private lateinit var navigator: TimelineNavigator
    private var path = listOf("universe")
    // La fiche ouverte : une seule fenêtre plein écran, avec l'historique de la flèche.
    private val fiche by lazy { ScienceFiche(this, palette, R.string.ct_back, R.string.ct_close) { currentSheet = null; sheetHistory.clear() } }
    private var currentSheet: (() -> Unit)? = null
    private val sheetHistory = ArrayList<() -> Unit>()
    private var historyRegion: HumanRegion? = null
    private var historyTopic = HistoryTopic.ALL
    private var locatedHistoryId: String? = null
    private lateinit var historyWindow: HumanTimeWindow
    private var historyExplorer: HumanHistoryExplorer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedPath = savedInstanceState?.getStringArrayList("path") ?: prefs.getString("path_v2", null)?.split('|')
        path = TimelineChapters.restore(savedPath)
            ?: TimelineChapters.canonical(prefs.getString("period", null) ?: "universe")
        val regionName = if (savedInstanceState != null) savedInstanceState.getString("history_region") else prefs.getString("history_region", null)
        historyRegion = HumanRegion.entries.firstOrNull { it.name == regionName }
        val topicName = if (savedInstanceState != null) savedInstanceState.getString("history_topic") else prefs.getString("history_topic", null)
        historyTopic = HistoryTopic.entries.firstOrNull { it.name == topicName } ?: HistoryTopic.ALL
        locatedHistoryId = if (savedInstanceState != null) savedInstanceState.getString("located_history") else prefs.getString("located_history", null)
        val oldHuman = TimelineChapters.get(path.last())?.takeIf { it.human != null }
        historyWindow = HumanTimeWindow()
        oldHuman?.takeUnless { it.id == "human" }?.human?.let {
            historyWindow.focus(it.first.year.toDouble(), it.last.year.toDouble())
        }
        if (savedInstanceState?.containsKey("history_start") == true) {
            historyWindow.set(savedInstanceState.getDouble("history_start"), savedInstanceState.getDouble("history_span"))
        } else if (prefs.contains("history_start")) {
            historyWindow.set(Double.fromBits(prefs.getLong("history_start", 0)), Double.fromBits(prefs.getLong("history_span", 0)))
        }
        if (savedInstanceState == null && !prefs.getBoolean("history_recent_entry_v1", false) &&
            oldHuman?.id == "human" && locatedHistoryId == null && !historyWindow.isRecent) {
            historyWindow.recentOverview()
        }
        if (oldHuman != null) path = TimelineChapters.navigate(path, TimelineChapters.humanWindow(historyWindow).id)
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToParent()
        })
        render()
        if (savedInstanceState == null) {
            openLinkedContent(intent)
        } else {
            val y = savedInstanceState.getInt("scroll")
            scroll.post { scroll.scrollTo(0, y) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        openLinkedContent(intent)
    }

    private fun openLinkedContent(intent: Intent) {
        TimelineChapters.get(intent.getStringExtra(EXTRA_CHAPTER_ID))?.let {
            navigate(it.id); return
        }
        LifeTimeline.get(intent.getStringExtra(EXTRA_LIFE_ID))?.let {
            navigate(it.periodId); return
        }
        CosmicTimeline.event(intent.getStringExtra(EXTRA_EVENT_ID))?.let { event ->
            navigate(TimelineChapters.chapterFor(event).id); showEvent(event)
        }
    }

    private fun buildUi() {
        header = ui.header(getString(R.string.ct_title), ScienceModule.TIMELINE, onBack = { returnToParent() }, onHome = { navigate("universe") },
            actions = listOf(
                ScienceUi.HeaderAction(R.drawable.ic_search, R.string.ct_catalog) { catalog() },
                ScienceUi.HeaderAction(R.drawable.ic_sci_help, R.string.ct_about) { about() }
            ))
        val column = ui.column()
        navigator = TimelineNavigator(this, ::navigate, ::chooseScale, ::zoomOut, ::zoomIn)
        content = ui.column().apply { setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(24)) }
        scroll = ScrollView(this).apply {
            isFillViewport = true; clipToPadding = false
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        // L'histoire humaine se navigue dans sa propre frise ; les chapitres cosmiques gardent leur navigateur.
        column.addView(navigator, LinearLayout.LayoutParams(-1, -2).apply { setMargins(ui.dp(4), 0, ui.dp(4), 0) })
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(ui.screen(header, column, maxWidthDp = 1100))
    }

    private fun contentWidth() = (if (scroll.width > 0) scroll.width else min(resources.displayMetrics.widthPixels, ui.dp(1100))) - ui.dp(32)

    private fun render() {
        historyExplorer?.timeline?.stopMotion()
        historyExplorer = null
        content.removeAllViews()
        val chapters = path.map { requireNotNull(TimelineChapters.get(it)) }
        val current = chapters.last()
        updateNavigation()
        header.back.tooltipText = null
        if (current.human != null) {
            historyExplorer = HumanHistoryExplorer(this, historyWindow, historyRegion, historyTopic, locatedHistoryId,
                { region, topic, selected ->
                    historyRegion = region; historyTopic = topic; locatedHistoryId = selected
                    path = TimelineChapters.navigate(path, TimelineChapters.humanWindow(historyWindow).id)
                    updateNavigation()
                }, ::chooseScale, ::showHumanPeriod).also { content.addView(it) }
            return
        }
        val hue = palette.mark(current.color)
        content.addView(CosmicTimelineView(this, current, null, ::navigate), LinearLayout.LayoutParams(-1, -2))
        if (TimelineChapters.zoomTargets(current).isNotEmpty()) content.addView(ui.note(getString(R.string.ct_explorer_hint)), ui.full(8))
        content.addView(ui.note(getString(R.string.ct_dates_key)), ui.full(6))
        // Le chapitre d'introduction, dans la couleur de l'époque, puis ses compléments de lecture.
        content.addView(ui.chapter(getString(current.title), hue, ui.paragraph(getString(TimelineReading.intro(current)))), ui.full(14))
        LifeTimeline.get(current.datedAncestorId)?.let { date ->
            val estimate = ui.column()
            estimate.addView(ui.paragraph(getString(date.note)))
            estimate.addView(ui.chipRow(listOf(ui.moduleChip(ScienceModule.TREE, {
                val target = Intent(this, ParentesActivity::class.java)
                    .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(ParentesActivity.EXTRA_NODE_ID, date.nodeId)
                    .putExtra(EXTRA_LIFE_ID, date.id)
                if (intent.getStringExtra(EXTRA_LIFE_ID) == date.id) {
                    target.putExtra(ParentesActivity.EXTRA_FIRST_ID, intent.getStringExtra(ParentesActivity.EXTRA_FIRST_ID))
                    target.putExtra(ParentesActivity.EXTRA_SECOND_ID, intent.getStringExtra(ParentesActivity.EXTRA_SECOND_ID))
                }
                startActivity(target)
            }))), ui.full(12))
            content.addView(ui.chapter(getString(R.string.ct_life_estimate), ScienceModule.TREE.color(palette), estimate), ui.full(12))
        }
        TimelineReading.sections(current.id).forEach { (heading, text) ->
            content.addView(ui.chapter(getString(heading), hue, ui.paragraph(getString(text))), ui.full(12))
        }
        if (current.events.isNotEmpty()) {
            content.addView(ui.section(getString(R.string.ct_events_in_period)), ui.full(22))
            content.addView(ui.grid(current.events.map { eventTile(it) }, ui.columns(contentWidth())), ui.full(10))
        }
        current.geology?.let { geology ->
            val precision = ui.column()
            precision.addView(ui.text(geology.precision(this), 14f))
            precision.addView(ui.paragraph(getString(if (current.id == "hadean") R.string.ct_geo_hadean_precision else R.string.ct_geo_precision)), ui.full(8))
            content.addView(ui.chapter(getString(R.string.ct_precision), palette.secondary, precision), ui.full(16))
        }
        val lifeDates = LifeTimeline.inPeriod(current)
        if (lifeDates.isNotEmpty()) {
            val landmarks = ui.column()
            landmarks.addView(ui.note(getString(R.string.ct_life_landmarks_hint)))
            landmarks.addView(ui.flow(lifeDates.map { date ->
                ui.chip(getString(R.string.ct_catalog_entry, getString(date.title), date.dateLabel(this)), false) { navigate(date.periodId) }
            }), ui.full(10))
            content.addView(ui.chapter(getString(R.string.ct_life_landmarks), ScienceModule.TREE.color(palette), landmarks), ui.full(12))
        }
    }

    /** Une tuile d'événement : son illustration, son titre, sa date. */
    private fun eventTile(event: CosmicEvent): View {
        val tile = ui.tile(.62f) { showEvent(event) }
        tile.image.addView(CosmicIllustrationView(this, event.art), FrameLayout.LayoutParams(-1, -1))
        tile.title.text = getString(event.title)
        tile.subtitle.text = getString(event.date)
        tile.subtitle.setTypeface(tile.subtitle.typeface, android.graphics.Typeface.NORMAL)
        tile.view.contentDescription = getString(R.string.ct_catalog_entry, getString(event.title), getString(event.date))
        return tile.view
    }

    private fun navigate(id: String) {
        navigateExact(if (id == "human") TimelineChapters.recentHistory.id else id)
    }

    private fun navigateExact(id: String) {
        locatedHistoryId = null
        applyNavigation(id)
    }

    private fun applyNavigation(id: String) {
        fiche.dismiss()
        val human = TimelineChapters.get(id)?.human
        if (human != null) {
            historyWindow.focus(human.first.year.toDouble(), human.last.year.toDouble())
        }
        path = TimelineChapters.navigate(path, id)
        render(); scroll.scrollTo(0, 0)
        content.announceForAccessibility(getString(requireNotNull(TimelineChapters.get(path.last())).title))
    }

    private fun returnToParent() {
        when {
            fiche.isShowing -> fiche.dismiss()
            ScienceNavigation.isModuleLink(intent) -> finish()
            path.size > 1 -> zoomOut()
            else -> finish()
        }
    }

    private fun updateNavigation() {
        val current = requireNotNull(TimelineChapters.get(path.last()))
        navigator.visibility = if (current.human == null) View.VISIBLE else View.GONE
        if (current.human == null) navigator.bind(path)
        header.back.contentDescription = if (ScienceNavigation.isModuleLink(intent)) getString(R.string.science_back_to_previous_module)
            else if (path.size > 1) getString(R.string.ct_history_zoom_out) else getString(R.string.ct_back)
    }

    private fun zoomOut() {
        TimelineChapters.wider(path, historyWindow)?.let { navigateExact(it.id) }
    }

    private fun changeHumanWindow(change: HumanTimeWindow.() -> Unit) {
        historyExplorer?.changeWindow(change)
        scroll.scrollTo(0, 0)
    }

    private fun zoomIn(anchor: View) {
        val current = requireNotNull(TimelineChapters.get(path.last()))
        val targets = TimelineChapters.zoomTargets(current)
        when {
            targets.size == 1 -> navigate(targets.single().id)
            targets.isNotEmpty() -> periodMenu(anchor, targets)
            current.human != null -> { historyExplorer?.zoomIn(); scroll.scrollTo(0, 0) }
        }
    }

    private fun periodMenu(anchor: View, periods: List<CosmicPeriod>) {
        com.Atom2Universe.app.util.ImmersivePopupMenu(this, anchor).apply {
            periods.forEachIndexed { index, period ->
                menu.add(0, index, index, periodMenuLabel(period))
            }
            setOnMenuItemClickListener { navigateExact(periods[it.itemId].id); true }
            show()
        }
    }

    private fun periodMenuLabel(period: CosmicPeriod): String {
        val dates = TimelineDates(this)
        return getString(R.string.ct_catalog_entry, getString(period.title),
            getString(R.string.ct_range, dates.edge(period, true), dates.edge(period, false)))
    }

    private fun chooseScale(anchor: View) {
        val current = requireNotNull(TimelineChapters.get(path.last()))
        com.Atom2Universe.app.util.ImmersivePopupMenu(this, anchor).apply {
            val choices = mutableListOf<CosmicPeriod>()
            if (current.human != null) {
                menu.add(1, 0, 0, R.string.ct_history_zoom_in).isEnabled = historyWindow.span > HumanTimeWindow.MIN_SPAN
                menu.add(1, 1, 0, R.string.ct_history_zoom_out).isEnabled = TimelineChapters.wider(path, historyWindow) != null
                menu.add(1, 2, 0, R.string.ct_explorer_recent)
            }
            fun group(title: Int, periods: List<CosmicPeriod>) {
                if (periods.isEmpty()) return
                val submenu = menu.addSubMenu(title)
                periods.forEach { period ->
                    val index = choices.size; choices.add(period)
                    submenu.add(0, index, index, periodMenuLabel(period))
                }
            }
            group(R.string.ct_explorer_wider, path.dropLast(1).reversed().mapNotNull(TimelineChapters::get))
            val (before, after) = TimelineChapters.neighbours(current, historyWindow.takeIf { current.human != null })
            group(R.string.ct_explorer_neighbours, listOfNotNull(before, current, after))
            group(R.string.ct_explorer_closer, TimelineChapters.zoomTargets(current))
            setOnMenuItemClickListener { item ->
                when {
                    item.hasSubMenu() -> false
                    item.groupId == 1 -> {
                        when (item.itemId) {
                            0 -> changeHumanWindow { zoom(2.0) }
                            1 -> zoomOut()
                            2 -> changeHumanWindow { recentOverview() }
                        }
                        true
                    }
                    else -> { navigateExact(choices[item.itemId].id); true }
                }
            }
            show()
        }
    }

    // ------------------------------------------------------------------ les fiches

    /** Montre une fiche ; celle d'avant va dans l'historique de la flèche, sauf quand on y revient ([push] faux). */
    private fun present(push: Boolean = true, build: (onBack: (() -> Unit)?) -> Unit) {
        historyExplorer?.timeline?.stopMotion()
        val previous = currentSheet
        if (push && previous != null) sheetHistory.add(previous)
        val self: () -> Unit = { present(false, build) }
        currentSheet = self
        build(if (sheetHistory.isEmpty()) null else ({ sheetHistory.removeAt(sheetHistory.lastIndex)() }))
    }

    /** Le catalogue : un champ de recherche, puis les périodes, repères du vivant, événements et repères humains. */
    private fun catalog(): Unit = present { back ->
        val body = ui.column()
        val list = ui.column()
        // Les textes sont normalisés et les lignes construites une fois ; une frappe ne fait que montrer ou cacher.
        val empty = ui.note(getString(R.string.ct_search_empty))
        list.addView(empty, ui.full(8))
        val sections = mutableListOf<Pair<View, List<Pair<View, String>>>>()
        fun section(title: Int, rows: List<Pair<View, String>>) {
            val head = ui.section(getString(title))
            list.addView(head, ui.full(20))
            rows.forEach { (view, _) -> list.addView(view, ui.full(2)) }
            sections += head to rows
        }
        fun row(name: String, detail: String?, action: () -> Unit): View {
            val line = ui.line().apply {
                background = ui.ripple(null); isClickable = true; isFocusable = true
                setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10)); setOnClickListener { action() }
            }
            val texts = ui.column()
            texts.addView(ui.text(name, 15f, bold = true))
            detail?.let { texts.addView(ui.note(it), ui.full(1)) }
            line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            return line
        }
        val chapters = TimelineChapters.all.map { it to normalize(getString(it.title) + " " + getString(it.description)) }
        section(R.string.ct_periods, chapters.filter { it.first.datedAncestorId == null }.map { (chapter, key) ->
            row(getString(chapter.title), null) { navigate(chapter.id) } to key
        })
        section(R.string.ct_life_landmarks, chapters.filter { it.first.datedAncestorId != null }.map { (chapter, key) ->
            row(getString(chapter.title), chapter.dateLabel(this)) { navigate(chapter.id) } to key
        })
        section(R.string.ct_events, CosmicTimeline.events.map { event ->
            row(getString(event.title), getString(event.date)) { showEvent(event) } to normalize(getString(event.title) + " " + getString(event.summary))
        })
        section(R.string.ct_history_landmarks, HumanHistory.entries.map { entry ->
            row(getString(entry.title), entry.dateLabel(this)) { focusHumanEntries(listOf(entry)) } to normalize(entry.indexedText(this))
        })
        fun results(query: String) {
            val normalized = normalize(query)
            var any = false
            for ((head, rows) in sections) {
                var shown = false
                for ((view, key) in rows) {
                    val match = key.contains(normalized)
                    view.visibility = if (match) View.VISIBLE else View.GONE
                    shown = shown || match
                }
                head.visibility = if (shown) View.VISIBLE else View.GONE
                any = any || shown
            }
            empty.visibility = if (any) View.GONE else View.VISIBLE
        }
        body.addView(ui.searchField(getString(R.string.ct_search_hint), "") { results(it) }.view, ui.full(4))
        body.addView(list)
        results("")
        fiche.show(getString(R.string.ct_catalog), null, null, body, back)
    }

    /** La fiche d'un événement : son illustration en tête, ses chapitres, puis l'événement d'avant et d'après. */
    private fun showEvent(event: CosmicEvent): Unit = present { back ->
        val hue = palette.mark(event.art.color)
        val top = ui.column()
        top.addView(ui.identity(null, listOf(ui.pill(getString(event.date), palette.categorySurface(hue)))))
        val media = ui.column()
        media.addView(ui.hero(CosmicIllustrationView(this, event.art), .6f), if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else ui.full())
        val body = ui.column()
        val story = ui.column()
        story.addView(ui.text(getString(event.summary), 16f, bold = true))
        story.addView(ui.paragraph(getString(event.detail)), ui.full(8))
        body.addView(ui.chapter(getString(event.title), hue, story), ui.full(4))
        body.addView(ui.chapter(getString(R.string.ct_precision), palette.secondary, ui.paragraph(getString(event.precision))), ui.full(12))
        val actions = mutableListOf<View>(ui.action(getString(R.string.ct_locate)) { navigate(TimelineChapters.chapterFor(event).id) })
        if (event.id in listOf("sun", "earth", "moon")) actions += ui.action(getString(R.string.ct_open_solar)) { startActivity(Intent(this, SolarSystemActivity::class.java)) }
        body.addView(ui.flow(actions), ui.full(16))
        if (event.sources.isNotEmpty()) {
            val sources = ui.column()
            event.sources.forEach { source -> sources.addView(ui.link(getString(source.label), source.url), ui.full(2)) }
            body.addView(ui.chapter(getString(R.string.ct_sources), palette.secondary, sources), ui.full(12))
        }
        val index = CosmicTimeline.events.indexOf(event)
        val previous = CosmicTimeline.events.getOrNull(index - 1)
        val next = CosmicTimeline.events.getOrNull(index + 1)
        body.addView(ui.section(getString(R.string.science_see_also)), ui.full(22))
        body.addView(ui.scrollRow(ui.line().apply {
            gravity = Gravity.TOP
            listOfNotNull(previous, next).forEach { other -> addView(eventTile(other), LinearLayout.LayoutParams(ui.dp(170), -2).apply { marginEnd = ui.dp(10) }) }
        }), ui.full(8))
        fiche.show(getString(event.title), top, media, body, back)
    }

    private fun about(): Unit = present { back ->
        val body = ui.column()
        body.addView(ui.paragraph(getString(R.string.ct_about_text)), ui.full(4))
        val hue = ScienceModule.TIMELINE.color(palette)
        val reading = ui.column()
        reading.addView(ui.paragraph(getString(R.string.ct_units_explanation)))
        reading.addView(ui.paragraph(getString(R.string.ct_geo_schematic)), ui.full(8))
        reading.addView(ui.paragraph(getString(R.string.ct_scope, HumanHistory.periods.size, HumanHistory.entries.size)), ui.full(8))
        body.addView(ui.chapter(getString(R.string.ct_about), hue, reading), ui.full(12))
        val navigation = ui.column()
        navigation.addView(ui.paragraph(getString(R.string.ct_history_navigation_help)))
        navigation.addView(ui.paragraph(getString(R.string.science_navigation_help)), ui.full(8))
        body.addView(ui.chapter(getString(R.string.science_help), hue, navigation), ui.full(12))
        val method = ui.column()
        method.addView(ui.paragraph(getString(R.string.ct_history_dates_key)))
        method.addView(ui.paragraph(getString(R.string.ct_history_method)), ui.full(8))
        method.addView(ui.paragraph(getString(R.string.ct_science_method)), ui.full(8))
        body.addView(ui.chapter(getString(R.string.ct_precision), palette.secondary, method), ui.full(12))
        fiche.show(getString(R.string.ct_about), null, null, body, back)
    }

    /** Depuis le catalogue : la frise se place sur le repère, dont la fiche s'affiche dessous. */
    private fun focusHumanEntries(entries: List<HumanLandmark>) {
        fiche.dismiss()
        locatedHistoryId = entries.singleOrNull()?.id
        if (historyExplorer == null) applyNavigation("human")
        historyExplorer?.focus(entries)
        scroll.scrollTo(0, 0)
    }

    private fun showHumanPeriod(period: CosmicPeriod): Unit = present { back ->
        val hue = palette.mark(period.color)
        val top = ui.column()
        top.addView(ui.identity(null, listOf(ui.pill(requireNotNull(period.human).label(this), palette.categorySurface(hue))),
            listOf(ui.moduleChip(ScienceModule.TREE, {
                startActivity(Intent(this, ParentesActivity::class.java)
                    .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(ParentesActivity.EXTRA_NODE_ID, "ott770315"))
            }, getString(R.string.ct_history_open_species)))))
        val media = ui.column()
        media.addView(ui.hero(object : View(this) {
            private val art = HumanTimelineArt()
            override fun onDraw(canvas: android.graphics.Canvas) {
                art.draw(canvas, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), period, palette)
            }
        }, .4f), if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else ui.full())
        val body = ui.column()
        body.addView(ui.chapter(getString(period.title), hue, ui.paragraph(getString(period.description))), ui.full(4))
        body.addView(ui.flow(listOf(ui.action(getString(R.string.ct_locate)) {
            fiche.dismiss(); historyExplorer?.focus(period); scroll.scrollTo(0, 0)
        })), ui.full(16))
        fiche.show(getString(period.title), top, media, body, back)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("path", ArrayList(path))
        outState.putInt("scroll", scroll.scrollY)
        outState.putString("history_region", historyRegion?.name)
        outState.putString("history_topic", historyTopic.name)
        outState.putString("located_history", locatedHistoryId)
        outState.putDouble("history_start", historyWindow.start); outState.putDouble("history_span", historyWindow.span)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        historyExplorer?.timeline?.stopMotion()
        prefs.edit().putString("path_v2", path.joinToString("|"))
            .putBoolean("history_recent_entry_v1", true)
            .putString("history_region", historyRegion?.name).putString("located_history", locatedHistoryId)
            .putString("history_topic", historyTopic.name)
            .putLong("history_start", historyWindow.start.toBits()).putLong("history_span", historyWindow.span.toBits()).apply()
        super.onStop()
    }
    override fun onDestroy() { fiche.dismiss(); super.onDestroy() }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)

    companion object {
        private val combiningMarks = Regex("\\p{M}+")
        /** Liens internes stables vers des nœuds datés et vérifiés de l'arbre. */
        const val EXTRA_EVENT_ID = "cosmic_event_id"
        const val EXTRA_CHAPTER_ID = "cosmic_chapter_id"
        const val EXTRA_LIFE_ID = "dated_life_id"
    }
}
