package com.Atom2Universe.app.games.puzzles.singles

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.ShadeView
import kotlin.random.Random

class SinglesActivity : PuzzleActivity<SinglesState>() {
    override val gameKey = "singles"
    override val titleRes = R.string.singles_title
    override val rulesRes = R.string.singles_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 7, 7),
        Preset(R.string.kit_size, 8, 8),
    )
    override val defaultPreset = 1

    override fun createBoard() = SinglesView(this)
    override fun generate(preset: Int, random: Random) = SinglesState.generate(preset + 5, random)
    override fun encode(state: SinglesState) = state.encode()
    override fun decode(text: String) = SinglesState.decode(text)
    override fun isSolved(state: SinglesState) = state.isSolved
    override val hasHints = true
    override fun hint(state: SinglesState) = state.hint()
}

class SinglesView(context: Context) : ShadeView<SinglesState>(context) {
    override val ringMark = true
    override fun size(state: SinglesState) = state.n to state.n
    override fun cellValue(state: SinglesState, i: Int) = state.cells[i]
    override fun label(state: SinglesState, i: Int) = state.numbers[i].toString()
    override fun change(state: SinglesState, i: Int, value: Int) = state.with(i, value)
    override fun errors(state: SinglesState) = state.errors()
}

class SinglesArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF90A4AE.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = SinglesState.generate(6, Random(17))
        val cells = IntArray(36) { if (it % 7 == 3) 1 else if (it % 5 == 1) 2 else 0 }
        drawWith(SinglesView(context), SinglesState(6, s.numbers, cells, s.solution), canvas, boardRect(w, h, 1.1f), palette)
    }
}
