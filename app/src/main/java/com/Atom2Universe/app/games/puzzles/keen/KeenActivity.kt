package com.Atom2Universe.app.games.puzzles.keen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.LatinPuzzleActivity
import com.Atom2Universe.app.games.kit.LatinView
import kotlin.random.Random

class KeenActivity : LatinPuzzleActivity<KeenState>() {
    override val gameKey = "keen"
    override val titleRes = R.string.keen_title
    override val rulesRes = R.string.keen_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 6, 6),
    )
    override val defaultPreset = 1

    override fun createBoard() = KeenView(this)
    override fun generate(preset: Int, random: Random) = KeenState.generate(preset + 4, random)
    override fun encode(state: KeenState) = state.encode()
    override fun decode(text: String) = KeenState.decode(text)
    override fun isSolved(state: KeenState) = state.isSolved
}

/** Les cages sont teintées en alternance et bordées d'un trait épais ; leur cible en haut à gauche. */
class KeenView(context: Context) : LatinView<KeenState>(context) {
    override fun clueErrors(state: KeenState) = state.broken()

    override fun drawCellBackground(canvas: Canvas, state: KeenState, cell: Int) {
        if (state.cage[cell] % 2 == 1) {
            fill.color = palette.withAlpha(palette.accent, 0.06f)
            canvas.drawRect(r, fill)
        }
    }

    override fun drawClues(canvas: Canvas, state: KeenState) {
        val n = state.n
        val c = grid.cell
        stroke.color = palette.gridBold
        stroke.strokeWidth = c * 0.06f
        for (cell in 0 until n * n) {
            val x = cell % n; val y = cell / n
            if (x < n - 1 && state.cage[cell] != state.cage[cell + 1]) canvas.drawLine(grid.x(x + 1), grid.y(y), grid.x(x + 1), grid.y(y + 1), stroke)
            if (y < n - 1 && state.cage[cell] != state.cage[cell + n]) canvas.drawLine(grid.x(x), grid.y(y + 1), grid.x(x + 1), grid.y(y + 1), stroke)
        }
        label.textAlign = Paint.Align.LEFT
        for (k in state.targets.indices) {
            val first = state.cage.indexOfFirst { it == k }
            val text = when (state.ops[k]) {
                KeenState.ADD -> context.getString(R.string.keen_add, state.targets[k])
                KeenState.SUB -> context.getString(R.string.keen_sub, state.targets[k])
                KeenState.MUL -> context.getString(R.string.keen_mul, state.targets[k])
                KeenState.DIV -> context.getString(R.string.keen_div, state.targets[k])
                else -> state.targets[k].toString()
            }
            label.textSize = c * 0.22f
            label.color = palette.secondary
            label.isFakeBoldText = true
            canvas.drawText(text, grid.x(first % n) + c * 0.07f, grid.y(first / n) + c * 0.25f, label)
        }
        label.textAlign = Paint.Align.CENTER
    }
}

class KeenArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFAED581.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val state = KeenState.generate(4, Random(12))
        drawWith(KeenView(context), state, canvas, boardRect(w, h, 1.1f), palette)
    }
}
