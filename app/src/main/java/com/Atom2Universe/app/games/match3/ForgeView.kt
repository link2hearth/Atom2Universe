package com.Atom2Universe.app.games.match3

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.HapticFeedbackConstants
import android.os.SystemClock
import com.Atom2Universe.app.R
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

class ForgeView(context: Context, var game: ForgeGame = ForgeGame(), private val demo: Boolean = false) : View(context) {
    private val art = ForgeArt()
    private val gravityFrame = GravityFrame()
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
    private var burst: ForgeGame.Burst? = null
    private var delivered = emptySet<Int>()
    private var sources: IntArray? = null
    private var swapA = -1
    private var swapB = -1
    private var combo = 0
    private var running = false
    private var activeSince = 0L
    private var sensedGravity: Int? = null
    val gravityDirection get() = sensedGravity ?: game.gravity
    var onGravityChanged: ((Int) -> Unit)? = null
    private var stable = game.save()
    var busy = false; private set
    var onChanged: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onReshuffled: (() -> Unit)? = null
    var onInputFeedback: ((Int) -> Unit)? = null

    init { isFocusable = true; keepScreenOn = true; contentDescription = context.getString(R.string.forge_board_description) }

    fun elapsedTimeMs(): Long = game.elapsedMs + if (activeSince > 0L) SystemClock.uptimeMillis() - activeSince else 0L

    fun stableSave(): String = JSONObject(stable).put("elapsedMs", elapsedTimeMs()).toString()

    fun refresh() {
        if (!busy) sensedGravity?.let { game.gravity = it }
        if (running && activeSince == 0L && !game.won && game.moves > 0) activeSince = SystemClock.uptimeMillis()
        if (width > 0 && height > 0) onSizeChanged(width, height, width, height)
        stable = game.save(); selected = -1; invalidate(); onChanged?.invoke()
    }

    fun setGravity(direction: Int) {
        val changed = gravityDirection != direction
        sensedGravity = direction
        if (changed) { onGravityChanged?.invoke(direction); invalidate() }
        // Listen throughout cascades; freeze the direction only for the current fall.
        if (!busy && game.gravity != direction) {
            game.gravity = direction
            stable = game.save()
            invalidate()
        }
    }

    fun resume() {
        if (!running && !demo) {
            running = true; sound.start()
            if (!game.won && game.moves > 0) activeSince = SystemClock.uptimeMillis()
        }
    }

