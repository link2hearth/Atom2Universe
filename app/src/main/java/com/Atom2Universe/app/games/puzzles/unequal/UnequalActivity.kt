package com.Atom2Universe.app.games.puzzles.unequal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.LatinPuzzleActivity
import com.Atom2Universe.app.games.kit.LatinView
import kotlin.random.Random

class UnequalActivity : LatinPuzzleActivity<UnequalState>() {
    override val gameKey = "unequal"
    override val titleRes = R.string.unequal_title
    override val rulesRes = R.string.unequal_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 7, 7),
    )
    override val defaultPreset = 1

    override fun createBoard() = UnequalView(this)
    override fun generate(preset: Int, random: Random) =
        UnequalState.generate(preset + 4, if (preset == 0) 2 else 0, random)
    override fun encode(state: UnequalState) = state.encode()
    override fun decode(text: String) = UnequalState.decode(text)
    override fun isSolved(state: UnequalState) = state.isSolved
}

/** Les cases sont séparées par un petit couloir où se logent les chevrons. */
class UnequalView(context: Context) : LatinView<UnequalState>(context) {
    override val gridGap = 0.14f
    private val chevron = Path()

    override fun clueErrors(state: UnequalState) = state.broken()

    override fun drawClues(canvas: Canvas, state: UnequalState) {
        val n = state.n
        val c = grid.cell
        stroke.strokeWidth = c * 0.05f
        stroke.color = palette.gridBold
        for (cell in 0 until n * n) for (d in 0..1) {
            val rel = state.rel[cell * 2 + d]
            if (rel == 0) continue
            val x = cell % n; val y = cell / n
            // Centre du couloir entre les deux cases ; la pointe regarde la plus petite.
            val cx = if (d == 0) grid.x(x + 1) else grid.cx(x)
            val cy = if (d == 0) grid.cy(y) else grid.y(y + 1)
            val dir = if (rel == UnequalState.LESS) -1f else 1f
            val s = c * 0.09f
            chevron.reset()
            if (d == 0) {
                chevron.moveTo(cx - dir * s, cy - s * 1.4f); chevron.lineTo(cx + dir * s, cy); chevron.lineTo(cx - dir * s, cy + s * 1.4f)
            } else {
                chevron.moveTo(cx - s * 1.4f, cy - dir * s); chevron.lineTo(cx, cy + dir * s); chevron.lineTo(cx + s * 1.4f, cy - dir * s)
            }
            canvas.drawPath(chevron, stroke)
        }
    }
}

class UnequalArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF4DB6AC.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val state = UnequalState.generate(5, 3, Random(5))
        drawWith(UnequalView(context), state, canvas, boardRect(w, h, 1.1f), palette)
    }
}
