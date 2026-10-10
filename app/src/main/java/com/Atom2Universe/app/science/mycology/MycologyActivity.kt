package com.Atom2Universe.app.science.mycology

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.followImmersiveMode
import com.Atom2Universe.app.util.paintSheetFrame
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import java.text.Collator
import kotlin.math.min

/**
 * L'atlas des champignons : des sosies comparés côte à côte (d'après les confusions réellement observées
 * en France), la liste des espèces, et celles dont le classement a changé. Chaque fiche montre le champignon
 * en trois vues dessinées en code. Les sources n'apparaissent que dans « À propos ».
 */
class MycologyActivity : ThemedActivity() {
    private enum class Tab { LOOKALIKES, SPECIES, CHANGED }

    private sealed class Sheet {
        data class Species(val id: String) : Sheet()
        data class Group(val id: String) : Sheet()
        object About : Sheet()
    }

    private val palette by lazy { SciencePalette(this) }
    private val prefs by lazy { getSharedPreferences("mycology", MODE_PRIVATE) }
    private val density by lazy { resources.displayMetrics.density }
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabsRow: LinearLayout
    private var tab = Tab.LOOKALIKES
    // Les choix de l'écran Espèces : plusieurs statuts, plusieurs critères, gardés d'une ouverture à l'autre.
    private val statuses = HashSet<FungusStatus>()
    private val criteria = HashSet<String>()
    private var criteriaOpen = true
    private var mode = PlateMode.SIDE
    private var marks = true

