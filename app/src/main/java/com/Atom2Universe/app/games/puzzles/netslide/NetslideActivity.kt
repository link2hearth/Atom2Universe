package com.Atom2Universe.app.games.puzzles.netslide

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.SlideGridView
import kotlin.random.Random

class NetslideActivity : PuzzleActivity<NetslideState>() {
    override val gameKey = "netslide"
    override val titleRes = R.string.netslide_title
    override val rulesRes = R.string.netslide_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 3, 3),
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 6, 6),
    )
    override val defaultPreset = 1

    override fun createBoard() = NetslideView(this)
    override fun generate(preset: Int, random: Random) = NetslideState.generate(preset + 3, preset + 3, random)
    override fun encode(state: NetslideState) = state.encode()
    override fun decode(text: String) = NetslideState.decode(text)
    override fun isSolved(state: NetslideState) = state.isSolved
    /** L'astuce est une démonstration : le journal des coups rembobiné sous les yeux du joueur. */
    override val hasHints = true
    override fun demo(state: NetslideState) = state.rewind()
    override fun statusText(state: NetslideState) = getString(R.string.kit_moves, state.moves)
}

/** Les tuyaux reliés à la source s'allument de la couleur du thème. */
class NetslideView(context: Context) : SlideGridView<NetslideState>(context) {
    private var powered = BooleanArray(0)

    override fun cols(state: NetslideState) = state.w
    override fun rows(state: NetslideState) = state.h
    override fun shiftRow(state: NetslideState, row: Int, by: Int) = state.shiftRow(row, by)
    override fun shiftCol(state: NetslideState, col: Int, by: Int) = state.shiftCol(col, by)

    override fun drawUnder(canvas: Canvas, state: NetslideState) {
        powered = state.powered()
    }

    override fun drawTile(canvas: Canvas, state: NetslideState, index: Int, r: RectF, cell: Float) {
        val t = state.tiles[index]
        val on = powered.getOrElse(index) { false }
        fill.color = palette.surface
        canvas.drawRoundRect(r, cell * 0.1f, cell * 0.1f, fill)
        val colour = if (on) palette.accent else palette.secondary
        stroke.color = colour
        stroke.strokeWidth = cell * 0.16f
        val cx = r.centerX(); val cy = r.centerY()
        if (t and NetslideState.UP != 0) canvas.drawLine(cx, cy, cx, r.top, stroke)
        if (t and NetslideState.RIGHT != 0) canvas.drawLine(cx, cy, r.right, cy, stroke)
        if (t and NetslideState.DOWN != 0) canvas.drawLine(cx, cy, cx, r.bottom, stroke)
        if (t and NetslideState.LEFT != 0) canvas.drawLine(cx, cy, r.left, cy, stroke)
        val ends = Integer.bitCount(t and 15)
        if (t and NetslideState.SOURCE != 0) {
            fill.color = palette.accent
            canvas.drawRoundRect(cx - cell * 0.22f, cy - cell * 0.22f, cx + cell * 0.22f, cy + cell * 0.22f, cell * 0.06f, cell * 0.06f, fill)
            fill.color = palette.onAccent
            canvas.drawCircle(cx, cy, cell * 0.08f, fill)
        } else if (ends == 1) {
            fill.color = if (on) palette.accent else palette.surface
            canvas.drawCircle(cx, cy, cell * 0.17f, fill)
            stroke.strokeWidth = cell * 0.06f
            canvas.drawCircle(cx, cy, cell * 0.17f, stroke)
        } else {
            fill.color = colour
            canvas.drawCircle(cx, cy, cell * 0.08f, fill)
        }
    }
}

class NetslideArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF26A69A.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = NetslideState.generate(5, 5, Random(27))
        drawWith(NetslideView(context), s, canvas, boardRect(w, h, 1.15f), palette)
    }
}
