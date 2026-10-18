package com.Atom2Universe.app.games.sokoban

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class SokobanBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: SokobanGame? = null
    var onChanged: (() -> Unit)? = null
    var onSolved: (() -> Unit)? = null

    private var solved = false
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // Le dépôt orbital : ardoise, cuivre et menthe. Dessin vectoriel natif.
    private val renderer = SokobanRenderer()
    private var animator: ValueAnimator? = null
    private var progress = 1f
    private var previousPlayer = 0
    private var pushedFrom = -1
    private var pushedTo = -1
    private var facing = SokobanDir.DOWN

    // Swipe
    private var downX = 0f
    private var downY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }

    private fun recalcLayout() {
        val p = game?.puzzle ?: return
        val cellW = (width - paddingLeft - paddingRight).toFloat() / p.width
        val cellH = (height - paddingTop - paddingBottom).toFloat() / p.height
        cellSize = minOf(cellW, cellH)
        offsetX = (width - cellSize * p.width) / 2f
        offsetY = (height - cellSize * p.height) / 2f
    }

    fun loadGame(g: SokobanGame) {
        animator?.cancel()
        progress = 1f
        game = g
        solved = false
        recalcLayout()
        invalidate()
    }

    /** Joue un déplacement et déclenche les rappels. Utilisé par le swipe et le D-pad. */
    fun move(dir: SokobanDir) {
        if (solved || !isEnabled) return
        val g = game ?: return
        animator?.end()
        previousPlayer = g.player
        val oldBoxes = g.boxes.toSet()
        facing = dir
        if (!g.move(dir)) { invalidate(); return }
        pushedFrom = (oldBoxes - g.boxes).firstOrNull() ?: -1
        pushedTo = (g.boxes - oldBoxes).firstOrNull() ?: -1
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 115
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
        invalidate()
        onChanged?.invoke()
        if (g.isSolved()) {
            solved = true
            onSolved?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        renderer.draw(canvas, g, cellSize, offsetX, offsetY,
            progress, previousPlayer, pushedFrom, pushedTo, facing)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = cellSize * 0.4f
                if (abs(dx) < threshold && abs(dy) < threshold) return true
                val dir = if (abs(dx) > abs(dy)) {
                    if (dx > 0) SokobanDir.RIGHT else SokobanDir.LEFT
                } else {
                    if (dy > 0) SokobanDir.DOWN else SokobanDir.UP
                }
                move(dir)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
