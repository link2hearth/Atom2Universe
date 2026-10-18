package com.Atom2Universe.app.games.puzzles.undead

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

class UndeadActivity : PuzzleActivity<UndeadState>() {
    override val gameKey = "undead"
    override val titleRes = R.string.undead_title
    override val rulesRes = R.string.undead_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 7, 7),
    )
    override val defaultPreset = 1

    override fun createBoard() = UndeadView(this)
    override fun generate(preset: Int, random: Random): UndeadState {
        val size = listOf(4, 5, 7)[preset]
        return UndeadState.generate(size, size, random)
    }
    override fun encode(state: UndeadState) = state.encode()
    override fun decode(text: String) = UndeadState.decode(text)
    override fun isSolved(state: UndeadState) = state.isSolved
    override val hasHints = true
    override fun hint(state: UndeadState) = state.hint()
}

/**
 * Appui : fantôme → vampire → zombie → vide ; appui long dans l'autre sens. Les monstres sont
 * dessinés, et leurs totaux sont rappelés au-dessus de la grille.
 */
class UndeadView(context: Context) : PuzzleView<UndeadState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private val path = Path()

    override fun drawBoard(canvas: Canvas, state: UndeadState, area: RectF) {
        grid.fit(area, state.w, state.h, 0.7f, 1.9f, 0.7f, 0.7f)
        val cell = grid.cell
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r, cell * 0.03f)
            fill.color = palette.cell
            canvas.drawRect(r, fill)
            when (state.mirrors[i]) {
                UndeadState.SLASH -> { stroke.color = palette.gridBold; stroke.strokeWidth = cell * 0.09f; canvas.drawLine(r.left + cell * 0.12f, r.bottom - cell * 0.12f, r.right - cell * 0.12f, r.top + cell * 0.12f, stroke) }
                UndeadState.BACKSLASH -> { stroke.color = palette.gridBold; stroke.strokeWidth = cell * 0.09f; canvas.drawLine(r.left + cell * 0.12f, r.top + cell * 0.12f, r.right - cell * 0.12f, r.bottom - cell * 0.12f, stroke) }
            }
            if (state.cells[i] != 0) monster(canvas, state.cells[i], r.centerX(), r.centerY(), cell * 0.8f)
        }
        // Les nombres du bord.
        for (port in state.clues.indices) {
            val v = state.clues[port]
            if (v < 0) continue
            val (x, y) = when {
                port < state.w -> grid.cx(port) to grid.top - cell * 0.38f
                port < state.w + state.h -> grid.right + cell * 0.38f to grid.cy(port - state.w)
                port < 2 * state.w + state.h -> grid.cx(state.w - 1 - (port - state.w - state.h)) to grid.bottom + cell * 0.38f
                else -> grid.left - cell * 0.38f to grid.cy(state.h - 1 - (port - 2 * state.w - state.h))
            }
            val seen = state.seen(port)
            val full = state.paths[port].all { (c, _) -> state.cells[c] != 0 }
            val colour = when { seen > v || (full && seen != v) -> palette.error; full -> palette.tertiary; else -> palette.text }
            canvas.centeredText(v.toString(), x, y, cell * 0.4f, colour, bold = true)
        }
        // Les totaux, au-dessus.
        val ty = grid.top - cell * 1.35f
        for (k in 1..3) {
            val cx = grid.left + (grid.right - grid.left) * (k - 0.5f) / 3f
            monster(canvas, k, cx - cell * 0.35f, ty, cell * 0.65f)
            val placed = state.placed(k)
            val total = state.totals[k - 1]
            canvas.centeredText(context.getString(R.string.undead_total, placed, total), cx + cell * 0.4f, ty, cell * 0.38f,
                if (placed > total) palette.error else palette.text, bold = true)
        }
    }

    private fun monster(canvas: Canvas, kind: Int, cx: Float, cy: Float, size: Float) {
        val s = size / 2f
        when (kind) {
            UndeadState.GHOST -> {
                path.reset()
                path.moveTo(cx - s * 0.6f, cy + s * 0.6f)
                path.lineTo(cx - s * 0.6f, cy - s * 0.1f)
                path.cubicTo(cx - s * 0.6f, cy - s * 0.85f, cx + s * 0.6f, cy - s * 0.85f, cx + s * 0.6f, cy - s * 0.1f)
                path.lineTo(cx + s * 0.6f, cy + s * 0.6f)
                path.lineTo(cx + s * 0.3f, cy + s * 0.4f); path.lineTo(cx, cy + s * 0.6f); path.lineTo(cx - s * 0.3f, cy + s * 0.4f)
                path.close()
                fill.color = palette.blend(palette.text, palette.piece(5), 0.25f)
                canvas.drawPath(path, fill)
                fill.color = palette.background
                canvas.drawCircle(cx - s * 0.22f, cy - s * 0.12f, s * 0.11f, fill)
                canvas.drawCircle(cx + s * 0.22f, cy - s * 0.12f, s * 0.11f, fill)
            }
            UndeadState.VAMPIRE -> {
                path.reset()
                path.moveTo(cx, cy - s * 0.1f); path.lineTo(cx + s * 0.75f, cy + s * 0.7f); path.lineTo(cx - s * 0.75f, cy + s * 0.7f); path.close()
                fill.color = palette.piece(7)
                canvas.drawPath(path, fill)
                fill.color = palette.blend(palette.text, palette.piece(0), 0.15f)
                canvas.drawCircle(cx, cy - s * 0.2f, s * 0.42f, fill)
                fill.color = palette.piece(0)
                canvas.drawCircle(cx - s * 0.15f, cy - s * 0.28f, s * 0.07f, fill)
                canvas.drawCircle(cx + s * 0.15f, cy - s * 0.28f, s * 0.07f, fill)
                fill.color = palette.surface
                path.reset(); path.moveTo(cx - s * 0.12f, cy - s * 0.02f); path.lineTo(cx - s * 0.05f, cy + s * 0.14f); path.lineTo(cx - s * 0.0f, cy - s * 0.02f); path.close()
                canvas.drawPath(path, fill)
                path.reset(); path.moveTo(cx + s * 0.0f, cy - s * 0.02f); path.lineTo(cx + s * 0.05f, cy + s * 0.14f); path.lineTo(cx + s * 0.12f, cy - s * 0.02f); path.close()
                canvas.drawPath(path, fill)
            }
            else -> {
                fill.color = palette.piece(3)
                canvas.drawRoundRect(cx - s * 0.5f, cy - s * 0.55f, cx + s * 0.5f, cy + s * 0.55f, s * 0.18f, s * 0.18f, fill)
                fill.color = palette.background
                canvas.drawCircle(cx - s * 0.2f, cy - s * 0.15f, s * 0.1f, fill)
                canvas.drawCircle(cx + s * 0.22f, cy - s * 0.1f, s * 0.07f, fill)
                stroke.color = palette.background; stroke.strokeWidth = s * 0.07f
                canvas.drawLine(cx - s * 0.25f, cy + s * 0.25f, cx + s * 0.25f, cy + s * 0.25f, stroke)
                for (k in -1..1) canvas.drawLine(cx + k * s * 0.12f, cy + s * 0.17f, cx + k * s * 0.12f, cy + s * 0.33f, stroke)
            }
        }
    }

    private fun act(x: Float, y: Float, forward: Boolean) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        s.cycle(i, forward)?.let { tick(); move(it) }
    }

    override fun onTap(x: Float, y: Float) = act(x, y, true)
    override fun onLongPress(x: Float, y: Float) = act(x, y, false)
}

class UndeadArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF66BB6A.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = UndeadState.generate(4, 4, Random(33))
        val cells = IntArray(16) { if (s.mirrors[it] == 0) 1 + it % 3 else 0 }
        drawWith(UndeadView(context), UndeadState(4, 4, s.mirrors, s.clues, s.totals, cells, s.solution), canvas, boardRect(w, h, 1.05f, 0.0f), palette)
    }
}
