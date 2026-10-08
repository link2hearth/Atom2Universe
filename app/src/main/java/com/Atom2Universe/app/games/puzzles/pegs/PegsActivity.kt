package com.Atom2Universe.app.games.puzzles.pegs

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

class PegsActivity : PuzzleActivity<PegsState>() {
    override val gameKey = "pegs"
    override val titleRes = R.string.pegs_title
    override val rulesRes = R.string.pegs_rules
    override val presets = listOf(
        Preset(R.string.pegs_random, 5, 5),
        Preset(R.string.pegs_random, 7, 7),
        Preset(R.string.pegs_random, 9, 9),
        Preset(R.string.pegs_cross),
        Preset(R.string.pegs_octagon),
    )
    override val defaultPreset = 1

    override fun createBoard() = PegsView(this)
    override fun generate(preset: Int, random: Random): PegsState = when (preset) {
        0 -> PegsState.random(5, 5, random)
        1 -> PegsState.random(7, 7, random)
        2 -> PegsState.random(9, 9, random)
        3 -> PegsState.cross(7)
        else -> PegsState.octagon(random)
    }
    override fun encode(state: PegsState) = state.encode()
    override fun decode(text: String) = PegsState.decode(text)
    override fun isSolved(state: PegsState) = state.isSolved
    /** L'astuce est une démonstration : la fin de partie jouée saut par saut (en revenant d'abord en arrière si besoin). */
    override val hasHints = true
    override val demoStepMs = 650L
    override val demoWhenLost = true
    override fun demo(state: PegsState) = state.demo()
    override fun isLost(state: PegsState) = !state.isSolved && !state.hasMoves
    override fun statusText(state: PegsState) = getString(R.string.pegs_left, state.pegs)
}

/**
 * Toucher une fiche la prend (ses arrivées possibles s'allument), toucher une arrivée la fait
 * sauter. On peut aussi la faire glisser jusqu'à son trou.
 */
class PegsView(context: Context) : PuzzleView<PegsState>(context) {
    private val grid = GridGeometry()
    private var selected = -1
    private var dragX = 0f
    private var dragY = 0f
    private var dragging = false

    override fun onStateChanged() {
        selected = -1
        dragging = false
    }

    override fun drawBoard(canvas: Canvas, state: PegsState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val targets = if (selected >= 0) state.targets(selected) else emptyList()
        // Le plateau : chaque trou posé sur un socle aux couleurs du thème.
        for (i in state.cells.indices) {
            if (state.cells[i] == PegsState.WALL) continue
            val cx = grid.cx(i % state.w); val cy = grid.cy(i / state.w)
            fill.color = palette.raised
            canvas.drawCircle(cx, cy, cell * 0.48f, fill)
        }
        for (i in state.cells.indices) {
            if (state.cells[i] == PegsState.WALL) continue
            val cx = grid.cx(i % state.w); val cy = grid.cy(i / state.w)
            fill.color = palette.blend(palette.background, palette.raised, 0.3f)
            canvas.drawCircle(cx, cy, cell * 0.2f, fill)
            if (i in targets) {
                stroke.color = palette.accent
                stroke.strokeWidth = cell * 0.06f
                canvas.drawCircle(cx, cy, cell * 0.3f, stroke)
            }
            if (state.cells[i] == PegsState.PEG && !(dragging && i == selected)) {
                drawPeg(canvas, cx, cy, cell, i == selected)
            }
        }
        if (dragging && selected >= 0) drawPeg(canvas, dragX, dragY, cell * 1.1f, true)
    }

    private fun drawPeg(canvas: Canvas, cx: Float, cy: Float, cell: Float, lifted: Boolean) {
        val base = if (lifted) palette.accent else palette.blend(palette.accent, palette.text, 0.25f)
        fill.shader = RadialGradient(cx - cell * 0.1f, cy - cell * 0.12f, cell * 0.4f,
            palette.blend(base, 0xFFFFFFFF.toInt(), 0.45f), base, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, cell * 0.33f, fill)
        fill.shader = null
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) { selected = -1; invalidate(); return }
        if (selected >= 0) {
            s.jump(selected, i)?.let { tick(); move(it); return }
        }
        selected = if (s.cells[i] == PegsState.PEG && i != selected) i else -1
        invalidate()
    }

    override fun wantsDrag(x: Float, y: Float): Boolean {
        val s = state ?: return false
        val i = grid.cellAt(x, y)
        return i >= 0 && s.cells[i] == PegsState.PEG
    }

    override fun onDragStart(x: Float, y: Float) {
        selected = grid.cellAt(x, y)
        dragging = true
        dragX = x; dragY = y
    }

    override fun onDragMove(x: Float, y: Float) {
        dragX = x; dragY = y
        invalidate()
    }

    override fun onDragEnd(x: Float, y: Float) {
        dragging = false
        val s = state
        val target = grid.cellAt(x, y)
        val next = if (s != null && selected >= 0 && target >= 0) s.jump(selected, target) else null
        if (next != null) { tick(); move(next) } else { selected = -1; invalidate() }
    }

    override fun onDragCancel() {
        dragging = false
        selected = -1
        invalidate()
    }
}

class PegsArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFF8A65.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        drawWith(PegsView(context), PegsState.cross(7), canvas, boardRect(w, h, 1.1f), palette)
    }
}
