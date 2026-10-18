package com.Atom2Universe.app.games.puzzles.unruly

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

class UnrulyActivity : PuzzleActivity<UnrulyState>() {
    override val gameKey = "unruly"
    override val titleRes = R.string.unruly_title
    override val rulesRes = R.string.unruly_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 12, 12),
    )
    override val defaultPreset = 1

    override fun createBoard() = UnrulyView(this)
    override fun generate(preset: Int, random: Random) =
        UnrulyState.generate(6 + preset * 2, if (preset == 0) 4 else 0, random)
    override fun encode(state: UnrulyState) = state.encode()
    override fun decode(text: String) = UnrulyState.decode(text)
    override fun isSolved(state: UnrulyState) = state.isSolved
    override val hasHints = true
    override fun hint(state: UnrulyState) = state.hint()
}

/** Un appui fait défiler vide → couleur du thème → couleur claire → vide ; l'appui long à l'envers. */
class UnrulyView(context: Context) : PuzzleView<UnrulyState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    override fun drawBoard(canvas: Canvas, state: UnrulyState, area: RectF) {
        grid.fit(area, state.n, state.n)
        val cell = grid.cell
        val errors = state.errors()
        for (i in state.cells.indices) {
            grid.rect(i % state.n, i / state.n, r, cell * 0.06f)
            val v = state.cells[i]
            fill.color = when (v) {
                0 -> palette.accent
                1 -> palette.withAlpha(palette.text, 0.88f)
                else -> palette.cell
            }
            canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, fill)
            if (state.fixed[i]) {
                fill.color = if (v == 0) palette.onAccent else palette.surface
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.08f, fill)
            }
            if (i in errors) {
                stroke.color = palette.error
                stroke.strokeWidth = cell * 0.08f
                canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, stroke)
            } else if (v < 0) {
                stroke.color = palette.outline
                stroke.strokeWidth = cell * 0.025f
                canvas.drawRoundRect(r, cell * 0.16f, cell * 0.16f, stroke)
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

class UnrulyArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF4FC3F7.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = UnrulyState.generate(8, 10, Random(8))
        drawWith(UnrulyView(context), s, canvas, boardRect(w, h, 1.1f), palette)
    }
}
