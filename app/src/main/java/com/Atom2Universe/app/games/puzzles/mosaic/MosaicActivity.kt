package com.Atom2Universe.app.games.puzzles.mosaic

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.puzzles.pattern.PatternPictures
import com.Atom2Universe.app.games.puzzles.pattern.PictureModeActivity
import com.Atom2Universe.app.games.kit.ShadeView
import kotlin.random.Random

class MosaicActivity : PictureModeActivity<MosaicState>() {
    override val gameKey = "mosaic"
    override val titleRes = R.string.mosaic_title
    override val rulesRes = R.string.mosaic_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 13, 13),
        Preset(R.string.pattern_pictures),
    )
    override val defaultPreset = 1
    override val picturesPreset = 3

    override fun createBoard() = MosaicView(this)
    override fun pictureOf(state: MosaicState) = state.picture
    override fun fromPicture(index: Int) = MosaicState.fromPicture(index)
    override fun generateRandom(preset: Int, random: Random): MosaicState {
        val size = listOf(6, 10, 13)[preset]
        return MosaicState.generate(size, size, random)
    }
    override fun encode(state: MosaicState) = state.encode()
    override fun decode(text: String) = MosaicState.decode(text)
    override fun isSolved(state: MosaicState) = state.isSolved
    override val hasHints = true
    override fun hint(state: MosaicState) = state.hint()

    /** Seule commande : la galerie, en mode Images. */
    override fun createControls(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        addView(makeGalleryChip())
    }
}

/** Une image réussie se révèle en couleurs, comme au Logimage. */
class MosaicView(context: Context) : ShadeView<MosaicState>(context) {
    override fun size(state: MosaicState) = state.w to state.h
    override fun cellValue(state: MosaicState, i: Int) = state.cells[i]
    override fun label(state: MosaicState, i: Int) = if (state.clues[i] >= 0) state.clues[i].toString() else null
    override fun change(state: MosaicState, i: Int, value: Int) = state.with(i, value)
    override fun errors(state: MosaicState) = state.clues.indices.filter { state.clues[it] >= 0 && state.clueState(it) < 0 }.toSet()
    override fun labelDone(state: MosaicState, i: Int) = state.clueState(i) == 1
    override fun revealColour(state: MosaicState, i: Int): Int? {
        if (state.picture < 0 || !state.isSolved) return null
        val p = PatternPictures.all[state.picture]
        val c = MosaicState.pictureCell(state, i)
        return if (c >= 0 && p.coloured(c)) p.colourAt(c) else palette.cell
    }
}

class MosaicArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF26C6DA.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = MosaicState.generate(7, 7, Random(18))
        val cells = IntArray(49) { if (it % 3 == 0) 1 else if (it % 4 == 1) 2 else 0 }
        drawWith(MosaicView(context), MosaicState(7, 7, s.clues, cells), canvas, boardRect(w, h, 1.1f), palette)
    }
}
