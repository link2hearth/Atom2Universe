package com.Atom2Universe.app.games.puzzles.magnets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.random.Random

class MagnetsActivity : PuzzleActivity<MagnetsState>() {
    override val gameKey = "magnets"
    override val titleRes = R.string.magnets_title
    override val rulesRes = R.string.magnets_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 5),
        Preset(R.string.kit_size, 8, 6),
        Preset(R.string.kit_size, 10, 8),
    )
    override val defaultPreset = 1

    override fun createBoard() = MagnetsView(this)
    override fun generate(preset: Int, random: Random): MagnetsState {
        val (w, h) = listOf(6 to 5, 8 to 6, 10 to 8)[preset]
        return MagnetsState.generate(w, h, random)
    }
    override fun encode(state: MagnetsState) = state.encode()
    override fun decode(text: String) = MagnetsState.decode(text)
    override fun isSolved(state: MagnetsState) = state.isSolved
    override val hasHints = true
    override fun hint(state: MagnetsState) = state.hint()
}

/**
 * Les « Magnets » de Tatham habillés en charges électriques : un domino est un dipôle (une charge
 * + et une −, reliées par des traits de champ) ou une paire neutre. Deux charges de même signe
 * qui se touchent font une étincelle. Appui : + −, puis − +, puis neutre, puis vide ; l'appui long
 * dans l'autre sens. Les comptes de + sont en haut et à gauche, ceux de − en bas et à droite.
 */
