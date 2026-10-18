package com.Atom2Universe.app.science.reaction

import com.Atom2Universe.app.science.SciencePalette
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.roundToInt

class ReactionDiffusionActivity : ThemedActivity() {

    private val palette by lazy { SciencePalette(this) }

    private lateinit var rdView: ReactionDiffusionView
    private lateinit var playPauseBtn: ImageButton

    private lateinit var feedSeek: SeekBar
    private lateinit var killSeek: SeekBar

    // Pause voulue par l'utilisateur : onResume ne relance pas une simulation mise en pause.
    private var pausedByUser = false

    private var paletteChips = mutableListOf<TextView>()
    private var presetChips = mutableListOf<TextView>()

    // (nom, feed, kill) — combinaisons classiques de l'espace de paramètres Gray-Scott
    private data class Preset(val nameRes: Int, val feed: Float, val kill: Float)

    private val presets by lazy {
        listOf(
            Preset(R.string.rd_preset_coral, 0.054f, 0.062f),
            Preset(R.string.rd_preset_mitosis, 0.037f, 0.065f),
            Preset(R.string.rd_preset_maze, 0.029f, 0.057f),
            Preset(R.string.rd_preset_worms, 0.058f, 0.065f),
            Preset(R.string.rd_preset_spots, 0.030f, 0.062f),
            Preset(R.string.rd_preset_waves, 0.014f, 0.054f),
            Preset(R.string.rd_preset_spirals, 0.018f, 0.051f),
            Preset(R.string.rd_preset_bubbles, 0.082f, 0.060f)
        )
    }

    private val paletteNames by lazy {
        listOf(
            R.string.rd_palette_inferno,
            R.string.rd_palette_ocean,
            R.string.rd_palette_acid,
            R.string.rd_palette_plasma,
            R.string.rd_palette_coral,
            R.string.rd_palette_mono
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        root.addView(buildTopBar(dp), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        rdView = ReactionDiffusionView(this)
        root.addView(rdView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(buildBottomBar(dp), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    private fun buildTopBar(dp: Float): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(palette.surface)
        }

        val backBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_app_back)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.text)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }
        bar.addView(backBtn, LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()))

        val title = TextView(this).apply {
            setText(R.string.rd_title)
            textSize = 16f
            setTextColor(palette.text)
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            setOnClickListener { togglePlay() }
        }
        bar.addView(playPauseBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (36 * dp).toInt()))

        bar.addView(iconTextBtn(dp, R.string.rd_random) {
            rdView.randomize()
        })
        bar.addView(iconTextBtn(dp, R.string.rd_clear) {
            rdView.clearAndSeed()
        })

        return bar
    }

    private fun iconTextBtn(dp: Float, labelRes: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            setText(labelRes)
            textSize = 12f
            setTextColor(palette.text)
            background = pillBackground(palette.raised)
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
            setBackgroundColor(palette.surface)
        }

        // Indice tactile
        bar.addView(TextView(this).apply {
            setText(R.string.rd_hint)
            textSize = 10f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, (8 * dp).toInt())
        })

        // ── Présets ──────────────────────────────────────────────────────────
        bar.addView(sectionLabel(dp, R.string.rd_presets))
        bar.addView(buildPresetRow(dp))

        // ── Palettes ─────────────────────────────────────────────────────────
        bar.addView(sectionLabel(dp, R.string.rd_palette))
        bar.addView(buildPaletteRow(dp))

        // ── Curseurs ─────────────────────────────────────────────────────────
        // roundToInt : 0.029f × 1000 = 28.999998, que toInt() ramenait à 28.
        feedSeek = addSeekRow(bar, dp, R.string.rd_feed, 8, 110, (rdView.feed * 1000).roundToInt(),
            { "%.3f".format(it / 1000f) }) { rdView.feed = it / 1000f }

        killSeek = addSeekRow(bar, dp, R.string.rd_kill, 40, 75, (rdView.kill * 1000).roundToInt(),
            { "%.3f".format(it / 1000f) }) { rdView.kill = it / 1000f }

        addSeekRow(bar, dp, R.string.rd_speed, 1, 24, rdView.iterationsPerFrame,
            { it.toString() }) { rdView.iterationsPerFrame = it }

        addSeekRow(bar, dp, R.string.rd_brush, 1, 12, rdView.brushRadius,
            { it.toString() }) { rdView.brushRadius = it }

        return bar
    }

    private fun sectionLabel(dp: Float, res: Int): TextView = TextView(this).apply {
        setText(res)
        textSize = 11f
        setTextColor(palette.secondary)
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
    }

    private fun buildPresetRow(dp: Float): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presets.forEachIndexed { index, preset ->
            val chip = TextView(this).apply {
                setText(preset.nameRes)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                setOnClickListener { selectPreset(index) }
            }
            presetChips.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (8 * dp).toInt() })
        }
        scroll.addView(row)
        highlightPreset(0)
        return scroll
    }

    private fun buildPaletteRow(dp: Float): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        paletteNames.forEachIndexed { index, nameRes ->
            val chip = TextView(this).apply {
                setText(nameRes)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                setOnClickListener { selectPalette(index) }
            }
            paletteChips.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (8 * dp).toInt() })
        }
        scroll.addView(row)
        highlightPalette(0)
        return scroll
    }

    private fun selectPreset(index: Int) {
        val p = presets[index]
        // Le curseur affichait 0,028 pour le préréglage Labyrinthe (0,029) : troncature du float.
        feedSeek.progress = (p.feed * 1000).roundToInt() - 8
        killSeek.progress = (p.kill * 1000).roundToInt() - 40
        rdView.feed = p.feed
        rdView.kill = p.kill
        rdView.clearAndSeed()
        highlightPreset(index)
    }

    private fun selectPalette(index: Int) {
        rdView.setPalette(index)
        highlightPalette(index)
    }

    private fun highlightPreset(index: Int) {
        presetChips.forEachIndexed { i, chip ->
            val active = i == index
            chip.background = pillBackground(if (active) palette.accent else palette.raised)
            chip.setTextColor(if (active) palette.onAccent else palette.text)
        }
    }

    private fun highlightPalette(index: Int) {
        paletteChips.forEachIndexed { i, chip ->
            val active = i == index
            chip.background = pillBackground(if (active) palette.accent else palette.raised)
            chip.setTextColor(if (active) palette.onAccent else palette.text)
        }
    }

    private fun pillBackground(color: Int): GradientDrawable = palette.shape(color, 24f)

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
            setTextColor(palette.secondary)
            minWidth = (80 * dp).toInt()
        }
        row.addView(label)

        val valueText = TextView(this).apply {
            text = format(initial)
            textSize = 11f
            setTextColor(palette.text)
            minWidth = (48 * dp).toInt()
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }

        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = (initial - min).coerceIn(0, max - min)
            contentDescription = getString(labelRes)
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

    private fun togglePlay() {
        pausedByUser = rdView.isRunning
        if (rdView.isRunning) rdView.stop() else rdView.start()
        updatePlayButton()
    }

    private fun updatePlayButton() {
        val running = rdView.isRunning
        playPauseBtn.setImageResource(if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        playPauseBtn.contentDescription = getString(if (running) R.string.gol_pause else R.string.gol_play)
    }

    override fun onResume() {
        super.onResume()
        if (!pausedByUser) rdView.start()
        updatePlayButton()
    }

    override fun onPause() {
        super.onPause()
        rdView.stop()
        updatePlayButton()
    }
}
