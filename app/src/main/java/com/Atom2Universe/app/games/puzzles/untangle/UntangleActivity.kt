package com.Atom2Universe.app.games.puzzles.untangle

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

class UntangleActivity : PuzzleActivity<UntangleState>() {
    override val gameKey = "untangle"
    override val titleRes = R.string.untangle_title
    override val rulesRes = R.string.untangle_rules
    override val presets = listOf(
        Preset(R.string.untangle_points, 6),
        Preset(R.string.untangle_points, 10),
        Preset(R.string.untangle_points, 15),
        Preset(R.string.untangle_points, 20),
        Preset(R.string.untangle_points, 25),
    )
    override val defaultPreset = 1

    override fun createBoard() = UntangleView(this)
    override fun generate(preset: Int, random: Random) =
        UntangleState.generate(listOf(6, 10, 15, 20, 25)[preset], random)
    override fun encode(state: UntangleState) = state.encode()
    override fun decode(text: String) = UntangleState.decode(text)
    override fun isSolved(state: UntangleState) = state.isSolved
    override val hasHints = true
    override fun hint(state: UntangleState) = state.hint()
    override fun statusText(state: UntangleState) = getString(R.string.untangle_crossings, state.crossingEdges().size)
}

/**
 * Les arêtes qui en croisent une autre sont tracées dans la couleur d'erreur ; le point qu'on
 * tient et ses voisins s'allument. On attrape le point le plus proche du doigt.
 */
class UntangleView(context: Context) : PuzzleView<UntangleState>(context) {
    private var held = -1
    private var heldX = 0f
    private var heldY = 0f
    private var crossing: Set<Int> = emptySet()
    private val box = RectF()

    override fun onStateChanged() {
        crossing = state?.crossingEdges() ?: emptySet()
    }

    private fun layout(area: RectF) {
        val side = min(area.width(), area.height())
        val margin = side * 0.06f
        box.set(area.centerX() - side / 2 + margin, area.centerY() - side / 2 + margin,
            area.centerX() + side / 2 - margin, area.centerY() + side / 2 - margin)
    }

    private fun px(state: UntangleState, i: Int) = if (i == held) heldX else box.left + state.xs[i] * box.width()
    private fun py(state: UntangleState, i: Int) = if (i == held) heldY else box.top + state.ys[i] * box.height()

    override fun drawBoard(canvas: Canvas, state: UntangleState, area: RectF) {
        layout(area)
        val unit = box.width()
        val live = if (held >= 0) {
            val xs = state.xs.copyOf(); val ys = state.ys.copyOf()
            xs[held] = (heldX - box.left) / unit; ys[held] = (heldY - box.top) / unit
            UntangleState(xs, ys, state.edges, 0, state.goalX, state.goalY).crossingEdges()
        } else if (this.state === state) crossing else state.crossingEdges()
        val solved = live.isEmpty()
        stroke.strokeWidth = unit * 0.008f
        for (e in 0 until state.edges.size / 2) {
            val a = state.edges[2 * e]; val b = state.edges[2 * e + 1]
            val near = held >= 0 && (a == held || b == held)
            stroke.color = when {
                solved -> palette.accent
                e in live -> palette.error
                near -> palette.accent
                else -> palette.secondary
            }
            canvas.drawLine(px(state, a), py(state, a), px(state, b), py(state, b), stroke)
        }
        val r = unit * 0.022f
        for (i in 0 until state.n) {
            val x = px(state, i); val y = py(state, i)
            fill.color = if (i == held) palette.accent else palette.text
            canvas.drawCircle(x, y, if (i == held) r * 1.5f else r, fill)
            fill.color = palette.surface
            canvas.drawCircle(x, y, r * 0.45f, fill)
        }
    }

    private fun nearest(x: Float, y: Float): Int {
        val s = state ?: return -1
        var best = -1
        var bestD = box.width() * 0.09f
        for (i in 0 until s.n) {
            val d = hypot(box.left + s.xs[i] * box.width() - x, box.top + s.ys[i] * box.height() - y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    override fun wantsDrag(x: Float, y: Float) = nearest(x, y) >= 0

    override fun onDragStart(x: Float, y: Float) {
        held = nearest(x, y)
        heldX = x; heldY = y
        invalidate()
    }

    override fun onDragMove(x: Float, y: Float) {
        heldX = x.coerceIn(box.left, box.right)
        heldY = y.coerceIn(box.top, box.bottom)
        invalidate()
    }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        val p = held
        held = -1
        if (s == null || p < 0) { invalidate(); return }
        move(s.moved(p, (heldX - box.left) / box.width(), (heldY - box.top) / box.height()))
    }

    override fun onDragCancel() {
        held = -1
        invalidate()
    }
}

class UntangleArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF81C784.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val state = UntangleState.generate(12, Random(9))
        drawWith(UntangleView(context), state, canvas, boardRect(w, h, 1.15f), palette)
    }
}
