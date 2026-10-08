package com.Atom2Universe.app.games.puzzles.range

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.ShadeView
import kotlin.random.Random

class RangeActivity : PuzzleActivity<RangeState>() {
    override val gameKey = "range"
    override val titleRes = R.string.range_title
    override val rulesRes = R.string.range_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 10),
    )
    override val defaultPreset = 1

    override fun createBoard() = RangeView(this)
    override fun generate(preset: Int, random: Random): RangeState {
        val size = listOf(6, 8, 10)[preset]
        return RangeState.generate(size, size, random)
    }
    override fun encode(state: RangeState) = state.encode()
    override fun decode(text: String) = RangeState.decode(text)
    override fun isSolved(state: RangeState) = state.isSolved
    override val hasHints = true
    override fun hint(state: RangeState) = state.hint()
}

class RangeView(context: Context) : ShadeView<RangeState>(context) {
    override fun size(state: RangeState) = state.w to state.h
    override fun cellValue(state: RangeState, i: Int) = state.cells[i]
    override fun label(state: RangeState, i: Int) = if (state.numbers[i] >= 0) state.numbers[i].toString() else null
    override fun change(state: RangeState, i: Int, value: Int) = state.with(i, value)
    override fun canShade(state: RangeState, i: Int) = state.numbers[i] < 0
    override fun errors(state: RangeState): Set<Int> {
        val out = HashSet<Int>()
        for (i in state.cells.indices) {
            if (state.cells[i] == 1 && RangeState.around(state.w, state.h, i).any { state.cells[it] == 1 }) out.add(i)
            // Les cases non noircies comptent déjà comme blanches : en dessous, c'est trop de noir.
            if (state.numbers[i] >= 0 && state.seen(i) < state.numbers[i]) out.add(i)
        }
        return out
    }
    override fun labelDone(state: RangeState, i: Int) = state.seen(i) == state.numbers[i]
}

class RangeArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFF48FB1.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = RangeState.generate(7, 7, Random(19))
        val cells = IntArray(49) { if (s.numbers[it] < 0 && it % 4 == 0) 1 else 0 }
        drawWith(RangeView(context), RangeState(7, 7, s.numbers, cells, s.solution), canvas, boardRect(w, h, 1.1f), palette)
    }
}
