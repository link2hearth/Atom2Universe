package com.Atom2Universe.app.games.puzzles.pearl

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

class PearlActivity : PuzzleActivity<PearlState>() {
    override val gameKey = "pearl"
    override val titleRes = R.string.pearl_title
    override val rulesRes = R.string.pearl_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 10),
    )
    override val defaultPreset = 1

    override fun createBoard() = PearlView(this)
    override fun generate(preset: Int, random: Random): PearlState {
        val size = listOf(6, 8, 10)[preset]
        return PearlState.generate(size, size, random)
    }
    override fun encode(state: PearlState) = state.encode()
    override fun decode(text: String) = PearlState.decode(text)
    override fun isSolved(state: PearlState) = state.isSolved
    override val hasHints = true
    override fun hint(state: PearlState) = state.hint()
}

class PearlView(context: Context) : EdgeView<PearlState>(context) {
    override val dual = true
    override fun edgeGrid(state: PearlState) = state.grid
    override fun edgeValue(state: PearlState, e: Int) = state.edges[e]
    override fun setEdges(state: PearlState, edges: Collection<Int>, value: Int) = state.set(edges, value)
    override fun margins(state: PearlState) = floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f)
    override fun usable(state: PearlState, e: Int) = state.grid.cells(e).all { it >= 0 }

    override fun drawBoard(canvas: Canvas, state: PearlState, area: RectF) {
        layout(state, area)
        val cell = grid.cell
        val over = state.overloaded()
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r, cell * 0.04f)
            fill.color = palette.cell
            canvas.drawRoundRect(r, cell * 0.1f, cell * 0.1f, fill)
        }
        drawEdges(canvas, state) { e -> if (state.grid.cells(e).any { it in over }) palette.error else palette.accent }
        for (i in 0 until state.w * state.h) {
            val p = state.pearls[i]
            if (p == 0) continue
            grid.rect(i % state.w, i / state.w, r)
            val bad = state.degree(i) == 2 && !state.pearlOk(i)
            if (p == PearlState.BLACK) {
                fill.color = if (bad) palette.error else palette.text
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.28f, fill)
            } else {
                fill.color = palette.surface
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.28f, fill)
                stroke.color = if (bad) palette.error else palette.text
                stroke.strokeWidth = cell * 0.06f
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.28f, stroke)
            }
        }
    }
}

class PearlArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFB0BEC5.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = PearlState.generate(6, 6, Random(30))
        val g = EdgeGrid(6, 6)
        val edges = IntArray(g.count) { if (g.cells(it).all { c -> c >= 0 } && it % 4 == 1) 1 else 0 }
        drawWith(PearlView(context), PearlState(6, 6, s.pearls, edges), canvas, boardRect(w, h, 1.1f), palette)
    }
}
