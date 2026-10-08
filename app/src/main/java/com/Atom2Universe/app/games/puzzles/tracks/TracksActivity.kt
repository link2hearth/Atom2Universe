package com.Atom2Universe.app.games.puzzles.tracks

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.EdgeView
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import kotlin.math.hypot
import kotlin.random.Random

class TracksActivity : PuzzleActivity<TracksState>() {
    override val gameKey = "tracks"
    override val titleRes = R.string.tracks_title
    override val rulesRes = R.string.tracks_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 10),
    )
    override val defaultPreset = 1

    override fun createBoard() = TracksView(this)
    override fun generate(preset: Int, random: Random): TracksState {
        val size = listOf(6, 8, 10)[preset]
        return TracksState.generate(size, size, random)
    }
    override fun encode(state: TracksState) = state.encode()
    override fun decode(text: String) = TracksState.decode(text)
    override fun isSolved(state: TracksState) = state.isSolved
    override val hasHints = true
    override fun hint(state: TracksState) = state.hint()
}

/** La voie est dessinée en vrais rails, avec leurs traverses. */
class TracksView(context: Context) : EdgeView<TracksState>(context) {
    override val dual = true
    override fun edgeGrid(state: TracksState) = state.grid
    override fun edgeValue(state: TracksState, e: Int) = state.edges[e]
    override fun setEdges(state: TracksState, edges: Collection<Int>, value: Int) = state.set(edges, value)
    override fun margins(state: TracksState) = floatArrayOf(0.6f, 0.8f, 0.8f, 0.6f)
    override fun usable(state: TracksState, e: Int) = !state.fixed[e] && TracksState.borderAllowed(state.grid, state.entry, state.exit, e)

    override fun drawBoard(canvas: Canvas, state: TracksState, area: RectF) {
        layout(state, area)
        val cell = grid.cell
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r, cell * 0.03f)
            fill.color = palette.cell
            canvas.drawRect(r, fill)
        }
        val g = state.grid
        // Le kit dessine croix et glissé en cours ; les traits posés, eux, deviennent des rails.
        drawEdges(canvas, state) { 0 }
        for (e in 0 until g.count) if (state.edges[e] == 1) rails(canvas, segment(g, e), cell, if (state.fixed[e]) palette.text else palette.accent)
        for (y in 0 until state.h) {
            val used = state.rowUsed(y)
            val c = when { used > state.rowCounts[y] -> palette.error; used == state.rowCounts[y] -> palette.tertiary; else -> palette.text }
            canvas.centeredText(state.rowCounts[y].toString(), grid.right + cell * 0.45f, grid.cy(y), cell * 0.42f, c, bold = true)
        }
        for (x in 0 until state.w) {
            val used = state.colUsed(x)
            val c = when { used > state.colCounts[x] -> palette.error; used == state.colCounts[x] -> palette.tertiary; else -> palette.text }
            canvas.centeredText(state.colCounts[x].toString(), grid.cx(x), grid.top - cell * 0.45f, cell * 0.42f, c, bold = true)
        }
        canvas.centeredText(context.getString(R.string.tracks_in), grid.left - cell * 0.35f, grid.cy(state.entry) - cell * 0.35f, cell * 0.3f, palette.secondary, bold = true)
        canvas.centeredText(context.getString(R.string.tracks_out), grid.cx(state.exit) + cell * 0.35f, grid.bottom + cell * 0.4f, cell * 0.3f, palette.secondary, bold = true)
    }

    private fun rails(canvas: Canvas, s: FloatArray, cell: Float, colour: Int) {
        val dx = s[2] - s[0]; val dy = s[3] - s[1]
        val len = hypot(dx, dy)
        if (len < 1f) return
        val nx = -dy / len * cell * 0.14f; val ny = dx / len * cell * 0.14f
        stroke.color = palette.withAlpha(colour, 0.55f)
        stroke.strokeWidth = cell * 0.05f
        val ties = (len / (cell * 0.25f)).toInt()
        for (k in 0..ties) {
            val t = k / ties.coerceAtLeast(1).toFloat()
            val px = s[0] + dx * t; val py = s[1] + dy * t
            canvas.drawLine(px - nx * 1.4f, py - ny * 1.4f, px + nx * 1.4f, py + ny * 1.4f, stroke)
        }
        stroke.color = colour
        stroke.strokeWidth = cell * 0.05f
        canvas.drawLine(s[0] + nx, s[1] + ny, s[2] + nx, s[3] + ny, stroke)
        canvas.drawLine(s[0] - nx, s[1] - ny, s[2] - nx, s[3] - ny, stroke)
    }
}

class TracksArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFD4A373.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        drawWith(TracksView(context), TracksState.generate(6, 6, Random(31)), canvas, boardRect(w, h, 1.05f), palette)
    }
}
