package com.Atom2Universe.app.games.puzzles.flood

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.random.Random

class FloodActivity : PuzzleActivity<FloodState>() {
    override val gameKey = "flood"
    override val titleRes = R.string.flood_title
    override val rulesRes = R.string.flood_rules
    private val configs = listOf(Triple(10, 5, 4), Triple(12, 6, 2), Triple(14, 6, 0), Triple(18, 8, 0))
    override val presets = listOf(
        Preset(R.string.flood_preset, 10, 10, 5),
        Preset(R.string.flood_preset, 12, 12, 6),
        Preset(R.string.flood_preset, 14, 14, 6),
        Preset(R.string.flood_preset, 18, 18, 8),
    )
    override val defaultPreset = 1

    private val swatches = ArrayList<View>()
    private var swatchRow: LinearLayout? = null

    override fun createBoard() = FloodView(this)
    override fun generate(preset: Int, random: Random): FloodState {
        val (size, colours, leeway) = configs[preset]
        return FloodState.generate(size, colours, leeway, random)
    }
    override fun encode(state: FloodState) = state.encode()
    override fun decode(text: String) = FloodState.decode(text)
    override fun isSolved(state: FloodState) = state.isSolved
    /** L'astuce est une démonstration : la fin jouée couleur par couleur, même si les coups sont épuisés. */
    override val hasHints = true
    override val demoStepMs = 700L
    override val demoWhenLost = true
    override fun demo(state: FloodState) = state.demo()
    override fun isLost(state: FloodState) = state.isLost
    override fun statusText(state: FloodState) = getString(R.string.flood_moves, state.moves, state.limit)

    /** Une rangée de pastilles de couleur : on peut choisir sa couleur ici ou sur la grille. */
    override fun createControls(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        swatchRow = this
    }

    override fun onStateShown(state: FloodState) {
        val row = swatchRow ?: return
        if (row.childCount != state.colours) {
            row.removeAllViews()
            swatches.clear()
            for (c in 0 until state.colours) {
                val v = View(this).apply {
                    setOnClickListener { current?.flood(c)?.let { next -> board.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); play(next) } }
                }
                swatches.add(v)
                row.addView(v, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                    marginStart = (5 * dp).toInt(); marginEnd = (5 * dp).toInt()
                })
            }
        }
        swatches.forEachIndexed { c, v ->
            v.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(palette.piece(c))
                setStroke((3 * dp).toInt(), if (c == state.cells[0]) palette.text else palette.outline)
            }
            v.alpha = if (c == state.cells[0]) 0.45f else 1f
        }
    }
}

class FloodView(context: Context) : PuzzleView<FloodState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    override fun drawBoard(canvas: Canvas, state: FloodState, area: RectF) {
        grid.fit(area, state.size, state.size)
        val cell = grid.cell
        val region = state.region()
        fill.color = palette.raised
        canvas.drawRoundRect(grid.left - cell * 0.15f, grid.top - cell * 0.15f, grid.right + cell * 0.15f,
            grid.bottom + cell * 0.15f, cell * 0.4f, cell * 0.4f, fill)
        for (i in state.cells.indices) {
            grid.rect(i % state.size, i / state.size, r)
            fill.color = palette.piece(state.cells[i])
            canvas.drawRect(r.left - 0.5f, r.top - 0.5f, r.right + 0.5f, r.bottom + 0.5f, fill)
        }
        // Le contour de la tache conquise.
        stroke.color = palette.withAlpha(palette.text, 0.85f)
        stroke.strokeWidth = cell * 0.08f
        for (i in state.cells.indices) {
            if (!region[i]) continue
            val x = i % state.size; val y = i / state.size
            if (x == state.size - 1 || !region[i + 1]) canvas.drawLine(grid.x(x + 1), grid.y(y), grid.x(x + 1), grid.y(y + 1), stroke)
            if (y == state.size - 1 || !region[i + state.size]) canvas.drawLine(grid.x(x), grid.y(y + 1), grid.x(x + 1), grid.y(y + 1), stroke)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        s.flood(s.cells[i])?.let { tick(); move(it) }
    }
}

class FloodArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF64B5F6.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val start = IntArray(100) { Random(it * 31 + 7).nextInt(5) }
        var cells = FloodState.fill(10, start, 2)
        cells = FloodState.fill(10, cells, 4)
        cells = FloodState.fill(10, cells, 1)
        drawWith(FloodView(context), FloodState(10, 5, cells, 3, 20), canvas, boardRect(w, h, 1.15f), palette)
    }
}
