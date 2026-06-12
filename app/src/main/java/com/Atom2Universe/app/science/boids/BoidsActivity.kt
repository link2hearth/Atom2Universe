package com.Atom2Universe.app.science.boids

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class BoidsActivity : ThemedActivity() {

    private lateinit var boidsView: BoidsView
    private lateinit var playPauseBtn: ImageButton
    private lateinit var topBar: LinearLayout
    private lateinit var settingsPanel: ScrollView
    private lateinit var panelToggleIcon: ImageView

    private lateinit var presetBtn: TextView
    private lateinit var colorBtn: TextView
    private lateinit var edgeBtn: TextView
    private lateinit var speciesBtn: TextView
    private lateinit var touchBtn: TextView
    private lateinit var trailsBtn: TextView
    private lateinit var visionBtn: TextView
    private lateinit var predatorBtn: TextView
    private lateinit var clearObstaclesBtn: TextView

    private var presetIndex = 0
    private var colorModeIndex = BoidsView.COLOR_DIRECTION
    private var touchModeIndex = BoidsView.TOUCH_ATTRACT
    private var speciesCount = 1
    private var edgeWalls = true

    private lateinit var sepCtrl: SliderControl
    private lateinit var aliCtrl: SliderControl
    private lateinit var cohCtrl: SliderControl
    private lateinit var perceptionCtrl: SliderControl
    private lateinit var speedCtrl: SliderControl
    private lateinit var countCtrl: SliderControl

    /** Bouton-paramètre : affiche « Nom valeur » et ouvre un slider vertical en popup. */
    private inner class SliderControl(
        val labelRes: Int,
        val min: Int,
        val max: Int,
        var value: Int,
        val format: (Int) -> String,
        val onChange: (Int) -> Unit
    ) {
        lateinit var button: TextView

        fun update(v: Int) {
            value = v.coerceIn(min, max)
            updateLabel()
            onChange(value)
        }

        fun updateLabel() {
            button.text = getString(R.string.boids_param_value, getString(labelRes), format(value))
        }
    }

    private data class Preset(
        val nameRes: Int,
        val sep: Int, val ali: Int, val coh: Int,   // poids en %
        val perception: Int,                          // dp
        val speed: Int,                               // %
        val count: Int,
        val trails: Boolean,
        val colorMode: Int
    )

    private val presets by lazy {
        listOf(
            Preset(R.string.boids_preset_starlings, 90, 150, 90, 90, 160, 280, false, BoidsView.COLOR_DIRECTION),
            Preset(R.string.boids_preset_fish, 120, 100, 150, 70, 90, 250, false, BoidsView.COLOR_SPEED),
            Preset(R.string.boids_preset_gnats, 70, 5, 130, 50, 120, 120, false, BoidsView.COLOR_FIREFLIES),
            Preset(R.string.boids_preset_comets, 100, 120, 60, 60, 220, 60, true, BoidsView.COLOR_AURORA),
            Preset(R.string.boids_preset_chaos, 200, 0, 0, 60, 150, 200, false, BoidsView.COLOR_DIRECTION)
        )
    }

    private val colorNames by lazy {
        listOf(
            R.string.boids_color_direction,
            R.string.boids_color_speed,
            R.string.boids_color_aurora,
            R.string.boids_color_fireflies
        )
    }

    private val touchNames by lazy {
        listOf(
            R.string.boids_touch_attract,
            R.string.boids_touch_repel,
            R.string.boids_touch_obstacle
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        enableImmersiveMode()
    }

    private fun buildUI(): View {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A12.toInt())
        }

        topBar = buildTopBar(dp)
        root.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        boidsView = BoidsView(this)
        boidsView.onObstaclesChanged = { n ->
            clearObstaclesBtn.visibility = if (n > 0) View.VISIBLE else View.GONE
        }
        root.addView(boidsView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(buildPanelHandle(dp), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Le panneau de réglages est plafonné pour que la nuée garde la majorité de l'écran
        val maxPanelHeight = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        settingsPanel = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(maxPanelHeight, MeasureSpec.AT_MOST)
                )
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(buildBottomBar(dp))
        }
        root.addView(settingsPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    private fun buildPanelHandle(dp: Float): View {
        panelToggleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_expand_more)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF7B8CDE.toInt())
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF161624.toInt())
            contentDescription = getString(R.string.boids_toggle_panel)
            addView(panelToggleIcon, LinearLayout.LayoutParams((24 * dp).toInt(), (20 * dp).toInt()))
            setOnClickListener { togglePanel() }
        }
    }

    private fun togglePanel() {
        val collapsed = settingsPanel.visibility == View.VISIBLE
        val visibility = if (collapsed) View.GONE else View.VISIBLE
        settingsPanel.visibility = visibility
        topBar.visibility = visibility
        panelToggleIcon.setImageResource(
            if (collapsed) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )
    }

    private fun buildTopBar(dp: Float): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(0xFF12121E.toInt())
        }

        val backBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setOnClickListener { finish() }
        }
        bar.addView(backBtn, LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()))

        val title = TextView(this).apply {
            setText(R.string.boids_title)
            textSize = 16f
            setTextColor(0xFFE0E0FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val infoBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_help)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF7B8CDE.toInt())
            setOnClickListener { showInfoDialog() }
        }
        bar.addView(infoBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (36 * dp).toInt()))

        playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF7B8CDE.toInt())
            setOnClickListener { togglePlay() }
        }
        bar.addView(playPauseBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (36 * dp).toInt()))

        bar.addView(controlBtn(dp, getString(R.string.boids_reset)) { resetDefaults() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = (8 * dp).toInt()
        })

        bar.addView(controlBtn(dp, getString(R.string.boids_scatter)) { boidsView.scatter() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = (8 * dp).toInt()
        })

        return bar
    }

    /** Alterne entre les deux ambiances par défaut. */
    private var resetAlt = false

    /**
     * Restaure un état par défaut, en alternant à chaque appui entre
     * « Étourneaux » (murs, 1 espèce, attirer) et
     * « Moucherons » (torique, 2 espèces, repousser).
     */
    private fun resetDefaults() {
        val alt = resetAlt
        resetAlt = !resetAlt
        presetIndex = if (alt) 2 else 0
        applyPreset(presetIndex)
        speciesCount = if (alt) 2 else 1
        boidsView.speciesCount = speciesCount
        speciesBtn.text = speciesLabel()
        edgeWalls = !alt
        boidsView.edgeMode = if (edgeWalls) BoidsView.EDGE_WALLS else BoidsView.EDGE_WRAP
        edgeBtn.text = edgeLabel()
        touchModeIndex = if (alt) BoidsView.TOUCH_REPEL else BoidsView.TOUCH_ATTRACT
        boidsView.touchMode = touchModeIndex
        touchBtn.text = getString(touchNames[touchModeIndex])
        boidsView.visionEnabled = false
        setToggle(visionBtn, false)
        boidsView.setPredatorCount(0)
        setToggle(predatorBtn, false)
        boidsView.clearObstacles()
    }

    private fun buildBottomBar(dp: Float): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt())
            setBackgroundColor(0xFF12121E.toInt())
        }

        bar.addView(TextView(this).apply {
            setText(R.string.boids_hint)
            textSize = 10f
            setTextColor(0xFF777799.toInt())
            setPadding(0, 0, 0, (8 * dp).toInt())
        })

        // ── Rangée 1 : boutons cycliques + toggles ───────────────────────────
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        presetBtn = controlBtn(dp, getString(presets[0].nameRes)) { cyclePreset() }
        colorBtn = controlBtn(dp, getString(colorNames[colorModeIndex])) { cycleColor() }
        edgeBtn = controlBtn(dp, edgeLabel()) { cycleEdges() }
        speciesBtn = controlBtn(dp, speciesLabel()) { cycleSpecies() }
        touchBtn = controlBtn(dp, getString(touchNames[touchModeIndex])) { cycleTouch() }
        trailsBtn = controlBtn(dp, getString(R.string.boids_opt_trails)) { toggleTrails() }
        visionBtn = controlBtn(dp, getString(R.string.boids_opt_vision)) { toggleVision() }
        predatorBtn = controlBtn(dp, getString(R.string.boids_opt_predator)) { togglePredator() }
        clearObstaclesBtn = controlBtn(dp, getString(R.string.boids_touch_clear)) {
            boidsView.clearObstacles()
        }.apply {
            background = pillBackground(0xFF402430.toInt())
            setTextColor(0xFFFFAACC.toInt())
            visibility = View.GONE
        }

        listOf(presetBtn, colorBtn, edgeBtn, speciesBtn, touchBtn,
            trailsBtn, visionBtn, predatorBtn, clearObstaclesBtn).forEach { row1.addView(it) }
        setToggle(trailsBtn, false)
        setToggle(visionBtn, false)
        setToggle(predatorBtn, false)

        bar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row1)
        })

        // ── Rangée 2 : paramètres à slider popup ─────────────────────────────
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        speedCtrl = SliderControl(R.string.boids_speed, 30, 250,
            (boidsView.speedFactor * 100).toInt(), { "$it%" }) { boidsView.speedFactor = it / 100f }
        countCtrl = SliderControl(R.string.boids_count, BoidsView.MIN_BOIDS, BoidsView.MAX_BOIDS,
            280, { it.toString() }) { boidsView.setBoidCount(it) }
        cohCtrl = SliderControl(R.string.boids_cohesion, 0, 200,
            (boidsView.cohesionWeight * 100).toInt(), { "$it%" }) { boidsView.cohesionWeight = it / 100f }
        aliCtrl = SliderControl(R.string.boids_alignment, 0, 200,
            (boidsView.alignmentWeight * 100).toInt(), { "$it%" }) { boidsView.alignmentWeight = it / 100f }
        sepCtrl = SliderControl(R.string.boids_separation, 0, 200,
            (boidsView.separationWeight * 100).toInt(), { "$it%" }) { boidsView.separationWeight = it / 100f }
        perceptionCtrl = SliderControl(R.string.boids_perception, 20, 160,
            boidsView.perceptionDp.toInt(), { it.toString() }) { boidsView.perceptionDp = it.toFloat() }

        listOf(speedCtrl, countCtrl, cohCtrl, aliCtrl, sepCtrl, perceptionCtrl).forEach { ctrl ->
            ctrl.button = controlBtn(dp, "") { showSliderPopup(ctrl) }
            ctrl.updateLabel()
            row2.addView(ctrl.button)
        }

        bar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row2)
        })

        return bar
    }

    // ── Boutons ──────────────────────────────────────────────────────────────
    private fun controlBtn(dp: Float, label: CharSequence, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFFCCCCFF.toInt())
            background = pillBackground(0xFF24243C.toInt())
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (8 * dp).toInt() }
        }
    }

    private fun setToggle(btn: TextView, active: Boolean) {
        btn.isActivated = active
        btn.background = pillBackground(if (active) 0xFF3A3A66.toInt() else 0xFF1C1C30.toInt())
        btn.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFFAAAACC.toInt())
    }

    private fun pillBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 24f * resources.displayMetrics.density
        setColor(color)
    }

    // ── Boutons cycliques ────────────────────────────────────────────────────
    private fun cyclePreset() {
        presetIndex = (presetIndex + 1) % presets.size
        applyPreset(presetIndex)
    }

    private fun applyPreset(index: Int) {
        val p = presets[index]
        presetBtn.text = getString(p.nameRes)
        sepCtrl.update(p.sep)
        aliCtrl.update(p.ali)
        cohCtrl.update(p.coh)
        perceptionCtrl.update(p.perception)
        speedCtrl.update(p.speed)
        countCtrl.update(p.count)
        boidsView.trailsEnabled = p.trails
        setToggle(trailsBtn, p.trails)
        setColorMode(p.colorMode)
    }

    private fun cycleColor() {
        setColorMode((colorModeIndex + 1) % colorNames.size)
    }

    private fun setColorMode(index: Int) {
        colorModeIndex = index
        boidsView.colorMode = index
        colorBtn.text = getString(colorNames[index])
    }

    private fun cycleEdges() {
        edgeWalls = !edgeWalls
        boidsView.edgeMode = if (edgeWalls) BoidsView.EDGE_WALLS else BoidsView.EDGE_WRAP
        edgeBtn.text = edgeLabel()
    }

    private fun edgeLabel(): String = getString(
        R.string.boids_param_value,
        getString(R.string.boids_edges),
        getString(if (edgeWalls) R.string.boids_edge_walls else R.string.boids_edge_wrap)
    )

    private fun cycleSpecies() {
        speciesCount = speciesCount % BoidsView.MAX_SPECIES + 1
        boidsView.speciesCount = speciesCount
        speciesBtn.text = speciesLabel()
    }

    private fun speciesLabel(): String = getString(
        R.string.boids_param_value,
        getString(R.string.boids_species),
        speciesCount.toString()
    )

    private fun cycleTouch() {
        touchModeIndex = (touchModeIndex + 1) % touchNames.size
        boidsView.touchMode = touchModeIndex
        touchBtn.text = getString(touchNames[touchModeIndex])
    }

    // ── Toggles ──────────────────────────────────────────────────────────────
    private fun toggleTrails() {
        boidsView.trailsEnabled = !boidsView.trailsEnabled
        setToggle(trailsBtn, boidsView.trailsEnabled)
    }

    private fun toggleVision() {
        boidsView.visionEnabled = !boidsView.visionEnabled
        setToggle(visionBtn, boidsView.visionEnabled)
    }

    private fun togglePredator() {
        val on = !predatorBtn.isActivated
        boidsView.setPredatorCount(if (on) 2 else 0)
        setToggle(predatorBtn, on)
    }

    // ── Popup à slider vertical ──────────────────────────────────────────────
    private fun showSliderPopup(ctrl: SliderControl) {
        val dp = resources.displayMetrics.density

        val valueText = TextView(this).apply {
            text = ctrl.format(ctrl.value)
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        val seek = SeekBar(this).apply {
            max = ctrl.max - ctrl.min
            progress = ctrl.value - ctrl.min
            rotation = -90f
            layoutParams = FrameLayout.LayoutParams(
                (150 * dp).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = progress + ctrl.min
                    valueText.text = ctrl.format(v)
                    ctrl.update(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        val frame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(seek)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable().apply {
                cornerRadius = 14f * dp
                setColor(0xF21C1C30.toInt())
            }
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            addView(valueText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(frame, LinearLayout.LayoutParams(
                (56 * dp).toInt(), (158 * dp).toInt()
            ).also { it.topMargin = (4 * dp).toInt() })
        }

        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popup = PopupWindow(content, content.measuredWidth, content.measuredHeight, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8f * dp
        }
        popup.showAsDropDown(
            ctrl.button,
            (ctrl.button.width - content.measuredWidth) / 2,
            -(content.measuredHeight + ctrl.button.height + (8 * dp).toInt())
        )
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.boids_info_title)
            .setMessage(R.string.boids_info_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun togglePlay() {
        if (boidsView.isRunning) {
            boidsView.stop()
            playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
        } else {
            boidsView.start()
            playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    override fun onResume() {
        super.onResume()
        boidsView.start()
        playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
    }

    override fun onPause() {
        super.onPause()
        boidsView.stop()
        playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
    }
}
