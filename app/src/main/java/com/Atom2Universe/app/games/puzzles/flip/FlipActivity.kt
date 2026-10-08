package com.Atom2Universe.app.games.puzzles.flip

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

class FlipActivity : PuzzleActivity<FlipState>() {
    override val gameKey = "flip"
    override val titleRes = R.string.flip_title
    override val rulesRes = R.string.flip_rules
    override val presets = listOf(
        Preset(R.string.flip_crosses, 3, 3),
        Preset(R.string.flip_crosses, 4, 4),
        Preset(R.string.flip_crosses, 5, 5),
        Preset(R.string.flip_random, 3, 3),
        Preset(R.string.flip_random, 4, 4),
        Preset(R.string.flip_random, 5, 5),
    )
    override val defaultPreset = 1

    override fun createBoard() = FlipView(this)
    override fun generate(preset: Int, random: Random): FlipState {
        val size = 3 + preset % 3
        return FlipState.generate(size, size, preset >= 3, random)
    }
    override fun encode(state: FlipState) = state.encode()
    override fun decode(text: String) = FlipState.decode(text)
    override fun isSolved(state: FlipState) = state.isSolved
    override val hasHints = true
    override fun hint(state: FlipState) = state.hint()
    override fun statusText(state: FlipState) = getString(R.string.kit_moves, state.moves)
}

class FlipView(context: Context) : PuzzleView<FlipState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    override fun drawBoard(canvas: Canvas, state: FlipState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val gap = cell * 0.05f
        for (i in 0 until state.w * state.h) {
            val x = i % state.w
            val y = i / state.w
            grid.rect(x, y, r, gap)
            val on = state.lit[i]
            fill.color = if (on) palette.accent else palette.surface
            canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, fill)
            stroke.strokeWidth = cell * 0.025f
            stroke.color = if (on) palette.accent else palette.outline
            canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, stroke)
            // Le motif de la case, en petit : les cases qu'elle retourne.
            val dot = cell * 0.11f
            val mark = if (on) palette.withAlpha(palette.onAccent, 0.75f) else palette.tertiary
            for (j in state.patterns[i]) {
                val dx = j % state.w - x
                val dy = j / state.w - y
                fill.color = mark
                val cx = r.centerX() + dx * dot * 1.25f
                val cy = r.centerY() + dy * dot * 1.25f
                canvas.drawRoundRect(cx - dot / 2f, cy - dot / 2f, cx + dot / 2f, cy + dot / 2f,
                    dot * 0.25f, dot * 0.25f, fill)
            }
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        tick()
        move(s.press(i))
    }
}

class FlipArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF4DD0E1.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val state = FlipState.generate(4, 4, false, Random(7))
        drawWith(FlipView(context), state, canvas, boardRect(w, h, 1.05f), palette)
    }
}
