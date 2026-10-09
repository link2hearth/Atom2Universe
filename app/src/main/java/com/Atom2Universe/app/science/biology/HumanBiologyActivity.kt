package com.Atom2Universe.app.science.biology

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.doOnLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppBrightness
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.followImmersiveMode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.PI

class HumanBiologyActivity : ThemedActivity() {
    override val themeBrightness = AppBrightness.DARK
    private var scene: SkeletonScene? = null
    private var catalog: AnatomyCatalog? = null
    private var selected: AnatomicalStructure? = null
    private var resumed = false
    private var pendingState: Bundle? = null
    private var atlasStates = Bundle()
    private var activeAtlas = AnatomyAtlas.MALE
    private var switchingAtlas = false
    private var showExternalGenitals = false
    private var loading: Job? = null
    private var openPanel: BottomSheetDialog? = null
    private var journey: AnatomyJourney? = null
    private var journeyStep = 0
    private var beforeJourney: Bundle? = null
    private var journeyPanelVisible = false
    private lateinit var surface: SurfaceView
    private lateinit var status: TextView
    private lateinit var selectionCard: LinearLayout
    private lateinit var emptyView: LinearLayout
    private lateinit var name: TextView
    private lateinit var detail: TextView
    private lateinit var layersButton: ImageButton
    private lateinit var hideButton: ImageButton
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var journeyControls: LinearLayout
    private val ink = Color.rgb(231, 238, 239)
    private val muted = Color.rgb(158, 180, 187)
    private val accent = Color.rgb(94, 221, 199)
    private val panel = Color.rgb(18, 35, 44)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        journey = AnatomyJourneys.find(savedInstanceState?.getString("bio_journey"))
        journeyStep = savedInstanceState?.getInt("bio_journey_step", 0) ?: 0
        beforeJourney = savedInstanceState?.getBundle("bio_before_journey")
        journeyPanelVisible = savedInstanceState?.getBoolean("bio_journey_panel", false) ?: false
        showExternalGenitals = savedInstanceState?.getBoolean("bio_external_genitals_visible", false) ?: false
        atlasStates = savedInstanceState?.getBundle("bio_atlas_states") ?: Bundle()
        activeAtlas = AnatomyAtlas.fromId(intent.getStringExtra("bio_atlas")
            ?: savedInstanceState?.getString("bio_atlas")
            ?: getSharedPreferences("human_biology", MODE_PRIVATE).getString("atlas", null))
        pendingState = atlasStates.getBundle(activeAtlas.id) ?: savedInstanceState?.takeIf {
            // Un chargement interrompu n'a pas encore d'état de scène. Ne jamais
            // réinsérer le Bundle parent (contenant atlasStates) dans son enfant.
            !it.containsKey("bio_atlas_states") &&
                (it.containsKey("bio_display_state_v2") || it.containsKey("bio_camera") || it.containsKey("bio_layers")) &&
                AnatomyAtlas.fromId(it.getString("bio_atlas")) == activeAtlas
        }?.let { legacy ->
            Bundle(legacy).apply {
                keySet().filter { !it.startsWith("bio_") || it == "bio_atlas" }.forEach { remove(it) }
            }
        }
        buildUi()
        enableImmersiveMode()
        loading = loadAtlas()
    }

    private fun buildUi() {
        // Le modèle garde toute la surface, y compris quand une fiche est ouverte.
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 20, 29)) }
        surface = SurfaceView(this).apply {
            contentDescription = getString(R.string.bio_ui_surface)
            isClickable = true
        }
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        status = label(R.string.bio_ui_loading, 15f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(80), dp(28), dp(32))
            setBackgroundColor(Color.rgb(8, 20, 29))
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -1))

        emptyView = column().apply {
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = rounded(panel)
            visibility = View.GONE
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(label(R.string.bio_ui_empty, 17f).apply {
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(button(R.string.bio_ui_visibility) { showVisibility() }, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(emptyView, FrameLayout.LayoutParams(cardWidth(340), -2, Gravity.CENTER))

        val toolbar = row().apply {
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = rounded(0xe812232c.toInt())
            elevation = dp(3).toFloat()
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        toolbar.addView(icon(R.drawable.ic_arrow_back_24, R.string.bio_back) { finish() }, square())
        undoButton = icon(R.drawable.ic_px_undo, R.string.bio_ui_undo) { scene?.undo() }
        redoButton = icon(R.drawable.ic_px_redo, R.string.bio_ui_redo) { scene?.redo() }
        toolbar.addView(undoButton, square())
        toolbar.addView(redoButton, square())
        toolbar.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        layersButton = icon(R.drawable.ic_px_layers, R.string.bio_ui_layers) { showVisibility() }
        toolbar.addView(layersButton, square())
        searchButton = icon(R.drawable.ic_search, R.string.bio_catalog) { showCatalog() }.apply { isEnabled = false }
        toolbar.addView(searchButton, square())
        toolbar.addView(icon(R.drawable.ic_more_vert_24, R.string.bio_ui_tools) { showTools() }, square())
        root.addView(toolbar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(8), dp(8), dp(8), 0)
        })
        journeyControls = row().apply {
            background = rounded(0xe812232c.toInt())
            visibility = View.GONE
            isClickable = true
        }
        root.addView(journeyControls, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(8), dp(68), dp(8), 0)
        })

        selectionCard = column().apply {
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = rounded(0xf512232c.toInt())
            elevation = dp(5).toFloat()
            visibility = View.GONE
            // Une touche sur la fiche ne doit pas sélectionner un objet situé derrière.
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val heading = row()
        val titles = column()
        name = label(R.string.bio_ui_title, 18f).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        detail = label(R.string.bio_ui_title, 12f, muted).apply {
            setPadding(0, dp(4), 0, dp(4))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        titles.addView(name)
        titles.addView(detail)
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(icon(R.drawable.ic_close, R.string.bio_ui_deselect) {
            scene?.select(null) ?: updateSelection(null)
        }, square())
        selectionCard.addView(heading)
        val actions = row()
        actions.addView(button(R.string.bio_sheet) { selected?.let { showSheet(it) } },
            LinearLayout.LayoutParams(0, -2, 1f))
        hideButton = icon(R.drawable.ic_px_eye_off, R.string.bio_hide) { scene?.hideSelection() }
        actions.addView(hideButton, square())
        actions.addView(icon(R.drawable.ic_more_vert_24, R.string.bio_ui_structure_actions) { showSelectionActions() }, square())
        selectionCard.addView(actions)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root.addView(selectionCard, FrameLayout.LayoutParams(cardWidth(if (landscape) 330 else 440), -2,
            Gravity.BOTTOM or (if (landscape) Gravity.START else Gravity.CENTER_HORIZONTAL)).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        setContentView(root)
        setRenderingEnabled(false)
    }

    private fun loadAtlas() = lifecycleScope.launch {
        try {
            val loaded = withContext(Dispatchers.IO) { AnatomyCatalog.load(this@HumanBiologyActivity, activeAtlas) }
            if (switchingAtlas || isDestroyed) return@launch
            catalog = loaded
            searchButton.isEnabled = true
            val buffer = withContext(Dispatchers.IO) {
                AnatomyModelReader.read(loaded.modelParts, loaded.modelBytes, checkActive = { coroutineContext.ensureActive() }) { name ->
                    assets.open("${loaded.atlas.directory}/$name")
                }
            }
            // Attendre les dimensions réelles pour le cadrage initial, notamment en paysage.
            surface.doOnLayout {
                if (isFinishing || isDestroyed || switchingAtlas) return@doOnLayout
                try {
                    val renderer = SkeletonScene(surface, loaded, showExternalGenitals) { updateSelection(it) }
                    scene = renderer
                    renderer.load(buffer)
                    pendingState?.let { renderer.restore(it) }
                    pendingState = null
                    status.visibility = View.GONE
                    setRenderingEnabled(true)
                    updateSelection(renderer.selected)
                    if (resumed) renderer.resume()
                    journey?.takeIf { journeyPanelVisible && activeAtlas == AnatomyAtlas.MALE }?.let {
                        showJourney(it, journeyStep, applyView = false)
                    }
                } catch (error: Exception) {
                    renderFailed(error)
                } catch (error: LinkageError) {
                    renderFailed(error)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            renderFailed(error)
        }
    }

    private fun renderFailed(error: Throwable) {
        Log.e("HumanBiology", "Unable to load anatomy atlas", error)
        scene?.releaseOwnedResources()
        scene = null
        setRenderingEnabled(false)
        status.setText(if (catalog == null) R.string.bio_catalog_error else R.string.bio_load_error)
        status.visibility = View.VISIBLE
        updateSelection(selected)
    }

    private fun setRenderingEnabled(enabled: Boolean) {
        layersButton.isEnabled = enabled
        layersButton.alpha = if (enabled) 1f else .4f
        updateDisplayState()
    }

    private fun updateSelection(item: AnatomicalStructure?) {
        selected = item
        selectionCard.visibility = if (item == null) View.GONE else View.VISIBLE
        if (item != null) {
            name.setText(item.name)
            detail.text = if (item.hasMesh) getString(R.string.bio_classification,
                getString(item.layer.label), getString(item.groupLabel)) else getString(R.string.bio_sheet_only)
        }
        updateDisplayState()
    }

    private fun updateDisplayState() {
        val renderer = scene
        val isEmpty = renderer != null && renderer.visibleStructureCount == 0
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        val filtered = renderer?.isFiltered == true
        layersButton.setColorFilter(if (filtered) accent else ink)
        listOf(undoButton to (renderer?.canUndo == true), redoButton to (renderer?.canRedo == true),
            hideButton to (renderer != null && selected?.hasMesh == true)).forEach { (view, enabled) ->
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else .35f
        }
        layersButton.contentDescription = getString(if (filtered) R.string.bio_ui_visibility_filtered else R.string.bio_ui_visibility)
        layersButton.tooltipText = layersButton.contentDescription
        updateJourneyControls()
    }

    private fun updateJourneyControls() {
        if (!::journeyControls.isInitialized) return
        journeyControls.removeAllViews()
        val current = journey
        journeyControls.visibility = if (current != null && scene != null) View.VISIBLE else View.GONE
        if (current == null) return
        journeyControls.addView(button(R.string.bio_j_resume) { showJourney(current, journeyStep) }.apply {
            text = getString(R.string.bio_j_progress, getString(current.title), journeyStep + 1, current.steps.size)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            contentDescription = getString(R.string.bio_j_resume_progress, text)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        journeyControls.addView(icon(R.drawable.ic_chevron_right,
            if (journeyStep == current.steps.lastIndex) R.string.bio_j_finish else R.string.bio_j_next) {
            if (journeyStep == current.steps.lastIndex) finishJourney()
            else showJourney(current, journeyStep + 1)
        }, square())
    }

    private fun showVisibility(layer: AnatomyLayer? = null) {
        val renderer = scene ?: return
        val data = catalog ?: return
        val content = column()
        if (layer == null) {
            val checks = mutableMapOf<AnatomyLayer, CheckBox>()
            var refreshing = false
            fun refresh() {
                refreshing = true
                checks.forEach { (key, view) -> view.isChecked = key in renderer.visibleLayers }
                refreshing = false
                updateDisplayState()
            }
            data.layers.forEach { key ->
                val line = row()
                val checkbox = checkBox("", key in renderer.visibleLayers) { enabled ->
                    if (!refreshing) { renderer.setLayerVisible(key, enabled); refresh() }
                }.apply { contentDescription = getString(key.label) }
                checks[key] = checkbox
                line.addView(checkbox, square())
                line.addView(button(key.label) { showVisibility(key) }.apply {
                    text = getString(R.string.bio_ui_group_count, getString(key.label), data.groups.filter { it.layer == key }.sumOf { it.count })
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0)
                    compoundDrawablePadding = dp(8)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(line)
            }
            content.addView(button(R.string.bio_show_all) {
                renderer.showAll(); refresh()
            }, LinearLayout.LayoutParams(-1, -2))
            showPanel(getString(R.string.bio_ui_layers), content)
            return
        }
        val collator = Collator.getInstance(resources.configuration.locales[0])
        val groups = data.groups.filter { it.layer == layer }.sortedWith { a, b ->
            collator.compare(getString(a.label), getString(b.label))
        }
        val ids = groups.map { it.id }.toSet()
        var refreshing = false
        val checks = mutableMapOf<String, CheckBox>()
        val layerCheck = checkBox(getString(layer.label), layer in renderer.visibleLayers) { enabled ->
            if (!refreshing) renderer.setLayerVisible(layer, enabled)
        }
        content.addView(layerCheck)
        content.addView(checkBox(getString(layer.colorLabel), layer in renderer.coloredLayers) {
            renderer.setColored(layer, it)
        })
        fun refresh() {
            refreshing = true
            layerCheck.isChecked = layer in renderer.visibleLayers
            checks.forEach { (id, check) -> check.isChecked = id in renderer.visibleGroups }
            refreshing = false
            updateDisplayState()
        }
        layerCheck.setOnCheckedChangeListener { _, enabled ->
            if (!refreshing) { renderer.setLayerVisible(layer, enabled); refresh() }
        }
        val tools = row()
        tools.addView(button(R.string.bio_ui_select_all) {
            renderer.setVisibleGroups(layer, ids); refresh()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tools.addView(button(R.string.bio_ui_select_none) {
            renderer.setVisibleGroups(layer, emptySet()); refresh()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(tools)
        val list = column()
        if (groups.size > 15) {
            val search = EditText(this).apply {
                setHint(R.string.bio_ui_filter_groups)
                setTextColor(ink); setHintTextColor(muted)
                isSingleLine = true
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                minHeight = dp(48)
            }
            content.addView(search)
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val tokens = AnatomyCatalog.normalize(s?.toString().orEmpty()).split(" ").filter { it.isNotBlank() }
                    checks.forEach { (_, check) ->
                        val name = AnatomyCatalog.normalize(check.text.toString())
                        check.visibility = if (tokens.all { it in name }) View.VISIBLE else View.GONE
                    }
                }
            })
        }
        content.addView(list)
        groups.forEach { group ->
            val checkbox = checkBox(getString(R.string.bio_ui_group_count, getString(group.label), group.count),
                group.id in renderer.visibleGroups) { enabled ->
                if (!refreshing) {
                    val selected = renderer.visibleGroups intersect ids
                    renderer.setVisibleGroups(layer, if (enabled) selected + group.id else selected - group.id)
                    refresh()
                }
            }
            checks[group.id] = checkbox
            list.addView(checkbox)
        }
        showPanel(getString(layer.label), content, onBack = { showVisibility() })
    }

    private fun showTools() {
        val content = column()
        val renderer = scene
        content.addView(section(R.string.bio_atlas_choice))
        AnatomyAtlas.entries.forEach { atlas ->
            content.addView(button(atlas.label) { switchAtlas(atlas) }.apply {
                styleButton(this, activeAtlas == atlas)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (activeAtlas != AnatomyAtlas.EAR) {
            content.addView(button(if (showExternalGenitals) R.string.bio_hide_external_genitals
                else R.string.bio_show_external_genitals) {
                renderer?.let {
                    showExternalGenitals = !showExternalGenitals
                    it.setExternalGenitalsVisible(showExternalGenitals)
                }
                openPanel?.dismiss()
            }.apply {
                isEnabled = renderer != null
                alpha = if (isEnabled) 1f else .4f
                styleButton(this, showExternalGenitals)
                compoundDrawablePadding = dp(8)
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    if (showExternalGenitals) R.drawable.ic_px_eye else R.drawable.ic_px_eye_off, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(
                    if (showExternalGenitals) Color.rgb(6, 31, 31) else ink)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(section(R.string.bio_ui_viewpoints))
        val views = if (activeAtlas == AnatomyAtlas.EAR)
            listOf(R.string.bio_ear_view_main to 0.0, R.string.bio_ear_view_opposite to PI,
                R.string.bio_ear_view_left to PI / 2, R.string.bio_ear_view_right to -PI / 2)
        else listOf(R.string.bio_front to 0.0, R.string.bio_back_view to PI,
            R.string.bio_left_view to PI / 2, R.string.bio_right_view to -PI / 2)
        views.chunked(2).forEach { pair ->
            val line = row()
            pair.forEach { (title, angle) ->
                line.addView(button(title) { renderer?.viewFrom(angle); openPanel?.dismiss() }.apply {
                    isEnabled = renderer != null
                    alpha = if (isEnabled) 1f else .4f
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            content.addView(line)
        }
        content.addView(button(R.string.bio_reset) { renderer?.resetCamera(); openPanel?.dismiss() }.apply {
            isEnabled = renderer != null
            alpha = if (isEnabled) 1f else .4f
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(button(R.string.bio_show_all) {
            renderer?.showAll()
            updateSelection(renderer?.selected)
            openPanel?.dismiss()
        }.apply { isEnabled = renderer != null; alpha = if (isEnabled) 1f else .4f }, LinearLayout.LayoutParams(-1, -2))
        content.addView(section(R.string.bio_ui_explore))
        content.addView(button(R.string.bio_j_title) { showJourneys() }, LinearLayout.LayoutParams(-1, -2))
        journey?.let { current ->
            content.addView(button(R.string.bio_j_resume) {
                showJourney(current, journeyStep)
            }, LinearLayout.LayoutParams(-1, -2))
            content.addView(button(R.string.bio_j_finish) { finishJourney() }, LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(button(R.string.bio_ui_gestures) { openPanel?.dismiss(); showGestures() }, LinearLayout.LayoutParams(-1, -2))
        content.addView(button(R.string.bio_about) { openPanel?.dismiss(); showCoverage() }, LinearLayout.LayoutParams(-1, -2))
        showPanel(getString(R.string.bio_ui_tools), content)
    }

    private fun saveAtlasState() {
        pendingState?.let { atlasStates.putBundle(activeAtlas.id, it) }
        scene?.let { renderer ->
            val state = Bundle()
            renderer.save(state)
            atlasStates.putBundle(activeAtlas.id, state)
        }
    }

    private fun switchAtlas(atlas: AnatomyAtlas) {
        openPanel?.dismiss()
        if (atlas == activeAtlas || switchingAtlas) return
        if (journey != null) finishJourney()
        saveAtlasState()
        journey = null
        beforeJourney = null
        journeyPanelVisible = false
        switchingAtlas = true
        loading?.cancel()
        scene?.releaseOwnedResources()
        scene = null
        // Le détachement détruit le moteur et ses buffers avant de charger le suivant.
        (surface.parent as? ViewGroup)?.removeView(surface)
        intent.putExtra("bio_atlas", atlas.id)
        getSharedPreferences("human_biology", MODE_PRIVATE).edit().putString("atlas", atlas.id).apply()
        recreate()
    }

    private fun showSelectionActions() {
        val item = selected ?: return
        val renderer = scene
        val content = column()
        listOf<Pair<Int, () -> Unit>>(
            R.string.bio_focus to { renderer?.focus(item) },
            R.string.bio_isolate to { renderer?.isolateSelection() },
        ).forEach { (title, action) ->
            content.addView(button(title) { action(); updateDisplayState(); openPanel?.dismiss() }.apply {
                isEnabled = renderer != null && item.hasMesh
                alpha = if (isEnabled) 1f else .4f
            }, LinearLayout.LayoutParams(-1, -2))
        }
        showPanel(getString(item.name), content)
    }

    private fun showGestures() {
        val content = dialogColumn()
        listOf(R.string.bio_ui_tap_hint, R.string.bio_hint, R.string.bio_ui_double_tap_hint, R.string.bio_ui_sides_hint).forEach {
            content.addView(paragraph(getString(it)))
        }
        showDocument(getString(R.string.bio_ui_gestures), content)
    }

    private fun showPanel(title: String, content: LinearLayout, pinnedControls: View? = null,
        onBack: (() -> Unit)? = null, heightFraction: Float = .72f, onDismiss: (() -> Unit)? = null) {
        openPanel?.dismiss()
        journeyPanelVisible = false
        val dialog = BottomSheetDialog(this)
        val body = column().apply {
            setPadding(dp(16), dp(8), dp(16), dp(16))
            background = rounded(panel)
        }
        val header = row()
        if (onBack != null) header.addView(icon(R.drawable.ic_arrow_back_24, R.string.bio_ui_layers, onBack), square())
        header.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(icon(R.drawable.ic_close, R.string.bio_close) { dialog.dismiss() }, square())
        body.addView(header)
        pinnedControls?.let { body.addView(it) }
        // Un panneau court laisse le modèle visible ; un long reste défilable en paysage.
        // Le plafond est réévalué quand on passe des couches à la liste des régions.
        val maximumHeight = (resources.displayMetrics.heightPixels * heightFraction).toInt()
        val maximumWidth = minOf(resources.displayMetrics.widthPixels, dp(560))
        val scroll = object : NestedScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = (maximumHeight - header.measuredHeight - (pinnedControls?.measuredHeight ?: 0) -
                    body.paddingTop - body.paddingBottom).coerceAtLeast(dp(80))
                val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) available
                    else minOf(available, MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; addView(content) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        dialog.setContentView(body)
        dialog.behavior.maxWidth = maximumWidth
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.window?.setDimAmount(.22f)
        dialog.setOnDismissListener {
            // Dismiss callbacks are posted: an old panel must not close the state of its replacement.
            if (openPanel === dialog) {
                openPanel = null
                onDismiss?.invoke()
            }
        }
        dialog.followImmersiveMode()
        openPanel = dialog
        dialog.show()
    }

    private fun showJourneys() {
        val content = column()
        if (activeAtlas != AnatomyAtlas.MALE) {
            content.addView(paragraph(getString(R.string.bio_j_atlas)))
            content.addView(button(AnatomyAtlas.MALE.label) { switchAtlas(AnatomyAtlas.MALE) })
        } else {
            content.addView(paragraph(getString(R.string.bio_j_intro)))
            AnatomyJourneys.all.forEach { current ->
                content.addView(button(current.title) {
                    if (scene == null) return@button
                    if (beforeJourney == null) beforeJourney = Bundle().also { scene?.save(it) }
                    showJourney(current, 0)
                }.apply { isEnabled = scene != null }, LinearLayout.LayoutParams(-1, -2))
                content.addView(paragraph(getString(current.intro), muted))
            }
        }
        showPanel(getString(R.string.bio_j_title), content)
    }

    private fun showJourney(current: AnatomyJourney, index: Int, applyView: Boolean = true) {
        val data = catalog ?: return
        if (activeAtlas != AnatomyAtlas.MALE || scene == null) return
        journey = current
        journeyStep = index.coerceIn(current.steps.indices)
        val step = current.steps[journeyStep]
        val structures = step.structures(data)
        if (applyView) scene?.showJourneyStructures(structures.map { it.id }.toSet())
        updateJourneyControls()
        val content = column()
        content.addView(label(step.title, 18f).apply { setTypeface(typeface, Typeface.BOLD) })
        content.addView(paragraph(getString(step.body)))
        content.addView(button(R.string.bio_j_view) { openPanel?.dismiss() })
        content.addView(paragraph(getString(R.string.bio_j_view_note), muted))
        content.addView(section(R.string.bio_j_organs))
        if (structures.isEmpty()) content.addView(paragraph(getString(R.string.bio_j_unavailable)))
        structures.sortedWith(compareBy(Collator.getInstance(resources.configuration.locales[0])) {
            getString(it.name)
        }).forEach { item ->
            content.addView(button(item.name) {
                scene?.select(item)
                scene?.focus(item)
                openPanel?.dismiss()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        val navigation = row()
        navigation.addView(button(R.string.bio_j_previous) {
            showJourney(current, journeyStep - 1)
        }.apply { isEnabled = journeyStep > 0 }, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(button(if (journeyStep == current.steps.lastIndex) R.string.bio_j_finish else R.string.bio_j_next) {
            if (journeyStep == current.steps.lastIndex) finishJourney()
            else showJourney(current, journeyStep + 1)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        showPanel(getString(R.string.bio_j_progress, getString(current.title), journeyStep + 1, current.steps.size),
            content, pinnedControls = navigation, onBack = { showJourneys() }, heightFraction = .52f,
            onDismiss = { journeyPanelVisible = false })
        journeyPanelVisible = true
    }

    private fun finishJourney() {
        openPanel?.dismiss()
        beforeJourney?.let { scene?.restore(it) }
        beforeJourney = null
        journey = null
        journeyPanelVisible = false
        updateDisplayState()
    }

    private fun showCatalog() {
        val data = catalog ?: return
        val content = column().apply { setPadding(dp(16), dp(4), dp(16), dp(8)) }
        val search = EditText(this).apply {
            setHint(R.string.bio_ui_search)
            setTextColor(ink)
            setHintTextColor(muted)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        content.addView(search)
        val locale = resources.configuration.locales[0]
        val englishContext = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) })
        val frenchContext = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(java.util.Locale.FRENCH) })
        val collator = Collator.getInstance(locale)
        val all = data.structures.sortedWith { a, b -> collator.compare(getString(a.name), getString(b.name)) }
        val searchKeys = all.associate { item -> item.id to AnatomyCatalog.normalize(
            getString(item.name) + " " + englishContext.getString(item.name) + " " + frenchContext.getString(item.name) + " " +
                getString(item.layer.label) + " " + getString(item.groupLabel) + " " + getString(item.region.label) + " " + item.sourceName + " " + item.id) }
        var filtered = all
        val list = ListView(this).apply { dividerHeight = dp(1) }
        val empty = label(R.string.bio_no_results, 15f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(24)) }
        content.addView(empty)
        list.emptyView = empty
        val adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = filtered[position]
                return column().apply {
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                    addView(label(item.name, 16f))
                    addView(TextView(this@HumanBiologyActivity).apply {
                        text = if (item.hasMesh) getString(R.string.bio_classification,
                            getString(item.layer.label), getString(item.groupLabel)) else getString(R.string.bio_sheet_only)
                        textSize = 12f
                        setTextColor(if (item.hasMesh) muted else accent)
                    })
                }
            }
        }
        list.adapter = adapter
        content.addView(list, LinearLayout.LayoutParams(-1, minOf(dp(380), (resources.displayMetrics.heightPixels * .46f).toInt())))
        val dialog = AlertDialog.Builder(this).setTitle(R.string.bio_catalog).setView(content)
            .setNegativeButton(R.string.bio_close, null).create()
        list.setOnItemClickListener { _, _, position, _ ->
            val item = filtered[position]
            if (!showExternalGenitals && item.id in data.externalGenitalIds) {
                dialog.dismiss()
                showSheet(item)
                return@setOnItemClickListener
            }
            scene?.select(item, reveal = true) ?: updateSelection(item)
            dialog.dismiss()
            if (!item.hasMesh || scene == null) showSheet(item)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val tokens = AnatomyCatalog.normalize(s?.toString().orEmpty()).split(Regex("\\s+")).filter { it.isNotEmpty() }
                filtered = all.filter { item -> tokens.all { searchKeys.getValue(item.id).contains(it) } }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialog.followImmersiveMode()
        dialog.show()
    }

    private fun showSheet(item: AnatomicalStructure) {
        val content = dialogColumn()
        val classification = when {
            activeAtlas != AnatomyAtlas.MALE -> getString(item.layer.label)
            item.layer == AnatomyLayer.MUSCLES -> getString(R.string.bio_muscle_structure)
            item.layer == AnatomyLayer.CARTILAGE -> getString(R.string.bio_cartilage_structure)
            item.layer == AnatomyLayer.ORGANS -> getString(R.string.bio_organ_structure)
            item.layer != AnatomyLayer.SKELETON -> getString(item.layer.label)
            item.region == AnatomyRegion.TEETH -> getString(R.string.bio_tooth)
            item.standardBone -> getString(R.string.bio_reference_bone)
            else -> getString(R.string.bio_extra_bone, item.boneCount)
        }
        content.addView(paragraph(getString(R.string.bio_classification, getString(item.region.label), classification), accent))
        content.addView(paragraph(getString(item.summary)))
        if (item.region == AnatomyRegion.TEETH) content.addView(paragraph(getString(R.string.bio_dental_number), muted))
        if (!item.hasMesh) content.addView(paragraph(getString(R.string.bio_missing_mesh), muted))
        if (item.id.startsWith("FMA")) content.addView(paragraph(getString(R.string.bio_identity, item.id), muted))
        val french = resources.configuration.locales[0].language == "fr"
        val topic = if (french) item.wikiFr else item.wikiEn
        val wiki = Uri.Builder().scheme("https").authority(if (french) "fr.wikipedia.org" else "en.wikipedia.org")
        val searchReference = item.layer != AnatomyLayer.SKELETON
        if (searchReference) {
            // La nomenclature FMA ne correspond pas toujours au titre exact de l'article.
            wiki.appendPath("w").appendPath("index.php").appendQueryParameter("search", topic)
        } else {
            wiki.appendPath("wiki").appendPath(topic.replace(' ', '_'))
        }
        content.addView(button(if (searchReference) R.string.bio_wikipedia_structure_search else R.string.bio_wikipedia) {
            openLink(wiki.build().toString())
        })
        showDocument(getString(item.name), content)
    }

    private fun showCoverage() {
        val content = dialogColumn()
        content.addView(button(R.string.bio_j_sources) { AnatomyJourneyCredits.show(this) })
        if (activeAtlas != AnatomyAtlas.MALE) {
            content.addView(paragraph(getString(activeAtlas.coverage)))
            catalog?.let { data ->
                data.layers.forEach { layer ->
                    content.addView(paragraph(getString(R.string.bio_system_coverage_count,
                        getString(layer.label), data.structures.count { it.layer == layer && it.hasMesh }), accent))
                }
            }
            showDocument(getString(activeAtlas.label), content)
            return
        }
        catalog?.let {
            content.addView(paragraph(getString(R.string.bio_coverage_detail, it.renderedBones, it.extraBones, it.teeth), accent))
            content.addView(paragraph(getString(R.string.bio_muscle_coverage, it.muscles), accent))
            content.addView(paragraph(getString(R.string.bio_cartilage_coverage, it.cartilages), accent))
            content.addView(paragraph(getString(R.string.bio_organ_coverage, it.organs), accent))
            for (layer in listOf(AnatomyLayer.SENSES, AnatomyLayer.NERVOUS, AnatomyLayer.VASCULAR,
                AnatomyLayer.LYMPHATIC, AnatomyLayer.CONNECTIVE, AnatomyLayer.SKIN)) {
                val count = it.structures.count { item -> item.hasMesh && item.layer == layer }
                content.addView(paragraph(getString(R.string.bio_system_coverage_count, getString(layer.label), count), accent))
            }
            content.addView(paragraph(getString(R.string.bio_systems_coverage)))
        }
        listOf(R.string.bio_coverage_missing, R.string.bio_dataset_detail, R.string.bio_facial_alignment).forEach {
            content.addView(paragraph(getString(it)))
        }
        showDocument(getString(R.string.bio_about), content)
    }

    private fun showDocument(title: String, content: LinearLayout) {
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(content) }).setPositiveButton(R.string.bio_close, null).create()
        dialog.followImmersiveMode()
        dialog.show()
    }

    private fun openLink(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.bio_link_error, Toast.LENGTH_SHORT).show() }
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dialogColumn() = column().apply { setPadding(dp(20), dp(8), dp(20), dp(16)) }
    private fun label(text: Int, size: Float, color: Int = ink) = TextView(this).apply { setText(text); textSize = size; setTextColor(color) }
    private fun section(text: Int) = label(text, 13f, muted).apply { setPadding(dp(4), dp(14), dp(4), dp(8)) }
    private fun paragraph(value: String, color: Int = ink) = TextView(this).apply {
        text = value; textSize = 15f; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(8), 0, dp(8)); setTextIsSelectable(true)
    }
    private fun checkBox(title: String, checked: Boolean, action: (Boolean) -> Unit) = AppCompatCheckBox(this).apply {
        text = title
        textSize = 15f
        setTextColor(ink)
        buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, muted))
        minHeight = dp(50)
        setPadding(dp(4), dp(5), dp(8), dp(5))
        isChecked = checked
        setOnCheckedChangeListener { _, value -> action(value) }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    private fun icon(drawable: Int, description: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable)
        setColorFilter(ink)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = RippleDrawable(ColorStateList.valueOf(0x335eddb7), rounded(Color.TRANSPARENT), rounded(Color.WHITE))
        contentDescription = getString(description)
        tooltipText = contentDescription
        setOnClickListener { action() }
    }
    private fun button(title: Int, action: () -> Unit) = TextView(this).apply {
        setText(title); textSize = 14f; gravity = Gravity.CENTER
        minHeight = dp(48); setPadding(dp(14), dp(8), dp(14), dp(8))
        isClickable = true; isFocusable = true
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        styleButton(this, false)
    }
    private fun styleButton(view: TextView, active: Boolean) {
        view.isSelected = active
        view.setTextColor(if (active) Color.rgb(6, 31, 31) else ink)
        view.background = RippleDrawable(ColorStateList.valueOf(0x335eddb7),
            rounded(if (active) accent else Color.rgb(29, 48, 58)), rounded(Color.WHITE))
    }
    private fun rounded(color: Int) = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(color) }
    private fun square() = LinearLayout.LayoutParams(dp(48), dp(48))
    private fun cardWidth(maximumDp: Int) = minOf(dp(maximumDp), resources.displayMetrics.widthPixels - dp(24))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); resumed = true; scene?.resume() }
    override fun onPause() { resumed = false; scene?.pause(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        saveAtlasState()
        outState.putBundle("bio_atlas_states", atlasStates)
        outState.putString("bio_atlas", activeAtlas.id)
        outState.putBoolean("bio_external_genitals_visible", showExternalGenitals)
        outState.putString("bio_journey", journey?.id)
        outState.putInt("bio_journey_step", journeyStep)
        outState.putBundle("bio_before_journey", beforeJourney)
        outState.putBoolean("bio_journey_panel", journeyPanelVisible)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        loading?.cancel()
        openPanel?.dismiss()
        scene?.releaseOwnedResources()
        scene = null
        super.onDestroy()
    }
}
