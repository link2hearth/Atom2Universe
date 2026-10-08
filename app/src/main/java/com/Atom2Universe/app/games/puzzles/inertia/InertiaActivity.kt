package com.Atom2Universe.app.games.puzzles.inertia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

class InertiaActivity : PuzzleActivity<InertiaState>() {
    override val gameKey = "inertia"
    override val titleRes = R.string.inertia_title
    override val rulesRes = R.string.inertia_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 8, 8),
        Preset(R.string.kit_size, 10, 8),
        Preset(R.string.kit_size, 14, 10),
    )
    override val defaultPreset = 1

    override fun createBoard() = InertiaView(this)
    override fun generate(preset: Int, random: Random): InertiaState = when (preset) {
        0 -> InertiaState.generate(8, 8, random)
        1 -> InertiaState.generate(10, 8, random)
        else -> InertiaState.generate(14, 10, random)
    }
    override fun encode(state: InertiaState) = state.encode()
    override fun decode(text: String) = InertiaState.decode(text)
    override fun isSolved(state: InertiaState) = state.isSolved
    /** L'astuce est une démonstration : toutes les gemmes ramassées sous les yeux du joueur. */
    override val hasHints = true
    override val demoStepMs = 550L
    override fun demo(state: InertiaState) = state.demo()
    override fun isLost(state: InertiaState) = state.dead
    override fun statusText(state: InertiaState) = getString(R.string.inertia_status, state.gems, state.moves)
}

/**
 * On lance la bille d'un glissé du doigt, n'importe où sur l'écran, dans l'une des huit
 * directions ; un simple appui la lance vers le doigt.
 */
class InertiaView(context: Context) : PuzzleView<InertiaState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private val path = Path()
    private var startX = 0f
    private var startY = 0f

    override fun drawBoard(canvas: Canvas, state: InertiaState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        for (i in state.cells.indices) {
            grid.rect(i % state.w, i / state.w, r, cell * 0.03f)
            when (state.cells[i]) {
                InertiaState.WALL -> { fill.color = palette.gridBold; canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, fill) }
                else -> { fill.color = palette.cell; canvas.drawRect(r, fill) }
            }
            val cx = r.centerX(); val cy = r.centerY()
            when (state.cells[i]) {
                InertiaState.STOP -> {
                    stroke.color = palette.secondary; stroke.strokeWidth = cell * 0.06f
                    canvas.drawCircle(cx, cy, cell * 0.3f, stroke)
                }
                InertiaState.GEM -> {
                    path.reset()
                    path.moveTo(cx, cy - cell * 0.28f); path.lineTo(cx + cell * 0.22f, cy)
                    path.lineTo(cx, cy + cell * 0.28f); path.lineTo(cx - cell * 0.22f, cy); path.close()
                    fill.color = palette.piece(5)
                    canvas.drawPath(path, fill)
                    fill.color = palette.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
                    canvas.drawCircle(cx - cell * 0.05f, cy - cell * 0.08f, cell * 0.05f, fill)
                }
                InertiaState.MINE -> {
                    fill.color = palette.error
                    canvas.drawCircle(cx, cy, cell * 0.2f, fill)
                    stroke.color = palette.error; stroke.strokeWidth = cell * 0.05f
                    for (k in 0..3) {
                        val a = Math.toRadians(45.0 * k * 2 + 22.5)
                        canvas.drawLine(cx, cy, cx + (Math.cos(a) * cell * 0.32).toFloat(), cy + (Math.sin(a) * cell * 0.32).toFloat(), stroke)
                    }
                }
            }
        }
        val bx = grid.cx(state.ball % state.w); val by = grid.cy(state.ball / state.w)
        val colour = if (state.dead) palette.error else palette.accent
        fill.shader = RadialGradient(bx - cell * 0.1f, by - cell * 0.1f, cell * 0.4f,
            palette.blend(colour, 0xFFFFFFFF.toInt(), 0.5f), colour, Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, cell * 0.33f, fill)
        fill.shader = null
    }

    private fun rollToward(dx: Float, dy: Float) {
        val s = state ?: return
        if (hypot(dx, dy) < 1f) return
        // L'angle se range dans l'une des huit directions (0 = haut, sens horaire).
        val angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
        val d = Math.floorMod((angle / 45.0).roundToInt(), 8)
        s.roll(d)?.let { tick(); move(it) }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        rollToward(x - grid.cx(s.ball % s.w), y - grid.cy(s.ball / s.w))
    }

    override fun wantsDrag(x: Float, y: Float) = true
    override fun onDragStart(x: Float, y: Float) { startX = x; startY = y }
    override fun onDragEnd(x: Float, y: Float) = rollToward(x - startX, y - startY)
}

class InertiaArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF7E57C2.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        drawWith(InertiaView(context), InertiaState.generate(8, 8, Random(28)), canvas, boardRect(w, h, 1.1f), palette)
    }
}
