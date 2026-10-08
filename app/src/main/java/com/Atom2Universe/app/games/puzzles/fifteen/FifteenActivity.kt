package com.Atom2Universe.app.games.puzzles.fifteen

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
import kotlin.random.Random

class FifteenActivity : PuzzleActivity<FifteenState>() {
    override val gameKey = "fifteen"
    override val titleRes = R.string.fifteen_title
    override val rulesRes = R.string.fifteen_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 3, 3),
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
    )
    override val defaultPreset = 1

    override fun createBoard() = FifteenView(this)
    override fun generate(preset: Int, random: Random) = FifteenState.generate(preset + 3, preset + 3, random)
    override fun encode(state: FifteenState) = state.encode()
    override fun decode(text: String) = FifteenState.decode(text)
    override fun isSolved(state: FifteenState) = state.isSolved
    override fun statusText(state: FifteenState) = getString(R.string.kit_moves, state.moves)

    /**
     * L'astuce du taquin est une démonstration : il se résout sous les yeux du joueur, lentement,
     * étape par étape comme on l'apprend ; les plaques que l'étape est en train de placer sont
     * mises en évidence, et leurs cases d'arrivée cerclées.
     */
    override val hasHints = true
    override val demoStepMs = 380L
    private var demoFocus: List<IntArray> = emptyList()
    override fun demo(state: FifteenState): List<FifteenState>? =
        FifteenSolver.solve(state)?.also { steps -> demoFocus = steps.map { it.focus } }?.map { it.state }
    override fun onDemoStep(step: Int?) {
        (board as FifteenView).focus = step?.let { demoFocus.getOrNull(it) }
    }
}

class FifteenView(context: Context) : PuzzleView<FifteenState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()

    /** Les plaques que la démonstration est en train de placer (null hors démonstration). */
    var focus: IntArray? = null
        set(value) { field = value; invalidate() }

    /** Position d'avant le coup de chaque plaque, pour la faire glisser. */
    private var previous: FifteenState? = null
    private var from = IntArray(0)
    private var progress = 1f
    private var animator: ValueAnimator? = null

    override fun onStateChanged() {
        val s = state ?: return
        val p = previous
        previous = s
        if (p == null || p.w != s.w || p.h != s.h || !ValueAnimator.areAnimatorsEnabled()) {
            progress = 1f
            return
        }
        from = IntArray(s.w * s.h)
        for (i in p.tiles.indices) from[p.tiles[i]] = i
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 110L
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun drawBoard(canvas: Canvas, state: FifteenState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val pad = cell * 0.05f
        fill.color = palette.raised
        canvas.drawRoundRect(grid.left - pad, grid.top - pad, grid.right + pad, grid.bottom + pad,
            cell * 0.14f, cell * 0.14f, fill)
        val solved = state.isSolved
        val focus = focus
        // Démonstration : les cases d'arrivée des plaques en cours, cerclées de l'accent.
        if (focus != null) for (v in focus) {
            val t = v - 1
            r.set(grid.left + (t % state.w) * cell + pad, grid.top + (t / state.w) * cell + pad,
                grid.left + (t % state.w + 1) * cell - pad, grid.top + (t / state.w + 1) * cell - pad)
            stroke.color = palette.withAlpha(palette.accent, 0.6f); stroke.strokeWidth = cell * 0.05f
            canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, stroke)
        }
        for (i in state.tiles.indices) {
            val v = state.tiles[i]
            if (v == 0) continue
            val start = if (progress < 1f && from.size == state.tiles.size) from[v] else i
            val x = (start % state.w) + ((i % state.w) - (start % state.w)) * progress
            val y = (start / state.w) + ((i / state.w) - (start / state.w)) * progress
            r.set(grid.left + x * cell + pad, grid.top + y * cell + pad,
                grid.left + (x + 1) * cell - pad, grid.top + (y + 1) * cell - pad)
            val home = v == i + 1
            val focused = focus?.contains(v) == true
            fill.color = when {
                solved -> palette.cellHighlight
                focused -> palette.blend(palette.surface, palette.accent, 0.38f)
                home -> palette.blend(palette.surface, palette.accent, 0.10f)
                else -> palette.surface
            }
            canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, fill)
            stroke.strokeWidth = cell * 0.03f
            stroke.color = if (home || focused) palette.withAlpha(palette.accent, 0.7f) else palette.outline
            if (focused) stroke.strokeWidth = cell * 0.06f
            canvas.drawRoundRect(r, cell * 0.12f, cell * 0.12f, stroke)
            canvas.centeredText(v.toString(), r.centerX(), r.centerY(), cell * 0.42f,
                if (home) palette.ink else palette.text, bold = true)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val index = grid.cellAt(x, y)
        if (index < 0) return
        s.slide(index)?.let { tick(); move(it) }
    }
}

class FifteenArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFFB74D.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val tiles = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 11, 13, 14, 15, 12)
        drawWith(FifteenView(context), FifteenState(4, 4, tiles, 0), canvas, boardRect(w, h, 1.05f), palette)
    }
}