    private var dialog: BottomSheetDialog? = null
    private var current: Sheet? = null
    private val history = ArrayList<Sheet>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = Tab.entries.firstOrNull { it.name == (savedInstanceState?.getString("tab") ?: prefs.getString("tab", null)) } ?: Tab.LOOKALIKES
        prefs.getStringSet("statuses", null)?.forEach { name -> FungusStatus.entries.firstOrNull { it.name == name }?.let { statuses.add(it) } }
        criteria.addAll(FungusCriteria.known(prefs.getStringSet("criteria", null) ?: emptySet()))
        criteriaOpen = prefs.getBoolean("criteria_open", true)
        mode = PlateMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: PlateMode.SIDE
        marks = prefs.getBoolean("marks", true)
        buildUi()
        render()
        savedInstanceState?.getString("sheet")?.let { restoreSheet(it) }
        val y = savedInstanceState?.getInt("scroll") ?: 0
        scroll.post { scroll.scrollTo(0, y) }
    }

    // ------------------------------------------------------------------ écran principal

    private fun buildUi() {
        val root = column().apply { setBackgroundColor(palette.background) }
        val toolbar = row()
        toolbar.addView(icon(R.drawable.ic_arrow_back_24, R.string.myco_back) { finish() }.apply {
            ScienceNavigation.bindHomeAction(this) {
                dialog?.dismiss()
                tab = Tab.LOOKALIKES
                render()
                scroll.scrollTo(0, 0)
                announceForAccessibility(getString(R.string.science_return_to_module_start))
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(label(getString(R.string.myco_title), 20f, true), LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(pill(getString(R.string.myco_urgent), 0xFFC62828.toInt()).apply { setOnClickListener { showUrgent() } },
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
        toolbar.addView(icon(R.drawable.ic_more_vert_24, R.string.myco_about) { open(Sheet.About) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar)

        val warning = row().apply {
            background = palette.shape(ColorUtils.blendARGB(palette.surface, 0xFFC62828.toInt(), 0.10f), 12f)
            setPadding(dp(10), dp(8), dp(12), dp(8))
        }
        warning.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_info); setColorFilter(0xFFC62828.toInt()); background = null; isClickable = false
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        warning.addView(label(getString(R.string.myco_warning), 12.5f), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        root.addView(warning, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(4), dp(16), dp(6)) })

        tabsRow = row().apply { setPadding(dp(12), 0, dp(12), dp(6)) }
        root.addView(tabsRow)

        content = column().apply { setPadding(dp(16), dp(6), dp(16), dp(24)) }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val centered = FrameLayout(this).apply { addView(scroll, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_HORIZONTAL)) }
        root.addView(centered, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            centered.post { scroll.layoutParams = (scroll.layoutParams as FrameLayout.LayoutParams).apply { width = min(centered.width, dp(840)) } }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun render() {
        tabsRow.removeAllViews()
        content.removeAllViews()
        listOf(Tab.LOOKALIKES to R.string.myco_tab_lookalikes, Tab.SPECIES to R.string.myco_tab_species, Tab.CHANGED to R.string.myco_tab_changed)
            .forEach { (t, title) ->
                tabsRow.addView(button(title, tab == t) { if (tab != t) { tab = t; render(); scroll.scrollTo(0, 0) } },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            }
        when (tab) {
            Tab.LOOKALIKES -> renderLookalikes()
            Tab.SPECIES -> renderSpecies()
            Tab.CHANGED -> renderChanged()
        }
    }

    private fun contentWidth() = min(resources.displayMetrics.widthPixels, dp(840)) - dp(32)

    private fun renderLookalikes() {
        FungusCatalog.groups.forEach { group ->
            val members = group.members.mapNotNull { FungusCatalog.get(it) }
            val card = column().apply {
                background = ripple(palette.shape(palette.raised, 16f))
                setPadding(dp(12), dp(10), dp(12), dp(12))
                isClickable = true; isFocusable = true
                contentDescription = getString(group.title)
                setOnClickListener { open(Sheet.Group(group.id)) }
            }
            val head = row()
            head.addView(label(getString(group.title), 17f, true), LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(pill(getString(group.evidence), palette.outline, small = true))
            card.addView(head)
            val plates = row().apply { gravity = Gravity.TOP }
            val looks = members.map { it.look }
            members.forEach { sp ->
                val cell = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
                cell.addView(plate(sp.look, PlateMode.SIDE, compact = true, shared = looks), LinearLayout.LayoutParams(-1, dp(150)))
                cell.addView(label(getString(sp.name), 11.5f).apply {
                    gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                cell.addView(statusChip(sp.status, small = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
                plates.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
            }
            card.addView(plates, full())
            content.addView(card, full())
        }
    }

    private fun sortedSpecies(): List<FungusSpecies> {
        val collator = Collator.getInstance(resources.configuration.locales[0])
        return FungusCatalog.species.sortedWith(compareBy(collator) { getString(it.name) })
    }

    /**
     * L'écran Espèces : les statuts (plusieurs à la fois, « Tous » les efface), puis des critères pour retrouver
     * un champignon d'après ce qu'on a vu. Un bouton qu'on touche change la liste sans reconstruire l'écran,
     * donc sans faire sauter le défilement.
     */
    private fun renderSpecies() {
        val results = column()
        val statusButtons = ArrayList<Pair<FungusStatus?, Button>>()
        val criterionButtons = ArrayList<Pair<String, Button>>()
        val toggle = button("") {}
        val clear = button(R.string.myco_criteria_clear) {}
        val panel = column().apply { visibility = if (criteriaOpen) View.VISIBLE else View.GONE }

        fun refresh() {
            statusButtons.forEach { (status, b) -> style(b, if (status == null) statuses.isEmpty() else status in statuses) }
            criterionButtons.forEach { (id, b) -> style(b, id in criteria) }
            val base = if (criteria.isEmpty()) getString(R.string.myco_criteria) else getString(R.string.myco_criteria_count, criteria.size)
            toggle.text = base + if (criteriaOpen) " ▴" else " ▾"
            style(toggle, criteria.isNotEmpty())
            clear.visibility = if (criteria.isEmpty()) View.GONE else View.VISIBLE
            fillResults(results)
        }

        val chips = row()
        (listOf<FungusStatus?>(null) + FungusStatus.entries).forEach { status ->
            val b = button(status?.label ?: R.string.myco_filter_all) {
                if (status == null) statuses.clear() else if (!statuses.remove(status)) statuses.add(status)
                refresh()
            }.apply { textSize = 13f; minHeight = dp(40) }
            statusButtons.add(status to b)
            chips.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, full())

        toggle.setOnClickListener {
            criteriaOpen = !criteriaOpen
            panel.visibility = if (criteriaOpen) View.VISIBLE else View.GONE
            refresh()
        }
        clear.setOnClickListener { criteria.clear(); refresh() }
        val head = row()
        head.addView(toggle.apply { textSize = 13f; minHeight = dp(40) }, LinearLayout.LayoutParams(-2, -2))
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(clear.apply { textSize = 13f; minHeight = dp(40) }, LinearLayout.LayoutParams(-2, -2))
        content.addView(head, full(6))

        for (group in CriterionGroup.entries) {
            val offered = FungusCriteria.offered[group] ?: continue
            panel.addView(label(getString(group.title), 12f, true).apply { setTextColor(palette.secondary) }, full(8))
            val flow = ChipGroup(this).apply { chipSpacingHorizontal = dp(6); chipSpacingVertical = dp(4) }
            for (c in offered) {
                val b = button(getString(c.label, *c.labelArgs.toTypedArray())) {
                    if (!criteria.remove(c.id)) criteria.add(c.id)
                    refresh()
                }.apply { textSize = 13f; minHeight = dp(38); minimumHeight = dp(38); minWidth = 0; minimumWidth = 0 }
                criterionButtons.add(c.id to b)
                flow.addView(b)
            }
            panel.addView(flow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        content.addView(panel, full(0))
        content.addView(results, full(0))
        refresh()
    }

    private fun fillResults(results: LinearLayout) {
        results.removeAllViews()
        val list = sortedSpecies().filter { (statuses.isEmpty() || it.status in statuses) && FungusCriteria.matches(it.look, criteria) }
        results.addView(label(resources.getQuantityString(R.plurals.myco_species_count, list.size, list.size), 12f).apply { setTextColor(palette.secondary) }, full())
        list.forEach { sp ->
            val rowView = row().apply {
                background = ripple(palette.shape(palette.raised, 14f))
                setPadding(dp(8), dp(8), dp(12), dp(8))
                isClickable = true; isFocusable = true
                setOnClickListener { open(Sheet.Species(sp.id)) }
            }
            rowView.addView(plate(sp.look, PlateMode.SIDE, compact = true), LinearLayout.LayoutParams(dp(68), dp(88)))
            val col = column()
            col.addView(label(getString(sp.name), 16f, true))
            col.addView(label(getString(sp.latin), 12.5f).apply { setTypeface(typeface, Typeface.ITALIC); setTextColor(palette.secondary) })
            val badges = row().apply { setPadding(0, dp(4), 0, 0) }
            badges.addView(statusChip(sp.status))
            if (sp.saleBanned) badges.addView(pill(getString(R.string.myco_sale_banned), 0xFF5B5B5B.toInt(), small = true),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
            col.addView(badges)
            rowView.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            results.addView(rowView, full())
        }
    }

    private fun renderChanged() {
        FungusCatalog.changes.forEach { change ->
            val sp = FungusCatalog.get(change.speciesId) ?: return@forEach
            val card = row().apply {
                background = ripple(palette.shape(palette.raised, 16f))
                setPadding(dp(10), dp(10), dp(12), dp(12))
                isClickable = true; isFocusable = true
                setOnClickListener { open(Sheet.Species(sp.id)) }
            }
            card.addView(plate(sp.look, PlateMode.SIDE, compact = true), LinearLayout.LayoutParams(dp(84), dp(112)))
            val col = column()
            col.addView(label(getString(sp.name), 17f, true))
            col.addView(label(getString(sp.latin), 12.5f).apply { setTypeface(typeface, Typeface.ITALIC); setTextColor(palette.secondary) })
            val states = row().apply { setPadding(0, dp(6), 0, dp(4)) }
            // L'ancien statut est barré et estompé : il n'a plus cours.
            states.addView(statusChip(change.before).apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                alpha = .55f
            })
            states.addView(label("→", 16f, true), LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(8), 0, dp(8), 0) })
            states.addView(statusChip(change.now))
            col.addView(states)
            col.addView(label(change.years.joinToString(" · "), 12.5f, true).apply { setTextColor(palette.ink(palette.accent)) })
            col.addView(label(getString(change.story), 13.5f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            card.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            content.addView(card, full())
        }
    }

    // ------------------------------------------------------------------ fenêtres

    private fun open(sheet: Sheet, push: Boolean = true) {
        val previous = current
        if (previous != null && push && previous != sheet) history.add(previous)
        if (previous == null) history.clear()
        current = sheet
        val body = when (sheet) {
            is Sheet.Species -> FungusCatalog.get(sheet.id)?.let { speciesBody(it) }
            is Sheet.Group -> FungusCatalog.groups.firstOrNull { it.id == sheet.id }?.let { groupBody(it) }
            Sheet.About -> aboutBody()
        } ?: run { current = previous; return }
        val d = BottomSheetDialog(this)
        body.setPadding(dp(20), dp(4), dp(20), dp(28))
        val wrapper = ScrollView(this).apply { setBackgroundColor(palette.surface); addView(body) }
        val container = column().apply { setBackgroundColor(palette.surface) }
        val bar = row().apply { setPadding(dp(8), 0, dp(12), 0) }
        if (history.isNotEmpty()) bar.addView(icon(R.drawable.ic_arrow_back_24, R.string.myco_back) { back() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(button(R.string.myco_close) { d.dismiss() })
        container.addView(bar)
        container.addView(wrapper, LinearLayout.LayoutParams(-1, 0, 1f))
        d.setContentView(container)
        d.paintSheetFrame(palette.surface)
        val old = dialog
        dialog = d
        old?.dismiss()
        d.setOnDismissListener { if (dialog === d) { dialog = null; current = null; history.clear() } }
        d.show()
        d.followImmersiveMode()
        d.behavior.maxHeight = (resources.displayMetrics.heightPixels * 0.92f).toInt()
        container.layoutParams = container.layoutParams.apply { height = d.behavior.maxHeight }
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
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

    private fun topView(look: FungusLook) = look.capShape == CapShape.MOREL || look.capShape == CapShape.BRAIN

    private fun speciesBody(sp: FungusSpecies): LinearLayout {
        val body = column()
        body.addView(label(getString(sp.name), 24f, true))
        body.addView(label(getString(sp.latin), 14.5f).apply { setTypeface(typeface, Typeface.ITALIC); setTextColor(palette.secondary) })
        body.addView(label(getString(sp.altNames), 13f).apply { setTextColor(palette.secondary) })
        val badges = row().apply { setPadding(0, dp(8), 0, dp(4)) }
        badges.addView(statusChip(sp.status, big = true))
        if (sp.saleBanned) badges.addView(pill(getString(R.string.myco_sale_banned), 0xFF5B5B5B.toInt()), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        body.addView(badges)

        val plateView = plate(sp.look, mode, compact = false)
        plateView.showMarks = marks
        val size = min(resources.displayMetrics.widthPixels, dp(520)) - dp(40)
        body.addView(modeBar(listOf(plateView), if (topView(sp.look)) R.string.myco_view_top else R.string.myco_view_under), full())
        body.addView(plateView, LinearLayout.LayoutParams(size, (size * 1.1f).toInt()).apply { topMargin = dp(8); gravity = Gravity.CENTER_HORIZONTAL })

        body.addView(label(getString(R.string.myco_sec_traits), 16f, true), full(16))
        resources.getStringArray(sp.traits).forEach { body.addView(bullet(it), full(4)) }

        val note = row().apply { setPadding(0, dp(2), 0, dp(2)) }
        note.addView(View(this).apply { setBackgroundColor(sp.status.color) }, LinearLayout.LayoutParams(dp(4), -1))
        val noteBody = column().apply { setPadding(dp(12), dp(6), 0, dp(6)) }
        noteBody.addView(label(getString(R.string.myco_sec_danger), 13f, true).apply { setTextColor(palette.ink(sp.status.color)) })
        noteBody.addView(label(getString(sp.note), 15f))
        note.addView(noteBody, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(note, full(16))

        if (sp.history != 0) {
            val card = column().apply {
                background = palette.shape(ColorUtils.blendARGB(palette.surface, palette.accent, 0.08f), 14f)
                setPadding(dp(14), dp(10), dp(14), dp(12))
            }
            card.addView(label(getString(R.string.myco_sec_history), 14f, true).apply { setTextColor(palette.ink(palette.accent)) })
            card.addView(label(getString(sp.history), 14.5f), full(4))
            body.addView(card, full(16))
        }

        val groups = FungusCatalog.groupsOf(sp.id)
        if (groups.isNotEmpty()) {
            body.addView(label(getString(R.string.myco_sec_lookalikes), 16f, true), full(20))
            groups.forEach { group ->
                val chips = row()
                group.members.filter { it != sp.id }.mapNotNull { FungusCatalog.get(it) }.forEach { other ->
                    chips.addView(button(other.name) { open(Sheet.Species(other.id)) }.apply {
                        textSize = 13f; minHeight = dp(40)
                        background = ripple(GradientDrawable().apply {
                            setColor(palette.raised); cornerRadius = dp(18).toFloat(); setStroke(dp(2), other.status.color)
                        })
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
                }
                body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, full(6))
                body.addView(button(group.title) { open(Sheet.Group(group.id)) }, full(6))
            }
        }
        return body
    }

    // ------------------------------------------------------------------ un groupe de sosies

    private fun groupBody(group: FungusGroup): LinearLayout {
        val body = column()
        body.addView(label(getString(group.title), 22f, true))
        body.addView(pill(getString(group.evidence), palette.outline, small = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        val members = group.members.mapNotNull { FungusCatalog.get(it) }
        val looks = members.map { it.look }
        val avail = min(resources.displayMetrics.widthPixels, dp(840)) - dp(40)
        val cellW = (avail / members.size).coerceIn(dp(150), dp(300))
        val plates = ArrayList<FungusPlateView>()
        val row = row().apply { gravity = Gravity.TOP }
        members.forEach { sp ->
            val col = column().apply { isClickable = true; setOnClickListener { open(Sheet.Species(sp.id)) } }
            val view = plate(sp.look, mode, compact = false, shared = looks)
            view.showMarks = false
            plates.add(view)
            col.addView(view, LinearLayout.LayoutParams(cellW, (cellW * 1.3f).toInt()))
            col.addView(label(getString(sp.name), 14f, true).apply { gravity = Gravity.CENTER_HORIZONTAL }, LinearLayout.LayoutParams(cellW, -2).apply { topMargin = dp(4) })
            col.addView(statusChip(sp.status), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(2) })
            row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        body.addView(modeBar(plates, if (members.all { topView(it.look) }) R.string.myco_view_top else R.string.myco_view_under, withMarks = false), full(10))
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, full(8))
        body.addView(label(getString(R.string.myco_sec_differences), 16f, true), full(18))
        resources.getStringArray(group.points).forEach { body.addView(bullet(it), full(4)) }
        return body
    }

    // ------------------------------------------------------------------ À propos, sources, urgence

    private fun aboutBody(): LinearLayout {
        val body = column()
        body.addView(label(getString(R.string.myco_about_title), 22f, true))
        listOf(R.string.myco_about_intro, R.string.myco_about_lookalikes, R.string.myco_about_method, R.string.myco_about_drawings)
            .forEach { body.addView(label(getString(it), 15f), full(10)) }
        body.addView(label(getString(R.string.myco_about_rules_title), 16f, true), full(16))
        body.addView(label(getString(R.string.myco_about_rules), 14.5f), full(6))
        body.addView(button(R.string.myco_urgent) { showUrgent() }, full(14))

        body.addView(label(getString(R.string.myco_about_sources), 18f, true), full(22))
        val sources = MycologySources.load(this)
        if (sources.isEmpty()) {
            body.addView(label(getString(R.string.myco_sources_error), 14f), full(6))
        } else {
            listOf(
                MycologySources.Kind.OFFICIAL to R.string.myco_sources_official,
                MycologySources.Kind.JOURNAL to R.string.myco_sources_journals,
                MycologySources.Kind.SOCIETY to R.string.myco_sources_society,
                MycologySources.Kind.ENCYCLOPEDIA to R.string.myco_sources_encyclopedia
            ).forEach { (kind, title) ->
                body.addView(label(getString(title), 15f, true).apply { setTextColor(palette.ink(palette.accent)) }, full(14))
                sources.filter { it.kind == kind }.forEach { body.addView(sourceLink(it), full(6)) }
                if (kind == MycologySources.Kind.ENCYCLOPEDIA) {
                    body.addView(label(getString(R.string.myco_sources_secondary), 12.5f).apply { setTextColor(palette.secondary) }, full(8))
                }
            }
            body.addView(button(R.string.myco_sources_by_species) { showSpeciesSources() }, full(16))
        }
        return body
    }

    private fun sourceLink(source: MycologySources.Source) = label(source.citation, 13.5f).apply {
        setTextColor(palette.ink(palette.accent))
        paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        isClickable = true
        setOnClickListener { openUrl(source.url) }
    }

    private fun showSpeciesSources() {
        val list = sortedSpecies()
        AlertDialog.Builder(this).setTitle(R.string.myco_sources_by_species)
            .setItems(list.map { getString(it.name) }.toTypedArray()) { _, index ->
                val sp = list[index]
                val body = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
                body.addView(label(getString(sp.latin), 14f).apply { setTypeface(typeface, Typeface.ITALIC) })
                MycologySources.forSpecies(this, sp.id).forEach { body.addView(sourceLink(it), full(8)) }
                AlertDialog.Builder(this).setTitle(sp.name).setView(ScrollView(this).apply { addView(body) })
                    .setPositiveButton(R.string.myco_close, null).show()
            }.setNegativeButton(R.string.myco_close, null).show()
    }

    private fun showUrgent() {
        val body = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        body.addView(label(getString(R.string.myco_urgent_hint), 14f).apply { setTextColor(palette.secondary) })
        listOf(
            R.string.myco_urgent_fr to R.string.myco_tel_fr,
            R.string.myco_urgent_fr_vital to R.string.myco_tel_fr_vital,
            R.string.myco_urgent_be to R.string.myco_tel_be,
            R.string.myco_urgent_ch to R.string.myco_tel_ch,
            R.string.myco_urgent_eu to R.string.myco_tel_eu
        ).forEach { (name, tel) ->
            val number = getString(tel)
            val line = row().apply {
                setPadding(0, dp(12), 0, dp(12)); isClickable = true; isFocusable = true
                background = ripple(null)
                setOnClickListener { dial(number) }
            }
            line.addView(label(getString(name), 15f), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label(number, 19f, true).apply { setTextColor(palette.ink(palette.accent)) })
            body.addView(line, full(2))
        }
        AlertDialog.Builder(this).setTitle(R.string.myco_urgent_title).setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.myco_close, null).show()
    }

    private fun dial(number: String) {
        try { startActivity(Intent(Intent.ACTION_DIAL, "tel:${number.filter { it.isDigit() }}".toUri())) }
        catch (_: ActivityNotFoundException) { /* pas de téléphone : rien ne se passe */ }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.myco_sources_no_browser, Toast.LENGTH_SHORT).show() }
    }

    // ------------------------------------------------------------------ morceaux d'interface

    private fun plate(look: FungusLook, plateMode: PlateMode, compact: Boolean, shared: List<FungusLook>? = null) =
        FungusPlateView(this).also {
            it.look = look; it.mode = plateMode; it.compact = compact; it.sharedScaleWith = shared
        }

    private fun modeBar(plates: List<FungusPlateView>, underLabel: Int, withMarks: Boolean = true): LinearLayout {
        val bar = row()
        val buttons = ArrayList<Pair<PlateMode, Button>>()
        PlateMode.entries.forEach { m ->
            val b = button(if (m == PlateMode.UNDER) underLabel else m.label, m == mode) {}
            buttons.add(m to b)
            b.setOnClickListener {
                mode = m
                plates.forEach { p -> p.mode = m }
                buttons.forEach { (mm, bb) -> style(bb, mm == mode) }
            }
            bar.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        if (withMarks) {
            val marksButton = button(R.string.myco_marks, marks) {}
            marksButton.setOnClickListener {
                marks = !marks
                plates.forEach { p -> p.showMarks = marks }
                style(marksButton, marks)
            }
            bar.addView(marksButton, LinearLayout.LayoutParams(0, -2, 1.1f))
        }
        return bar
    }

    private fun statusChip(status: FungusStatus, small: Boolean = false, big: Boolean = false) =
        pill(getString(status.label), status.color, small, big)

    private fun pill(text: String, color: Int, small: Boolean = false, big: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = if (small) 10.5f else if (big) 14f else 12.5f
        // Une pastille peut être translucide (le contour du thème) : le contraste se calcule sur ce qu'on voit vraiment.
        val seen = ColorUtils.compositeColors(color, ColorUtils.setAlphaComponent(palette.raised, 255))
        setTextColor(SciencePalette.contrastingText(seen))
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        val h = if (big) 6 else 3
        setPadding(dp(if (big) 12 else 8), dp(h), dp(if (big) 12 else 8), dp(h))
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
    }

    private fun bullet(text: String) = row().apply {
        gravity = Gravity.TOP
        addView(label("•", 15f).apply { setTextColor(palette.secondary) }, LinearLayout.LayoutParams(dp(16), -2))
        addView(label(text, 15f), LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(palette.text)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun button(title: Int, selected: Boolean = false, action: () -> Unit) = button(getString(title), selected, action)

    private fun button(title: String, selected: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 14f; minHeight = dp(46); minimumHeight = dp(46)
        setPadding(dp(10), dp(6), dp(10), dp(6)); setOnClickListener { action() }
        style(this, selected)
    }

    private fun style(button: Button, selected: Boolean) {
        button.background = ripple(palette.control(selected))
        button.setTextColor(if (selected) palette.onAccent else palette.text)
        button.isSelected = selected
    }

    private fun icon(drawable: Int, title: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable); setColorFilter(palette.text)
        background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 55)), null, palette.shape(android.graphics.Color.WHITE, 24f))
        contentDescription = getString(title); setOnClickListener { action() }
    }

    private fun ripple(shape: android.graphics.drawable.Drawable?) =
        RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 55)), shape, null)

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun full(top: Int = 8) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * density).toInt()

    // ------------------------------------------------------------------ cycle de vie

    override fun onPause() {
        prefs.edit().putString("tab", tab.name).putString("mode", mode.name).putBoolean("marks", marks)
            .putStringSet("statuses", statuses.mapTo(HashSet()) { it.name }).putStringSet("criteria", HashSet(criteria))
            .putBoolean("criteria_open", criteriaOpen).apply()
        super.onPause()
    }

    override fun onDestroy() { dialog?.dismiss(); super.onDestroy() }

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
}
