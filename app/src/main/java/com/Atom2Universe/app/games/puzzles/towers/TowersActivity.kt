package com.Atom2Universe.app.games.puzzles.towers

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.LatinBoard
import com.Atom2Universe.app.games.kit.LatinPuzzleActivity
import com.Atom2Universe.app.games.kit.LatinView
import kotlin.random.Random

class TowersActivity : LatinPuzzleActivity<TowersState>() {
    override val gameKey = "towers"
    override val titleRes = R.string.towers_title
    override val rulesRes = R.string.towers_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 6, 6),
    )
    override val defaultPreset = 1

    override fun createBoard() = TowersView(this)
    override fun generate(preset: Int, random: Random) =
        TowersState.generate(preset + 4, if (preset == 0) 2 else 0, random)
    override fun encode(state: TowersState) = state.encode()
    override fun decode(text: String) = TowersState.decode(text)
    override fun isSolved(state: TowersState) = state.isSolved
}

/** Chaque chiffre posé dresse un petit immeuble de sa hauteur dans la case. */
class TowersView(context: Context) : LatinView<TowersState>(context) {
    override fun margin(state: TowersState) = floatArrayOf(0.8f, 0.8f, 0.8f, 0.8f)

    override fun drawCellBackground(canvas: Canvas, state: TowersState, cell: Int) {
        val v = state.board.values[cell]
        if (v == 0) return
        val c = grid.cell
        val h = (c * 0.8f) * v / state.n
        fill.color = palette.withAlpha(palette.accent, if (state.board.fixed[cell]) 0.16f else 0.24f)
        canvas.drawRect(r.left + c * 0.22f, r.bottom - c * 0.08f - h, r.right - c * 0.22f, r.bottom - c * 0.08f, fill)
        fill.color = palette.withAlpha(palette.accent, 0.35f)
        val w = (r.width() - c * 0.44f) / 3f
        var y = r.bottom - c * 0.08f - h + c * 0.08f
        while (y < r.bottom - c * 0.16f) {
            for (k in 0..2) canvas.drawRect(r.left + c * 0.22f + k * w + w * 0.3f, y, r.left + c * 0.22f + (k + 1) * w - w * 0.3f, y + c * 0.04f, fill)
            y += c * 0.1f
        }
    }

    override fun drawClues(canvas: Canvas, state: TowersState) {
        val n = state.n
        val broken = state.brokenClues()
        val c = grid.cell
        for (k in 0 until 4 * n) {
            val v = state.clues[k]
            if (v == 0) continue
            val i = k % n
            val (x, y) = when (k / n) {
                0 -> grid.cx(i) to grid.top - c * 0.42f
                1 -> grid.cx(i) to grid.bottom + c * 0.42f
                2 -> grid.left - c * 0.42f to grid.cy(i)
                else -> grid.right + c * 0.42f to grid.cy(i)
            }
            canvas.centeredText(v.toString(), x, y, c * 0.42f, if (k in broken) palette.error else palette.secondary, bold = true)
        }
    }
}

class TowersArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF7986CB.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val sol = LatinBoard.randomSquare(4, Random(4))
        val vals = IntArray(16) { if (it % 3 == 0) 0 else sol[it] }
        val clues = IntArray(16) { TowersState.visible(TowersState.line(sol, 4, it)) }
        val state = TowersState(LatinBoard(4, vals, IntArray(16), BooleanArray(16) { it % 2 == 0 }), clues)
        drawWith(TowersView(context), state, canvas, boardRect(w, h, 1.05f), palette)
    }
}
