package com.Atom2Universe.app.games.puzzles.loopy

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.EdgeView
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import kotlin.random.Random

class LoopyActivity : PuzzleActivity<LoopyState>() {
    override val gameKey = "loopy"
    override val titleRes = R.string.loopy_title
    override val rulesRes = R.string.loopy_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 7, 7),
        Preset(R.string.kit_size, 10, 10),
    )
    override val defaultPreset = 1

    override fun createBoard() = LoopyView(this)
    override fun generate(preset: Int, random: Random): LoopyState {
        val size = listOf(5, 7, 10)[preset]
        return LoopyState.generate(size, size, random)
    }
    override fun encode(state: LoopyState) = state.encode()
    override fun decode(text: String) = LoopyState.decode(text)
    override fun isSolved(state: LoopyState) = state.isSolved
    override val hasHints = true
    override fun hint(state: LoopyState) = state.hint()
}

class LoopyView(context: Context) : EdgeView<LoopyState>(context) {
    override fun edgeGrid(state: LoopyState) = state.grid
    override fun edgeValue(state: LoopyState, e: Int) = state.edges[e]
    override fun setEdges(state: LoopyState, edges: Collection<Int>, value: Int) = state.set(edges, value)

    override fun drawBoard(canvas: Canvas, state: LoopyState, area: RectF) {
        layout(state, area)
        val cell = grid.cell
        val bad = state.badVertices()
        for (i in state.clues.indices) {
            val c = state.clues[i]
            if (c < 0) continue
            grid.rect(i % state.w, i / state.w, r)
            val n = state.lines(i)
            canvas.centeredText(c.toString(), r.centerX(), r.centerY(), cell * 0.5f,
                when { n > c -> palette.error; n == c -> palette.tertiary; else -> palette.text }, bold = true)
        }
        drawEdges(canvas, state) { e ->
            val (a, b) = state.grid.ends(e)
            if (a in bad || b in bad) palette.error else palette.accent
        }
        fill.color = palette.text
        for (y in 0..state.h) for (x in 0..state.w) canvas.drawCircle(grid.x(x), grid.y(y), cell * 0.06f, fill)
    }
}

class LoopyArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFF7043.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = LoopyState.generate(6, 6, Random(29))
        val g = EdgeGrid(6, 6)
        val edges = IntArray(g.count) { if (it % 5 == 0) 1 else if (it % 7 == 3) 2 else 0 }
        drawWith(LoopyView(context), LoopyState(6, 6, s.clues, edges), canvas, boardRect(w, h, 1.1f), palette)
    }
}
