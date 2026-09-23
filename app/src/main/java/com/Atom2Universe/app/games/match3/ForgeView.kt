package com.Atom2Universe.app.games.match3

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

class ForgeView(context: Context, var game: ForgeGame = ForgeGame(), private val demo: Boolean = false) : View(context) {
    private val art = ForgeArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sound = Match3SoundEngine()
    private var tile = 0f
    private var left = 0f
    private var top = 0f
    private var selected = -1
    private var downX = 0f
    private var downY = 0f
    private var touchCell = -1
    private var touchType = -1
    private var pointerId = -1
    private var gestureConsumed = false
    private data class PendingSwap(val a: Int, val b: Int, val typeA: Int, val typeB: Int)
    private var pendingSwap: PendingSwap? = null
    private var animation: ValueAnimator? = null
    private var progress = 0f
    private var popping = emptySet<Int>()
    private var sources: IntArray? = null
    private var swapA = -1
    private var swapB = -1
    private var combo = 0
    private var running = false
    private var sensedGravity: Int? = null
    private var stable = game.save()
    var busy = false; private set
    var onChanged: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onReshuffled: (() -> Unit)? = null
    var onInputFeedback: ((Int) -> Unit)? = null

    init { isFocusable = true; keepScreenOn = true; contentDescription = context.getString(R.string.forge_board_description) }

    fun stableSave(): String = stable

    fun refresh() {
        if (!busy) sensedGravity?.let { game.gravity = it }
        stable = game.save(); selected = -1; invalidate(); onChanged?.invoke()
    }

    fun setGravity(direction: Int) {
        sensedGravity = direction
        // Listen throughout cascades; freeze the direction only for the current fall.
        if (!busy && game.gravity != direction) {
            game.gravity = direction
            stable = game.save()
            invalidate()
        }
    }

    fun resume() { if (!running && !demo) { running = true; sound.start() } }