class MagnetsView(context: Context) : PuzzleView<MagnetsState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private val path = Path()

    override fun drawBoard(canvas: Canvas, state: MagnetsState, area: RectF) {
        grid.fit(area, state.w, state.h, 0.8f, 0.8f, 0.8f, 0.8f)
        val cell = grid.cell
        val clash = state.clashes()
        val plus = context.getString(R.string.magnets_plus)
        val minus = context.getString(R.string.magnets_minus)
        val warm = palette.piece(0); val cold = palette.piece(5)
        for (i in 0 until state.w * state.h) {
            val j = state.mate[i]
            if (j < i) continue
            val ax = minOf(i % state.w, j % state.w); val ay = minOf(i / state.w, j / state.w)
            val bx = maxOf(i % state.w, j % state.w); val by = maxOf(i / state.w, j / state.w)
            r.set(grid.x(ax) + cell * 0.06f, grid.y(ay) + cell * 0.06f, grid.x(bx + 1) - cell * 0.06f, grid.y(by + 1) - cell * 0.06f)
            val v = state.cells[i]
            fill.color = when (v) {
                MagnetsState.NEUTRAL -> palette.blend(palette.cell, palette.text, 0.18f)
                0 -> palette.cell
                else -> palette.raised
            }
            canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, fill)
            stroke.color = palette.gridBold; stroke.strokeWidth = cell * 0.04f
            canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, stroke)
        }
        // Un dipôle : un trait de champ courbe relie ses deux charges, sous elles.
        for (i in 0 until state.w * state.h) {
            val j = state.mate[i]
            if (j < i || state.cells[i] !in listOf(MagnetsState.PLUS, MagnetsState.MINUS)) continue
            val x1 = grid.cx(i % state.w); val y1 = grid.cy(i / state.w)
            val x2 = grid.cx(j % state.w); val y2 = grid.cy(j / state.w)
            stroke.color = palette.withAlpha(palette.text, 0.35f); stroke.strokeWidth = cell * 0.035f
            val bend = cell * 0.22f
            for (side in listOf(-1, 1)) {
                path.reset(); path.moveTo(x1, y1)
                // Perpendiculaire au domino, d'un côté puis de l'autre.
                path.quadTo((x1 + x2) / 2 + side * (y2 - y1) / cell * bend, (y1 + y2) / 2 + side * (x2 - x1) / cell * bend, x2, y2)
                canvas.drawPath(path, stroke)
            }
        }
        for (i in 0 until state.w * state.h) {
            val v = state.cells[i]
            if (v == 0) continue
            grid.rect(i % state.w, i / state.w, r)
            if (v == MagnetsState.NEUTRAL) { neutron(canvas, r.centerX(), r.centerY(), cell); continue }
            val colour = if (v == MagnetsState.PLUS) warm else cold
            charge(canvas, r.centerX(), r.centerY(), cell, colour, if (v == MagnetsState.PLUS) plus else minus)
        }
        // Une étincelle entre deux charges de même signe qui se touchent.
        for (i in clash) {
            val x = i % state.w; val y = i / state.w
            for ((n, dx, dy) in listOf(Triple(i + 1, 1, 0), Triple(i + state.w, 0, 1))) {
                if ((dx == 1 && x == state.w - 1) || (dy == 1 && y == state.h - 1) || n !in clash) continue
                if (state.cells[n] != state.cells[i]) continue
                spark(canvas, grid.x(x) + cell * (0.5f + dx * 0.5f), grid.y(y) + cell * (0.5f + dy * 0.5f), cell, dx == 1)
            }
        }
        fun clue(text: String, value: Int, done: Int, x: Float, y: Float, colour: Int) {
            val c = when { done > value -> palette.error; done == value -> palette.tertiary; else -> colour }
            canvas.centeredText(value.toString(), x, y, cell * 0.4f, c, bold = true)
        }
        for (y in 0 until state.h) {
            clue(plus, state.rowPlus[y], state.count(true, y, MagnetsState.PLUS), grid.left - cell * 0.4f, grid.cy(y), warm)
            clue(minus, state.rowMinus[y], state.count(true, y, MagnetsState.MINUS), grid.right + cell * 0.4f, grid.cy(y), cold)
        }
        for (x in 0 until state.w) {
            clue(plus, state.colPlus[x], state.count(false, x, MagnetsState.PLUS), grid.cx(x), grid.top - cell * 0.4f, warm)
            clue(minus, state.colMinus[x], state.count(false, x, MagnetsState.MINUS), grid.cx(x), grid.bottom + cell * 0.4f, cold)
        }
        canvas.centeredText(plus, grid.left - cell * 0.4f, grid.top - cell * 0.4f, cell * 0.5f, warm, bold = true)
        canvas.centeredText(minus, grid.right + cell * 0.4f, grid.bottom + cell * 0.4f, cell * 0.5f, cold, bold = true)
    }

    /** Une particule chargée : un halo, une sphère et son signe. */
    private fun charge(canvas: Canvas, cx: Float, cy: Float, cell: Float, colour: Int, sign: String) {
        fill.color = palette.withAlpha(colour, 0.18f)
        canvas.drawCircle(cx, cy, cell * 0.42f, fill)
        fill.color = colour
        canvas.drawCircle(cx, cy, cell * 0.28f, fill)
        fill.color = palette.blend(colour, 0xFFFFFFFF.toInt(), 0.45f)
        canvas.drawCircle(cx - cell * 0.09f, cy - cell * 0.09f, cell * 0.08f, fill)
        canvas.centeredText(sign, cx, cy, cell * 0.42f, KitPalette.contrasting(colour), bold = true)
    }

    /** Une case neutre : une petite sphère grise, sans halo ni signe. */
    private fun neutron(canvas: Canvas, cx: Float, cy: Float, cell: Float) {
        fill.color = palette.blend(palette.text, palette.cell, 0.55f)
        canvas.drawCircle(cx, cy, cell * 0.2f, fill)
        fill.color = palette.blend(palette.text, palette.cell, 0.3f)
        canvas.drawCircle(cx - cell * 0.06f, cy - cell * 0.06f, cell * 0.06f, fill)
    }

    /** Un éclair en zigzag, à cheval sur le bord commun de deux cases. */
    private fun spark(canvas: Canvas, mx: Float, my: Float, cell: Float, vertical: Boolean) {
        path.reset()
        val pts = listOf(-0.3f to 0f, -0.1f to 0.12f, 0.05f to -0.1f, 0.3f to 0.05f)
        pts.forEachIndexed { k, (along, across) ->
            val px = if (vertical) mx + across * cell else mx + along * cell
            val py = if (vertical) my + along * cell else my + across * cell
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        stroke.color = palette.withAlpha(palette.error, 0.35f); stroke.strokeWidth = cell * 0.14f
        canvas.drawPath(path, stroke)
        stroke.color = palette.error; stroke.strokeWidth = cell * 0.05f
        canvas.drawPath(path, stroke)
    }

    private fun act(x: Float, y: Float, forward: Boolean) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        tick(); move(s.cycle(i, forward))
    }

    override fun onTap(x: Float, y: Float) = act(x, y, true)
    override fun onLongPress(x: Float, y: Float) = act(x, y, false)
}

class MagnetsArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFEF5350.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        var s = MagnetsState.generate(6, 5, Random(32))
        for (i in listOf(0, 7, 14, 21)) s = s.cycle(i, true)
        drawWith(MagnetsView(context), s, canvas, boardRect(w, h, 1.1f), palette)
    }
}
