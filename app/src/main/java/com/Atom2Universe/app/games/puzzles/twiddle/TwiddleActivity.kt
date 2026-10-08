package com.Atom2Universe.app.games.puzzles.twiddle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.roundToInt
import kotlin.random.Random

class TwiddleActivity : PuzzleActivity<TwiddleState>() {
    override val gameKey = "twiddle"
    override val titleRes = R.string.twiddle_title
    override val rulesRes = R.string.twiddle_rules
    override val presets = listOf(
        Preset(R.string.twiddle_preset, 3, 3, 2),
        Preset(R.string.twiddle_preset, 4, 4, 2),
        Preset(R.string.twiddle_preset, 5, 5, 3),
        Preset(R.string.twiddle_preset, 6, 6, 3),
    )
    override val defaultPreset = 1

    override fun createBoard() = TwiddleView(this)
    override fun generate(preset: Int, random: Random): TwiddleState {
        val (n, k) = listOf(3 to 2, 4 to 2, 5 to 3, 6 to 3)[preset]
        return TwiddleState.generate(n, k, random)
    }
    override fun encode(state: TwiddleState) = state.encode()
    override fun decode(text: String) = TwiddleState.decode(text)
    override fun isSolved(state: TwiddleState) = state.isSolved
    /** L'astuce est une démonstration : le journal des coups rembobiné sous les yeux du joueur. */
    override val hasHints = true
    override fun demo(state: TwiddleState) = state.rewind()
    override fun statusText(state: TwiddleState) = getString(R.string.kit_moves, state.moves)
}

/**
 * Chaque plaque porte la couleur de sa ligne d'arrivée : les bandes de couleur se reforment à
 * mesure qu'on avance. Un appui tourne dans le sens horaire le bloc centré sous le doigt, un
 * appui long dans l'autre sens.
 */
class TwiddleView(context: Context) : PuzzleView<TwiddleState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private var lastX = -1
    private var lastY = -1
    private var lastClockwise = true
    private var progress = 1f
    private var animator: ValueAnimator? = null

    override fun drawBoard(canvas: Canvas, state: TwiddleState, area: RectF) {
        grid.fit(area, state.n, state.n)
        val cell = grid.cell
        val pad = cell * 0.05f
        fill.color = palette.raised
        canvas.drawRoundRect(grid.left - pad, grid.top - pad, grid.right + pad, grid.bottom + pad,
            cell * 0.14f, cell * 0.14f, fill)
        val animating = progress < 1f && lastX >= 0
        for (pass in 0..1) for (i in state.tiles.indices) {
            val x = i % state.n
            val y = i / state.n
            val inBlock = animating && x in lastX until lastX + state.k && y in lastY until lastY + state.k
            if (inBlock != (pass == 1)) continue
            canvas.save()
            if (inBlock) {
                val angle = (1f - progress) * 90f * if (lastClockwise) -1f else 1f
                canvas.rotate(angle, grid.x(lastX) + state.k * cell / 2f, grid.y(lastY) + state.k * cell / 2f)
            }
            grid.rect(x, y, r, pad)
            drawTile(canvas, state, state.tiles[i], r, cell)
            canvas.restore()
        }
    }

    private fun drawTile(canvas: Canvas, state: TwiddleState, v: Int, r: RectF, cell: Float) {
        val row = (v - 1) / state.n
        val color = palette.piece(row)
        fill.color = palette.blend(palette.surface, color, if (palette.isLight) 0.35f else 0.45f)
        canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, fill)
        stroke.strokeWidth = cell * 0.035f
        stroke.color = color
        canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, stroke)
        canvas.centeredText(v.toString(), r.centerX(), r.centerY(), cell * 0.4f, palette.text, bold = true)
    }

    /** Le bloc dont le centre est le plus proche du doigt. */
    private fun blockAt(state: TwiddleState, x: Float, y: Float): Pair<Int, Int>? {
        if (x < grid.left || x > grid.right || y < grid.top || y > grid.bottom) return null
        val bx = ((x - grid.left) / grid.cell - state.k / 2f).roundToInt().coerceIn(0, state.n - state.k)
        val by = ((y - grid.top) / grid.cell - state.k / 2f).roundToInt().coerceIn(0, state.n - state.k)
        return bx to by
    }

    private fun turn(x: Float, y: Float, clockwise: Boolean) {
        val s = state ?: return
        val (bx, by) = blockAt(s, x, y) ?: return
        lastX = bx; lastY = by; lastClockwise = clockwise
        tick()
        move(s.rotate(bx, by, clockwise))
        if (ValueAnimator.areAnimatorsEnabled()) {
            animator?.cancel()
            progress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 160L
                interpolator = DecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onTap(x: Float, y: Float) = turn(x, y, true)
    override fun onLongPress(x: Float, y: Float) = turn(x, y, false)
}

class TwiddleArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFBA68C8.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val state = TwiddleState.generate(4, 2, Random(3))
        drawWith(TwiddleView(context), state, canvas, boardRect(w, h, 1.05f), palette)
    }
}
