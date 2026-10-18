package com.Atom2Universe.app.games.puzzles.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
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

class MapActivity : PuzzleActivity<MapState>() {
    override val gameKey = "map"
    override val titleRes = R.string.map_title
    override val rulesRes = R.string.map_rules
    override val presets = listOf(
        Preset(R.string.map_preset, 15),
        Preset(R.string.map_preset, 25),
        Preset(R.string.map_preset, 40),
    )
    override val defaultPreset = 1
    private val chips = ArrayList<View>()

    override fun createBoard() = MapView(this)
    override fun generate(preset: Int, random: Random): MapState = when (preset) {
        0 -> MapState.generate(10, 10, 15, random)
        1 -> MapState.generate(14, 14, 25, random)
        else -> MapState.generate(18, 18, 40, random)
    }
    override fun encode(state: MapState) = state.encode()
    override fun decode(text: String) = MapState.decode(text)
    override fun isSolved(state: MapState) = state.isSolved
    override val hasHints = true
    override fun hint(state: MapState) = state.hint()

    /** Les quatre couleurs : on en choisit une, puis on touche les pays à peindre. */
    override fun createControls(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        for (k in 0..3) {
            val v = View(context).apply {
                setOnClickListener {
                    (board as MapView).brush = k
                    refreshChips()
                }
            }
            chips.add(v)
            addView(v, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                marginStart = (6 * dp).toInt(); marginEnd = (6 * dp).toInt()
            })
        }
        post { refreshChips() }
    }

    private fun refreshChips() {
        val brush = (board as MapView).brush
        chips.forEachIndexed { k, v ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(MapView.colour(palette, k))
                setStroke((if (k == brush) 4 else 1) * dp.toInt().coerceAtLeast(1), if (k == brush) palette.text else palette.outline)
            }
        }
    }
}

/** Appui : peindre le pays de la couleur choisie ; appui long : l'effacer. */
class MapView(context: Context) : PuzzleView<MapState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    var brush = 0

    override fun drawBoard(canvas: Canvas, state: MapState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val clash = state.clashes()
        for (i in state.region.indices) {
            grid.rect(i % state.w, i / state.w, r)
            val c = state.colours[state.region[i]]
            fill.color = if (c >= 0) colour(palette, c) else palette.cell
            canvas.drawRect(r.left - 0.5f, r.top - 0.5f, r.right + 0.5f, r.bottom + 0.5f, fill)
        }
        stroke.strokeWidth = cell * 0.1f
        for (i in state.region.indices) {
            val x = i % state.w; val y = i / state.w
            val a = state.region[i]
            if (x < state.w - 1 && state.region[i + 1] != a) {
                stroke.color = if (a in clash && state.region[i + 1] in clash && state.colours[a] == state.colours[state.region[i + 1]]) palette.error else palette.gridBold
                canvas.drawLine(grid.x(x + 1), grid.y(y), grid.x(x + 1), grid.y(y + 1), stroke)
            }
            if (y < state.h - 1 && state.region[i + state.w] != a) {
                stroke.color = if (a in clash && state.region[i + state.w] in clash && state.colours[a] == state.colours[state.region[i + state.w]]) palette.error else palette.gridBold
                canvas.drawLine(grid.x(x), grid.y(y + 1), grid.x(x + 1), grid.y(y + 1), stroke)
            }
        }
        stroke.color = palette.gridBold
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, stroke)
        // Un point au cœur des pays donnés.
        for (k in 0 until state.regions) {
            if (!state.fixed[k]) continue
            val cells = state.region.indices.filter { state.region[it] == k }
            val cx = cells.map { grid.cx(it % state.w) }.average().toFloat()
            val cy = cells.map { grid.cy(it / state.w) }.average().toFloat()
            val best = cells.minByOrNull { (grid.cx(it % state.w) - cx).let { d -> d * d } + (grid.cy(it / state.w) - cy).let { d -> d * d } }!!
            fill.color = palette.withAlpha(0xFF000000.toInt(), 0.45f)
            canvas.drawCircle(grid.cx(best % state.w), grid.cy(best / state.w), cell * 0.16f, fill)
        }
    }

    private fun act(x: Float, y: Float, erase: Boolean) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        s.paint(s.region[i], if (erase) -1 else brush)?.let { tick(); move(it) }
    }

    override fun onTap(x: Float, y: Float) = act(x, y, false)
    override fun onLongPress(x: Float, y: Float) = act(x, y, true)

    companion object {
        fun colour(palette: KitPalette, k: Int) = palette.piece(intArrayOf(0, 2, 4, 6)[k])
    }
}

class MapArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFA5D6A7.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = MapState.generate(10, 10, 14, Random(23))
        val colours = IntArray(s.regions) { if (it % 3 == 2) -1 else -2 }
        // Un coloriage valide pour l'illustration.
        val adj = s.adjacency
        for (r in 0 until s.regions) if (colours[r] == -2) colours[r] = (0..3).firstOrNull { k -> adj[r].none { colours[it] == k } } ?: -1
        drawWith(MapView(context), MapState(10, 10, s.region, colours, s.fixed, s.solution), canvas, boardRect(w, h, 1.1f), palette)
    }
}
