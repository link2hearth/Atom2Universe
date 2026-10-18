package com.Atom2Universe.app.games.puzzles.sixteen

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.SlideGridView
import kotlin.random.Random

class SixteenActivity : PuzzleActivity<SixteenState>() {
    override val gameKey = "sixteen"
    override val titleRes = R.string.sixteen_title
    override val rulesRes = R.string.sixteen_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 3, 3),
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
    )
    override val defaultPreset = 1

    override fun createBoard() = SixteenView(this)
    override fun generate(preset: Int, random: Random) = SixteenState.generate(preset + 3, preset + 3, random)
    override fun encode(state: SixteenState) = state.encode()
    override fun decode(text: String) = SixteenState.decode(text)
    override fun isSolved(state: SixteenState) = state.isSolved
    /** L'astuce est une démonstration : le journal des coups rembobiné sous les yeux du joueur. */
    override val hasHints = true
    override fun demo(state: SixteenState) = state.rewind()
    override fun statusText(state: SixteenState) = getString(R.string.kit_moves, state.moves)
}

/** Les flèches tout autour décalent d'un cran ; un glissé décale ligne ou colonne. */
class SixteenView(context: Context) : SlideGridView<SixteenState>(context) {
    override fun cols(state: SixteenState) = state.w
    override fun rows(state: SixteenState) = state.h
    override fun shiftRow(state: SixteenState, row: Int, by: Int) = state.shiftRow(row, by)
    override fun shiftCol(state: SixteenState, col: Int, by: Int) = state.shiftCol(col, by)

    override fun drawTile(canvas: Canvas, state: SixteenState, index: Int, r: RectF, cell: Float) {
        val v = state.tiles[index]
        val home = v == index + 1
        fill.color = if (home) palette.blend(palette.surface, palette.accent, 0.12f) else palette.surface
        canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, fill)
        stroke.strokeWidth = cell * 0.03f
        stroke.color = if (home) palette.withAlpha(palette.accent, 0.7f) else palette.outline
        canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, stroke)
        canvas.centeredText(v.toString(), r.centerX(), r.centerY(), cell * 0.4f,
            if (home) palette.ink else palette.text, bold = true)
    }
}

class SixteenArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFF06292.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val state = SixteenState.generate(4, 4, Random(16))
        drawWith(SixteenView(context), state, canvas, boardRect(w, h, 1.2f), palette)
    }
}
