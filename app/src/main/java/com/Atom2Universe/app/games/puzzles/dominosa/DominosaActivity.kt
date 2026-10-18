package com.Atom2Universe.app.games.puzzles.dominosa

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.abs
import kotlin.random.Random

class DominosaActivity : PuzzleActivity<DominosaState>() {
    override val gameKey = "dominosa"
    override val titleRes = R.string.dominosa_title
    override val rulesRes = R.string.dominosa_rules
    override val presets = listOf(
        Preset(R.string.dominosa_preset, 3),
        Preset(R.string.dominosa_preset, 4),
        Preset(R.string.dominosa_preset, 5),
        Preset(R.string.dominosa_preset, 6),
    )
    override val defaultPreset = 1

    override fun createBoard() = DominosaView(this)
    override fun generate(preset: Int, random: Random) = DominosaState.generate(preset + 3, random)
    override fun encode(state: DominosaState) = state.encode()
    override fun decode(text: String) = DominosaState.decode(text)
    override fun isSolved(state: DominosaState) = state.isSolved
    override val hasHints = true
    override fun hint(state: DominosaState) = state.hint()
}

/**
 * Les nombres sont dessinés en points, comme sur de vrais dominos. Toucher la frontière entre
 * deux cases (ou glisser de l'une à l'autre) pose le domino ; le retoucher l'enlève.
 */
class DominosaView(context: Context) : PuzzleView<DominosaState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private var dragFrom = -1

    override fun drawBoard(canvas: Canvas, state: DominosaState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val dup = state.duplicates()
        for (i in state.numbers.indices) {
            val j = state.partner[i]
            if (j in 0 until i) continue
            if (j < 0) {
                grid.rect(i % state.w, i / state.w, r, cell * 0.08f)
                fill.color = palette.blend(palette.cell, palette.background, 0.3f)
                canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, fill)
            } else {
                val ax = minOf(i % state.w, j % state.w); val ay = minOf(i / state.w, j / state.w)
                val bx = maxOf(i % state.w, j % state.w); val by = maxOf(i / state.w, j / state.w)
                r.set(grid.x(ax) + cell * 0.06f, grid.y(ay) + cell * 0.06f, grid.x(bx + 1) - cell * 0.06f, grid.y(by + 1) - cell * 0.06f)
                fill.color = if (i in dup) palette.withAlpha(palette.error, 0.25f) else palette.raised
                canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, fill)
                stroke.color = if (i in dup) palette.error else palette.accent
                stroke.strokeWidth = cell * 0.05f
                canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, stroke)
                stroke.color = palette.withAlpha(palette.accent, 0.6f)
                stroke.strokeWidth = cell * 0.03f
                if (ax != bx) canvas.drawLine(grid.x(bx), r.top + cell * 0.15f, grid.x(bx), r.bottom - cell * 0.15f, stroke)
                else canvas.drawLine(r.left + cell * 0.15f, grid.y(by), r.right - cell * 0.15f, grid.y(by), stroke)
            }
        }
        for (i in state.numbers.indices) pips(canvas, state.numbers[i], grid.cx(i % state.w), grid.cy(i / state.w), cell)
    }

    /** Les points d'une face de domino (0 à 9), disposés comme sur les vrais. */
    private fun pips(canvas: Canvas, v: Int, cx: Float, cy: Float, cell: Float) {
        val s = cell * 0.2f
        val spots = when (v) {
            0 -> emptyList()
            1 -> listOf(0 to 0)
            2 -> listOf(-1 to -1, 1 to 1)
            3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
            4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
            5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
            6 -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1)
            7 -> listOf(-1 to -1, 1 to -1, -1 to 0, 0 to 0, 1 to 0, -1 to 1, 1 to 1)
            8 -> listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
            else -> (-1..1).flatMap { a -> (-1..1).map { b -> a to b } }
        }
        fill.color = palette.text
        for ((dx, dy) in spots) canvas.drawCircle(cx + dx * s, cy + dy * s, cell * 0.075f, fill)
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        // La frontière la plus proche du doigt désigne la voisine.
        val fx = (x - grid.x(i % s.w)) / grid.cell - 0.5f
        val fy = (y - grid.y(i / s.w)) / grid.cell - 0.5f
        val j = if (abs(fx) > abs(fy)) {
            if (fx > 0 && i % s.w < s.w - 1) i + 1 else if (fx < 0 && i % s.w > 0) i - 1 else -1
        } else {
            if (fy > 0 && i / s.w < s.h - 1) i + s.w else if (fy < 0 && i / s.w > 0) i - s.w else -1
        }
        if (j < 0) return
        tick(); move(s.toggle(i, j))
    }

    override fun wantsDrag(x: Float, y: Float) = grid.cellAt(x, y) >= 0
    override fun onDragStart(x: Float, y: Float) { dragFrom = grid.cellAt(x, y) }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state ?: return
        val j = grid.cellAt(x, y)
        val i = dragFrom
        dragFrom = -1
        if (i < 0 || j < 0) return
        val adjacent = (abs(i % s.w - j % s.w) + abs(i / s.w - j / s.w)) == 1
        if (adjacent) { tick(); move(s.toggle(i, j)) }
    }
}

class DominosaArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFCE93D8.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        var s = DominosaState.generate(4, Random(15))
        s = s.toggle(0, 1).toggle(2, 2 + s.w).toggle(s.w * 2, s.w * 3)
        drawWith(DominosaView(context), s, canvas, boardRect(w, h, 1.2f), palette)
    }
}