    fun pause() {
        animation?.removeAllListeners(); animation?.cancel(); animation = null
        if (busy) game.restore(JSONObject(stable))
        busy = false; popping = emptySet(); sources = null; swapA = -1; swapB = -1
        selected = -1
        touchCell = -1; pointerId = -1; pendingSwap = null
        if (running) { sound.stop(); running = false }
        invalidate(); onChanged?.invoke()
    }

    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        tile = min((w - 28f * resources.displayMetrics.density) / game.cols, (h - 28f * resources.displayMetrics.density) / game.rows).coerceAtLeast(1f)
        left = (w - tile * game.cols) / 2f; top = (h - tile * game.rows) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val right = left + tile * game.cols; val bottom = top + tile * game.rows
        paint.color = 0xff34444a.toInt(); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(left - 9, top - 9, right + 9, bottom + 9, 14f, 14f, paint)
        paint.color = 0xff101d24.toInt(); canvas.drawRoundRect(left - 3, top - 3, right + 3, bottom + 3, 8f, 8f, paint)
        paint.color = 0xff9da8a5.toInt()
        for (x in floatArrayOf(left - 5, right + 5)) for (y in floatArrayOf(top - 5, bottom + 5)) {
            canvas.drawCircle(x, y, 2.5f, paint)
        }
        for (i in game.cells.indices) {
            val x = left + (i % game.cols) * tile; val y = top + (i / game.cols) * tile
            paint.color = if (game.slag[i]) 0xff655447.toInt() else if ((i + i / game.cols) % 2 == 0) 0xff1c2a31.toInt() else 0xff203039.toInt()
            canvas.drawRoundRect(x + 1, y + 1, x + tile - 1, y + tile - 1, 4f, 4f, paint)
            if (game.slag[i]) {
                paint.color = 0xffaa8052.toInt(); paint.strokeWidth = 2f
                canvas.drawLine(x + 4, y + tile - 6, x + tile - 5, y + 5, paint)
                canvas.drawLine(x + 5, y + 5, x + tile - 5, y + tile - 6, paint)
            }
            if (selected == i) {
                paint.color = 0xffffcc78.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                canvas.drawRoundRect(x + 2, y + 2, x + tile - 2, y + tile - 2, 5f, 5f, paint); paint.style = Paint.Style.FILL
            }
        }
        // Highlight the physical down edge without rotating the board.
        if (game.kind == 2) {
            paint.color = 0xff73e4df.toInt()
            val verticalEdge = game.deliveryEdge == 1 || game.deliveryEdge == 2
            repeat(if (verticalEdge) game.rows else game.cols) { cell ->
                if (verticalEdge) {
                    val x = if (game.deliveryEdge == 1) left - 8 else right + 3
                    val y = top + cell * tile
                    canvas.drawRect(x, y + tile * 0.15f, x + 5, y + tile * 0.85f, paint)
                } else {
                    val x = left + cell * tile
                    val y = if (game.deliveryEdge == 3) top - 8 else bottom + 3
                    canvas.drawRect(x + tile * 0.15f, y, x + tile * 0.85f, y + 5, paint)
                }
            }
        }
        paint.color = 0xffffb15c.toInt(); paint.strokeWidth = 3f
        if (game.gravity == 0) canvas.drawLine(left, bottom + 5, right, bottom + 5, paint)
        if (game.gravity == 1) canvas.drawLine(left - 5, top, left - 5, bottom, paint)
        if (game.gravity == 2) canvas.drawLine(right + 5, top, right + 5, bottom, paint)
        if (game.gravity == 3) canvas.drawLine(left, top - 5, right, top - 5, paint)
        canvas.save(); canvas.clipRect(left, top, right, bottom)
        for (i in game.cells.indices) {
            var col = (i % game.cols).toFloat(); var row = (i / game.cols).toFloat()
            val other = if (i == swapA) swapB else if (i == swapB) swapA else -1
            if (other >= 0) { col += (other % game.cols - col) * progress; row += (other / game.cols - row) * progress }
            sources?.let { from ->
                val source = from[i]
                val sourceCol = if (source >= 0) (source % game.cols).toFloat() else if (game.gravity == 1) game.cols - source - 1f else if (game.gravity == 2) source.toFloat() else col
                val sourceRow = if (source >= 0) (source / game.cols).toFloat() else if (game.gravity == 0) source.toFloat() else if (game.gravity == 3) game.rows - source - 1f else row
                col = sourceCol + (col - sourceCol) * progress; row = sourceRow + (row - sourceRow) * progress
            }
            val scale = if (i in popping) 1f - progress * 0.8f else 1f
            val inset = tile * (1f - scale) / 2
            art.piece(canvas, game.cells[i], left + col * tile + inset, top + row * tile + inset, tile * scale, if (i in popping) ((1 - progress) * 255).toInt() else 255)
            if (i in popping) {
                paint.color = art.colors[game.cells[i]]; paint.alpha = ((1 - progress) * 255).toInt()
                repeat(6) { n ->
                    val angle = n * Math.PI / 3
                    canvas.drawCircle(left + (col + 0.5f) * tile + kotlin.math.cos(angle).toFloat() * tile * progress,
                        top + (row + 0.5f) * tile + kotlin.math.sin(angle).toFloat() * tile * progress, tile * 0.045f, paint)
                }
                paint.alpha = 255
            }
        }
        canvas.restore()
    }

    private fun animate(duration: Long, after: () -> Unit) {
        progress = 0f
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { after() } })
            start()
        }
    }

    private fun attempt(a: Int, b: Int) {
        if (abs(a % game.cols - b % game.cols) + abs(a / game.cols - b / game.cols) != 1) { selected = b; invalidate(); return }
        stable = game.save(); busy = true; selected = -1; combo = 0; swapA = a; swapB = b
        onChanged?.invoke()
        animate(160) {
            game.swap(a, b); swapA = -1; swapB = -1
            val matches = game.matches()
            if (matches.isEmpty()) {
                swapA = a; swapB = b
                animate(160) {
                    game.swap(a, b); swapA = -1; swapB = -1
                    settle()
                    if (!busy) onInputFeedback?.invoke(R.string.forge_invalid_swap)
                }
            } else { game.moves--; resolve(matches) }
        }
    }

    private fun resolve(matches: Set<Int>) {
        if (matches.isEmpty()) { settle(); return }
        popping = matches; sound.playMatch(game.cells[matches.first()], combo++)
        animate(240) {
            game.clear(matches); popping = emptySet()
            sensedGravity?.let { game.gravity = it }
            sources = game.collapse()
            animate(300) { sources = null; game.deliver(); resolve(game.matches()) }
        }
    }

    private fun settle() {
        game.deliver()
        if (!game.won && game.moves > 0 && game.ensurePlayable()) onReshuffled?.invoke()
        busy = false; refresh()
        val pending = pendingSwap
        pendingSwap = null
        if (game.won || game.moves == 0) {
            touchCell = -1; pointerId = -1
            onFinished?.invoke()
        } else if (pending != null) {
            if (game.cells[pending.a] == pending.typeA && game.cells[pending.b] == pending.typeB) {
                attempt(pending.a, pending.b)
            } else onInputFeedback?.invoke(R.string.forge_board_changed)
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun cellAt(x: Float, y: Float): Int {
        if (x < left || y < top || x >= left + tile * game.cols || y >= top + tile * game.rows) return -1
        return ((y - top) / tile).toInt() * game.cols + ((x - left) / tile).toInt()
    }

    private fun stationary(cell: Int): Boolean = cell in game.cells.indices &&
        cell != swapA && cell != swapB && cell !in popping &&
        (sources == null || sources?.get(cell) == cell)

    private fun requestSwap(a: Int, b: Int, originalType: Int) {
        if (!stationary(a) || !stationary(b) || game.cells[a] != originalType) {
            onInputFeedback?.invoke(R.string.forge_board_changed)
            return
        }
        if (busy) {
            if (pendingSwap == null) pendingSwap = PendingSwap(a, b, originalType, game.cells[b])
            onInputFeedback?.invoke(R.string.forge_input_queued)
        } else attempt(a, b)
    }

    private fun swipe(x: Float, y: Float): Boolean {
        val a = touchCell
        if (a < 0 || gestureConsumed) return false
        val dx = x - downX; val dy = y - downY
        if (maxOf(abs(dx), abs(dy)) < tile * 0.3f) return false
        gestureConsumed = true
        val c = a % game.cols + if (abs(dx) >= abs(dy)) { if (dx > 0) 1 else -1 } else 0
        val r = a / game.cols + if (abs(dy) > abs(dx)) { if (dy > 0) 1 else -1 } else 0
        if (c in 0 until game.cols && r in 0 until game.rows) requestSwap(a, r * game.cols + c, touchType)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (demo || game.won || game.moves <= 0) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                downX = event.x; downY = event.y; gestureConsumed = false
                touchCell = cellAt(downX, downY)
                if (!stationary(touchCell)) {
                    touchCell = -1
                    if (busy) onInputFeedback?.invoke(R.string.forge_resolving)
                } else touchType = game.cells[touchCell]
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) swipe(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                val index = event.findPointerIndex(pointerId)
                if (index >= 0 && touchCell >= 0 && !gestureConsumed && !swipe(event.getX(index), event.getY(index))) {
                    val b = touchCell
                    if (busy) onInputFeedback?.invoke(R.string.forge_resolving)
                    else if (selected >= 0 && selected != b) requestSwap(selected, b, game.cells[selected])
                    else { selected = if (selected == b) -1 else b; invalidate() }
                }
                touchCell = -1; pointerId = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) { touchCell = -1; pointerId = -1 }
            }
            MotionEvent.ACTION_CANCEL -> {
                touchCell = -1; pointerId = -1; selected = -1; invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
