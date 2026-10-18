package com.Atom2Universe.app.games.puzzles.tents

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

class TentsActivity : PuzzleActivity<TentsState>() {
    override val gameKey = "tents"
    override val titleRes = R.string.tents_title
    override val rulesRes = R.string.tents_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 12, 12),
    )
    override val defaultPreset = 1

    override fun createBoard() = TentsView(this)
    override fun generate(preset: Int, random: Random): TentsState {
        val size = listOf(6, 8, 10, 12)[preset]
        return TentsState.generate(size, size, random)
    }
    override fun encode(state: TentsState) = state.encode()
    override fun decode(text: String) = TentsState.decode(text)
    override fun isSolved(state: TentsState) = state.isSolved
    override val hasHints = true
    override fun hint(state: TentsState) = state.hint()
}

/**
 * Les « Tentes » de Tatham habillées en espace : chaque arbre devient une planète, chaque tente
 * une lune qui lui est collée. Appui : une lune ; appui long : du vide (marqué d'un petit point).
 * Deux lunes qui se touchent, ou une lune collée à aucune planète, passent en rouge ; une fois
 * toutes les lunes posées, une planète restée seule est cerclée de rouge. Le fond de la grille est
 * semé d'étoiles fixes.
 */
class TentsView(context: Context) : PuzzleView<TentsState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    override fun drawBoard(canvas: Canvas, state: TentsState, area: RectF) {
        grid.fit(area, state.w, state.h, 0f, 0f, 0.8f, 0.8f)
        val cell = grid.cell
        val touching = state.touchingTents() + state.orphanMoons()
        val lonely = state.lonelyPlanets()
        val space = palette.blend(palette.cell, 0xFF1A237E.toInt(), if (palette.isLight) 0.06f else 0.18f)
        for (i in state.cells.indices) {
            grid.rect(i % state.w, i / state.w, r)
            fill.color = space
            canvas.drawRect(r, fill)
            // Deux étoiles par case, toujours au même endroit (tirées de l'indice de la case).
            val seed = Random(i * 7919 + state.w)
            fill.color = palette.withAlpha(palette.text, 0.18f)
            repeat(2) { canvas.drawCircle(r.left + cell * (0.1f + 0.8f * seed.nextFloat()), r.top + cell * (0.1f + 0.8f * seed.nextFloat()), cell * 0.018f, fill) }
            when {
                state.trees[i] -> {
                    planet(canvas, i, cell)
                    if (i in lonely) {
                        stroke.color = palette.error; stroke.strokeWidth = cell * 0.06f
                        canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.44f, stroke)
                    }
                }
                state.cells[i] == 1 -> moon(canvas, cell, i in touching)
                state.cells[i] == 2 -> {
                    fill.color = palette.withAlpha(palette.tertiary, 0.7f)
                    canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.06f, fill)
                }
            }
        }
        stroke.color = palette.gridLine
        stroke.strokeWidth = cell * 0.02f
        for (k in 0..state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        for (y in 0 until state.h) {
            val n = state.rowTents(y)
            val c = when { n > state.rowCounts[y] -> palette.error; n == state.rowCounts[y] -> palette.tertiary; else -> palette.text }
            canvas.centeredText(state.rowCounts[y].toString(), grid.right + cell * 0.45f, grid.cy(y), cell * 0.45f, c, bold = true)
        }
        for (x in 0 until state.w) {
            val n = state.colTents(x)
            val c = when { n > state.colCounts[x] -> palette.error; n == state.colCounts[x] -> palette.tertiary; else -> palette.text }
            canvas.centeredText(state.colCounts[x].toString(), grid.cx(x), grid.bottom + cell * 0.45f, cell * 0.45f, c, bold = true)
        }
    }

    /** Une planète : un disque coloré (une teinte par case), une bande plus claire, parfois un anneau. */
    private fun planet(canvas: Canvas, i: Int, cell: Float) {
        val hue = palette.piece(i * 3 + 1)
        val cx = r.centerX(); val cy = r.centerY(); val rad = cell * 0.3f
        val ringed = i % 3 == 0
        if (ringed) {
            stroke.color = palette.blend(hue, palette.text, 0.35f); stroke.strokeWidth = cell * 0.045f
            canvas.drawOval(cx - rad * 1.45f, cy - rad * 0.42f, cx + rad * 1.45f, cy + rad * 0.42f, stroke)
        }
        fill.color = hue
        canvas.drawCircle(cx, cy, rad, fill)
        fill.color = palette.blend(hue, 0xFFFFFFFF.toInt(), 0.3f)
        canvas.drawRect(cx - rad * 0.86f, cy - rad * 0.32f, cx + rad * 0.86f, cy - rad * 0.12f, fill)
        fill.color = palette.blend(hue, 0xFF000000.toInt(), 0.25f)
        canvas.drawRect(cx - rad * 0.8f, cy + rad * 0.2f, cx + rad * 0.8f, cy + rad * 0.38f, fill)
        if (ringed) {
            // L'avant de l'anneau passe devant la planète.
            stroke.color = palette.blend(hue, palette.text, 0.35f)
            canvas.drawArc(cx - rad * 1.45f, cy - rad * 0.42f, cx + rad * 1.45f, cy + rad * 0.42f, 10f, 160f, false, stroke)
        }
    }

    /** Une lune : un disque gris et ses cratères ; rouge si elle touche une autre lune. */
    private fun moon(canvas: Canvas, cell: Float, wrong: Boolean) {
        val cx = r.centerX(); val cy = r.centerY(); val rad = cell * 0.2f
        val base = if (wrong) palette.error else palette.blend(palette.text, palette.cell, 0.35f)
        fill.color = base
        canvas.drawCircle(cx, cy, rad, fill)
        fill.color = palette.blend(base, palette.cell, 0.35f)
        canvas.drawCircle(cx - rad * 0.35f, cy - rad * 0.2f, rad * 0.22f, fill)
        canvas.drawCircle(cx + rad * 0.3f, cy + rad * 0.35f, rad * 0.15f, fill)
        canvas.drawCircle(cx + rad * 0.4f, cy - rad * 0.4f, rad * 0.1f, fill)
    }

    private fun act(x: Float, y: Float, v: Int) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0 || s.trees[i]) return
        tick(); move(s.with(i, if (s.cells[i] == v) 0 else v))
    }

    override fun onTap(x: Float, y: Float) = act(x, y, 1)
    override fun onLongPress(x: Float, y: Float) = act(x, y, 2)
}

class TentsArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF5C6BC0.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = TentsState.generate(7, 7, Random(16))
        val cells = IntArray(49) { i -> if (!s.trees[i] && TentsState.neighbours4(7, 7, i).any { s.trees[it] } && i % 4 == 0) 1 else 0 }
        drawWith(TentsView(context), TentsState(7, 7, s.trees, s.rowCounts, s.colCounts, cells, s.solution), canvas, boardRect(w, h, 1.1f), palette)
    }
}
