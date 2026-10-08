package com.Atom2Universe.app.games.puzzles.palisade

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.EdgeView
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import kotlin.random.Random

class PalisadeActivity : PuzzleActivity<PalisadeState>() {
    override val gameKey = "palisade"
    override val titleRes = R.string.palisade_title
    override val rulesRes = R.string.palisade_rules
    override val presets = listOf(
        Preset(R.string.palisade_preset, 5, 5, 5),
        Preset(R.string.palisade_preset, 6, 6, 4),
        Preset(R.string.palisade_preset, 8, 8, 4),
    )
    override val defaultPreset = 1

    override fun createBoard() = PalisadeView(this)
    override fun generate(preset: Int, random: Random): PalisadeState = when (preset) {
        0 -> PalisadeState.generate(5, 5, 5, random)
        1 -> PalisadeState.generate(6, 6, 4, random)
        else -> PalisadeState.generate(8, 8, 4, random)
    }
    override fun encode(state: PalisadeState) = state.encode()
    override fun decode(text: String) = PalisadeState.decode(text)
    override fun isSolved(state: PalisadeState) = state.isSolved
    override val hasHints = true
    override fun hint(state: PalisadeState) = state.hint()
}

/** Une région fermée qui a la bonne taille prend une couleur. */
class PalisadeView(context: Context) : EdgeView<PalisadeState>(context) {
    override fun edgeGrid(state: PalisadeState) = state.grid
    override fun edgeValue(state: PalisadeState, e: Int) = state.edges[e]
    override fun setEdges(state: PalisadeState, edges: Collection<Int>, value: Int) = state.set(edges, value)
    override fun usable(state: PalisadeState, e: Int) = !state.border(e)

    override fun drawBoard(canvas: Canvas, state: PalisadeState, area: RectF) {
        layout(state, area)
        val cell = grid.cell
        val id = state.regions()
        val sizes = IntArray(state.w * state.h)
        for (r0 in id) sizes[r0]++
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r)
            fill.color = if (sizes[id[i]] == state.k) palette.blend(palette.cell, palette.piece(id[i] * 3), if (palette.isLight) 0.3f else 0.35f)
                else palette.cell
            canvas.drawRect(r, fill)
        }
        stroke.color = palette.gridLine; stroke.strokeWidth = cell * 0.02f
        for (k in 0..state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        for (i in state.clues.indices) {
            val c = state.clues[i]
            if (c < 0) continue
            grid.rect(i % state.w, i / state.w, r)
            val n = state.walls(i)
            canvas.centeredText(c.toString(), r.centerX(), r.centerY(), cell * 0.45f,
                when { n > c -> palette.error; n == c -> palette.tertiary; else -> palette.text }, bold = true)
        }
        drawEdges(canvas, state) { palette.text }
        stroke.color = palette.text; stroke.strokeWidth = cell * 0.11f
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, stroke)
    }
}

class PalisadeArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF8D6E63.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        drawWith(PalisadeView(context), PalisadeState.generate(5, 5, 5, Random(34)), canvas, boardRect(w, h, 1.05f), palette)
    }
}
