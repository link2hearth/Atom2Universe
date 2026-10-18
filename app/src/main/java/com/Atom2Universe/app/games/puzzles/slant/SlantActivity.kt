package com.Atom2Universe.app.games.puzzles.slant

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.random.Random

class SlantActivity : PuzzleActivity<SlantState>() {
    override val gameKey = "slant"
    override val titleRes = R.string.slant_title
    override val rulesRes = R.string.slant_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 12, 12),
    )
    override val defaultPreset = 1

    override fun createBoard() = SlantView(this)
    override fun generate(preset: Int, random: Random): SlantState {
        val size = listOf(5, 8, 10, 12)[preset]
        return SlantState.generate(size, size, random)
    }
    override fun encode(state: SlantState) = state.encode()
    override fun decode(text: String) = SlantState.decode(text)
    override fun isSolved(state: SlantState) = state.isSolved
    override val hasHints = true
    override fun hint(state: SlantState) = state.hint()
}

/** Appui : vide → \ → / ; appui long dans l'autre sens. Les boucles sont tracées en rouge. */
class SlantView(context: Context) : PuzzleView<SlantState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    override fun drawBoard(canvas: Canvas, state: SlantState, area: RectF) {
        grid.fit(area, state.w, state.h, 0.35f, 0.35f, 0.35f, 0.35f)
        val cell = grid.cell
        fill.color = palette.cell
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, fill)
        stroke.color = palette.gridLine
        stroke.strokeWidth = cell * 0.02f
        for (k in 0..state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        val loops = state.loopCells()
        stroke.strokeWidth = cell * 0.09f
        for (c in state.cells.indices) {
            val v = state.cells[c]
            if (v < 0) continue
            grid.rect(c % state.w, c / state.w, r)
            stroke.color = if (c in loops) palette.error else palette.text
            if (v == SlantState.BACK) canvas.drawLine(r.left, r.top, r.right, r.bottom, stroke)
            else canvas.drawLine(r.right, r.top, r.left, r.bottom, stroke)
        }
        for (v in state.clues.indices) {
            val clue = state.clues[v]
            if (clue < 0) continue
            val x = grid.x(v % (state.w + 1)); val y = grid.y(v / (state.w + 1))
            val (on, open) = SlantState.touchingOf(state.w, state.h, state.cells, v)
            val done = on == clue && open == 0
            fill.color = if (on > clue || on + open < clue) palette.error else if (done) palette.raised else palette.surface
            canvas.drawCircle(x, y, cell * 0.26f, fill)
            stroke.color = palette.gridBold; stroke.strokeWidth = cell * 0.035f
            canvas.drawCircle(x, y, cell * 0.26f, stroke)
            canvas.centeredText(clue.toString(), x, y, cell * 0.32f, if (done) palette.tertiary else palette.text, bold = true)
        }
    }

    private fun act(x: Float, y: Float, forward: Boolean) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        val v = s.cells[i]
        val next = if (forward) when (v) { -1 -> 0; 0 -> 1; else -> -1 } else when (v) { -1 -> 1; 1 -> 0; else -> -1 }
        tick(); move(s.with(i, next))
    }

    override fun onTap(x: Float, y: Float) = act(x, y, true)
    override fun onLongPress(x: Float, y: Float) = act(x, y, false)
}

class SlantArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFBCAAA4.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = SlantState.generate(6, 6, Random(26))
        val rnd = Random(3)
        val cells = IntArray(36) { if (rnd.nextInt(3) == 0) -1 else rnd.nextInt(2) }
        drawWith(SlantView(context), SlantState(6, 6, s.clues, cells, s.solution), canvas, boardRect(w, h, 1.1f), palette)
    }
}