    fun pause() {
        val elapsed = elapsedTimeMs()
        animation?.removeAllListeners(); animation?.cancel(); animation = null
        if (busy) game.restore(JSONObject(stable))
        game.elapsedMs = elapsed; activeSince = 0L
        stable = JSONObject(stable).put("elapsedMs", elapsed).toString()
        busy = false; popping = emptySet(); sources = null; swapA = -1; swapB = -1
        burst = null; delivered = emptySet()
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
            paint.color = if (game.slag[i]) when (game.slagRules[i]) {
                1 -> 0xff245fa0.toInt()
                2 -> 0xffa33937.toInt()
                else -> when (game.slagLayers[i]) {
                    1 -> 0xffc08a55.toInt()
                    2 -> 0xff865032.toInt()
                    else -> 0xff503021.toInt()
                }
            } else if ((i + i / game.cols) % 2 == 0) 0xff1c2a31.toInt() else 0xff203039.toInt()
            canvas.drawRoundRect(x + 1, y + 1, x + tile - 1, y + tile - 1, 4f, 4f, paint)
            if (selected == i) {
                paint.color = 0xffffcc78.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                canvas.drawRoundRect(x + 2, y + 2, x + tile - 2, y + tile - 2, 5f, 5f, paint); paint.style = Paint.Style.FILL
            }
        }
        // Highlight the physical down edge without rotating the board.
        if (game.kind == 2) for (edge in game.activeDeliveryEdges) {
            paint.color = 0xff78ffa9.toInt()
            val verticalEdge = edge == 1 || edge == 2
            repeat(if (verticalEdge) game.rows else game.cols) { cell ->
                if (verticalEdge) {
                    val x = if (edge == 1) left - 8 else right + 3
                    val y = top + cell * tile
                    canvas.drawRect(x, y + tile * 0.15f, x + 5, y + tile * 0.85f, paint)
                } else {
                    val x = left + cell * tile
                    val y = if (edge == 3) top - 8 else bottom + 3
                    canvas.drawRect(x + tile * 0.15f, y, x + tile * 0.85f, y + 5, paint)
                }
            }
        }
        gravityFrame.draw(canvas, left, top, right, bottom, gravityDirection, resources.displayMetrics.density)
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
            val alpha = when {
                i in popping -> ((1 - progress) * 255).toInt()
                sources != null && game.spawned[i] -> (progress * 255).toInt()
                else -> 255
            }
            art.piece(canvas, game.cells[i], left + col * tile + inset, top + row * tile + inset, tile * scale, alpha, game.coreEdges[i])
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
        drawEffects(canvas, right, bottom)
        canvas.restore()
    }

    private fun drawEffects(canvas: Canvas, right: Float, bottom: Float) {
        val fade = (1f - progress).coerceIn(0f, 1f)
        burst?.let { effect ->
            for (beam in effect.beams) {
                val x = left + (beam.origin % game.cols + .5f) * tile
                val y = top + (beam.origin / game.cols + .5f) * tile
                paint.color = 0xffffb657.toInt(); paint.alpha = (fade * 130).toInt(); paint.strokeWidth = tile * .6f * fade
                if (beam.horizontal) canvas.drawLine(left, y, right, y, paint) else canvas.drawLine(x, top, x, bottom, paint)
                paint.color = 0xfffff4d0.toInt(); paint.alpha = (fade * 255).toInt(); paint.strokeWidth = tile * .1f
                if (beam.horizontal) canvas.drawLine(left, y, right, y, paint) else canvas.drawLine(x, top, x, bottom, paint)
            }
            for (origin in effect.bombs) {
                val x = left + (origin % game.cols + .5f) * tile
                val y = top + (origin / game.cols + .5f) * tile
                val radius = tile * (ForgeGame.BLAST_RADIUS + .5f) * kotlin.math.sqrt(progress)
                paint.style = Paint.Style.FILL; paint.color = 0xffffaa4a.toInt(); paint.alpha = (fade * 65).toInt()
                canvas.drawCircle(x, y, radius, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = tile * .12f * fade + 1
                paint.color = 0xffffdc97.toInt(); paint.alpha = (fade * 255).toInt()
                canvas.drawCircle(x, y, radius, paint)
                paint.strokeWidth = tile * .04f; paint.color = 0xffff754e.toInt(); paint.alpha = (fade * 220).toInt()
                canvas.drawCircle(x, y, radius * .76f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        for (i in delivered) {
            val x = left + (i % game.cols + .5f) * tile
            val y = top + (i / game.cols + .5f) * tile
            paint.style = Paint.Style.STROKE; paint.color = 0xff78ffa9.toInt(); paint.alpha = (fade * 255).toInt(); paint.strokeWidth = tile * .08f
            canvas.drawCircle(x, y, tile * (.3f + progress * 1.1f), paint)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
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
        if (!game.swappable(a, b)) { onInputFeedback?.invoke(R.string.forge_rivet_hint); return }
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
        val effect = game.burst()
        burst = effect; popping = effect.cleared
        sound.playMatch(game.cells[matches.first()], combo++)
        if (effect.bombs.isNotEmpty() || effect.beams.isNotEmpty()) {
            sound.playForgeBurst(effect.bombs.isNotEmpty())
            performHapticFeedback(if (effect.bombs.isNotEmpty()) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
        }
        animate(if (effect.bombs.isNotEmpty()) 480 else if (effect.beams.isNotEmpty()) 340 else 220) {
            game.scoreBurst(effect, combo)
            game.clear(effect.cleared, effect); popping = emptySet(); burst = null
            fall()
        }
    }

    private fun fall() {
        sensedGravity?.let { game.gravity = it }
        sources = game.collapse()
        animate(260) {
            sources = null
            delivered = game.exitingCores()
            if (game.deliver()) {
                sound.playMatch(ForgeGame.CORE, 3)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                animate(220) { delivered = emptySet(); fall() }
            } else resolve(game.matches())
        }
    }

    private fun settle() {
        game.completeStage()
        if (game.won || game.moves == 0) { game.elapsedMs = elapsedTimeMs(); activeSince = 0L }
        if (!game.won && game.moves > 0 && game.ensurePlayable()) onReshuffled?.invoke()
        busy = false; refresh()
        val pending = pendingSwap
        pendingSwap = null
        if (game.won || game.moves == 0) {
            touchCell = -1; pointerId = -1
            onFinished?.invoke()
        } else if (pending != null) {
            if (game.pieceKey(pending.a) == pending.typeA && game.pieceKey(pending.b) == pending.typeB) {
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
        if (game.cells[a] == ForgeGame.RIVET || game.cells[b] == ForgeGame.RIVET) {
            onInputFeedback?.invoke(R.string.forge_rivet_hint); return
        }
        if (!stationary(a) || !stationary(b) || game.pieceKey(a) != originalType) {
            onInputFeedback?.invoke(R.string.forge_board_changed)
            return
        }
        if (busy) {
            if (pendingSwap == null) pendingSwap = PendingSwap(a, b, originalType, game.pieceKey(b))
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
                } else touchType = game.pieceKey(touchCell)
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
                    else if (selected >= 0 && selected != b) requestSwap(selected, b, game.pieceKey(selected))
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
