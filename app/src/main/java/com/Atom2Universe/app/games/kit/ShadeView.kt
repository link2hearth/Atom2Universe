package com.Atom2Universe.app.games.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF

/**
 * La vue commune des puzzles où l'on noircit des cases (Hitori, Mosaic, Kurodoko…) : un appui
 * noircit ou blanchit, un appui long pose la marque « reste blanc ». Chaque jeu dit ce qu'il
 * écrit dans une case, et quelles cases sont en faute.
 */
abstract class ShadeView<S : Any>(context: Context) : PuzzleView<S>(context) {
    protected val grid = GridGeometry()
    protected val r = RectF()

    protected abstract fun size(state: S): Pair<Int, Int>
    /** 0 indécis, 1 noir, 2 marqué blanc. */
    protected abstract fun cellValue(state: S, i: Int): Int
    protected abstract fun label(state: S, i: Int): String?
    protected abstract fun change(state: S, i: Int, value: Int): S
    protected open fun errors(state: S): Set<Int> = emptySet()
    /** Les nombres déjà satisfaits s'estompent. */
    protected open fun labelDone(state: S, i: Int): Boolean = false
    /** La marque « blanc » : un anneau (Hitori) ou un point. */
    protected open val ringMark = false
    protected open fun canShade(state: S, i: Int): Boolean = true
    /** Une image révélée (mode Images) : la couleur de chaque case, sans chiffres ni marques. */
    protected open fun revealColour(state: S, i: Int): Int? = null

    override fun drawBoard(canvas: Canvas, state: S, area: RectF) {
        val (w, h) = size(state)
        grid.fit(area, w, h)
        val cell = grid.cell
        val bad = errors(state)
        for (i in 0 until w * h) {
            grid.rect(i % w, i / w, r)
            revealColour(state, i)?.let { fill.color = it; canvas.drawRect(r, fill); continue }
            val v = cellValue(state, i)
            fill.color = when (v) {
                1 -> if (i in bad) palette.error else palette.text
                2 -> palette.blend(palette.cell, palette.accent, 0.12f)
                else -> palette.cell
            }
            canvas.drawRect(r, fill)
            val text = label(state, i)
            if (text != null) {
                val colour = when {
                    v == 1 -> palette.withAlpha(palette.surface, 0.55f)
                    i in bad -> palette.error
                    labelDone(state, i) -> palette.tertiary
                    else -> palette.text
                }
                canvas.centeredText(text, r.centerX(), r.centerY(), cell * 0.5f, colour, bold = v != 1)
            }
            if (v == 2) {
                if (ringMark) {
                    stroke.color = if (i in bad) palette.error else palette.accent
                    stroke.strokeWidth = cell * 0.05f
                    canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.36f, stroke)
                } else if (text == null) {
                    fill.color = palette.accent
                    canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.08f, fill)
                }
            }
        }
        stroke.color = palette.gridLine
        stroke.strokeWidth = cell * 0.025f
        for (k in 0..w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
    }

    private fun act(x: Float, y: Float, value: Int) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0 || (value == 1 && !canShade(s, i))) return
        tick()
        move(change(s, i, if (cellValue(s, i) == value) 0 else value))
    }

    override fun onTap(x: Float, y: Float) = act(x, y, 1)
    override fun onLongPress(x: Float, y: Float) = act(x, y, 2)
}
