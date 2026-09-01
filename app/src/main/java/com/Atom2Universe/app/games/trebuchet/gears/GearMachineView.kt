package com.Atom2Universe.app.games.trebuchet.gears

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.games.trebuchet.SkyClock
import com.Atom2Universe.app.games.trebuchet.SkyState
import com.Atom2Universe.app.games.trebuchet.CloudField
import com.Atom2Universe.app.games.trebuchet.BirdField
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.floor

class GearMachineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onGearMachineChanged()
        fun onGearSelectionChanged()
    }

    enum class TouchMode { NONE, MOVE, STRUCTURE_MOVE, SPIN, LAYER_DOWN, LAYER_UP }
    private enum class CameraView { BUILD, FULL }
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
    var layoutConflictIds: Set<Int> = emptySet()
        private set
    /** Quand il est actif, le glissement d'une roue déplace tout son bâti assemblé. */
    var structureTool = false
        private set

    private val dp = resources.displayMetrics.density
    private val gearPath = Path()
    private val pBackground = Paint().apply { color = Color.rgb(10, 16, 36) }
    private val pSky = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGround = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(68, 83, 48) }
    private val pGrass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(159, 188, 100); strokeWidth = 2f * dp }
    private val pSun = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pMoon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 239, 246) }
    private val pMoonDark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pCloud = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBird = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f * dp; strokeCap = Paint.Cap.ROUND
        color = Color.rgb(40, 38, 46)
    }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = Color.argb(90, 180, 220, 190)
    }
    private val pTickLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.argb(150, 200, 230, 205)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pHint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.argb(180, 220, 235, 255) }
    private val pFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 83, 112); strokeWidth = 0.12f; style = Paint.Style.STROKE }
    private val pMesh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 255, 209, 102); strokeWidth = 0.035f }
    private val pBelt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 126, 74); style = Paint.Style.STROKE; strokeWidth = 0.09f }
    private val pChain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 214, 224); style = Paint.Style.STROKE; strokeWidth = 0.075f }
    private val pGear = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 234, 245); style = Paint.Style.STROKE; strokeWidth = 0.035f }
    private val pHub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 58); style = Paint.Style.FILL }
    private val pSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 209, 102); style = Paint.Style.STROKE; strokeWidth = 0.08f }
    private val pConflict = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 91, 91)
        style = Paint.Style.STROKE
        strokeWidth = 0.10f
    }
    private val pTexture = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.045f }
    private val pAssembly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(119, 239, 196); style = Paint.Style.STROKE; strokeWidth = 0.07f
        pathEffect = DashPathEffect(floatArrayOf(0.16f, 0.10f), 0f)
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * dp; textAlign = Paint.Align.CENTER }
    private val pUpperLayer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.045f
        pathEffect = DashPathEffect(floatArrayOf(0.16f, 0.11f), 0f)
    }
    private val pMagnet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        style = Paint.Style.STROKE
        strokeWidth = 0.055f
    }
    private val pLayerPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 16, 25, 50) }
    private val pLayerButton = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 143, 166, 200) }
    private val pLayerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * dp
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val screenRect = RectF()
    private val skyClock = SkyClock()
    private val sky = SkyState().apply { update(skyClock.instant) }
    private val clouds = CloudField()
    private val birds = BirdField()
    private var ambientClock = 0f
    private var cloudTint = 0
    private val starsX = FloatArray(56) { ((it * 83 + 31) % 997) / 997f }
    private val starsY = FloatArray(56) { ((it * 149 + 17) % 503) / 503f }
    private val starsR = FloatArray(56) { if (it % 5 == 0) 1.35f else 0.75f }

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
    private var pointerDownRawX = 0f
    private var pointerDownRawY = 0f
    private var lastGestureRawX = 0f
    private var lastGestureRawY = 0f
    private var pointerTravel = 0f
    private var manipulationStarted = false
    private var lastUiNotifyMillis = 0L
    private var magneticTargetId: Int? = null
    private var magnetFirstId: Int? = null
    private var magnetSecondId: Int? = null
    private var magnetPulseStarted = 0L
    private var magnetFeedbackUntil = 0L
    private var cameraView = CameraView.BUILD
    private var lastTapAt = 0L
    private var gestureLocked = false
    private var pinching = false
    private var pinchStartDistance = 0f
    private var pinchStartScale = 0f
    private var pinchLastFocusX = 0f
    private var panLastX = 0f
    private var panLastY = 0f
    private var sceneDragging = false
    private var timePressAt = 0L
    private var timePressX = 0f
    private var timeFingerX = 0f
    private var timeCandidate = false
    private var timeMode = false
    private var timeRate = 0f

    fun toggleStructureTool(): Boolean {
        structureTool = !structureTool
        invalidate()
        return structureTool
    }

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
        layoutConflictIds = emptySet()
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
        layoutConflictIds = emptySet()
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
            layoutConflictIds = emptySet()
            selectedId = null
            fitCamera()
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
            invalidate()
        }
        return removed
    }

    fun setSelectedTeeth(teeth: Int): Int? {
        val id = selectedId ?: return null
        val result = game.resizeGearAndReflow(id, teeth) ?: return null
        layoutConflictIds = result.conflicts
        if (result.success) {
            fitCamera()
            listener?.onGearMachineChanged()
        } else {
            listener?.onGearSelectionChanged()
        }
        invalidate()
        return result.teeth
    }

    fun duplicateSelected(): Int? {
        val source = selectedId ?: return null
        val id = game.duplicateGear(source) ?: return null
        selectedId = id
        currentLayer = game.config.wheels.firstOrNull { it.id == id }?.layer ?: currentLayer
        pendingLink = null
        layoutConflictIds = emptySet()
        fitCamera()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return id
    }

    fun changeSelectedLayer(delta: Int): Int? {
        val id = selectedId ?: return null
        val layer = game.changeLayer(id, delta) ?: return null
        currentLayer = layer
        layoutConflictIds = emptySet()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return layer
    }

    fun cycleSelectedMaterial(): GearWheelMaterial? {
        val id = selectedId ?: return null
        val material = game.cycleMaterial(id) ?: return null
        layoutConflictIds = emptySet()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return material
    }

    fun changeSelectedMaterial(delta: Int): GearWheelMaterial? {
        val id = selectedId ?: return null
        val material = game.changeMaterial(id, delta) ?: return null
        layoutConflictIds = emptySet()
        listener?.onGearMachineChanged()
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
        if (selectedWheel()?.layer != currentLayer) selectedId = null
        if (pendingLink?.kind != GearLinkKind.SHAFT_CLUTCH) pendingLink = null
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
        // L'atelier se cadre comme le trébuchet : sol en bas, machine à gauche,
        // grande réserve de terrain à droite pour étendre une transmission.
        val spanX = (bounds[1] - bounds[0] + 8f).coerceAtLeast(30f)
        val spanY = (bounds[3].coerceAtLeast(0f) + 4f).coerceAtLeast(14f)
        camScale = min(width / spanX, (height - 34f * dp) / spanY).coerceAtLeast(0.01f)
        val center = (bounds[0] + bounds[1]) / 2f
        camX = center + spanX * 0.16f
        camY = (height / 2f - 30f * dp) / camScale
        clampCamera()
        cameraView = CameraView.BUILD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateSimulation()
        drawWorkshopSky(canvas)

        canvas.save()
        canvas.translate(width / 2f - camX * camScale, height / 2f + camY * camScale)
        canvas.scale(camScale, -camScale)
        drawAutomaticFrame(canvas)
        drawWorkshopObjective(canvas)
        drawTransmissions(canvas)
        for (mesh in game.meshes) {
            val a = game.gears.firstOrNull { it.wheel.id == mesh.firstId } ?: continue
            val b = game.gears.firstOrNull { it.wheel.id == mesh.secondId } ?: continue
            pMesh.color = layerColor(a.wheel.layer, pastel = a.wheel.layer < currentLayer)
            pMesh.alpha = layerAlpha(a.wheel.layer, 170, 80)
            canvas.drawLine(a.body.x, a.body.y, b.body.x, b.body.y, pMesh)
        }
        drawLauncher(canvas)
        for (gear in game.gears.sortedBy { it.wheel.layer }) drawGear(canvas, gear)
        drawAssemblySelection(canvas)
        drawMagnetFeedback(canvas)
        game.projectile?.let { shot ->
            pGear.color = Color.rgb(245, 240, 222)
            pGear.alpha = 255
            canvas.drawCircle(shot.body.x, shot.body.y, shot.body.radius, pGear)
        }
        canvas.restore()

        for (gear in game.gears) {
            val x = sx(gear.body.x)
            val y = sy(gear.body.y)
            pText.color = if (gear.wheel.id == selectedId) Color.rgb(255, 209, 102)
                else layerColor(gear.wheel.layer, pastel = gear.wheel.layer < currentLayer)
            pText.alpha = layerAlpha(gear.wheel.layer, 210, 75)
            val kind = if (gear.wheel.kind == GearWheelKind.FLYWHEEL) "V" else "${gear.wheel.teeth}T"
            canvas.drawText("L${gear.wheel.layer} · $kind", x, y + 4f * dp, pText)
        }
        pText.alpha = 255
        drawLayerSelector(canvas)
        drawStructureTool(canvas)
        drawTimeControl(canvas)
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
            updateTimeControl(fixed)
            if (!timeMode) skyClock.advance(fixed)
            ambientClock += fixed
            clouds.update(fixed, sin(ambientClock * 0.08f) * 2.2f)
            birds.update(fixed, sin(ambientClock * 0.08f) * 2.2f)
            accumulator -= fixed
        }
        sky.update(skyClock.instant)
        if (hadProjectile && game.projectile == null) fitCamera()
        game.projectile?.let { shot ->
            val visibleRight = camX + width / camScale * 0.38f
            if (shot.body.x > visibleRight) camX += shot.body.x - visibleRight
        }
        clampCamera()
        val nowMillis = now / 1_000_000L
        if (nowMillis - lastUiNotifyMillis >= 250L) {
            lastUiNotifyMillis = nowMillis
            listener?.onGearMachineChanged()
        }
    }

    /** Même ciel vivant et mêmes repères de terrain que la vue du trébuchet. */
    private fun drawWorkshopSky(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        pSky.shader = LinearGradient(0f, 0f, 0f, h, sky.zenith, sky.horizon, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, pSky)
        pSky.shader = null
        if (sky.starAlpha > 0.03f) {
            pStar.alpha = (sky.starAlpha * 170f).toInt().coerceIn(0, 255)
            for (i in starsX.indices) canvas.drawCircle(starsX[i] * w, starsY[i] * h * 0.55f, starsR[i] * dp, pStar)
        }
        drawMoon(canvas, w, h)
        drawSun(canvas, w, h)
        drawClouds(canvas, w, h)
        drawBirds(canvas, w, h)

        val groundY = sy(0f).coerceIn(0f, h)
        val light = sky.light.coerceIn(0f, 1f)
        pGround.color = Color.rgb((48 + light * 38).toInt(), (58 + light * 46).toInt(), (35 + light * 22).toInt())
        canvas.drawRect(0f, groundY, w, h, pGround)
        pGrass.alpha = (120 + light * 135).toInt()
        canvas.drawLine(0f, groundY, w, groundY, pGrass)
        pGrass.alpha = 255

        val viewWidth = w / camScale
        val left = camX - viewWidth / 2f
        val right = camX + viewWidth / 2f
        val step = when {
            viewWidth > 180f -> 50f
            viewWidth > 70f -> 25f
            else -> 10f
        }
        pTickLabel.textSize = 11f * dp
        var distance = 0f
        val firingLine = game.bounds()[0]
        while (firingLine + distance < right + step) {
            val x = firingLine + distance
            if (x > left - step) {
                val px = sx(x)
                canvas.drawLine(px, groundY, px, groundY + 10f * dp, pTick)
                if (distance > 0f) canvas.drawText("${distance.toInt()} m", px, groundY + 24f * dp, pTickLabel)
            }
            distance += step
        }
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float) {
        if (sky.sunAltitude < -0.12f) return
        val cx = skyX(sky.sunX, w)
        val cy = skyY(sky.sunAltitude, h)
        val radius = 13f * dp
        pSun.color = SkyState.mix(Color.rgb(255, 154, 60), Color.rgb(255, 246, 208), sky.sunAltitude.coerceIn(0f, 1f))
        pSun.alpha = 70
        canvas.drawCircle(cx, cy, radius * 3.2f, pSun)
        pSun.alpha = 255
        canvas.drawCircle(cx, cy, radius, pSun)
        if (sky.eclipse > 0f) {
            pMoonDark.color = SkyState.mix(sky.zenith, Color.rgb(10, 10, 18), 0.7f)
            canvas.drawCircle(cx + radius * 2f * (1f - sky.eclipse), cy, radius, pMoonDark)
        }
    }

    private fun drawMoon(canvas: Canvas, w: Float, h: Float) {
        if (sky.moonAltitude < -0.12f) return
        val cx = skyX(sky.moonX, w)
        val cy = skyY(sky.moonAltitude, h)
        val radius = 15f * dp
        pMoon.alpha = ((0.35f + 0.65f * sky.starAlpha) * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, pMoon)
        if (sky.moonPhase < 0.99f) {
            pMoonDark.color = SkyState.mix(sky.zenith, sky.horizon, 0.35f)
            pMoonDark.alpha = pMoon.alpha
            val offset = radius * 2f * (1f - sky.moonPhase) * if (sky.moonWaxing) -1f else 1f
            canvas.drawCircle(cx + offset, cy, radius, pMoonDark)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float) {
        val tint = SkyState.mix(sky.horizon, Color.WHITE, 0.5f)
        if (tint != cloudTint) {
            cloudTint = tint
            pCloud.shader = RadialGradient(0f, 0f, 1f, intArrayOf(tint, tint and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
        pCloud.alpha = 140
        val half = w / 2f / camScale
        for (cloud in clouds.clouds) {
            var x = cloud.x
            x -= floor((x - camX + CloudField.SPAN / 2f) / CloudField.SPAN) * CloudField.SPAN
            if (x < camX - half - cloud.size * 3f || x > camX + half + cloud.size * 3f) continue
            val cx = sx(x); val cy = sy(cloud.altitude); val size = cloud.size * camScale
            if (cy !in (-size * 3f)..(h + size * 2f)) continue
            var i = 0
            while (i < cloud.puffs.size) {
                val radius = cloud.puffs[i + 2] * size
                if (radius >= 0.5f) {
                    canvas.save()
                    canvas.translate(cx + cloud.puffs[i] * size, cy + cloud.puffs[i + 1] * size)
                    canvas.scale(radius, radius)
                    canvas.drawCircle(0f, 0f, 1f, pCloud)
                    canvas.restore()
                }
                i += 3
            }
        }
    }

    private fun drawBirds(canvas: Canvas, w: Float, h: Float) {
        if (sky.light <= 0.02f) return
        pBird.alpha = (sky.light * 200f).toInt().coerceIn(0, 255)
        val half = w / 2f / camScale
        for (bird in birds.birds) {
            var x = bird.x
            x -= floor((x - camX + BirdField.SPAN / 2f) / BirdField.SPAN) * BirdField.SPAN
            if (x < camX - half - bird.span * 6f || x > camX + half + bird.span * 6f) continue
            val span = bird.span * camScale
            if (span < 1.5f) continue
            val cy = sy(bird.altitude + sin(ambientClock * 0.9f + bird.bobPhase) * bird.span * 1.4f)
            val cx = sx(x)
            val lift = span * (0.15f + 0.55f * (sin(ambientClock * bird.flapRate * 6.2832f + bird.flapPhase) * 0.5f + 0.5f))
            canvas.drawLine(cx, cy, cx - span, cy - lift, pBird)
            canvas.drawLine(cx, cy, cx + span, cy - lift, pBird)
        }
        pBird.alpha = 255
    }

    private fun skyX(fraction: Float, w: Float) = (0.08f + 0.84f * fraction) * w
    private fun skyY(altitude: Float, h: Float): Float {
        val skyBand = h * 0.72f
        return skyBand - altitude.coerceIn(-0.2f, 1f) * skyBand * 0.86f
    }

    /** L'objectif de tir donne un second point de repère au double-appui large. */
    private fun objectiveX(): Float {
        val launcher = game.config.launcherWheelId?.let { id ->
            game.gears.firstOrNull { it.wheel.id == id }?.body?.x
        }
        return (launcher ?: game.bounds()[1]) + 60f
    }

    private fun drawWorkshopObjective(canvas: Canvas) {
        val x = objectiveX()
        pFrame.color = Color.rgb(239, 103, 79)
        pFrame.alpha = 210
        pFrame.strokeWidth = 0.10f
        canvas.drawLine(x, 0f, x, 2.1f, pFrame)
        canvas.drawCircle(x, 1.72f, 0.38f, pFrame)
        canvas.drawLine(x - 0.27f, 1.45f, x + 0.27f, 1.99f, pFrame)
        canvas.drawLine(x - 0.27f, 1.99f, x + 0.27f, 1.45f, pFrame)
        pFrame.color = Color.rgb(66, 83, 112)
        pFrame.alpha = 255
    }

    private fun focusCamera(view: CameraView) {
        if (width <= 0 || height <= 0) return
        cameraView = view
        if (view == CameraView.BUILD) {
            fitCamera()
            return
        }
        val bounds = game.bounds()
        val target = objectiveX()
        val left = minOf(bounds[0], target)
        val right = maxOf(bounds[1], target)
        val spanX = (right - left + 12f).coerceAtLeast(70f)
        val spanY = (bounds[3].coerceAtLeast(2.2f) + 4f).coerceAtLeast(14f)
        camScale = min(width / spanX, (height - 34f * dp) / spanY).coerceAtLeast(0.01f)
        camX = (left + right) / 2f
        camY = (height / 2f - 30f * dp) / camScale
        clampCamera()
    }

    /** Même butées de terrain que le trébuchet, adaptées à l'atelier extensible. */
    private fun leftCameraLimit(): Float {
        val leftmostPiece = game.bounds()[0]
        return leftmostPiece - maxOf(8f, abs(leftmostPiece) * 0.40f)
    }

    private fun clampCamera() {
        if (width <= 0 || camScale <= 0f) return
        val left = leftCameraLimit()
        val right = 1_000f
        val halfWidth = width / camScale / 2f
        camX = if (right - left <= halfWidth * 2f) {
            (left + right) * 0.5f
        } else {
            camX.coerceIn(left + halfWidth, right - halfWidth)
        }
        camY = (height / 2f - 30f * dp) / camScale
    }

    private fun updateTimeControl(dt: Float) {
        if (!timeMode) {
            if (timeCandidate && SystemClock.uptimeMillis() - timePressAt >= 420L) {
                timeMode = true
                timePressX = timeFingerX
            }
            return
        }
        timeRate = SkyClock.scrubRate(timeFingerX - timePressX, width * 0.33f)
        skyClock.scrub(timeRate * dt)
    }

    private fun stopTimeControl() {
        timeMode = false
        timeCandidate = false
        timeRate = 0f
    }

    /** Retour détaillé pendant l'appui long, identique à celui du trébuchet. */
    private fun drawTimeControl(canvas: Canvas) {
        if (!timeMode) return
        val minutes = ((skyClock.instant % 86_400_000L) + 86_400_000L) % 86_400_000L / 60_000L
        val hour = minutes / 60
        val minute = minutes % 60
        val direction = when {
            timeRate > 0.02f -> "▶"
            timeRate < -0.02f -> "◀"
            else -> "■"
        }
        pHint.textSize = 20f * dp
        canvas.drawText("%02d:%02d  %s %.1f h/s".format(hour, minute, direction, abs(timeRate)), width / 2f, height * 0.18f, pHint)
    }

    private fun drawAutomaticFrame(canvas: Canvas) {
        for (gear in game.gears) {
            drawGearSupport(canvas, gear)
        }
        pFrame.alpha = 255
    }

    /** Un bâti en A proportionné à chaque roue, avec traverse et contreventements. */
    private fun drawGearSupport(canvas: Canvas, gear: GearMachineGame.GearState) {
        val x = gear.body.x; val y = gear.body.y
        val spread = (gear.wheel.outerRadius * 0.60f).coerceAtLeast(0.34f)
        val top = (y - gear.wheel.pitchRadius * 0.20f).coerceAtLeast(0.22f)
        val alpha = layerAlpha(gear.wheel.layer, 145, 45)
        pFrame.color = supportColor(gear.wheel.material)
        pFrame.alpha = alpha
        pFrame.strokeWidth = (0.065f + gear.wheel.outerRadius * 0.018f).coerceAtMost(0.14f)
        canvas.drawLine(x - spread, 0f, x, top, pFrame)
        canvas.drawLine(x + spread, 0f, x, top, pFrame)
        canvas.drawLine(x - spread, 0f, x + spread, 0f, pFrame)
        canvas.drawLine(x - spread * 0.72f, 0.04f, x + spread * 0.24f, top * 0.52f, pFrame)
        canvas.drawLine(x + spread * 0.72f, 0.04f, x - spread * 0.24f, top * 0.52f, pFrame)
        canvas.drawLine(x - spread * 0.48f, top * 0.56f, x + spread * 0.48f, top * 0.56f, pFrame)
        canvas.drawCircle(x, y, (0.10f + gear.wheel.outerRadius * 0.025f).coerceAtMost(0.20f), pFrame)
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
            val layerAlpha = layerAlpha(a.wheel.layer, 150, 60)
            pBelt.alpha = layerAlpha
            pChain.alpha = layerAlpha
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
                    if (!(transmission.joint as com.Atom2Universe.app.games.physics.OneWayRotaryJoint).engaged) {
                        pChain.alpha = (layerAlpha * 0.55f).toInt()
                    }
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
                GearLinkKind.SHAFT_CLUTCH -> {
                    val radius = (minOf(a.wheel.pitchRadius, b.wheel.pitchRadius) * 0.32f)
                        .coerceAtLeast(0.13f)
                    pChain.alpha = layerAlpha
                    pChain.strokeWidth = 0.065f
                    canvas.drawCircle(a.body.x, a.body.y, radius, pChain)
                    canvas.drawCircle(a.body.x, a.body.y, radius * 0.58f, pChain)
                    pGear.color = Color.rgb(255, 209, 102)
                    pGear.alpha = layerAlpha
                    canvas.drawCircle(a.body.x, a.body.y, 0.065f, pGear)
                }
            }
        }
        pBelt.alpha = 255
        pChain.alpha = 255
        pChain.strokeWidth = 0.075f
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
        pFrame.alpha = layerAlpha(gear.wheel.layer, 180, 70)
        pFrame.strokeWidth = 0.14f
        canvas.drawLine(gear.body.x + nx * start, gear.body.y + ny * start,
            gear.body.x + nx * end, gear.body.y + ny * end, pFrame)
        canvas.drawLine(gear.body.x - ny * 0.22f + nx * end, gear.body.y + nx * 0.22f + ny * end,
            gear.body.x + ny * 0.22f + nx * end, gear.body.y - nx * 0.22f + ny * end, pFrame)
        pFrame.color = Color.rgb(66, 83, 112)
        pFrame.alpha = 255
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
        val relativeLayer = gear.wheel.layer - currentLayer
        if (relativeLayer > 0) {
            pUpperLayer.color = layerColor(gear.wheel.layer, pastel = true)
            pUpperLayer.alpha = (105 - (relativeLayer - 1) * 16).coerceAtLeast(35)
            canvas.drawPath(gearPath, pUpperLayer)
            val hubRadius = minOf(0.22f, pitch * 0.25f).coerceAtLeast(0.08f)
            canvas.drawCircle(gear.body.x, gear.body.y, hubRadius, pUpperLayer)
            if (gear.wheel.id in layoutConflictIds) {
                canvas.drawCircle(gear.body.x, gear.body.y, outer + 0.22f, pConflict)
            }
            return
        }

        pGear.color = materialColor(gear.wheel.material, gear.wheel.layer, pastel = relativeLayer < 0)
        pGear.alpha = if (relativeLayer == 0 || gear.wheel.id == selectedId) 235
            else (175 - (-relativeLayer - 1) * 20).coerceAtLeast(75)
        canvas.drawPath(gearPath, pGear)
        drawMaterialTexture(canvas, gear, pitch, pGear.alpha)
        if (gear.wheel.kind == GearWheelKind.FLYWHEEL) {
            pHub.color = Color.rgb(16, 24, 40)
            pHub.alpha = if (relativeLayer == 0) 255 else 150
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.55f, pHub)
            pEdge.strokeWidth = 0.10f
            pEdge.alpha = pGear.alpha
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.82f, pEdge)
        }
        pEdge.strokeWidth = 0.035f
        pEdge.color = materialEdgeColor(gear.wheel.material, relativeLayer != 0)
        pEdge.alpha = if (relativeLayer == 0) 255 else 150
        canvas.drawPath(gearPath, pEdge)
        val hubRadius = minOf(0.22f, pitch * 0.25f).coerceAtLeast(0.08f)
        pHub.alpha = if (relativeLayer == 0) 255 else 150
        canvas.drawCircle(gear.body.x, gear.body.y, hubRadius, pHub)
        if (gear.wheel.id == selectedId) {
            pSelect.strokeWidth = 0.08f
            canvas.drawCircle(gear.body.x, gear.body.y, outer + 0.12f, pSelect)
        }
        if (gear.wheel.id in layoutConflictIds) {
            val radius = outer + 0.22f
            canvas.drawCircle(gear.body.x, gear.body.y, radius, pConflict)
            val cross = minOf(radius * 0.35f, 0.42f)
            canvas.drawLine(gear.body.x - cross, gear.body.y - cross, gear.body.x + cross, gear.body.y + cross, pConflict)
            canvas.drawLine(gear.body.x - cross, gear.body.y + cross, gear.body.x + cross, gear.body.y - cross, pConflict)
        }
        pHub.alpha = 255
        pEdge.alpha = 255
    }

    private fun drawMaterialTexture(canvas: Canvas, gear: GearMachineGame.GearState, pitch: Float, alpha: Int) {
        val spokes = when (gear.wheel.material) {
            GearWheelMaterial.WOOD -> 5
            GearWheelMaterial.ALUMINUM -> 6
            GearWheelMaterial.STEEL -> 8
            GearWheelMaterial.TITANIUM -> 7
        }
        pTexture.color = materialEdgeColor(gear.wheel.material, false)
        pTexture.alpha = (alpha * 0.62f).toInt()
        pTexture.strokeWidth = when (gear.wheel.material) {
            GearWheelMaterial.WOOD -> 0.035f
            else -> 0.055f
        }
        canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.62f, pTexture)
        val inner = (pitch * 0.22f).coerceAtLeast(0.11f)
        for (i in 0 until spokes) {
            val angle = gear.body.angle + i * (2f * PI.toFloat() / spokes)
            val end = if (gear.wheel.material == GearWheelMaterial.WOOD) pitch * 0.78f else pitch * 0.66f
            canvas.drawLine(gear.body.x + cos(angle) * inner, gear.body.y + sin(angle) * inner,
                gear.body.x + cos(angle) * end, gear.body.y + sin(angle) * end, pTexture)
        }
        if (gear.wheel.material == GearWheelMaterial.WOOD) {
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.42f, pTexture)
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.84f, pTexture)
        }
        pTexture.alpha = 255
    }

    private fun materialColor(material: GearWheelMaterial, layer: Int, pastel: Boolean): Int {
        val base = when (material) {
            GearWheelMaterial.WOOD -> Color.rgb(158, 95, 48)
            GearWheelMaterial.ALUMINUM -> Color.rgb(164, 184, 194)
            GearWheelMaterial.STEEL -> Color.rgb(76, 118, 151)
            GearWheelMaterial.TITANIUM -> Color.rgb(117, 91, 156)
        }
        return if (pastel) SkyState.mix(base, Color.WHITE, 0.45f) else {
            // Une légère teinte d'étage reste présente sans masquer le matériau.
            SkyState.mix(base, layerColor(layer, pastel = false), 0.14f)
        }
    }

    private fun materialEdgeColor(material: GearWheelMaterial, muted: Boolean): Int {
        val color = when (material) {
            GearWheelMaterial.WOOD -> Color.rgb(91, 50, 24)
            GearWheelMaterial.ALUMINUM -> Color.rgb(238, 246, 249)
            GearWheelMaterial.STEEL -> Color.rgb(205, 228, 241)
            GearWheelMaterial.TITANIUM -> Color.rgb(227, 207, 255)
        }
        return if (muted) SkyState.mix(color, Color.WHITE, 0.36f) else color
    }

    private fun supportColor(material: GearWheelMaterial): Int = when (material) {
        GearWheelMaterial.WOOD -> Color.rgb(108, 65, 33)
        GearWheelMaterial.ALUMINUM -> Color.rgb(184, 199, 202)
        GearWheelMaterial.STEEL -> Color.rgb(95, 119, 135)
        GearWheelMaterial.TITANIUM -> Color.rgb(141, 119, 170)
    }

    private fun drawAssemblySelection(canvas: Canvas) {
        if (!structureTool) return
        val id = selectedId ?: return
        for (member in game.gears) if (member.wheel.id in game.assemblyIds(id)) {
            canvas.drawCircle(member.body.x, member.body.y, member.wheel.outerRadius + 0.18f, pAssembly)
        }
    }

    /** Une teinte stable et très distincte pour chaque étage voisin. */
    private fun layerColor(layer: Int, pastel: Boolean): Int {
        val hue = ((205f + layer * 53f) % 360f + 360f) % 360f
        return Color.HSVToColor(floatArrayOf(hue, if (pastel) 0.32f else 0.68f, if (pastel) 0.96f else 0.92f))
    }

    private fun layerAlpha(layer: Int, below: Int, above: Int): Int = when {
        layer == currentLayer -> 255
        layer < currentLayer -> (below - (currentLayer - layer - 1) * 18).coerceAtLeast(55)
        else -> (above - (layer - currentLayer - 1) * 12).coerceAtLeast(28)
    }

    /** Retour bref mais net quand l'aimant vient de trouver une prise. */
    private fun drawMagnetFeedback(canvas: Canvas) {
        val firstId = magnetFirstId ?: return
        val secondId = magnetSecondId ?: return
        val now = SystemClock.uptimeMillis()
        if (magneticTargetId == null && now > magnetFeedbackUntil) return
        val a = game.gears.firstOrNull { it.wheel.id == firstId } ?: return
        val b = game.gears.firstOrNull { it.wheel.id == secondId } ?: return
        val dx = b.body.x - a.body.x
        val dy = b.body.y - a.body.y
        val distance = hypot(dx, dy).coerceAtLeast(1e-4f)
        val pulse = ((now - magnetPulseStarted) / 420f).coerceIn(0f, 1f)
        pMagnet.alpha = if (magneticTargetId != null) 210 else ((1f - pulse) * 190f).toInt()
        pMagnet.strokeWidth = 0.055f + (1f - pulse) * 0.035f
        canvas.drawLine(a.body.x, a.body.y, b.body.x, b.body.y, pMagnet)
        val expansion = pulse * 0.18f
        canvas.drawCircle(a.body.x, a.body.y, a.wheel.outerRadius + 0.10f + expansion, pMagnet)
        canvas.drawCircle(b.body.x, b.body.y, b.wheel.outerRadius + 0.10f + expansion, pMagnet)
        val contactX = a.body.x + dx / distance * a.wheel.pitchRadius
        val contactY = a.body.y + dy / distance * a.wheel.pitchRadius
        canvas.drawCircle(contactX, contactY, 0.10f + (1f - pulse) * 0.06f, pMagnet)
        pMagnet.alpha = 255
    }

    private fun updateMagnetFeedback(movedId: Int, targetId: Int?) {
        if (targetId == null) {
            if (magneticTargetId != null) magnetFeedbackUntil = 0L
            magneticTargetId = null
            return
        }
        if (targetId != magneticTargetId) {
            magnetFirstId = movedId
            magnetSecondId = targetId
            magnetPulseStarted = SystemClock.uptimeMillis()
        }
        magneticTargetId = targetId
        magnetFeedbackUntil = SystemClock.uptimeMillis() + 650L
    }

    /** Sélecteur d'étage fixe : il reste lisible quel que soit le zoom de la machine. */
    private fun drawLayerSelector(canvas: Canvas) {
        val left = 10f * dp
        val top = 10f * dp
        val height = 44f * dp
        val button = 44f * dp
        val width = 152f * dp
        screenRect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerPanel)
        screenRect.set(left, top, left + button, top + height)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerButton)
        screenRect.set(left + width - button, top, left + width, top + height)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerButton)

        pLayerText.color = layerColor(currentLayer, pastel = false)
        canvas.drawText(
            "L${if (currentLayer > 0) "+$currentLayer" else currentLayer}",
            left + width / 2f,
            top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f,
            pLayerText
        )
        pLayerText.color = Color.rgb(255, 209, 102)
        canvas.drawText("▼", left + button / 2f, top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f, pLayerText)
        canvas.drawText("▲", left + width - button / 2f, top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f, pLayerText)
    }

    private fun layerControlAt(x: Float, y: Float): Int {
        val left = 10f * dp
        val top = 10f * dp
        val height = 44f * dp
        val width = 152f * dp
        val button = 44f * dp
        if (y !in top..(top + height)) return 0
        return when {
            x in left..(left + button) -> -1
            x in (left + width - button)..(left + width) -> +1
            else -> 0
        }
    }

    private fun drawStructureTool(canvas: Canvas) {
        val left = 10f * dp
        val top = 60f * dp
        val width = 152f * dp
        val height = 38f * dp
        screenRect.set(left, top, left + width, top + height)
        pLayerPanel.color = if (structureTool) Color.argb(235, 21, 77, 78) else Color.argb(220, 16, 25, 50)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerPanel)
        pLayerPanel.color = Color.argb(225, 16, 25, 50)
        pLayerText.textSize = 12f * dp
        pLayerText.color = if (structureTool) Color.rgb(119, 239, 196) else Color.rgb(221, 230, 239)
        canvas.drawText(
            if (structureTool) "BÂTI · DÉPLACER" else "OUTIL · PIÈCE",
            left + width / 2f,
            top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f,
            pLayerText
        )
        pLayerText.textSize = 14f * dp
    }

    private fun structureControlAt(x: Float, y: Float): Boolean {
        val left = 10f * dp
        val top = 60f * dp
        return x in left..(left + 152f * dp) && y in top..(top + 38f * dp)
    }

    private fun beginPinch(event: MotionEvent) {
        if (event.pointerCount < 2) return
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        pinchStartDistance = hypot(dx, dy).coerceAtLeast(1f)
        pinchStartScale = camScale
        pinchLastFocusX = (event.getX(0) + event.getX(1)) * 0.5f
        pinching = true
        gestureLocked = true
        touchMode = TouchMode.NONE
        touchGearId = null
        stopTimeControl()
    }

    private fun applyPinch(event: MotionEvent) {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        val distance = hypot(dx, dy).coerceAtLeast(1f)
        val focusX = (event.getX(0) + event.getX(1)) * 0.5f
        val focusY = (event.getY(0) + event.getY(1)) * 0.5f
        // On garde le monde sous le centre *précédent* des deux doigts : si les
        // doigts voyagent ensemble, cela devient naturellement un pan horizontal.
        val worldX = wx(pinchLastFocusX)
        val minScale = (width / (1_000f - leftCameraLimit())).coerceAtLeast(0.01f)
        val maxScale = 190f * dp
        camScale = (pinchStartScale * distance / pinchStartDistance).coerceIn(minScale, maxScale)
        camX = worldX - (focusX - width / 2f) / camScale
        pinchLastFocusX = focusX
        // Le sol ne suit jamais le doigt : il reste posé en bas de l'écran,
        // comme dans TrebuchetView, quelle que soit l'échelle choisie.
        camY = (height / 2f - 30f * dp) / camScale
        clampCamera()
    }

    private fun panScene(event: MotionEvent) {
        val dx = event.x - panLastX
        val dy = event.y - panLastY
        if (abs(dx) > 0.1f || abs(dy) > 0.1f) sceneDragging = true
        camX -= dx / camScale
        clampCamera()
        panLastX = event.x
        panLastY = event.y
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = wx(event.x)
        val y = wy(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointerDownRawX = event.rawX
                pointerDownRawY = event.rawY
                lastGestureRawX = event.rawX
                lastGestureRawY = event.rawY
                pointerTravel = 0f
                manipulationStarted = false
                gestureLocked = false
                pinching = false
                sceneDragging = false
                panLastX = event.x
                panLastY = event.y
                stopTimeControl()
                val layerDelta = layerControlAt(event.x, event.y)
                if (layerDelta != 0) {
                    touchMode = if (layerDelta < 0) TouchMode.LAYER_DOWN else TouchMode.LAYER_UP
                    setCurrentLayer(layerDelta)
                    return true
                }
                if (structureControlAt(event.x, event.y)) {
                    toggleStructureTool()
                    return true
                }
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastTapAt < 300L
                lastTapAt = now
                if (doubleTap && placementTeeth == null) {
                    selectedId = null
                    cameraView = if (cameraView == CameraView.FULL) CameraView.BUILD else CameraView.FULL
                    focusCamera(cameraView)
                    gestureLocked = true
                    touchMode = TouchMode.NONE
                    listener?.onGearSelectionChanged()
                    invalidate()
                    return true
                }
                val hit = game.gearAt(x, y, currentLayer)
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
                        timeCandidate = event.y < sy(0f)
                        timePressAt = now
                        timePressX = event.x
                        timeFingerX = event.x
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
                layoutConflictIds = emptySet()
                game.captureWheelAngles()
                magneticTargetId = null
                val distance = hypot(x - hit.body.x, y - hit.body.y)
                touchMode = when {
                    structureTool -> TouchMode.STRUCTURE_MOVE
                    distance > hit.wheel.pitchRadius * 0.55f -> TouchMode.SPIN
                    else -> TouchMode.MOVE
                }
                touchGearId = hit.wheel.id
                lastPointerAngle = atan2(y - hit.body.y, x - hit.body.x)
                gestureStartMillis = event.eventTime
                gestureAngle = 0f
                listener?.onGearSelectionChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                beginPinch(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    applyPinch(event)
                    invalidate()
                    return true
                }
                if (touchGearId == null) {
                    timeFingerX = event.x
                    if (timeMode) {
                        invalidate()
                        return true
                    }
                    if (timeCandidate && abs(event.x - timePressX) > 8f * dp) timeCandidate = false
                    if (!gestureLocked) panScene(event)
                    invalidate()
                    return true
                }
                pointerTravel += hypot(
                    event.rawX - lastGestureRawX,
                    event.rawY - lastGestureRawY
                )
                lastGestureRawX = event.rawX
                lastGestureRawY = event.rawY
                val id = touchGearId ?: return true
                when (touchMode) {
                    TouchMode.MOVE -> {
                        if (!manipulationStarted) {
                            val distanceFromDown = hypot(
                                event.rawX - pointerDownRawX,
                                event.rawY - pointerDownRawY
                            )
                            if (distanceFromDown < 8f * dp) return true
                            manipulationStarted = true
                        }
                        layoutConflictIds = emptySet()
                        val target = game.moveGearMagnetic(id, x, y, magneticTargetId)
                        updateMagnetFeedback(id, target)
                    }
                    TouchMode.STRUCTURE_MOVE -> {
                        if (!manipulationStarted) {
                            val distanceFromDown = hypot(
                                event.rawX - pointerDownRawX,
                                event.rawY - pointerDownRawY
                            )
                            if (distanceFromDown < 8f * dp) return true
                            manipulationStarted = true
                        }
                        layoutConflictIds = emptySet()
                        game.moveAssembly(id, x, y)
                    }
                    TouchMode.SPIN -> {
                        if (pointerTravel < 8f * dp) return true
                        manipulationStarted = true
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
            MotionEvent.ACTION_POINTER_UP -> {
                pinching = false
                panLastX = event.x
                panLastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchMode == TouchMode.MOVE && manipulationStarted) {
                    touchGearId?.let { id ->
                        val target = game.moveGearMagnetic(id, x, y, magneticTargetId)
                        updateMagnetFeedback(id, target)
                        if (target != null) {
                            magnetPulseStarted = SystemClock.uptimeMillis()
                            magnetFeedbackUntil = magnetPulseStarted + 650L
                        }
                    }
                    fitCamera()
                } else if (touchMode == TouchMode.STRUCTURE_MOVE && manipulationStarted) {
                    touchGearId?.let { id -> game.moveAssembly(id, x, y) }
                    fitCamera()
                } else if (touchMode == TouchMode.SPIN && manipulationStarted &&
                    event.actionMasked == MotionEvent.ACTION_UP) {
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
                magneticTargetId = null
                pinching = false
                stopTimeControl()
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
