package com.Atom2Universe.app.games.puzzles.rect

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

class RectActivity : PuzzleActivity<RectState>() {
    override val gameKey = "rect"
    override val titleRes = R.string.rect_title
    override val rulesRes = R.string.rect_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 7, 7),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 13, 13),
    )
    override val defaultPreset = 1

    override fun createBoard() = RectView(this)
    override fun generate(preset: Int, random: Random): RectState {
        val size = listOf(7, 10, 13)[preset]
        return RectState.generate(size, size, listOf(8, 10, 12)[preset], random)
    }
    override fun encode(state: RectState) = state.encode()
    override fun decode(text: String) = RectState.decode(text)
    override fun isSolved(state: RectState) = state.isSolved
    override val hasHints = true
    override fun hint(state: RectState) = state.hint()
}

/**
 * Glisser d'une case à l'autre trace le rectangle qu'elles délimitent ; un appui sur un
 * rectangle l'efface. Chaque rectangle juste prend une couleur pleine.
 */
class RectView(context: Context) : PuzzleView<RectState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private var from = -1
    private var to = -1

    private fun dragRect(state: RectState): IntArray? {
        if (from < 0 || to < 0) return null
        val x0 = minOf(from % state.w, to % state.w); val x1 = maxOf(from % state.w, to % state.w)
        val y0 = minOf(from / state.w, to / state.w); val y1 = maxOf(from / state.w, to / state.w)
        return intArrayOf(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    override fun drawBoard(canvas: Canvas, state: RectState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        fill.color = palette.cell
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, fill)
        stroke.color = palette.gridLine
        stroke.strokeWidth = cell * 0.02f
        for (k in 0..state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        val all = state.rects + listOfNotNull(dragRect(state))
        all.forEachIndexed { i, rc ->
            r.set(grid.x(rc[0]) + cell * 0.07f, grid.y(rc[1]) + cell * 0.07f,
                grid.x(rc[0] + rc[2]) - cell * 0.07f, grid.y(rc[1] + rc[3]) - cell * 0.07f)
            val drafting = i == state.rects.size
            val ok = state.valid(rc)
            val colour = palette.piece(i * 3)
            fill.color = when {
                drafting -> palette.withAlpha(palette.accent, 0.25f)
                ok -> palette.blend(palette.cell, colour, if (palette.isLight) 0.4f else 0.5f)
                else -> palette.withAlpha(palette.error, 0.12f)
            }
            canvas.drawRoundRect(r, cell * 0.18f, cell * 0.18f, fill)
            stroke.color = when {
                drafting -> palette.accent
                ok -> colour
                else -> palette.error
            }
            stroke.strokeWidth = cell * 0.06f
            canvas.drawRoundRect(r, cell * 0.18f, cell * 0.18f, stroke)
        }
        for (i in state.numbers.indices) {
            val v = state.numbers[i]
            if (v == 0) continue
            grid.rect(i % state.w, i / state.w, r)
            canvas.centeredText(v.toString(), r.centerX(), r.centerY(), cell * 0.5f, palette.text, bold = true)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        val k = s.rectAt(i)
        if (k >= 0) { tick(); move(s.erase(k)) }
        else { tick(); move(s.draw(intArrayOf(i % s.w, i / s.w, 1, 1))) }
    }

    override fun wantsDrag(x: Float, y: Float) = grid.cellAt(x, y) >= 0

    override fun onDragStart(x: Float, y: Float) {
        from = grid.cellAt(x, y); to = from
    }

    override fun onDragMove(x: Float, y: Float) {
        val s = state ?: return
        val c = grid.colAt(x.coerceIn(grid.left, grid.right - 1f))
        val rr = grid.rowAt(y.coerceIn(grid.top, grid.bottom - 1f))
        to = rr * s.w + c
        invalidate()
    }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        val rc = s?.let { dragRect(it) }
        from = -1; to = -1
        if (s != null && rc != null) { tick(); move(s.draw(rc)) } else invalidate()
    }

    override fun onDragCancel() {
        from = -1; to = -1
        invalidate()
    }
}

class RectArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFFAB91.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = RectState.generate(7, 7, 8, Random(14))
        // Une partie des rectangles déjà posés : on les retrouve par le solveur de candidats.
        val rects = s.numbers.indices.filter { s.numbers[it] > 0 }.take(5).mapNotNull {
            RectState.candidates(7, 7, s.numbers, it).firstOrNull()
        }
        var state = s
        rects.forEach { state = state.draw(it) }
        drawWith(RectView(context), state, canvas, boardRect(w, h, 1.1f), palette)
    }
}
