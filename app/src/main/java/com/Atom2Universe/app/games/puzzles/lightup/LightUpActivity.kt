package com.Atom2Universe.app.games.puzzles.lightup

import android.content.Context
import android.graphics.Canvas
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.random.Random

class LightUpActivity : PuzzleActivity<LightUpState>() {
    override val gameKey = "lightup"
    override val titleRes = R.string.lightup_title
    override val rulesRes = R.string.lightup_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 7, 7),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 12, 12),
    )
    override val defaultPreset = 1

    override fun createBoard() = LightUpView(this)
    override fun generate(preset: Int, random: Random): LightUpState {
        val size = listOf(7, 10, 12)[preset]
        return LightUpState.generate(size, size, 0.2f, random)
    }
    override fun encode(state: LightUpState) = state.encode()
    override fun decode(text: String) = LightUpState.decode(text)
    override fun isSolved(state: LightUpState) = state.isSolved
    override val hasHints = true
    override fun hint(state: LightUpState) = state.hint()
}

/** Appui : ampoule ; appui long : petit point « pas d'ampoule ici ». */
class LightUpView(context: Context) : PuzzleView<LightUpState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    override fun drawBoard(canvas: Canvas, state: LightUpState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val lit = state.lit()
        val clash = state.clashingLights()
        for (i in state.walls.indices) {
            grid.rect(i % state.w, i / state.w, r)
            if (state.isOpen(i)) {
                fill.color = if (lit[i]) palette.blend(palette.cell, palette.accent, if (palette.isLight) 0.28f else 0.32f) else palette.cell
                canvas.drawRect(r, fill)
            } else {
                fill.color = palette.gridBold
                canvas.drawRect(r, fill)
                val v = state.walls[i]
                if (v >= 0) {
                    val on = state.lightsAround(i)
                    val colour = when {
                        on > v -> palette.error
                        on == v -> palette.withAlpha(palette.surface, 0.55f)
                        else -> palette.surface
                    }
                    canvas.centeredText(v.toString(), r.centerX(), r.centerY(), cell * 0.5f, colour, bold = true)
                }
            }
        }
        stroke.color = palette.gridLine
        stroke.strokeWidth = cell * 0.02f
        for (k in 0..state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 0..state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        for (i in state.cells.indices) {
            grid.rect(i % state.w, i / state.w, r)
            when (state.cells[i]) {
                1 -> {
                    val colour = if (i in clash) palette.error else palette.accent
                    fill.shader = RadialGradient(r.centerX(), r.centerY() - cell * 0.05f, cell * 0.32f,
                        palette.blend(colour, 0xFFFFFFFF.toInt(), 0.55f), colour, Shader.TileMode.CLAMP)
                    canvas.drawCircle(r.centerX(), r.centerY() - cell * 0.04f, cell * 0.26f, fill)
                    fill.shader = null
                    fill.color = palette.secondary
                    canvas.drawRect(r.centerX() - cell * 0.09f, r.centerY() + cell * 0.18f, r.centerX() + cell * 0.09f, r.centerY() + cell * 0.3f, fill)
                }
                2 -> {
                    fill.color = palette.secondary
                    canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.07f, fill)
                }
            }
        }
    }

    private fun act(x: Float, y: Float, light: Boolean) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0 || !s.isOpen(i)) return
        val target = if (light) 1 else 2
        tick()
        move(s.with(i, if (s.cells[i] == target) 0 else target))
    }

    override fun onTap(x: Float, y: Float) = act(x, y, true)
    override fun onLongPress(x: Float, y: Float) = act(x, y, false)
}

class LightUpArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFFE082.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = LightUpState.generate(7, 7, 0.2f, Random(10))
        val cells = IntArray(49) { if (s.isOpen(it) && it % 5 == 0 && LightUpState.sightOf(7, 7, s.walls, it).size > 3) 1 else 0 }
        drawWith(LightUpView(context), LightUpState(7, 7, s.walls, cells, s.solution), canvas, boardRect(w, h, 1.1f), palette)
    }
}
