package com.Atom2Universe.app.games.puzzles.cube

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.abs
import kotlin.random.Random

class CubeActivity : PuzzleActivity<CubeState>() {
    override val gameKey = "cube"
    override val titleRes = R.string.cube_title
    override val rulesRes = R.string.cube_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 7, 7),
    )
    override val defaultPreset = 1

    override fun createBoard() = CubeView(this)
    override fun generate(preset: Int, random: Random): CubeState {
        val size = listOf(4, 5, 7)[preset]
        return CubeState.generate(size, size, random)
    }
    override fun encode(state: CubeState) = state.encode()
    override fun decode(text: String) = CubeState.decode(text)
    override fun isSolved(state: CubeState) = state.isSolved
    /** L'astuce est une démonstration : le cube roule sous les yeux du joueur jusqu'aux six faces. */
    override val hasHints = true
    override val demoStepMs = 500L
    override fun demo(state: CubeState) = CubeSolver.demo(state)
    override fun statusText(state: CubeState) = getString(R.string.cube_moves, state.moves)
}

/**
 * Le cube est vu d'en haut, en tronc de pyramide : le carré du milieu est le dessus, les quatre
 * trapèzes sont les côtés. Sous la grille, son patron déplié montre aussi la face du dessous.
 * Un appui fait rouler le cube vers le doigt ; un glissé, dans le sens du geste.
 */
class CubeView(context: Context) : PuzzleView<CubeState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private val path = Path()
    private var startX = 0f
    private var startY = 0f

    private fun faceColour(on: Boolean) = if (on) palette.piece(5) else palette.raised

    override fun drawBoard(canvas: Canvas, state: CubeState, area: RectF) {
        grid.fit(area, state.w, state.h, 0.3f, 0.3f, 0.3f, 2.2f)
        val cell = grid.cell
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r, cell * 0.03f)
            fill.color = if (state.blue[i]) palette.blend(palette.cell, palette.piece(5), if (palette.isLight) 0.55f else 0.6f) else palette.cell
            canvas.drawRect(r, fill)
        }
        stroke.color = palette.gridBold; stroke.strokeWidth = cell * 0.04f
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, stroke)

        // Le cube, vu d'en haut.
        grid.rect(state.x, state.y, r)
        val o = cell * 0.06f; val inner = cell * 0.24f
        val l = r.left + o; val t = r.top + o; val rr = r.right - o; val b = r.bottom - o
        val il = r.left + inner; val it = r.top + inner; val ir = r.right - inner; val ib = r.bottom - inner
        trapezoid(canvas, l, t, rr, t, ir, it, il, it, state.faces[CubeState.NORTH], 0.82f)
        trapezoid(canvas, l, b, rr, b, ir, ib, il, ib, state.faces[CubeState.SOUTH], 0.7f)
        trapezoid(canvas, l, t, il, it, il, ib, l, b, state.faces[CubeState.WEST], 0.76f)
        trapezoid(canvas, rr, t, ir, it, ir, ib, rr, b, state.faces[CubeState.EAST], 0.76f)
        trapezoid(canvas, il, it, ir, it, ir, ib, il, ib, state.faces[CubeState.TOP], 1f)

        // Le patron déplié : ouest, dessus, est, dessous sur une ligne ; nord et sud autour du dessus.
        val s = cell * 0.55f
        val ox = (grid.left + grid.right) / 2 - 2 * s
        val oy = grid.bottom + cell * 0.45f
        val net = listOf(
            CubeState.WEST to (0 to 1), CubeState.TOP to (1 to 1), CubeState.EAST to (2 to 1), CubeState.BOTTOM to (3 to 1),
            CubeState.NORTH to (1 to 0), CubeState.SOUTH to (1 to 2),
        )
        for ((face, pos) in net) {
            val x0 = ox + pos.first * s; val y0 = oy + pos.second * s
            fill.color = faceColour(state.faces[face])
            canvas.drawRect(x0, y0, x0 + s, y0 + s, fill)
            stroke.color = palette.outline; stroke.strokeWidth = cell * 0.03f
            canvas.drawRect(x0, y0, x0 + s, y0 + s, stroke)
        }
    }

    private fun trapezoid(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float,
                          on: Boolean, light: Float) {
        path.reset()
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.lineTo(x4, y4); path.close()
        fill.color = palette.blend(palette.text, faceColour(on), light)
        canvas.drawPath(path, fill)
        stroke.color = palette.text; stroke.strokeWidth = grid.cell * 0.03f
        canvas.drawPath(path, stroke)
    }

    private fun rollToward(dx: Float, dy: Float) {
        val s = state ?: return
        if (abs(dx) < grid.cell * 0.3f && abs(dy) < grid.cell * 0.3f) return
        val next = if (abs(dx) > abs(dy)) s.roll(if (dx > 0) 1 else -1, 0) else s.roll(0, if (dy > 0) 1 else -1)
        next?.let { tick(); move(it) }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        rollToward(x - grid.cx(s.x), y - grid.cy(s.y))
    }

    override fun wantsDrag(x: Float, y: Float) = state != null
    override fun onDragStart(x: Float, y: Float) { startX = x; startY = y }
    override fun onDragEnd(x: Float, y: Float) = rollToward(x - startX, y - startY)
}

class CubeArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF42A5F5.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = CubeState.generate(4, 4, Random(36))
        val faces = BooleanArray(6) { it % 2 == 0 }
        drawWith(CubeView(context), CubeState(4, 4, s.x, s.y, faces, s.blue, 0), canvas, boardRect(w, h, 1.0f), palette)
    }
}
