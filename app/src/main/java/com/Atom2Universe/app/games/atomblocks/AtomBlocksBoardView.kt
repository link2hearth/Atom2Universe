package com.Atom2Universe.app.games.atomblocks

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A square board and a hand of three pieces. The activity owns every game mutation. */
class AtomBlocksBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var game: AtomBlocksEngine? = null
        set(value) {
            field = value
            clearSelection()
            moveEffect = null
            particles.clear()
            celebrationAt = -1L
            refresh()
        }
    var onPlace: ((slot: Int, row: Int, col: Int) -> Unit)? = null
    var onSelect: (() -> Unit)? = null
    var effectsEnabled = true
        set(value) { field = value; if (!value) discardEffects(); invalidate() }
    var reducedEffects = false
        set(value) { field = value; if (value) discardEffects(); invalidate() }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val surface = AppearanceStyle.color(context, R.attr.a2uSurfaceColor)
    private val raised = AppearanceStyle.color(context, R.attr.a2uRaisedColor)
    private val textColor = AppearanceStyle.color(context, R.attr.a2uTextColor)
    private val muted = AppearanceStyle.color(context, R.attr.a2uSecondaryTextColor)
    private val accent = AppearanceStyle.color(context, R.attr.a2uMusicAccent)
    private val outline = AppearanceStyle.color(context, R.attr.a2uOutlineColor)
    private val light = AppearanceStyle.isLight(context)
    private val gradients = AppearanceStyle.hasGradients(context)
    private val ornaments = AppearanceStyle.hasOrnaments(context)
    private val cornerScale = (AppearanceStyle.corner(context, 10f) / dp(10f)).coerceAtLeast(0f)
    private val colors = intArrayOf(
        accent,
        blend(0xFF62CDB4.toInt(), accent, .16f),
        blend(0xFFF1B968.toInt(), accent, .10f),
        blend(0xFFA994EA.toInt(), accent, .15f),
        blend(0xFFEB879A.toInt(), accent, .10f),
        blend(0xFF75BCEC.toInt(), accent, .15f)
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val shapePath = Path()
    private val rect = RectF()
    private val boardRect = RectF()
    private val slots = Array(3) { RectF() }
    private var cellSize = 0f
    private var selectedSlot = -1
    private var previewRow = -99
    private var previewCol = -99
    private var previewLegal = false
    private var previewClears: List<AtomBlocksEngine.Cell> = emptyList()
    private var dragging = false
    private var downInTray = false
    private var downOnBoard = false
    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var invalidAt = -1L
    private var hintVisible = false
    private var celebrationAt = -1L
    private var celebrationWin = false
    private data class MoveEffect(val result: AtomBlocksEngine.MoveResult, val started: Long)
    private var moveEffect: MoveEffect? = null
    private data class Particle(
        val x: Float, val y: Float, val vx: Float, val vy: Float,
        val radius: Float, val color: Int, val started: Long, val duration: Long
    )
    private val particles = ArrayList<Particle>()
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private var accessibilityFocus = NO_VIRTUAL_ID
    private var hoveredNode = NO_VIRTUAL_ID
    private val screenLocation = IntArray(2)
    private val nodeProvider = BoardAccessibilityProvider()

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.ab_board_accessibility)
    }

    /** Keeps effects alive after a score refresh, but drops a piece that has been consumed. */
    fun refresh() {
        if (game?.tray?.getOrNull(selectedSlot) == null) clearSelection()
        if (accessibilityFocus >= TRAY_NODE && game?.tray?.getOrNull(accessibilityFocus - TRAY_NODE) == null) {
            virtualEvent(accessibilityFocus, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
            accessibilityFocus = NO_VIRTUAL_ID
        }
        updatePreview()
        invalidate()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    fun clearSelection() {
        selectedSlot = -1
        previewRow = -99
        previewCol = -99
        previewLegal = false
        previewClears = emptyList()
        hintVisible = false
        dragging = false
        downInTray = false
        downOnBoard = false
        pointerId = -1
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    fun showHint(move: AtomBlocksEngine.Move?) {
        clearSelection()
        if (move == null) return
        selectedSlot = move.slot
        previewRow = move.row
        previewCol = move.col
        hintVisible = true
        updatePreview()
        announceForAccessibility(context.getString(R.string.ab_hint_announcement, move.slot + 1, move.row + 1, move.col + 1))
        invalidate()
    }

    fun showMove(result: AtomBlocksEngine.MoveResult) {
        clearSelection()
        val now = SystemClock.uptimeMillis()
        moveEffect = MoveEffect(result, now)
        if (animated()) {
            // Bounded particle count, also for an eight-by-eight all-clear.
            result.cleared.forEachIndexed { index, c ->
                repeat(if (result.cleared.size > 24) 2 else 4) { ray ->
                    val angle = index * 2.39996f + ray * 1.5708f
                    val speed = cellSize * (1.4f + (index % 4) * .25f)
                    particles.add(Particle(
                        c.col + .5f, c.row + .5f,
                        cos(angle) * speed, sin(angle) * speed - cellSize,
                        cellSize * (.035f + (ray % 2) * .025f), blockColor(c.color), now, 720L
                    ))
                }
            }
        }
        refresh()
    }

    fun celebrate(win: Boolean) {
        celebrationWin = win
        celebrationAt = SystemClock.uptimeMillis()
        if (win && animated()) {
            val now = celebrationAt
            repeat(48) { i ->
                val angle = i * 2.39996f
                val speed = cellSize * (2.2f + (i % 6) * .55f)
                particles.add(Particle(4f, 4f, cos(angle) * speed, sin(angle) * speed - cellSize * 1.5f,
                    cellSize * (.04f + i % 3 * .015f), colors[i % colors.size], now, 1250L))
            }
        }
        invalidate()
    }

    private fun animated() = effectsEnabled && !reducedEffects && ValueAnimator.areAnimatorsEnabled()

    private fun discardEffects() {
        particles.clear()
        moveEffect = null
        celebrationAt = -1L
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clearSelection()
        val availableW = (w - paddingLeft - paddingRight).toFloat().coerceAtLeast(1f)
        val availableH = (h - paddingTop - paddingBottom).toFloat().coerceAtLeast(1f)
        val margin = min(dp(12f), min(availableW, availableH) * .035f)
        val gap = min(dp(16f), availableH * .035f)
        val wide = availableW > availableH * 1.28f
        if (wide) {
            val size = min(availableH - margin * 2f, (availableW - margin * 2f - gap) * .72f).coerceAtLeast(8f)
            val handW = min(size * .37f, availableW - size - margin * 2f - gap)
            val left = paddingLeft + (availableW - size - gap - handW) * .5f
            val top = paddingTop + (availableH - size) * .5f
            boardRect.set(left, top, left + size, top + size)
            val slotH = (size - gap * 2f) / 3f
            for (i in 0..2) slots[i].set(boardRect.right + gap, top + i * (slotH + gap), boardRect.right + gap + handW, top + i * (slotH + gap) + slotH)
        } else {
            val size = min(availableW - margin * 2f, (availableH - margin * 2f - gap) / 1.29f).coerceAtLeast(8f)
            val handH = size * .29f
            val left = paddingLeft + (availableW - size) * .5f
            val top = paddingTop + (availableH - size - gap - handH) * .5f
            boardRect.set(left, top, left + size, top + size)
            val slotW = (size - gap * 2f) / 3f
            for (i in 0..2) slots[i].set(left + i * (slotW + gap), boardRect.bottom + gap, left + i * (slotW + gap) + slotW, boardRect.bottom + gap + handH)
        }
        cellSize = boardRect.width() / AtomBlocksEngine.SIZE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = game ?: return
        if (cellSize <= 0f) return
        val now = SystemClock.uptimeMillis()
        drawBoard(canvas, current)
        drawPreview(canvas)
        drawHand(canvas, current)
        drawMoveEffects(canvas, now)
        drawCelebration(canvas, now)
        drawParticles(canvas, now)
        if (dragging) selectedPiece()?.let { piece ->
            val shape = AtomBlocksEngine.shape(piece.shapeId)
            drawPiece(canvas, piece, dragX - shape.width * cellSize * .5f, dragTop(dragY, shape.height), cellSize, .88f)
        }
        drawAccessibilityFocus(canvas)
        if (moveEffect != null || particles.isNotEmpty() || celebrationAt >= 0L || invalidAt >= 0L) postInvalidateOnAnimation()
    }

    private fun drawBoard(canvas: Canvas, current: AtomBlocksEngine) {
        val radius = min(cellSize * .25f, dp(14f)) * cornerScale
        rect.set(boardRect)
        rect.inset(-cellSize * .085f, -cellSize * .085f)
        fill.color = surface
        canvas.drawRoundRect(rect, radius, radius, fill)
        line.color = outline
        line.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, radius, radius, line)
        for (row in 0 until AtomBlocksEngine.SIZE) for (col in 0 until AtomBlocksEngine.SIZE) {
            val index = row * AtomBlocksEngine.SIZE + col
            val x = boardRect.left + col * cellSize
            val y = boardRect.top + row * cellSize
            val color = current.board[index]
            if (color == 0) {
                val inset = cellSize * .057f
                fill.color = blend(surface, raised, if ((row + col) % 2 == 0) .67f else .52f)
                canvas.drawRoundRect(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset, cellSize * .12f * cornerScale, cellSize * .12f * cornerScale, fill)
                if (ornaments) {
                    fill.color = withAlpha(muted, .13f)
                    canvas.drawCircle(x + cellSize * .5f, y + cellSize * .5f, max(dp(.65f), cellSize * .018f), fill)
                }
            } else drawBlock(canvas, x, y, cellSize, blockColor(color))
            if (current.gems[index] != 0) drawGem(canvas, x + cellSize * .5f, y + cellSize * .5f, cellSize, current.gems[index])
        }
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, alpha: Float = 1f) {
        val inset = size * .055f
        val radius = size * .13f * cornerScale
        rect.set(x + inset, y + inset, x + size - inset, y + size - inset)
        fill.color = withAlpha(blend(color, Color.BLACK, .26f), alpha)
        canvas.drawRoundRect(rect, radius, radius, fill)
        rect.bottom -= size * .055f
        fill.color = withAlpha(color, alpha)
        if (gradients) fill.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            withAlpha(blend(color, Color.WHITE, .15f), alpha), withAlpha(blend(color, Color.BLACK, .09f), alpha), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
        // A bevel rather than a flat colored square; remains legible in the light palette.
        line.strokeWidth = max(dp(.5f), size * .025f)
        line.color = withAlpha(Color.WHITE, .31f * alpha)
        canvas.drawLine(rect.left + radius * .7f, rect.top + size * .045f, rect.right - radius * .7f, rect.top + size * .045f, line)
        if (ornaments && size > dp(14f)) {
            line.color = withAlpha(Color.WHITE, .10f * alpha)
            rect.inset(size * .13f, size * .13f)
            canvas.drawRoundRect(rect, radius * .7f, radius * .7f, line)
        }
    }

    private fun drawGem(canvas: Canvas, x: Float, y: Float, size: Float, kind: Int) {
        val radius = size * .22f
        shapePath.reset()
        shapePath.moveTo(x, y - radius)
        shapePath.lineTo(x + radius * .82f, y)
        shapePath.lineTo(x, y + radius)
        shapePath.lineTo(x - radius * .82f, y)
        shapePath.close()
        fill.color = Color.WHITE
        canvas.drawPath(shapePath, fill)
        fill.color = blend(colors[(kind + 1) % colors.size], Color.WHITE, .52f)
        shapePath.reset()
        shapePath.moveTo(x, y - radius * .65f)
        shapePath.lineTo(x + radius * .55f, y)
        shapePath.lineTo(x, y + radius * .65f)
        shapePath.close()
        canvas.drawPath(shapePath, fill)
    }

    private fun drawHand(canvas: Canvas, current: AtomBlocksEngine) {
        for (i in slots.indices) {
            val slot = slots[i]
            val radius = min(dp(14f), slot.height() * .18f) * cornerScale
            fill.color = if (i == selectedSlot) blend(surface, accent, if (light) .12f else .16f) else withAlpha(surface, .74f)
            canvas.drawRoundRect(slot, radius, radius, fill)
            line.color = if (i == selectedSlot) withAlpha(accent, .85f) else outline
            line.strokeWidth = if (i == selectedSlot) dp(1.8f) else dp(.8f)
            canvas.drawRoundRect(slot, radius, radius, line)
            val piece = current.tray.getOrNull(i)
            if (piece == null) {
                line.color = withAlpha(muted, .32f)
                line.strokeWidth = dp(2f)
                line.strokeCap = Paint.Cap.ROUND
                val s = min(slot.width(), slot.height()) * .09f
                canvas.drawLine(slot.centerX() - s, slot.centerY(), slot.centerX() - s * .25f, slot.centerY() + s * .7f, line)
                canvas.drawLine(slot.centerX() - s * .25f, slot.centerY() + s * .7f, slot.centerX() + s, slot.centerY() - s * .7f, line)
                line.strokeCap = Paint.Cap.BUTT
                continue
            }
            val shape = AtomBlocksEngine.shape(piece.shapeId)
            val size = min(cellSize * .65f, min(slot.width() * .78f / shape.width, slot.height() * .72f / shape.height))
            drawPiece(canvas, piece, slot.centerX() - shape.width * size * .5f, slot.centerY() - shape.height * size * .5f,
                size, if (dragging && selectedSlot == i) .22f else 1f)
            if (i == selectedSlot) {
                fill.color = accent
                canvas.drawCircle(slot.centerX(), slot.bottom - min(dp(7f), slot.height() * .09f), dp(2f), fill)
            }
        }
    }

    private fun drawPiece(canvas: Canvas, piece: AtomBlocksEngine.Piece, x: Float, y: Float, size: Float, alpha: Float) {
        for (c in AtomBlocksEngine.shape(piece.shapeId).cells) drawBlock(canvas, x + c.col * size, y + c.row * size, size, blockColor(piece.color), alpha)
    }

    private fun drawPreview(canvas: Canvas) {
        val piece = selectedPiece() ?: return
        if (previewRow == -99 || previewCol == -99) return
        val previewColor = if (previewLegal) blockColor(piece.color) else blend(0xFFE48491.toInt(), accent, .12f)
        canvas.save()
        canvas.clipRect(boardRect)
        for (cell in previewClears) {
            val x = boardRect.left + cell.col * cellSize
            val y = boardRect.top + cell.row * cellSize
            fill.color = withAlpha(Color.WHITE, if (light) .35f else .30f)
            canvas.drawRoundRect(x + cellSize * .055f, y + cellSize * .055f, x + cellSize * .945f, y + cellSize * .945f, cellSize * .13f * cornerScale, cellSize * .13f * cornerScale, fill)
            line.color = withAlpha(accent, .85f)
            line.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(x + cellSize * .055f, y + cellSize * .055f, x + cellSize * .945f, y + cellSize * .945f, cellSize * .13f * cornerScale, cellSize * .13f * cornerScale, line)
        }
        for (c in AtomBlocksEngine.shape(piece.shapeId).cells) {
            val x = boardRect.left + (previewCol + c.col) * cellSize
            val y = boardRect.top + (previewRow + c.row) * cellSize
            rect.set(x + cellSize * .075f, y + cellSize * .075f, x + cellSize * .925f, y + cellSize * .925f)
            fill.color = withAlpha(previewColor, if (hintVisible || !dragging) .28f else .23f)
            canvas.drawRoundRect(rect, cellSize * .12f * cornerScale, cellSize * .12f * cornerScale, fill)
            line.color = withAlpha(previewColor, .95f)
            line.strokeWidth = dp(2f)
            canvas.drawRoundRect(rect, cellSize * .12f * cornerScale, cellSize * .12f * cornerScale, line)
            if (!previewLegal) {
                line.strokeWidth = dp(1.5f)
                canvas.drawLine(x + cellSize * .38f, y + cellSize * .38f, x + cellSize * .62f, y + cellSize * .62f, line)
                canvas.drawLine(x + cellSize * .62f, y + cellSize * .38f, x + cellSize * .38f, y + cellSize * .62f, line)
            }
        }
        canvas.restore()
    }

    private fun drawMoveEffects(canvas: Canvas, now: Long) {
        val effect = moveEffect
        if (effect != null) {
            val age = (now - effect.started) / 1100f
            if (age >= 1f) moveEffect = null else {
                val result = effect.result
                if (animated()) {
                    val clearProgress = ((now - effect.started) / 380f).coerceIn(0f, 1f)
                    if (clearProgress < 1f) for (c in result.cleared) {
                        val size = cellSize * (1f - clearProgress * .75f)
                        val x = boardRect.left + (c.col + .5f) * cellSize - size * .5f
                        val y = boardRect.top + (c.row + .5f) * cellSize - size * .5f
                        drawBlock(canvas, x, y, size, blend(blockColor(c.color), Color.WHITE, clearProgress), 1f - clearProgress)
                    }
                    if (clearProgress < .8f) for (c in result.placed) {
                        line.color = withAlpha(Color.WHITE, (1f - clearProgress / .8f) * .7f)
                        line.strokeWidth = cellSize * .04f
                        val inset = cellSize * (.06f + clearProgress * .08f)
                        rect.set(boardRect.left + c.col * cellSize + inset, boardRect.top + c.row * cellSize + inset,
                            boardRect.left + (c.col + 1) * cellSize - inset, boardRect.top + (c.row + 1) * cellSize - inset)
                        canvas.drawRoundRect(rect, cellSize * .12f * cornerScale, cellSize * .12f * cornerScale, line)
                    }
                }
                if (result.lines > 0 && celebrationAt < 0L) {
                    val title = when {
                        result.allClear -> context.getString(R.string.ab_all_clear)
                        result.combo > 1 -> context.getString(R.string.ab_combo_popup, result.combo)
                        else -> context.getString(R.string.ab_points_popup, result.scoreGained)
                    }
                    val fade = min(1f, (1f - age) * 3.5f)
                    val y = boardRect.centerY() - if (animated()) cellSize * age * .65f else 0f
                    drawToast(canvas, title, y, fade)
                    if (result.allClear || result.combo > 1) drawSmallLabel(canvas, context.getString(R.string.ab_points_popup, result.scoreGained), y + cellSize * .66f, fade)
                } else if (result.lines == 0 && result.placed.isNotEmpty() && celebrationAt < 0L) {
                    val centerCol = result.placed.sumOf { it.col }.toFloat() / result.placed.size + .5f
                    val centerRow = result.placed.sumOf { it.row }.toFloat() / result.placed.size + .5f
                    val rise = if (animated()) age * cellSize * .65f else 0f
                    label.textSize = min(dp(18f), cellSize * .45f)
                    label.color = withAlpha(textColor, min(1f, (1f - age) * 2.5f))
                    label.setShadowLayer(dp(3f), 0f, dp(1f), surface)
                    canvas.drawText(context.getString(R.string.ab_points_popup, result.scoreGained),
                        boardRect.left + centerCol * cellSize, boardRect.top + centerRow * cellSize - rise, label)
                    label.clearShadowLayer()
                }
            }
        }
        if (invalidAt >= 0L) {
            val progress = ((now - invalidAt) / 600f).coerceIn(0f, 1f)
            if (progress >= 1f) invalidAt = -1L else {
                line.color = withAlpha(0xFFE48491.toInt(), if (animated()) .7f * (1f - progress) else .5f)
                line.strokeWidth = dp(2f)
                canvas.drawRoundRect(boardRect, cellSize * .15f * cornerScale, cellSize * .15f * cornerScale, line)
            }
        }
    }

    private fun drawCelebration(canvas: Canvas, now: Long) {
        if (celebrationAt < 0L) return
        val progress = ((now - celebrationAt) / 1650f).coerceIn(0f, 1f)
        if (progress >= 1f) { celebrationAt = -1L; return }
        if (animated() && celebrationWin) {
            line.strokeWidth = cellSize * .05f * (1f - progress)
            line.color = withAlpha(accent, (1f - progress) * .65f)
            canvas.drawCircle(boardRect.centerX(), boardRect.centerY(), boardRect.width() * progress * .75f, line)
        }
        val alpha = min(1f, (1f - progress) * 4f)
        val title = context.getString(if (celebrationWin) R.string.ab_board_victory else R.string.ab_board_blocked)
        drawToast(canvas, title, boardRect.centerY(), alpha)
    }

    private fun drawToast(canvas: Canvas, title: String, y: Float, alpha: Float) {
        label.textSize = min(dp(24f), cellSize * .63f)
        label.color = withAlpha(textColor, alpha)
        val maxWidth = boardRect.width() * .87f
        val textWidth = label.measureText(title)
        if (textWidth > maxWidth) label.textSize *= maxWidth / textWidth
        val pad = cellSize * .28f
        rect.set(boardRect.centerX() - label.measureText(title) * .5f - pad, y - label.textSize - pad * .5f,
            boardRect.centerX() + label.measureText(title) * .5f + pad, y + pad)
        fill.color = withAlpha(surface, .95f * alpha)
        canvas.drawRoundRect(rect, dp(12f) * cornerScale, dp(12f) * cornerScale, fill)
        line.color = withAlpha(accent, .55f * alpha)
        line.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, dp(12f) * cornerScale, dp(12f) * cornerScale, line)
        canvas.drawText(title, boardRect.centerX(), y, label)
    }

    private fun drawSmallLabel(canvas: Canvas, value: String, y: Float, alpha: Float) {
        label.textSize = min(dp(20f), cellSize * .55f)
        label.color = withAlpha(textColor, alpha)
        label.setShadowLayer(dp(3f), 0f, dp(1f), surface)
        canvas.drawText(value, boardRect.centerX(), y, label)
        label.clearShadowLayer()
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        if (!animated()) { particles.clear(); return }
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val t = (now - p.started) / p.duration.toFloat()
            if (t >= 1f) { iterator.remove(); continue }
            val seconds = (now - p.started) / 1000f
            val x = boardRect.left + p.x * cellSize + p.vx * seconds
            val y = boardRect.top + p.y * cellSize + p.vy * seconds + cellSize * 2.5f * seconds * seconds
            fill.color = withAlpha(p.color, (1f - t) * .9f)
            canvas.drawCircle(x, y, p.radius * (1f - t * .5f), fill)
        }
    }

    private fun selectedPiece() = game?.tray?.getOrNull(selectedSlot)

    private fun selectSlot(slot: Int) {
        if (game?.tray?.getOrNull(slot) == null) return
        selectedSlot = slot
        hintVisible = false
        previewRow = -99
        previewCol = -99
        updatePreview()
        onSelect?.invoke()
        announceForAccessibility(context.getString(R.string.ab_piece_selected, slot + 1))
        invalidate()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun updatePreview() {
        val current = game
        val piece = selectedPiece()
        previewLegal = current != null && piece != null && current.canPlace(piece, previewRow, previewCol)
        previewClears = if (previewLegal && current != null && piece != null) current.previewClears(piece, previewRow, previewCol) else emptyList()
    }

    private fun liftDistance() = max(dp(28f), cellSize * .7f)

    /** Near the bottom edge the piece returns towards the finger, keeping the last row reachable. */
    private fun dragTop(fingerY: Float, shapeHeight: Int): Float {
        val fingerLimit = height - paddingBottom - min(dp(6f), cellSize * .15f)
        val lift = min(liftDistance(), (fingerLimit - fingerY).coerceAtLeast(0f))
        return fingerY - lift - shapeHeight * cellSize
    }

    private fun updateDrag(x: Float, y: Float) {
        val piece = selectedPiece() ?: return
        val shape = AtomBlocksEngine.shape(piece.shapeId)
        dragX = x
        dragY = y
        previewCol = ((x - shape.width * cellSize * .5f - boardRect.left) / cellSize).roundToInt()
        previewRow = ((dragTop(y, shape.height) - boardRect.top) / cellSize).roundToInt()
        hintVisible = false
        updatePreview()
        invalidate()
    }

    private fun commitSelection() {
        val slot = selectedSlot
        if (slot < 0) return
        val row = previewRow
        val col = previewCol
        if (previewLegal) {
            clearSelection()
            onPlace?.invoke(slot, row, col)
        } else {
            invalidAt = SystemClock.uptimeMillis()
            announceForAccessibility(context.getString(R.string.ab_invalid_placement))
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val current = game ?: return false
        if (!isEnabled || current.won) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                dragging = false
                val slot = slots.indexOfFirst { it.contains(event.x, event.y) }
                downInTray = slot >= 0 && current.tray.getOrNull(slot) != null
                downOnBoard = boardRect.contains(event.x, event.y) && selectedSlot >= 0
                if (downInTray) selectSlot(slot)
                if (downOnBoard) {
                    previewRow = floor((event.y - boardRect.top) / cellSize).toInt()
                    previewCol = floor((event.x - boardRect.left) / cellSize).toInt()
                    hintVisible = false
                    updatePreview()
                    invalidate()
                }
                if (downInTray || downOnBoard) parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0 || (!downInTray && !downOnBoard)) return true
                val x = event.getX(index)
                val y = event.getY(index)
                if (!dragging && hypot(x - downX, y - downY) > touchSlop) dragging = true
                if (dragging) updateDrag(x, y)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) cancelGesture()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    updateDrag(event.x, event.y)
                    dragging = false
                    commitSelection()
                } else if (downOnBoard) commitSelection()
                performClick()
                cancelGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { cancelGesture(); return true }
        }
        return true
    }

    private fun cancelGesture() {
        dragging = false
        downInTray = false
        downOnBoard = false
        pointerId = -1
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (game == null || game?.won == true || !isEnabled) return super.onKeyDown(keyCode, event)
        if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_3) {
            selectSlot(keyCode - KeyEvent.KEYCODE_1)
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL -> { clearSelection(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                if (selectedSlot < 0) selectSlot(game!!.tray.indexOfFirst { it != null }) else commitSelection()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedSlot < 0) selectSlot(game!!.tray.indexOfFirst { it != null })
                if (previewRow == -99) { previewRow = 0; previewCol = 0 }
                else when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> previewCol--
                    KeyEvent.KEYCODE_DPAD_RIGHT -> previewCol++
                    KeyEvent.KEYCODE_DPAD_UP -> previewRow--
                    KeyEvent.KEYCODE_DPAD_DOWN -> previewRow++
                }
                previewCol = previewCol.coerceIn(0, AtomBlocksEngine.SIZE - 1)
                previewRow = previewRow.coerceIn(0, AtomBlocksEngine.SIZE - 1)
                hintVisible = true
                updatePreview()
                invalidate()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        discardEffects()
        super.onDetachedFromWindow()
    }

    // Virtual cells and tray entries make the same two-tap control available to TalkBack.
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = nodeProvider

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (!accessibilityManager.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val id = virtualIdAt(event.x, event.y)
            if (id != hoveredNode) {
                if (hoveredNode != NO_VIRTUAL_ID) virtualEvent(hoveredNode, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                hoveredNode = id
                if (id != NO_VIRTUAL_ID) virtualEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            }
            return id != NO_VIRTUAL_ID
        }
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            if (hoveredNode != NO_VIRTUAL_ID) virtualEvent(hoveredNode, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            hoveredNode = NO_VIRTUAL_ID
            return true
        }
        return super.dispatchHoverEvent(event)
    }

    private fun virtualIdAt(x: Float, y: Float): Int {
        if (cellSize > 0 && boardRect.contains(x, y)) return floor((y - boardRect.top) / cellSize).toInt() * AtomBlocksEngine.SIZE + floor((x - boardRect.left) / cellSize).toInt()
        val slot = slots.indexOfFirst { it.contains(x, y) }
        return if (slot >= 0 && game?.tray?.getOrNull(slot) != null) TRAY_NODE + slot else NO_VIRTUAL_ID
    }

    private fun virtualBounds(id: Int): Rect {
        if (id in TRAY_NODE until TRAY_NODE + 3) return Rect().apply { slots[id - TRAY_NODE].roundOut(this) }
        val row = id / AtomBlocksEngine.SIZE
        val col = id % AtomBlocksEngine.SIZE
        return Rect((boardRect.left + col * cellSize).toInt(), (boardRect.top + row * cellSize).toInt(),
            (boardRect.left + (col + 1) * cellSize).toInt(), (boardRect.top + (row + 1) * cellSize).toInt())
    }

    private fun virtualDescription(id: Int): String {
        val current = game ?: return context.getString(R.string.ab_board_accessibility)
        if (id in TRAY_NODE until TRAY_NODE + 3) {
            val piece = current.tray.getOrNull(id - TRAY_NODE) ?: return context.getString(R.string.ab_cell_empty)
            val shape = AtomBlocksEngine.shape(piece.shapeId)
            val summary = context.getString(R.string.ab_piece_accessibility, id - TRAY_NODE + 1, shape.width, shape.height, shape.cells.size)
            val occupied = context.getString(R.string.ab_cell_filled)
            // Local coordinates describe the actual shape, including holes in L/T/stair pieces.
            return summary + shape.cells.joinToString(separator = context.getString(R.string.ab_separator), prefix = "\n") {
                context.getString(R.string.ab_cell_accessibility, it.row + 1, it.col + 1, occupied)
            }
        }
        val state = context.getString(when {
            current.gems.getOrElse(id) { 0 } != 0 -> R.string.ab_cell_gem
            current.board.getOrElse(id) { 0 } != 0 -> R.string.ab_cell_filled
            else -> R.string.ab_cell_empty
        })
        return context.getString(R.string.ab_cell_accessibility, id / AtomBlocksEngine.SIZE + 1, id % AtomBlocksEngine.SIZE + 1, state)
    }

    private fun virtualEvent(id: Int, type: Int) {
        if (!accessibilityManager.isEnabled || id == NO_VIRTUAL_ID) return
        val event = AccessibilityEvent.obtain(type)
        event.packageName = context.packageName
        event.className = "android.view.View"
        event.setSource(this, id)
        event.contentDescription = virtualDescription(id)
        parent?.requestSendAccessibilityEvent(this, event)
    }

    private fun drawAccessibilityFocus(canvas: Canvas) {
        if (accessibilityFocus == NO_VIRTUAL_ID) return
        val bounds = virtualBounds(accessibilityFocus)
        line.color = accent
        line.strokeWidth = dp(3f)
        rect.set(bounds)
        rect.inset(dp(2f), dp(2f))
        canvas.drawRoundRect(rect, dp(4f) * cornerScale, dp(4f) * cornerScale, line)
    }

    private inner class BoardAccessibilityProvider : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == View.NO_ID) {
                return AccessibilityNodeInfo.obtain(this@AtomBlocksBoardView).apply {
                    onInitializeAccessibilityNodeInfo(this)
                    className = "android.view.ViewGroup"
                    for (i in 0 until AtomBlocksEngine.SIZE * AtomBlocksEngine.SIZE) addChild(this@AtomBlocksBoardView, i)
                    game?.tray?.forEachIndexed { i, piece -> if (piece != null) addChild(this@AtomBlocksBoardView, TRAY_NODE + i) }
                }
            }
            if (virtualViewId !in 0 until AtomBlocksEngine.SIZE * AtomBlocksEngine.SIZE && virtualViewId !in TRAY_NODE until TRAY_NODE + 3) return null
            if (virtualViewId >= TRAY_NODE && game?.tray?.getOrNull(virtualViewId - TRAY_NODE) == null) return null
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@AtomBlocksBoardView, virtualViewId)
                setParent(this@AtomBlocksBoardView)
                packageName = context.packageName
                className = "android.widget.Button"
                contentDescription = virtualDescription(virtualViewId)
                isEnabled = this@AtomBlocksBoardView.isEnabled
                isVisibleToUser = isShown
                isFocusable = true
                isAccessibilityFocused = accessibilityFocus == virtualViewId
                isSelected = virtualViewId == TRAY_NODE + selectedSlot
                val bounds = virtualBounds(virtualViewId)
                setBoundsInParent(bounds)
                getLocationOnScreen(screenLocation)
                bounds.offset(screenLocation[0], screenLocation[1])
                setBoundsInScreen(bounds)
                val canAct = this@AtomBlocksBoardView.isEnabled && game != null && game?.won != true &&
                    (virtualViewId >= TRAY_NODE || selectedPiece() != null)
                isClickable = canAct
                if (canAct) addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,
                    if (virtualViewId < TRAY_NODE) context.getString(R.string.ab_place_here) else null))
                addAction(if (isAccessibilityFocused) AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS else AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId == View.NO_ID) return this@AtomBlocksBoardView.performAccessibilityAction(action, arguments)
            if (virtualViewId !in 0 until AtomBlocksEngine.SIZE * AtomBlocksEngine.SIZE && virtualViewId !in TRAY_NODE until TRAY_NODE + 3) return false
            when (action) {
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (accessibilityFocus == virtualViewId) return false
                    if (accessibilityFocus != NO_VIRTUAL_ID) virtualEvent(accessibilityFocus, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    accessibilityFocus = virtualViewId
                    virtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    invalidate()
                    return true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (accessibilityFocus != virtualViewId) return false
                    accessibilityFocus = NO_VIRTUAL_ID
                    virtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    invalidate()
                    return true
                }
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    if (game?.won == true || !isEnabled) return false
                    if (virtualViewId >= TRAY_NODE) selectSlot(virtualViewId - TRAY_NODE)
                    else {
                        if (selectedPiece() == null) return false
                        previewRow = virtualViewId / AtomBlocksEngine.SIZE
                        previewCol = virtualViewId % AtomBlocksEngine.SIZE
                        updatePreview()
                        commitSelection()
                    }
                    virtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    return true
                }
            }
            return false
        }

        override fun findFocus(focus: Int): AccessibilityNodeInfo? =
            if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && accessibilityFocus != NO_VIRTUAL_ID) createAccessibilityNodeInfo(accessibilityFocus) else null
    }

    private fun blockColor(index: Int) = colors[(index - 1).coerceAtLeast(0) % colors.size]

    private fun withAlpha(color: Int, alpha: Float) = Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(first: Int, second: Int, amount: Float): Int = Color.rgb(
        (Color.red(first) + (Color.red(second) - Color.red(first)) * amount).roundToInt().coerceIn(0, 255),
        (Color.green(first) + (Color.green(second) - Color.green(first)) * amount).roundToInt().coerceIn(0, 255),
        (Color.blue(first) + (Color.blue(second) - Color.blue(first)) * amount).roundToInt().coerceIn(0, 255)
    )

    companion object {
        private const val TRAY_NODE = 100
        private const val NO_VIRTUAL_ID = Int.MIN_VALUE
    }
}
