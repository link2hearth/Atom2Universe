package com.Atom2Universe.app.games.puzzles.galaxies

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.EdgeView
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import kotlin.random.Random

class GalaxiesActivity : PuzzleActivity<GalaxiesState>() {
    override val gameKey = "galaxies"
    override val titleRes = R.string.galaxies_title
    override val rulesRes = R.string.galaxies_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 7, 7),
        Preset(R.string.kit_size, 9, 9),
    )
    override val defaultPreset = 1

    override fun createBoard() = GalaxiesView(this)
    override fun generate(preset: Int, random: Random): GalaxiesState {
        val size = listOf(5, 7, 9)[preset]
        return GalaxiesState.generate(size, size, random)
    }
    override fun encode(state: GalaxiesState) = state.encode()
    override fun decode(text: String) = GalaxiesState.decode(text)
    override fun isSolved(state: GalaxiesState) = state.isSolved
    override val hasHints = true
    override fun hint(state: GalaxiesState) = state.hint()
}

/** Chaque galaxie bien découpée prend la couleur de son point ; les points sont des étoiles. */
class GalaxiesView(context: Context) : EdgeView<GalaxiesState>(context) {
    override fun edgeGrid(state: GalaxiesState) = state.grid
    override fun edgeValue(state: GalaxiesState, e: Int) = state.edges[e]
    override fun setEdges(state: GalaxiesState, edges: Collection<Int>, value: Int) = state.set(edges, value)
    override fun usable(state: GalaxiesState, e: Int) = !state.border(e) && !state.crossesDot(e)

    override fun drawBoard(canvas: Canvas, state: GalaxiesState, area: RectF) {
        layout(state, area)
        val cell = grid.cell
        val id = state.regions()
        val good = state.goodRegions()
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r)
            val d = good[id[i]]
            fill.color = if (d != null) palette.blend(palette.cell, palette.piece(d), if (palette.isLight) 0.3f else 0.35f) else palette.cell
            canvas.drawRect(r, fill)
        }
        stroke.color = palette.gridLine; stroke.strokeWidth = cell * 0.02f
        for (k in 0..state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        drawEdges(canvas, state) { palette.text }
        stroke.color = palette.text; stroke.strokeWidth = cell * 0.11f
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, stroke)
        for (d in state.dotX.indices) {
            val x = grid.left + state.dotX[d] * cell / 2f; val y = grid.top + state.dotY[d] * cell / 2f
            fill.color = palette.text
            canvas.drawCircle(x, y, cell * 0.17f, fill)
            fill.color = palette.blend(palette.surface, palette.piece(d), 0.45f)
            canvas.drawCircle(x, y, cell * 0.11f, fill)
        }
    }
}

class GalaxiesArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF7E57C2.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        drawWith(GalaxiesView(context), GalaxiesState.generate(5, 5, Random(35)), canvas, boardRect(w, h, 1.05f), palette)
    }
}
