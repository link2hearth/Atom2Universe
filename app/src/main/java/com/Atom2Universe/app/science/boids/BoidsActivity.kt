package com.Atom2Universe.app.science.boids

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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

    private lateinit var sepSeek: SeekBar
    private lateinit var aliSeek: SeekBar
    private lateinit var cohSeek: SeekBar
    private lateinit var perceptionSeek: SeekBar
    private lateinit var speedSeek: SeekBar
    private lateinit var countSeek: SeekBar

    private val presetChips = mutableListOf<TextView>()
    private val colorChips = mutableListOf<TextView>()
    private val touchChips = mutableListOf<TextView>()
    private lateinit var trailsChip: TextView
    private lateinit var visionChip: TextView
    private lateinit var predatorChip: TextView

    private lateinit var settingsPanel: ScrollView
    private lateinit var panelToggleIcon: ImageView

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

        root.addView(buildTopBar(dp), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        boidsView = BoidsView(this)
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
        settingsPanel.visibility = if (collapsed) View.GONE else View.VISIBLE
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

        bar.addView(pillBtn(dp, R.string.boids_scatter) { boidsView.scatter() })

        return bar
    }

    private fun pillBtn(dp: Float, labelRes: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            setText(labelRes)
            textSize = 12f
            setTextColor(0xFFCCCCFF.toInt())
            background = pillBackground(0xFF24243C.toInt())
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = (8 * dp).toInt() }
        }
    }

    private fun buildBottomBar(dp: Float): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (18 * dp).toInt())
            setBackgroundColor(0xFF12121E.toInt())
        }

        bar.addView(TextView(this).apply {
            setText(R.string.boids_hint)
            textSize = 10f
            setTextColor(0xFF777799.toInt())
            setPadding(0, 0, 0, (8 * dp).toInt())
        })

        bar.addView(sectionLabel(dp, R.string.boids_behaviors))
        bar.addView(buildPresetRow(dp))

        bar.addView(sectionLabel(dp, R.string.boids_colors))
        bar.addView(buildColorRow(dp))

        bar.addView(sectionLabel(dp, R.string.boids_options))
        bar.addView(buildOptionsRow(dp))

        bar.addView(sectionLabel(dp, R.string.boids_touch))
        bar.addView(buildTouchRow(dp))

        sepSeek = addSeekRow(bar, dp, R.string.boids_separation, 0, 200,
            (boidsView.separationWeight * 100).toInt(), { "$it%" }) {
            boidsView.separationWeight = it / 100f
        }
        aliSeek = addSeekRow(bar, dp, R.string.boids_alignment, 0, 200,
            (boidsView.alignmentWeight * 100).toInt(), { "$it%" }) {
            boidsView.alignmentWeight = it / 100f
        }
        cohSeek = addSeekRow(bar, dp, R.string.boids_cohesion, 0, 200,
            (boidsView.cohesionWeight * 100).toInt(), { "$it%" }) {
            boidsView.cohesionWeight = it / 100f
        }
        perceptionSeek = addSeekRow(bar, dp, R.string.boids_perception, 20, 160,
            boidsView.perceptionDp.toInt(), { it.toString() }) {
            boidsView.perceptionDp = it.toFloat()
        }
        speedSeek = addSeekRow(bar, dp, R.string.boids_speed, 30, 250,
            (boidsView.speedFactor * 100).toInt(), { "$it%" }) {
            boidsView.speedFactor = it / 100f
        }
        countSeek = addSeekRow(bar, dp, R.string.boids_count, BoidsView.MIN_BOIDS, BoidsView.MAX_BOIDS,
            280, { it.toString() }) {
            boidsView.setBoidCount(it)
        }

        return bar
    }

    private fun sectionLabel(dp: Float, res: Int): TextView = TextView(this).apply {
        setText(res)
        textSize = 11f
        setTextColor(0xFF8888AA.toInt())
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
    }

    private fun chipRow(dp: Float, build: (LinearLayout) -> Unit): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        build(row)
        scroll.addView(row)
        return scroll
    }

    private fun chip(dp: Float, labelRes: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        setText(labelRes)
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.marginEnd = (8 * dp).toInt() }
    }

    private fun buildPresetRow(dp: Float): HorizontalScrollView = chipRow(dp) { row ->
        presets.forEachIndexed { index, preset ->
            val c = chip(dp, preset.nameRes) { applyPreset(index) }
            presetChips.add(c)
            row.addView(c)
        }
        highlightSingle(presetChips, 0)
    }

    private fun buildColorRow(dp: Float): HorizontalScrollView = chipRow(dp) { row ->
        colorNames.forEachIndexed { index, nameRes ->
            val c = chip(dp, nameRes) { selectColorMode(index) }
            colorChips.add(c)
            row.addView(c)
        }
        highlightSingle(colorChips, boidsView.colorMode)
    }

    private fun buildOptionsRow(dp: Float): HorizontalScrollView = chipRow(dp) { row ->
        trailsChip = chip(dp, R.string.boids_opt_trails) {
            boidsView.trailsEnabled = !boidsView.trailsEnabled
            setChipActive(trailsChip, boidsView.trailsEnabled)
        }
        visionChip = chip(dp, R.string.boids_opt_vision) {
            boidsView.visionEnabled = !boidsView.visionEnabled
            setChipActive(visionChip, boidsView.visionEnabled)
        }
        predatorChip = chip(dp, R.string.boids_opt_predator) {
            val on = !predatorChip.isActivated
            boidsView.setPredatorCount(if (on) 2 else 0)
            setChipActive(predatorChip, on)
        }
        row.addView(trailsChip)
        row.addView(visionChip)
        row.addView(predatorChip)
        setChipActive(trailsChip, false)
        setChipActive(visionChip, false)
        setChipActive(predatorChip, false)
    }

    private fun buildTouchRow(dp: Float): HorizontalScrollView = chipRow(dp) { row ->
        touchNames.forEachIndexed { index, nameRes ->
            val c = chip(dp, nameRes) {
                boidsView.touchMode = index
                highlightSingle(touchChips, index)
            }
            touchChips.add(c)
            row.addView(c)
        }
        row.addView(chip(dp, R.string.boids_touch_clear) { boidsView.clearObstacles() }.apply {
            background = pillBackground(0xFF402430.toInt())
            setTextColor(0xFFFFAACC.toInt())
        })
        highlightSingle(touchChips, BoidsView.TOUCH_ATTRACT)
    }

    private fun applyPreset(index: Int) {
        val p = presets[index]
        sepSeek.progress = p.sep
        aliSeek.progress = p.ali
        cohSeek.progress = p.coh
        perceptionSeek.progress = p.perception - 20
        speedSeek.progress = p.speed - 30
        countSeek.progress = p.count - BoidsView.MIN_BOIDS
        boidsView.trailsEnabled = p.trails
        setChipActive(trailsChip, p.trails)
        selectColorMode(p.colorMode)
        highlightSingle(presetChips, index)
    }

    private fun selectColorMode(index: Int) {
        boidsView.colorMode = index
        highlightSingle(colorChips, index)
    }

    private fun highlightSingle(chips: List<TextView>, index: Int) {
        chips.forEachIndexed { i, c -> setChipActive(c, i == index) }
    }

    private fun setChipActive(chipView: TextView, active: Boolean) {
        chipView.isActivated = active
        chipView.background = pillBackground(if (active) 0xFF3A3A66.toInt() else 0xFF1C1C30.toInt())
        chipView.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFFAAAACC.toInt())
    }

    private fun pillBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 24f * resources.displayMetrics.density
        setColor(color)
    }

    private fun addSeekRow(
        parent: LinearLayout,
        dp: Float,
        labelRes: Int,
        min: Int, max: Int, initial: Int,
        format: (Int) -> String,
        onChanged: (Int) -> Unit
    ): SeekBar {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (2 * dp).toInt() }
        }

        val label = TextView(this).apply {
            setText(labelRes)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            minWidth = (80 * dp).toInt()
        }
        row.addView(label)

        val valueText = TextView(this).apply {
            text = format(initial)
            textSize = 11f
            setTextColor(0xFFCCCCFF.toInt())
            minWidth = (48 * dp).toInt()
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }

        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = (initial - min).coerceIn(0, max - min)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    valueText.text = format(v)
                    onChanged(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        row.addView(seek)
        row.addView(valueText)
        parent.addView(row)
        return seek
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
