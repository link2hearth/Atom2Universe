package com.Atom2Universe.app.games.jigsaw

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.widget.FrameLayout
import android.widget.OverScroller
import android.view.animation.LinearInterpolator
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One source bitmap/shader, cached paths and exact hit regions; no per-piece bitmap allocation.
 * Two layers: the scene (frame, resting pieces, carousel) is cached on the GPU, and the held piece
 * is drawn above it, so moving a piece never redraws the hundreds of others.
 */
class JigsawView(context: Context, val game: JigsawGame, private val image: Bitmap) : FrameLayout(context) {
    var onChanged: () -> Unit = {}
    var onVictoryFinished: () -> Unit = {}
    var onSelectionChanged: () -> Unit = {}
    private val palette = KitPalette.from(context)
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val texture = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(game.grid.width / image.width, game.grid.height / image.height) })
    }
    private val paths = JigsawGeometry(game.grid, game.seed).let { geometry ->
        (0 until game.grid.count).map { id -> Path().apply {
            val curves = geometry.piece(id)
            moveTo(curves.first().start.x, curves.first().start.y)
            curves.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
            close()
        } }
    }
    private val regions = paths.map { path -> Region().apply {
        setPath(path, Region(-1000, -1000, 1900, 1900))
    } }
    private val frame = RectF(0f, 0f, game.grid.width, game.grid.height)
    private val box = FloatArray(4)
    private var trayLabel = ""
    private var trayLabelKey = -1
    private var scale = 1f
    private var fitScale = 1f
    private var cameraX = 0f
    private var cameraY = 0f
    private var trayScroll = 0f
    private var edgesOnly = false
    private var selected: PieceGroup? = null
    private var dragging: PieceGroup? = null
    private var original: PieceGroup? = null
    private var originalIndex = -1
    private var grab: JigsawGrab? = null
    private var liftScale = 1f
    private var resizeStarted: Long? = null
    private var victoryAnimator: ValueAnimator? = null
    private var victoryRenderer: JigsawVictoryRenderer? = null
    private var victoryProgress = 1f
    private var firstVictory = !game.solved
    private var victoryTrayHeight = 0f
    val isCelebrating get() = victoryAnimator != null
    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private val touchConfig = ViewConfiguration.get(context)
    private val slop = touchConfig.scaledTouchSlop
    private val doubleTap = JigsawPieceDoubleTap(ViewConfiguration.getDoubleTapTimeout().toLong(),
        touchConfig.scaledDoubleTapSlop.toFloat())
    private var trayGesture = JigsawTrayGesture(slop.toFloat())
    private var cameraGesture: JigsawCameraGesture? = null
    private var cameraFirstId = -1
    private var cameraSecondId = -1
    private var velocity: VelocityTracker? = null
    private val trayFling = OverScroller(context)
    private enum class Mode { NONE, TRAY, TRAY_SCROLL, TRAY_LIFT, DRAG, PAN, CAMERA }
    private fun trayHeightFor(solved: Boolean): Float {
        if (isCelebrating) return victoryTrayHeight * (1f - JigsawVictoryMotion.camera(victoryProgress))
        return if (game.layout == JigsawLayout.TABLE || solved) 0f else min(126f * density, height * .28f)
    }
    private val trayHeight get() = trayHeightFor(game.solved)
    private val trayTop get() = height - trayHeight
    private val slot get() = 94f * density
    private val trayLabelHeight get() = 28f * density
    private fun thumbnailScale(tray: Float) = min(62f * density, (tray - trayLabelHeight) * .68f) / game.grid.span
    private fun thumbnailY(top: Float, tray: Float) = top + trayLabelHeight + (tray - trayLabelHeight) / 2

    /** Resting content, redrawn only when it changes: a moving piece is drawn above it in [dispatchDraw]. */
    private val scene = object : View(context) {
        override fun onDraw(canvas: Canvas) = drawScene(canvas)
        override fun computeScroll() {
            if (trayFling.computeScrollOffset()) {
                trayScroll = trayFling.currX.toFloat()
                clampTray()
                postInvalidateOnAnimation()
            }
        }
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(if (game.solved) R.string.jigsaw_completed_accessibility
            else if (game.layout == JigsawLayout.TABLE) R.string.jigsaw_table_accessibility else R.string.jigsaw_board_accessibility)
        scene.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        scene.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(scene, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun redrawScene() { scene.invalidate(); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cancelGesture()
        if (!isCelebrating) fitBoard()
    }

    fun fitBoard() {
        cancelGesture()
        finishVictory()
        if (width == 0 || height == 0) return
        fitScale = scaleFor(PuzzleBounds(0f, 0f, game.grid.width, game.grid.height))
        fitArea(game.tableBounds())
    }

    fun focusImage() {
        cancelGesture()
        finishVictory()
        if (width == 0 || height == 0) return
        fitArea(PuzzleBounds(0f, 0f, game.grid.width, game.grid.height))
    }

    private fun scaleFor(area: PuzzleBounds) = min((width - 28 * density).coerceAtLeast(1f) / area.width,
        (trayTop - 28 * density).coerceAtLeast(1f) / area.height)
    private val minScale get() = min(fitScale * .3f, scaleFor(game.tableBounds()))

    private fun fitArea(area: PuzzleBounds) {
        scale = scaleFor(area)
        cameraX = (width - area.width * scale) / 2 - area.left * scale
        cameraY = (trayTop - area.height * scale) / 2 - area.top * scale
        redrawScene()
    }

    fun playVictory() {
        if (!game.solved || width == 0 || height == 0) return
        cancelGesture()
        finishVictory()
        selected = null
        victoryTrayHeight = if (firstVictory && game.layout == JigsawLayout.TRAY) min(126f * density, height * .28f) else 0f
        firstVictory = false
        contentDescription = context.getString(R.string.jigsaw_completed_accessibility)
        if (!ValueAnimator.areAnimatorsEnabled()) {
            focusImage()
            onVictoryFinished()
            return
        }
        val fromScale = scale; val fromX = cameraX; val fromY = cameraY
        if (victoryRenderer == null) victoryRenderer = JigsawVictoryRenderer(game, image, texture, paths, palette, density)
        victoryProgress = 0f
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = JigsawVictoryMotion.DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                victoryProgress = it.animatedValue as Float
                val focus = JigsawVictoryMotion.camera(victoryProgress)
                val toScale = min((width - 40f * density).coerceAtLeast(1f) / game.grid.width,
                    (height - 40f * density).coerceAtLeast(1f) / game.grid.height)
                val toX = (width - game.grid.width * toScale) / 2f
                val toY = (height - game.grid.height * toScale) / 2f
                scale = fromScale + (toScale - fromScale) * focus
                cameraX = fromX + (toX - fromX) * focus
                cameraY = fromY + (toY - fromY) * focus
                redrawScene()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (victoryAnimator !== animation) return
                    victoryAnimator = null; victoryProgress = 1f
                    fitScale = scaleFor(PuzzleBounds(0f, 0f, game.grid.width, game.grid.height))
                    redrawScene()
                    onVictoryFinished()
                }
            })
        }
        victoryAnimator = animator
        animator.start()
    }

    fun pauseVictory() { victoryAnimator?.pause() }
    fun resumeVictory() { victoryAnimator?.resume() }

    private fun finishVictory() {
        val animator = victoryAnimator ?: return
        victoryAnimator = null; victoryProgress = 1f
        animator.removeAllUpdateListeners(); animator.removeAllListeners(); animator.cancel()
        redrawScene()
        onVictoryFinished()
    }

    fun toggleEdges(): Boolean {
        cancelGesture(); edgesOnly = !edgesOnly; selected = null; trayScroll = 0f; redrawScene()
        return edgesOnly
    }
    val canRotateSelection get() = game.rotation && selected?.let { it in game.groups && !it.locked } == true
    val filteringEdges get() = edgesOnly
    fun setGuideVisible(visible: Boolean) {
        game.guideVisible = visible && game.guideUnlocked
        changed()
    }
    fun rotateSelected() {
        cancelGesture()
        selected?.takeIf { it in game.groups && !it.locked }?.let { game.rotate(it); changed() }
    }
    fun tidy() { cancelGesture(); game.tidy(); selected = null; trayScroll = 0f; fitBoard(); changed() }

    private fun world(x: Float, y: Float) = PuzzlePoint((x - cameraX) / scale, (y - cameraY) / scale)
    private fun isEdge(id: Int) = id % game.grid.columns == 0 || id % game.grid.columns == game.grid.columns - 1 ||
        id / game.grid.columns == 0 || id / game.grid.columns == game.grid.rows - 1
    private fun inTrayList(group: PieceGroup) = group.inTray && (!edgesOnly || isEdge(group.ids[0]))
    private fun trayGroups() = game.groups.filter { inTrayList(it) }
    private fun trayCount(): Int {
        var count = 0
        for (i in game.groups.indices) if (inTrayList(game.groups[i])) count++
        return count
    }
    private fun groupVisible(group: PieceGroup): Boolean {
        if (game.layout != JigsawLayout.TABLE || !edgesOnly || group.locked || group.ids.size > 1) return true
        return isEdge(group.ids[0])
    }
    private fun maxTrayScroll(count: Int = trayCount()) = max(0f, count * slot - width)
    private fun clampTray(count: Int = trayCount()) { trayScroll = trayScroll.coerceIn(0f, maxTrayScroll(count)) }
    private fun revealInTray(group: PieceGroup) {
        val index = trayGroups().indexOf(group)
        if (index < 0) return
        trayFling.abortAnimation()
        trayScroll = ((index + .5f) * slot - width / 2f).coerceIn(0f, maxTrayScroll())
    }
    private fun constrainCamera() {
        val area = game.tableBounds()
        val left = -(area.left - 1200f) * scale
        val right = width - (area.right + 1200f) * scale
        val top = -(area.top - 1200f) * scale
        val bottom = trayTop - (area.bottom + 1200f) * scale
        cameraX = cameraX.coerceIn(min(left, right), max(left, right))
        cameraY = cameraY.coerceIn(min(top, bottom), max(top, bottom))
    }

    private fun drawScene(canvas: Canvas) {
        val solved = game.solved
        val tray = trayHeightFor(solved)
        val top = height - tray
        canvas.drawColor(palette.background)
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), top)
        canvas.translate(cameraX, cameraY)
        canvas.scale(scale, scale)
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = palette.surface
        canvas.drawRect(frame, paint)
        if (isCelebrating) {
            victoryRenderer?.draw(canvas, victoryProgress, scale)
        } else if ((game.guideUnlocked && game.guideVisible) || solved) {
            paint.alpha = if (solved) 255 else 64
            canvas.drawBitmap(image, null, frame, paint)
            paint.alpha = 255
        }
        if (!solved || isCelebrating) {
            paint.shader = null; paint.style = Paint.Style.STROKE; paint.color = palette.secondary
            paint.alpha = (135 * (if (isCelebrating) JigsawVictoryMotion.frameAlpha(victoryProgress) else 1f)).roundToInt()
            paint.strokeWidth = 1.5f * density / scale
            canvas.drawRect(frame, paint)
            if (!isCelebrating) drawFrameCorners(canvas)
            paint.alpha = 255
        }
        if (!solved) {
            val groups = game.groups
            for (i in groups.indices) groups[i].let { if (it.locked) drawGroup(canvas, it, top) }
            for (i in groups.indices) groups[i].let {
                if (!it.locked && !it.inTray && it !== dragging) drawGroup(canvas, it, top)
            }
        }
        canvas.restore()
        if (game.layout == JigsawLayout.TRAY && tray > 0f) {
            if (isCelebrating) {
                paint.shader = null; paint.style = Paint.Style.FILL; paint.color = palette.raised
                canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)
            } else drawTray(canvas, top, tray)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawHeldPiece(canvas)
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    private fun drawFrameCorners(canvas: Canvas) {
        paint.color = palette.accent; paint.alpha = 220
        paint.strokeWidth = 2.2f * density / scale
        val length = min(18f * density / scale, game.grid.cell * .4f)
        for (corner in 0 until 4) {
            val x = if (corner % 2 == 0) 0f else game.grid.width
            val y = if (corner < 2) 0f else game.grid.height
            canvas.drawLine(x, y, x + (if (x == 0f) length else -length), y, paint)
            canvas.drawLine(x, y, x, y + (if (y == 0f) length else -length), paint)
        }
    }

    /** Pieces outside the viewport are skipped one by one, so a zoomed-in large puzzle stays light. */
    private fun drawGroup(canvas: Canvas, group: PieceGroup, viewportBottom: Float) {
        if (!groupVisible(group)) return
        game.bounds(group, box)
        if (box[2] * scale + cameraX < 0 || box[0] * scale + cameraX > width ||
            box[3] * scale + cameraY < 0 || box[1] * scale + cameraY > viewportBottom) return
        val viewLeft = -cameraX / scale; val viewRight = (width - cameraX) / scale
        val viewTop = -cameraY / scale; val viewBottom = (viewportBottom - cameraY) / scale
        val radius = game.grid.span * .85f
        val turns = (group.turns % 4 + 4) % 4
        val highlight = group === selected && !group.locked
        canvas.save()
        canvas.translate(group.offset.x, group.offset.y)
        canvas.rotate(group.turns * 90f)
        for (i in group.ids.indices) {
            val id = group.ids[i]
            val cx = (id % game.grid.columns + .5f) * game.grid.cellWidth
            val cy = (id / game.grid.columns + .5f) * game.grid.cellHeight
            val x = when (turns) { 1 -> -cy; 2 -> -cx; 3 -> cy; else -> cx } + group.offset.x
            val y = when (turns) { 1 -> cx; 2 -> -cy; 3 -> -cx; else -> cy } + group.offset.y
            if (x + radius < viewLeft || x - radius > viewRight || y + radius < viewTop || y - radius > viewBottom) continue
            drawPiece(canvas, id, scale, highlight)
        }
        canvas.restore()
    }

    /** Draw above the tray so its edge never clips or displaces the held piece. */
    private fun drawHeldPiece(canvas: Canvas) {
        val group = if (mode == Mode.TRAY_LIFT) selected else dragging
        if (group == null) return
        val grip = grab ?: return
        val started = resizeStarted
        val progress = if (started == null) 1f else ((SystemClock.uptimeMillis() - started) / 120f).coerceIn(0f, 1f)
        val resizing = started != null && progress < 1f
        if (mode == Mode.TRAY_LIFT || resizing) {
            val eased = progress * progress * (3f - 2f * progress)
            val pixelScale = if (mode == Mode.TRAY_LIFT) liftScale else liftScale + (scale - liftScale) * eased
            canvas.save()
            canvas.translate(lastX, lastY)
            canvas.scale(pixelScale, pixelScale)
            canvas.rotate(group.turns * 90f)
            canvas.translate(-grip.point.x, -grip.point.y)
            for (i in group.ids.indices) drawPiece(canvas, group.ids[i], pixelScale, true)
            canvas.restore()
            if (resizing) postInvalidateOnAnimation()
        } else {
            resizeStarted = null
            canvas.save()
            canvas.translate(cameraX, cameraY)
            canvas.scale(scale, scale)
            drawGroup(canvas, group, height.toFloat())
            canvas.restore()
        }
    }

    private fun drawPiece(canvas: Canvas, id: Int, pixelScale: Float, highlight: Boolean) {
        val path = paths[id]
        paint.style = Paint.Style.FILL; paint.shader = texture; paint.alpha = 255
        canvas.drawPath(path, paint)
        paint.shader = null; paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * density / pixelScale; paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawPath(path, paint)
        paint.strokeWidth = .85f * density / pixelScale
        paint.color = if (highlight) palette.accent else Color.argb(170, 255, 255, 255)
        canvas.drawPath(path, paint)
    }

    private fun drawTray(canvas: Canvas, top: Float, tray: Float) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = palette.raised
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)
        val count = trayCount()
        // Formatting a string is costly: the label only changes with the count or the edge filter.
        val key = count * 2 + if (edgesOnly) 1 else 0
        if (key != trayLabelKey) {
            trayLabelKey = key
            trayLabel = resources.getQuantityString(if (edgesOnly) R.plurals.jigsaw_tray_edges else R.plurals.jigsaw_tray, count, count)
        }
        paint.color = palette.secondary; paint.textSize = 12f * density
        canvas.drawText(trayLabel, 12f * density, top + 19f * density, paint)
        canvas.save()
        canvas.clipRect(0f, top + trayLabelHeight, width.toFloat(), height.toFloat())
        clampTray(count)
        val thumbnailScale = thumbnailScale(tray)
        val thumbnailY = thumbnailY(top, tray)
        var index = -1
        for (i in game.groups.indices) {
            val group = game.groups[i]
            if (!inTrayList(group)) continue
            index++
            if (mode == Mode.TRAY_LIFT && group === selected) continue
            val x = (index + .5f) * slot - trayScroll
            if (x + slot < 0 || x - slot > width) continue
            val id = group.ids[0]
            canvas.save()
            canvas.translate(x, thumbnailY)
            canvas.scale(thumbnailScale, thumbnailScale)
            canvas.rotate(group.turns * 90f)
            canvas.translate(-(id % game.grid.columns + .5f) * game.grid.cellWidth,
                -(id / game.grid.columns + .5f) * game.grid.cellHeight)
            drawPiece(canvas, id, thumbnailScale, group === selected)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun hit(x: Float, y: Float): PieceGroup? {
        val p = world(x, y)
        return game.groups.asReversed().firstOrNull { group ->
            if (group.inTray || group.locked || !groupVisible(group)) false else {
                val local = (p - group.offset).rotated(-group.turns)
                group.ids.any { regions[it].contains(local.x.toInt(), local.y.toInt()) }
            }
        }
    }

    private fun extract(group: PieceGroup, x: Float, y: Float) {
        val grip = grab ?: return
        original = group.copyGroup(); dragging = group; selected = group
        originalIndex = game.groups.indexOf(group)
        group.inTray = false
        group.offset = grip.offsetAt(world(x, y), group.turns)
        game.groups.remove(group); game.groups.add(group)
        mode = Mode.DRAG
        resizeStarted = SystemClock.uptimeMillis()
    }

    private fun moveFinger(x: Float, y: Float) {
        val wasMoved = moved
        if (abs(x - downX) + abs(y - downY) > slop) {
            moved = true
            doubleTap.reset()
        }
        when (mode) {
            Mode.TRAY -> {
                val group = selected
                when (trayGesture.move(x - downX, y - downY, group != null)) {
                    TrayIntent.SCROLL -> { mode = Mode.TRAY_SCROLL; trayScroll -= x - lastX; clampTray() }
                    TrayIntent.EXTRACT -> if (group != null) {
                        mode = Mode.TRAY_LIFT
                        if (y < trayTop) extract(group, x, y)
                    }
                    TrayIntent.PENDING -> Unit
                }
            }
            Mode.TRAY_SCROLL -> { trayScroll -= x - lastX; clampTray() }
            Mode.TRAY_LIFT -> if (y < trayTop) selected?.let { extract(it, x, y) }
            Mode.DRAG -> dragging?.let {
                grab?.let { grip -> it.offset = grip.offsetAt(world(x, y), it.turns) }
            }
            Mode.PAN -> if (moved) {
                cameraX += x - (if (wasMoved) lastX else downX)
                cameraY += y - (if (wasMoved) lastY else downY)
                constrainCamera()
            }
            else -> Unit
        }
        lastX = x; lastY = y
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCelebrating) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN && mode == Mode.CAMERA) cancelGesture()
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            cancelGesture()
            mode = Mode.CAMERA
            parent?.requestDisallowInterceptTouchEvent(true)
            beginCameraGesture(event)
        }
        if (mode == Mode.CAMERA || event.pointerCount > 1) return moveCamera(event)
        val modeBefore = mode
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            trayFling.abortAnimation()
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
        }
        velocity?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y; moved = false
                grab = null; resizeStarted = null
                if (game.layout == JigsawLayout.TRAY && event.y >= trayTop) {
                    doubleTap.reset()
                    mode = Mode.TRAY
                    trayGesture = JigsawTrayGesture(slop.toFloat())
                    val index = ((event.x + trayScroll) / slot).toInt()
                    selected = if (event.y >= trayTop + trayLabelHeight) trayGroups().getOrNull(index) else null
                    liftScale = thumbnailScale(trayHeight)
                    selected?.let {
                        grab = JigsawGrab.fromThumbnail(PuzzlePoint(event.x, event.y),
                            PuzzlePoint((index + .5f) * slot - trayScroll, thumbnailY(trayTop, trayHeight)),
                            liftScale, game.center(it.ids.first()), it.turns)
                    }
                } else {
                    dragging = hit(event.x, event.y)
                    selected = dragging
                    original = dragging?.copyGroup()
                    originalIndex = dragging?.let { game.groups.indexOf(it) } ?: -1
                    // Keep the gesture chosen at touch-down, even when crossing a piece.
                    mode = if (dragging == null) Mode.PAN else Mode.DRAG
                    if (dragging == null) doubleTap.reset()
                    dragging?.let {
                        grab = JigsawGrab((world(event.x, event.y) - it.offset).rotated(-it.turns))
                        game.groups.remove(it); game.groups.add(it)
                    }
                }
                onSelectionChanged()
            }
            MotionEvent.ACTION_MOVE -> moveFinger(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                // Include the final touch sample, also for very short flicks with no MOVE event.
                moveFinger(event.x, event.y)
                if (mode == Mode.TRAY_SCROLL) {
                    velocity?.computeCurrentVelocity(1000, touchConfig.scaledMaximumFlingVelocity.toFloat())
                    val speed = -(velocity?.xVelocity ?: 0f)
                    if (abs(speed) >= touchConfig.scaledMinimumFlingVelocity) {
                        trayFling.fling(trayScroll.roundToInt(), 0, speed.roundToInt(), 0,
                            0, maxTrayScroll().roundToInt(), 0, 0)
                    }
                }
                val group = dragging
                if (group != null) {
                    val snapPixels = (if (game.layout == JigsawLayout.TABLE) 9f else 16f) * density
                    // A loose piece dropped back onto the carousel goes back into it.
                    if (moved && game.layout == JigsawLayout.TRAY && event.y >= trayTop && game.returnToTray(group)) {
                        revealInTray(group)
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // A tap only selects a piece. Joining requires an actual drag and release.
                    } else if (moved && game.release(group, snapPixels / scale, 22f * density / scale)) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else if (!moved) {
                        // Sub-slop touch jitter must not move a selected piece.
                        original?.let { group.offset = it.offset }
                        val canReturn = game.layout == JigsawLayout.TRAY && !group.inTray && !group.locked && group.ids.size == 1
                        if (canReturn && event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
                            if (doubleTap.tap(group, PuzzlePoint(event.x, event.y), event.eventTime) && game.returnToTray(group)) {
                                revealInTray(group)
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else doubleTap.reset()
                    }
                    if (group.locked) selected = null
                    dragging = null; original = null; originalIndex = -1
                    changed()
                }
                if (!moved) performClick()
                mode = Mode.NONE; grab = null; resizeStarted = null
                velocity?.recycle(); velocity = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        // Moving a held piece only redraws that piece; anything else also refreshes the cached scene.
        val heldOnly = event.actionMasked == MotionEvent.ACTION_MOVE && mode == modeBefore &&
            (mode == Mode.DRAG || mode == Mode.TRAY_LIFT || mode == Mode.TRAY || (mode == Mode.PAN && !moved))
        if (heldOnly) invalidate() else redrawScene()
        return true
    }

    private fun beginCameraGesture(event: MotionEvent, excluding: Int = -1) {
        val indices = (0 until event.pointerCount).filter { it != excluding }
        if (indices.size < 2) { cameraGesture = null; return }
        val first = indices[0]; val second = indices[1]
        cameraFirstId = event.getPointerId(first); cameraSecondId = event.getPointerId(second)
        cameraGesture = JigsawCameraGesture(PuzzlePoint(event.getX(first), event.getY(first)),
            PuzzlePoint(event.getX(second), event.getY(second)), JigsawCamera(PuzzlePoint(cameraX, cameraY), scale))
    }

    private fun moveCamera(event: MotionEvent): Boolean {
        mode = Mode.CAMERA
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val first = event.findPointerIndex(cameraFirstId)
                val second = event.findPointerIndex(cameraSecondId)
                if (event.pointerCount >= 2) {
                    if (cameraGesture == null || first < 0 || second < 0) beginCameraGesture(event)
                    else cameraGesture?.move(PuzzlePoint(event.getX(first), event.getY(first)),
                        PuzzlePoint(event.getX(second), event.getY(second)), minScale, fitScale * 6f)?.let {
                        cameraX = it.offset.x; cameraY = it.offset.y; scale = it.scale
                        constrainCamera()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> beginCameraGesture(event, event.actionIndex)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        redrawScene()
        return true
    }

    /** Cancelling a gesture (pinch, backgrounding, resize) restores the pre-drag placement. */
    fun cancelGesture() {
        trayFling.abortAnimation()
        velocity?.recycle(); velocity = null
        val group = dragging; val saved = original
        if (group != null && saved != null) {
            group.offset = saved.offset; group.turns = saved.turns; group.inTray = saved.inTray
            game.groups.remove(group)
            game.groups.add(originalIndex.coerceIn(0, game.groups.size), group)
        }
        dragging = null; original = null; originalIndex = -1; mode = Mode.NONE
        grab = null; resizeStarted = null
        cameraGesture = null; cameraFirstId = -1; cameraSecondId = -1
        doubleTap.reset()
        parent?.requestDisallowInterceptTouchEvent(false)
        redrawScene()
    }
    override fun onDetachedFromWindow() {
        cancelGesture()
        finishVictory()
        super.onDetachedFromWindow()
    }
    private fun changed() { redrawScene(); onChanged() }
    override fun performClick(): Boolean { super.performClick(); return true }
}
