package com.Atom2Universe.app.games.trebuchet.gears

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class GearMachineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onGearMachineChanged()
        fun onGearSelectionChanged()
    }

    enum class TouchMode { NONE, MOVE, SPIN }
    data class PendingLink(val firstId: Int, val kind: GearLinkKind, val inputDirection: Int)

    val game = GearMachineGame()
    var listener: Listener? = null
    var selectedId: Int? = null
        private set
    var placementTeeth: Int? = null
        private set
    var placementKind = GearWheelKind.GEAR
        private set
    var currentLayer = 0
        private set
    var pendingLink: PendingLink? = null
        private set

    private val dp = resources.displayMetrics.density
    private val gearPath = Path()
    private val pBackground = Paint().apply { color = Color.rgb(10, 16, 36) }
    private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 110, 130, 170); strokeWidth = dp }
    private val pFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 83, 112); strokeWidth = 0.12f; style = Paint.Style.STROKE }
    private val pMesh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 255, 209, 102); strokeWidth = 0.035f }
    private val pBelt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 126, 74); style = Paint.Style.STROKE; strokeWidth = 0.09f }
    private val pChain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 214, 224); style = Paint.Style.STROKE; strokeWidth = 0.075f }
    private val pGear = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 234, 245); style = Paint.Style.STROKE; strokeWidth = 0.035f }
    private val pHub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 58); style = Paint.Style.FILL }
    private val pSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 209, 102); style = Paint.Style.STROKE; strokeWidth = 0.08f }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * dp; textAlign = Paint.Align.CENTER }
    private val layerColors = intArrayOf(
        Color.rgb(94, 196, 255), Color.rgb(255, 158, 94), Color.rgb(151, 224, 119),
        Color.rgb(207, 145, 255), Color.rgb(255, 109, 145), Color.rgb(103, 221, 205)
    )

    private var camX = 0f
    private var camY = 3f
    private var camScale = 60f
    private var running = false
    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var touchMode = TouchMode.NONE
    private var touchGearId: Int? = null
    private var lastPointerAngle = 0f
    private var gestureStartMillis = 0L
    private var gestureAngle = 0f
    private var lastUiNotifyMillis = 0L

    fun resume() {
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun pause() { running = false }

    fun loadConfig(config: GearMachineConfig) {
        game.loadConfig(config)
        selectedId = null
        placementTeeth = null
        placementKind = GearWheelKind.GEAR
        pendingLink = null
        fitCamera()
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun resetMachine() = loadConfig(GearMachineConfig())
    fun snapshot(): GearMachineConfig = game.config.deepCopy()

    fun armPlacement(teeth: Int) {
        placementTeeth = teeth
        placementKind = GearWheelKind.GEAR
        selectedId = null
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun armFlywheel() {
        placementTeeth = GearMachineRules.FLYWHEEL_TEETH
        placementKind = GearWheelKind.FLYWHEEL
        selectedId = null
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun cancelPlacement() {
        placementTeeth = null
        invalidate()
    }

    fun armLink(kind: GearLinkKind, inputDirection: Int = 1): Boolean {
        val first = selectedId ?: return false
        pendingLink = PendingLink(first, kind, if (inputDirection < 0) -1 else 1)
        placementTeeth = null
        listener?.onGearSelectionChanged()
        invalidate()
        return true
    }

    fun cancelLink() {
        pendingLink = null
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun removeSelectedLinks(): Int {
        val id = selectedId ?: return 0
        val removed = game.removeLinksFor(id)
        if (removed > 0) {
            pendingLink = null
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
            invalidate()
        }
        return removed
    }

    fun deleteSelected(): Boolean {
        val id = selectedId ?: return false
        val removed = game.deleteGear(id)
        if (removed) {
            selectedId = null
            fitCamera()
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
            invalidate()
        }
        return removed
    }

    fun changeSelectedLayer(delta: Int): Int? {
        val id = selectedId ?: return null
        val layer = game.changeLayer(id, delta) ?: return null
        currentLayer = layer
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return layer
    }

    fun cycleSelectedMaterial(): GearWheelMaterial? {
        val id = selectedId ?: return null
        val material = game.cycleMaterial(id) ?: return null
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return material
    }

    fun attachLauncher(): Boolean {
        val id = selectedId ?: return false
        val attached = game.attachLauncher(id)
        if (attached) {
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
            invalidate()
        }
        return attached
    }

    fun adjustLauncherAngle(delta: Float): Float {
        val angle = game.adjustLauncherAngle(delta)
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return angle
    }

    fun launchProjectile(): Boolean {
        val launched = game.launchProjectile()
        if (launched) {
            selectedId = null
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
        }
        invalidate()
        return launched
    }

    fun setCurrentLayer(delta: Int): Int {
        currentLayer = (currentLayer + delta).coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        listener?.onGearSelectionChanged()
        invalidate()
        return currentLayer
    }

    fun stopAll() {
        game.stopAll()
        invalidate()
    }

    fun selectedWheel(): GearWheelConfig? =
        selectedId?.let { id -> game.config.wheels.firstOrNull { it.id == id } }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCamera()
    }

    private fun fitCamera() {
        if (width <= 0 || height <= 0) return
        val bounds = game.bounds()
        val spanX = (bounds[1] - bounds[0] + 2f).coerceAtLeast(8f)
        val spanY = (bounds[3] - bounds[2] + 2f).coerceAtLeast(7f)
        camX = (bounds[0] + bounds[1]) / 2f
        camY = (bounds[2] + bounds[3]) / 2f
        camScale = min(width / spanX, height / spanY).coerceAtLeast(0.01f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateSimulation()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBackground)

        canvas.save()
        canvas.translate(width / 2f - camX * camScale, height / 2f + camY * camScale)
        canvas.scale(camScale, -camScale)
        drawGrid(canvas)
        drawAutomaticFrame(canvas)
        drawTransmissions(canvas)
        for (mesh in game.meshes) {
            val a = game.gears.firstOrNull { it.wheel.id == mesh.firstId } ?: continue
            val b = game.gears.firstOrNull { it.wheel.id == mesh.secondId } ?: continue
            canvas.drawLine(a.body.x, a.body.y, b.body.x, b.body.y, pMesh)
        }
        drawLauncher(canvas)
        for (gear in game.gears.sortedBy { it.wheel.layer }) drawGear(canvas, gear)
        game.projectile?.let { shot ->
            pGear.color = Color.rgb(245, 240, 222)
            pGear.alpha = 255
            canvas.drawCircle(shot.body.x, shot.body.y, shot.body.radius, pGear)
        }
        canvas.restore()

        for (gear in game.gears) {
            val x = sx(gear.body.x)
            val y = sy(gear.body.y)
            pText.color = if (gear.wheel.id == selectedId) Color.rgb(255, 209, 102) else Color.WHITE
            val kind = if (gear.wheel.kind == GearWheelKind.FLYWHEEL) "V" else "${gear.wheel.teeth}T"
            canvas.drawText("L${gear.wheel.layer} · $kind", x, y + 4f * dp, pText)
        }
        if (running) postInvalidateOnAnimation()
    }

    private fun updateSimulation() {
        if (!running) return
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now
            return
        }
        accumulator += ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
        lastFrameNanos = now
        val fixed = 1f / 120f
        var guard = 0
        val hadProjectile = game.projectile != null
        while (accumulator >= fixed && guard++ < 8) {
            game.step(fixed)
            accumulator -= fixed
        }
        if (hadProjectile && game.projectile == null) fitCamera()
        game.projectile?.let { shot ->
            val visibleRight = camX + width / camScale * 0.38f
            if (shot.body.x > visibleRight) camX += shot.body.x - visibleRight
            val visibleTop = camY + height / camScale * 0.36f
            if (shot.body.y > visibleTop) camY += shot.body.y - visibleTop
        }
        val nowMillis = now / 1_000_000L
        if (nowMillis - lastUiNotifyMillis >= 250L) {
            lastUiNotifyMillis = nowMillis
            listener?.onGearMachineChanged()
        }
    }

    private fun drawGrid(canvas: Canvas) {
        pGrid.strokeWidth = dp / camScale
        val left = wx(0f); val right = wx(width.toFloat())
        val bottom = wy(height.toFloat()); val top = wy(0f)
        val startX = kotlin.math.floor(left).toInt()
        val endX = kotlin.math.ceil(right).toInt()
        val startY = kotlin.math.floor(bottom).toInt()
        val endY = kotlin.math.ceil(top).toInt()
        for (x in startX..endX) canvas.drawLine(x.toFloat(), bottom, x.toFloat(), top, pGrid)
        for (y in startY..endY) canvas.drawLine(left, y.toFloat(), right, y.toFloat(), pGrid)
    }

    private fun drawAutomaticFrame(canvas: Canvas) {
        pFrame.strokeWidth = 0.10f
        val bounds = game.bounds()
        val baseY = minOf(0f, bounds[2] - 0.4f)
        canvas.drawLine(bounds[0] - 0.5f, baseY, bounds[1] + 0.5f, baseY, pFrame)
        for (gear in game.gears) {
            // Le bâti est seulement dessiné ; son minuscule support physique a collidesWith=0.
            canvas.drawLine(gear.body.x, baseY, gear.body.x, gear.body.y, pFrame)
            canvas.drawCircle(gear.body.x, gear.body.y, 0.12f, pFrame)
        }
    }

    private fun drawTransmissions(canvas: Canvas) {
        for (transmission in game.transmissions) {
            val a = game.gears.firstOrNull { it.wheel.id == transmission.config.firstId } ?: continue
            val b = game.gears.firstOrNull { it.wheel.id == transmission.config.secondId } ?: continue
            val dx = b.body.x - a.body.x
            val dy = b.body.y - a.body.y
            val length = hypot(dx, dy).coerceAtLeast(1e-4f)
            val ox = -dy / length * 0.10f
            val oy = dx / length * 0.10f
            when (transmission.config.kind) {
                GearLinkKind.BELT_OPEN -> {
                    canvas.drawLine(a.body.x + ox, a.body.y + oy, b.body.x + ox, b.body.y + oy, pBelt)
                    canvas.drawLine(a.body.x - ox, a.body.y - oy, b.body.x - ox, b.body.y - oy, pBelt)
                }
                GearLinkKind.BELT_CROSSED -> {
                    canvas.drawLine(a.body.x + ox, a.body.y + oy, b.body.x - ox, b.body.y - oy, pBelt)
                    canvas.drawLine(a.body.x - ox, a.body.y - oy, b.body.x + ox, b.body.y + oy, pBelt)
                }
                GearLinkKind.CHAIN_FREEWHEEL -> {
                    pChain.alpha = if ((transmission.joint as com.Atom2Universe.app.games.physics.OneWayRotaryJoint).engaged) 255 else 135
                    canvas.drawLine(a.body.x + ox, a.body.y + oy, b.body.x + ox, b.body.y + oy, pChain)
                    canvas.drawLine(a.body.x - ox, a.body.y - oy, b.body.x - ox, b.body.y - oy, pChain)
                    val mx = (a.body.x + b.body.x) * 0.5f
                    val my = (a.body.y + b.body.y) * 0.5f
                    val direction = transmission.config.inputDirection.toFloat()
                    gearPath.reset()
                    gearPath.moveTo(mx + dx / length * 0.24f * direction, my + dy / length * 0.24f * direction)
                    gearPath.lineTo(mx - dx / length * 0.12f * direction + ox * 1.8f, my - dy / length * 0.12f * direction + oy * 1.8f)
                    gearPath.lineTo(mx - dx / length * 0.12f * direction - ox * 1.8f, my - dy / length * 0.12f * direction - oy * 1.8f)
                    gearPath.close()
                    pGear.color = pChain.color
                    pGear.alpha = pChain.alpha
                    canvas.drawPath(gearPath, pGear)
                }
            }
        }
    }

    private fun drawLauncher(canvas: Canvas) {
        val id = game.config.launcherWheelId ?: return
        val gear = game.gears.firstOrNull { it.wheel.id == id } ?: return
        val angle = Math.toRadians(game.config.launcherAngleDeg.toDouble()).toFloat()
        val nx = cos(angle)
        val ny = sin(angle)
        val start = gear.wheel.outerRadius * 0.75f
        val end = gear.wheel.outerRadius + 1.15f
        pFrame.color = Color.rgb(230, 112, 82)
        pFrame.strokeWidth = 0.14f
        canvas.drawLine(gear.body.x + nx * start, gear.body.y + ny * start,
            gear.body.x + nx * end, gear.body.y + ny * end, pFrame)
        canvas.drawLine(gear.body.x - ny * 0.22f + nx * end, gear.body.y + nx * 0.22f + ny * end,
            gear.body.x + ny * 0.22f + nx * end, gear.body.y - nx * 0.22f + ny * end, pFrame)
        pFrame.color = Color.rgb(66, 83, 112)
    }

    private fun drawGear(canvas: Canvas, gear: GearMachineGame.GearState) {
        val teeth = gear.wheel.teeth
        val pitch = gear.wheel.pitchRadius
        val outer = gear.wheel.outerRadius
        val root = (pitch - GearMachineRules.MODULE * 0.9f).coerceAtLeast(outer * 0.2f)
        gearPath.reset()
        val points = teeth * 4
        for (i in 0 until points) {
            val angle = gear.body.angle + i * (2f * PI.toFloat() / points)
            val radius = if (i % 4 == 1 || i % 4 == 2) outer else root
            val x = gear.body.x + cos(angle) * radius
            val y = gear.body.y + sin(angle) * radius
            if (i == 0) gearPath.moveTo(x, y) else gearPath.lineTo(x, y)
        }
        gearPath.close()
        val colorIndex = Math.floorMod(gear.wheel.layer, layerColors.size)
        pGear.color = layerColors[colorIndex]
        pGear.alpha = if (gear.wheel.layer == currentLayer || gear.wheel.id == selectedId) 235 else 135
        canvas.drawPath(gearPath, pGear)
        if (gear.wheel.kind == GearWheelKind.FLYWHEEL) {
            pHub.color = Color.rgb(16, 24, 40)
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.55f, pHub)
            pEdge.strokeWidth = 0.10f
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.82f, pEdge)
        }
        pEdge.strokeWidth = 0.035f
        canvas.drawPath(gearPath, pEdge)
        val hubRadius = minOf(0.22f, pitch * 0.25f).coerceAtLeast(0.08f)
        canvas.drawCircle(gear.body.x, gear.body.y, hubRadius, pHub)
        if (gear.wheel.id == selectedId) {
            pSelect.strokeWidth = 0.08f
            canvas.drawCircle(gear.body.x, gear.body.y, outer + 0.12f, pSelect)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = wx(event.x)
        val y = wy(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val hit = game.gearAt(x, y)
                if (hit == null) {
                    val teeth = placementTeeth
                    if (teeth != null) {
                        val id = if (placementKind == GearWheelKind.FLYWHEEL) {
                            game.addFlywheel(x, y, currentLayer)
                        } else {
                            game.addGear(teeth, x, y, currentLayer)
                        }
                        selectedId = id
                        placementTeeth = null
                        placementKind = GearWheelKind.GEAR
                        fitCamera()
                        listener?.onGearMachineChanged()
                    } else {
                        selectedId = null
                    }
                    touchMode = TouchMode.NONE
                    listener?.onGearSelectionChanged()
                    invalidate()
                    return true
                }
                pendingLink?.let { pending ->
                    if (hit.wheel.id != pending.firstId) {
                        val linked = game.addLink(
                            pending.firstId, hit.wheel.id, pending.kind, pending.inputDirection
                        )
                        if (linked) {
                            pendingLink = null
                            selectedId = hit.wheel.id
                            currentLayer = hit.wheel.layer
                            touchMode = TouchMode.NONE
                            listener?.onGearMachineChanged()
                            listener?.onGearSelectionChanged()
                            invalidate()
                            return true
                        }
                    }
                }
                selectedId = hit.wheel.id
                currentLayer = hit.wheel.layer
                val distance = hypot(x - hit.body.x, y - hit.body.y)
                touchMode = if (distance > hit.wheel.pitchRadius * 0.55f) TouchMode.SPIN else TouchMode.MOVE
                touchGearId = hit.wheel.id
                lastPointerAngle = atan2(y - hit.body.y, x - hit.body.x)
                gestureStartMillis = event.eventTime
                gestureAngle = 0f
                listener?.onGearSelectionChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = touchGearId ?: return true
                when (touchMode) {
                    TouchMode.MOVE -> game.moveGear(id, x, y, snap = false)
                    TouchMode.SPIN -> {
                        val state = game.gears.firstOrNull { it.wheel.id == id } ?: return true
                        val angle = atan2(y - state.body.y, x - state.body.x)
                        var delta = angle - lastPointerAngle
                        while (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                        while (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                        gestureAngle += delta
                        lastPointerAngle = angle
                    }
                    else -> Unit
                }
                listener?.onGearMachineChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchMode == TouchMode.MOVE) {
                    touchGearId?.let { id -> game.moveGear(id, x, y, snap = true) }
                    fitCamera()
                } else if (touchMode == TouchMode.SPIN && event.actionMasked == MotionEvent.ACTION_UP) {
                    touchGearId?.let { id ->
                        game.gears.firstOrNull { it.wheel.id == id }?.let { state ->
                            val angle = atan2(y - state.body.y, x - state.body.x)
                            var delta = angle - lastPointerAngle
                            while (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                            while (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                            gestureAngle += delta
                        }
                    }
                    val duration = ((event.eventTime - gestureStartMillis) / 1000f).coerceAtLeast(1f / 120f)
                    touchGearId?.let { id -> game.flickGear(id, gestureAngle / duration) }
                }
                touchMode = TouchMode.NONE
                touchGearId = null
                listener?.onGearMachineChanged()
                listener?.onGearSelectionChanged()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun sx(x: Float) = width / 2f + (x - camX) * camScale
    private fun sy(y: Float) = height / 2f - (y - camY) * camScale
    private fun wx(x: Float) = camX + (x - width / 2f) / camScale
    private fun wy(y: Float) = camY - (y - height / 2f) / camScale
}
