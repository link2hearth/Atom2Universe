package com.Atom2Universe.app.science.timeline

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.solarsystem.SolarSystemActivity
import java.text.Normalizer
import java.util.Locale

class CosmicTimelineActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private val prefs by lazy { getSharedPreferences("cosmic_timeline", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var back: ImageButton
    private lateinit var navigator: TimelineNavigator
    private var path = listOf("universe")
    private val expanded = mutableSetOf<String>()
    // La fiche ouverte : une seule fenêtre plein écran. Pas de retour entre fiches ici : la flèche ferme.
    private val fiche by lazy { ScienceFiche(this, palette, R.string.ct_close, R.string.ct_close) {} }
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
        expanded.addAll(savedInstanceState?.getStringArrayList("expanded") ?: prefs.getStringSet("expanded_v2", emptySet()).orEmpty())
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
        val root = column().apply { setBackgroundColor(palette.background) }
        val toolbar = row()
        back = icon(R.drawable.ic_arrow_back_24, R.string.ct_back) { returnToParent() }
        ScienceNavigation.bindHomeAction(back) { navigate("universe") }
        toolbar.addView(back, square())
        toolbar.addView(label(getString(R.string.ct_title), 20f, true), LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(icon(R.drawable.ic_search, R.string.ct_catalog) { catalog() }, square())
        toolbar.addView(icon(R.drawable.ic_more_vert_24, R.string.ct_about) { about() }, square())
        root.addView(toolbar)
        navigator = TimelineNavigator(this, ::navigate, ::chooseScale, ::zoomOut, ::zoomIn)
        content = column().apply { setPadding(dp(12), dp(6), dp(12), dp(20)) }
        scroll = ScrollView(this).apply {
            isFillViewport = true; clipToPadding = false
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        // Human navigation lives inside its chart; cosmic chapters retain their wider-scale navigator.
        root.addView(navigator, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun render() {
        historyExplorer?.timeline?.stopMotion()
        historyExplorer = null
        content.removeAllViews()
        val chapters = path.map { requireNotNull(TimelineChapters.get(it)) }
        val current = chapters.last()
        updateNavigation()
        back.tooltipText = null
        if (current.human != null) {
            historyExplorer = HumanHistoryExplorer(this, historyWindow, historyRegion, historyTopic, locatedHistoryId,
                { region, topic, selected ->
                    historyRegion = region; historyTopic = topic; locatedHistoryId = selected
                    path = TimelineChapters.navigate(path, TimelineChapters.humanWindow(historyWindow).id)
                    updateNavigation()
                }, ::chooseScale, ::showHumanPeriod).also { content.addView(it) }
            return
        }
        content.addView(CosmicTimelineView(this, current, null, ::navigate), LinearLayout.LayoutParams(-1, -2))
        if (TimelineChapters.zoomTargets(current).isNotEmpty()) content.addView(paragraph(R.string.ct_explorer_hint))
        content.addView(label(getString(R.string.ct_dates_key), 11f).apply {
            setTextColor(palette.secondary); setPadding(dp(4), dp(12), dp(4), dp(4))
        })
        content.addView(paragraph(TimelineReading.intro(current)))
        LifeTimeline.get(current.datedAncestorId)?.let { date ->
            content.addView(paragraph(R.string.ct_life_estimate, true))
            content.addView(paragraph(date.note))
            content.addView(button(R.string.ct_open_life_tree) {
                val target = Intent(this, ParentesActivity::class.java)
                    .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(ParentesActivity.EXTRA_NODE_ID, date.nodeId)
                    .putExtra(EXTRA_LIFE_ID, date.id)
                if (intent.getStringExtra(EXTRA_LIFE_ID) == date.id) {
                    target.putExtra(ParentesActivity.EXTRA_FIRST_ID, intent.getStringExtra(ParentesActivity.EXTRA_FIRST_ID))
                    target.putExtra(ParentesActivity.EXTRA_SECOND_ID, intent.getStringExtra(ParentesActivity.EXTRA_SECOND_ID))
                }
                startActivity(target)
            }, fullRow())
        }
        val sections = TimelineReading.sections(current.id)
        if (sections.isNotEmpty()) disclosure("reading:${current.id}", R.string.ct_read_more) { body ->
            sections.forEach { (heading, text) ->
                body.addView(paragraph(heading, true)); body.addView(paragraph(text))
            }
        }
        if (current.events.isNotEmpty()) disclosure("events:${current.id}", R.string.ct_events_in_period) { body ->
            current.events.forEach { body.addView(eventButton(it), fullRow()) }
        }
        current.geology?.let { geology ->
            disclosure("precision:${current.id}", R.string.ct_precision) { body ->
                body.addView(label(geology.precision(this), 14f).apply { setPadding(0, dp(10), 0, dp(4)) })
                body.addView(paragraph(if (current.id == "hadean") R.string.ct_geo_hadean_precision else R.string.ct_geo_precision))
            }
        }
        val lifeDates = LifeTimeline.inPeriod(current)
        if (lifeDates.isNotEmpty()) disclosure("life:${current.id}", R.string.ct_life_landmarks) { body ->
            body.addView(paragraph(R.string.ct_life_landmarks_hint))
            lifeDates.forEach { date ->
                body.addView(button(date.title) { navigate(date.periodId) }.apply {
                    text = getString(R.string.ct_catalog_entry, getString(date.title), date.dateLabel(this@CosmicTimelineActivity))
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }, fullRow())
            }
        }
    }

    private fun navigationRow(previous: Int?, next: Int?, onPrevious: () -> Unit, onNext: () -> Unit): LinearLayout {
        val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
        listOf(Triple(R.string.ct_previous, previous, onPrevious), Triple(R.string.ct_next, next, onNext))
            .forEachIndexed { index, (label, destination, action) ->
                line.addView(button(label, action).apply {
                    isEnabled = destination != null; alpha = if (isEnabled) 1f else .35f
                    if (destination != null) text = getString(R.string.ct_navigation_destination, getString(label), getString(destination))
                    textSize = 12f
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        if (index == 0) R.drawable.ic_chevron_left else 0, 0,
                        if (index == 1) R.drawable.ic_chevron_right else 0, 0)
                    compoundDrawableTintList = ColorStateList.valueOf(palette.secondary)
                    compoundDrawablePadding = dp(4)
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(6) })
        }
        return line
    }

    private fun disclosure(key: String, title: Int, fill: (LinearLayout) -> Unit) {
        val body = column().apply { visibility = if (key in expanded) View.VISIBLE else View.GONE }
        fill(body)
        val toggle = button(title) { }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; background = null }
        fun update() {
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                if (key in expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0)
            toggle.compoundDrawableTintList = ColorStateList.valueOf(palette.secondary)
            ViewCompat.setStateDescription(toggle, getString(if (key in expanded) R.string.ct_expanded else R.string.ct_collapsed))
        }
        toggle.setOnClickListener {
            if (!expanded.add(key)) expanded.remove(key)
            body.visibility = if (key in expanded) View.VISIBLE else View.GONE
            update()
        }
        update(); content.addView(toggle, fullRow()); content.addView(body)
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
            ScienceNavigation.isModuleLink(intent) -> finish()
            path.size > 1 -> zoomOut()
            else -> finish()
        }
    }

    private fun updateNavigation() {
        val current = requireNotNull(TimelineChapters.get(path.last()))
        navigator.visibility = if (current.human == null) View.VISIBLE else View.GONE
        if (current.human == null) navigator.bind(path)
        back.contentDescription = if (ScienceNavigation.isModuleLink(intent)) getString(R.string.science_back_to_previous_module)
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

    private fun catalog() {
        val body = column()
        val input = EditText(this).apply {
            setHint(R.string.ct_search_hint); setSingleLine(true); textSize = 16f
            setTextColor(palette.text); setHintTextColor(palette.secondary)
        }
        body.addView(input, fullRow())
        val list = column(); body.addView(list)
        fun results(query: String) {
            list.removeAllViews()
            val normalized = normalize(query)
            val matches = TimelineChapters.all.filter { normalize(getString(it.title) + " " + getString(it.description)).contains(normalized) }
            val chapters = matches.filter { it.datedAncestorId == null }
            val ancestors = matches.filter { it.datedAncestorId != null }
            val events = CosmicTimeline.events.filter { normalize(getString(it.title) + " " + getString(it.summary)).contains(normalized) }
            val history = HumanHistory.entries.filter { normalize(it.indexedText(this)).contains(normalized) }
            if (matches.isEmpty() && events.isEmpty() && history.isEmpty()) list.addView(paragraph(R.string.ct_search_empty))
            if (chapters.isNotEmpty()) list.addView(paragraph(R.string.ct_periods, true))
            chapters.forEach { chapter -> list.addView(button(chapter.title) { navigate(chapter.id) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }, fullRow()) }
            if (ancestors.isNotEmpty()) list.addView(paragraph(R.string.ct_life_landmarks, true))
            ancestors.forEach { chapter -> list.addView(button(chapter.title) { navigate(chapter.id) }.apply {
                text = getString(R.string.ct_catalog_entry, getString(chapter.title), chapter.dateLabel(this@CosmicTimelineActivity))
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }, fullRow()) }
            if (events.isNotEmpty()) list.addView(paragraph(R.string.ct_events, true))
            events.forEach { list.addView(eventButton(it), fullRow()) }
            if (history.isNotEmpty()) list.addView(paragraph(R.string.ct_history_landmarks, true))
            history.forEach { entry -> list.addView(button(entry.title) { focusHumanEntries(listOf(entry)) }.apply {
                text = getString(R.string.ct_catalog_entry, getString(entry.title), entry.dateLabel(this@CosmicTimelineActivity))
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }, fullRow()) }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { results(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        results(""); sheet(getString(R.string.ct_catalog), body)
    }

    private fun eventButton(event: CosmicEvent) = button(event.title) { showEvent(event) }.apply {
        text = getString(R.string.ct_catalog_entry, getString(event.title), getString(event.date))
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun showEvent(event: CosmicEvent) {
        val body = column()
        body.addView(CosmicIllustrationView(this, event.art), LinearLayout.LayoutParams(-1, dp(128)))
        body.addView(label(getString(event.date), 12f).apply {
            setTextColor(palette.secondary); setPadding(0, dp(16), 0, dp(6))
        })
        body.addView(paragraph(event.summary, true)); body.addView(paragraph(event.detail))
        body.addView(paragraph(R.string.ct_precision, true))
        body.addView(paragraph(event.precision).apply { setTextColor(palette.secondary) })
        body.addView(button(R.string.ct_locate) { navigate(TimelineChapters.chapterFor(event).id) }, fullRow())
        if (event.id in listOf("sun", "earth", "moon")) {
            body.addView(button(R.string.ct_open_solar) { startActivity(Intent(this, SolarSystemActivity::class.java)) }, fullRow())
        }
        val index = CosmicTimeline.events.indexOf(event)
        val previous = CosmicTimeline.events.getOrNull(index - 1)
        val next = CosmicTimeline.events.getOrNull(index + 1)
        body.addView(navigationRow(previous?.title, next?.title,
            { previous?.let(::showEvent) }, { next?.let(::showEvent) }), fullRow())
        sheet(getString(event.title), body)
    }

    private fun about() {
        val body = column()
        body.addView(paragraph(R.string.ct_about_text)); body.addView(paragraph(R.string.ct_units_explanation))
        body.addView(paragraph(R.string.ct_geo_schematic))
        body.addView(paragraph(R.string.ct_scope, false, HumanHistory.periods.size, HumanHistory.entries.size))
        body.addView(paragraph(R.string.ct_history_navigation_help))
        body.addView(paragraph(R.string.science_navigation_help))
        body.addView(paragraph(R.string.ct_history_dates_key))
        body.addView(paragraph(R.string.ct_history_method))
        body.addView(paragraph(R.string.ct_science_method))
        sheet(getString(R.string.ct_about), body)
    }

    /** Depuis le catalogue : la frise se place sur le repère, dont la fiche s'affiche dessous. */
    private fun focusHumanEntries(entries: List<HumanLandmark>) {
        fiche.dismiss()
        locatedHistoryId = entries.singleOrNull()?.id
        if (historyExplorer == null) applyNavigation("human")
        historyExplorer?.focus(entries)
        scroll.scrollTo(0, 0)
    }

    private fun showHumanPeriod(period: CosmicPeriod) {
        historyExplorer?.timeline?.stopMotion()
        val body = column()
        body.addView(label(requireNotNull(period.human).label(this), 13f).apply { setTextColor(palette.secondary) })
        body.addView(object : View(this) {
            private val art = HumanTimelineArt()
            override fun onDraw(canvas: android.graphics.Canvas) {
                art.draw(canvas, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), period, palette)
            }
        }, LinearLayout.LayoutParams(-1, dp(100)))
        body.addView(paragraph(period.description))
        body.addView(button(R.string.ct_locate) {
            fiche.dismiss(); historyExplorer?.focus(period); scroll.scrollTo(0, 0)
        }, fullRow())
        body.addView(button(R.string.ct_history_open_species) {
            startActivity(Intent(this, ParentesActivity::class.java)
                .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                .putExtra(ParentesActivity.EXTRA_NODE_ID, "ott770315"))
        }, fullRow())
        sheet(getString(period.title), body)
    }

    private fun sheet(title: String, body: LinearLayout) {
        historyExplorer?.timeline?.stopMotion()
        fiche.show(title, null, null, body)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("path", ArrayList(path)); outState.putStringArrayList("expanded", ArrayList(expanded))
        outState.putInt("scroll", scroll.scrollY)
        outState.putString("history_region", historyRegion?.name)
        outState.putString("history_topic", historyTopic.name)
        outState.putString("located_history", locatedHistoryId)
        outState.putDouble("history_start", historyWindow.start); outState.putDouble("history_span", historyWindow.span)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        historyExplorer?.timeline?.stopMotion()
        prefs.edit().putString("path_v2", path.joinToString("|")).putStringSet("expanded_v2", expanded.toSet())
            .putBoolean("history_recent_entry_v1", true)
            .putString("history_region", historyRegion?.name).putString("located_history", locatedHistoryId)
            .putString("history_topic", historyTopic.name)
            .putLong("history_start", historyWindow.start.toBits()).putLong("history_span", historyWindow.span.toBits()).apply()
        super.onStop()
    }
    override fun onDestroy() { fiche.dismiss(); super.onDestroy() }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(palette.text)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun paragraph(res: Int, bold: Boolean = false, vararg arguments: Any) = label(
        if (arguments.isEmpty()) getString(res) else getString(res, *arguments), 15f, bold).apply {
        setPadding(dp(4), dp(10), dp(4), dp(4)); setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(res: Int, action: () -> Unit) = Button(this).apply {
        setText(res); isAllCaps = false; textSize = 14f; minHeight = dp(48)
        setTextColor(palette.text); background = palette.shape(palette.raised)
        setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action() }
    }
    private fun icon(drawable: Int, description: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(palette.text)
        contentDescription = getString(description); tooltipText = contentDescription
        val value = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        setBackgroundResource(value.resourceId); setOnClickListener { action() }
    }
    private fun square() = LinearLayout.LayoutParams(dp(48), dp(48))
    private fun fullRow() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    companion object {
        /** Stable internal links to verified, dated tree nodes. */
        const val EXTRA_EVENT_ID = "cosmic_event_id"
        const val EXTRA_CHAPTER_ID = "cosmic_chapter_id"
        const val EXTRA_LIFE_ID = "dated_life_id"
    }
}
