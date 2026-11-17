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

    /** Ce qui ne change qu'avec l'état : les fautes, les nombres satisfaits, l'image révélée. */
    private class Analysis(val bad: BooleanArray, val done: BooleanArray, val revealed: BooleanArray?, val colours: IntArray)
    private fun newAnalysis() = PuzzleStateCache<S, Analysis> { state ->
        val (w, h) = size(state)
        val n = w * h
        val bad = BooleanArray(n)
        for (i in errors(state)) if (i in 0 until n) bad[i] = true
        val done = BooleanArray(n) { labelDone(state, it) }
        // Les couleurs de l'image (mode Images) sont lues une fois, pas case par case à chaque image.
        var revealed: BooleanArray? = null
        val colours = IntArray(n)
        for (i in 0 until n) {
            val c = revealColour(state, i) ?: continue
            val mask = revealed ?: BooleanArray(n).also { revealed = it }
            mask[i] = true
            colours[i] = c
        }
        Analysis(bad, done, revealed, colours)
    }
    private var analysis = newAnalysis()
    /** L'image révélée dépend aussi de la palette (case vide) : l'analyse se refait quand elle change. */
    private var analysisPalette: KitPalette? = null

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
        if (palette !== analysisPalette) { analysisPalette = palette; analysis = newAnalysis() }
        val a = analysis[state]
        val bad = a.bad
        val done = a.done
        val revealed = a.revealed
        for (i in 0 until w * h) {
            grid.rect(i % w, i / w, r)
            if (revealed != null && revealed[i]) { fill.color = a.colours[i]; canvas.drawRect(r, fill); continue }
            val v = cellValue(state, i)
            fill.color = when (v) {
                1 -> if (bad[i]) palette.error else palette.text
                2 -> palette.blend(palette.cell, palette.accent, 0.12f)
                else -> palette.cell
            }
            canvas.drawRect(r, fill)
            val text = label(state, i)
            if (text != null) {
                val colour = when {
                    v == 1 -> palette.withAlpha(palette.surface, 0.55f)
                    bad[i] -> palette.error
                    done[i] -> palette.tertiary
                    else -> palette.text
                }
                canvas.centeredText(text, r.centerX(), r.centerY(), cell * 0.5f, colour, bold = v != 1)
            }
            if (v == 2) {
                if (ringMark) {
                    stroke.color = if (bad[i]) palette.error else palette.accent
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
