package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.Atom2Universe.app.R

/**
 * A quick tactile mini-game before a debris cell actually clears.
 * Rock: circle a finger around the cell to roll it away (rotation accumulates from the finger's angle).
 * Weed/tuft: a few small sprigs sit at fixed spots; drag each one out past a short threshold, one by one.
 */
private class DebrisGame(val cell: Int, debris: Int, val startTime: Long) {
    val rock = debris == 2
    val anchors = if (rock) emptyList() else List(if (debris == 1) 3 else 2) { i ->
        val r = kotlin.random.Random(cell * 97 + i * 13)
        PointF(.22f + r.nextFloat() * .56f, .4f + r.nextFloat() * .4f)
    }
    val plucked = BooleanArray(anchors.size)
    var rotation = 0f
    var lastAngle = Float.NaN
    var dragAnchor = -1
    var dragFromX = 0f
    var dragFromY = 0f
    var dragX = 0f
    var dragY = 0f
    var burstAt = 0L
    var burstX = 0f
    var burstY = 0f
    var lastActivityAt = startTime
    var done = false
    var doneAt = 0L
    val angles = FloatArray(6) { kotlin.random.Random(cell * 31 + it).nextFloat() * 360f }
    fun progress() = if (rock) (rotation / 720f).coerceIn(0f, 1f)
        else if (anchors.isEmpty()) 1f else plucked.count { it } / anchors.size.toFloat()
    fun checkDone(now: Long) { if (!done && progress() >= 1f) { done = true; doneAt = now } }
}

/**
 * A watering-can timing challenge: a marker swings back and forth like a metronome pendulum
 * (easing at the ends, fast through the middle); tapping the cell while it sits in the
 * highlighted zone scores a hit. Several hits in a row are needed - more for a bigger job -
 * and the zone narrows after each one. A miss never costs progress, only another try.
 */
private class WateringGauge(val cell: Int, val targets: List<Int>, val startTime: Long, val needed: Int) {
    companion object { const val PERIOD = 2400L }
    var hits = 0
    var missAt = 0L
    var hitAt = 0L
    var done = false
    var doneAt = 0L
    var targetStart = 0f
    var targetWidth = 0f
    init { rerollTarget() }
    private fun rerollTarget() {
        targetWidth = .34f - .2f * (hits.toFloat() / (needed - 1).coerceAtLeast(1))
        targetStart = .06f + kotlin.random.Random(cell * 53 + hits * 97 + startTime.toInt()).nextFloat() * (.88f - targetWidth)
    }
    fun pos(now: Long): Float {
        val phase = ((now - startTime) % PERIOD) / PERIOD.toFloat()
        return 0.5f - 0.5f * kotlin.math.cos(2.0 * kotlin.math.PI * phase).toFloat()
    }
    fun attempt(now: Long): Boolean {
        val p = pos(now)
        if (p < targetStart || p > targetStart + targetWidth) { missAt = now; return false }
        hits++; hitAt = now
        if (hits >= needed) { done = true; doneAt = now } else rerollTarget()
        return true
    }
}

/**
 * A harvest grip-and-pull: hold a ripe plant, it starts trembling after a grip delay and a
 * vertical gauge appears beside it, marker bouncing up and down; drag upward past a threshold
 * while the marker sits in the top band to yank it free. A mistimed pull just settles back down.
 */
private class HarvestGauge(val cell: Int, val startTime: Long, val downX: Float, val downY: Float,
                           val cropAtStart: FarmCrop?, val variant: Int, val critical: Boolean) {
    companion object { const val GRIP_DELAY = 500L; const val PERIOD = 1100L }
    val zoneStart = if (critical) .88f else .80f
    var armed = true
    var failedAt = 0L
    var done = false
    var doneAt = 0L
    val angles = FloatArray(6) { kotlin.random.Random(cell * 41 + it).nextFloat() * 360f }
    fun phase(now: Long): Float {
        val elapsed = (now - startTime - GRIP_DELAY).coerceAtLeast(0)
        val p = (elapsed % PERIOD) / PERIOD.toFloat()
        return 0.5f - 0.5f * kotlin.math.cos(2.0 * kotlin.math.PI * p).toFloat()
    }
    /** Returns true only on the exact pull that succeeds. */
    fun tryPull(deltaY: Float, now: Long): Boolean {
        if (done) return false
        if (deltaY > 26f) {
            if (!armed) return false
            armed = false
            if (phase(now) >= zoneStart) { done = true; doneAt = now; return true }
            failedAt = now
            return false
        }
        if (deltaY < 10f) armed = true
        return false
    }
}

/** Coordinates stay in world units; drawing and hit testing use the same transform. */
class FarmWorldView(context: Context, private val state: FarmState,
                    private val onPlant: (Int) -> Unit, private val onParcel: (Int) -> Unit,
                    private val onRemove: (Int) -> Unit, private val onDebrisCleared: (Int) -> Unit,
                    private val onWatered: (Int) -> Unit,
                    private val onHarvested: (cell: Int, amount: Int, count: Int) -> Unit) : View(context) {
    private var harvestGame: HarvestGauge? = null
    private var harvestTicking = false
    private val harvestTick = object : Runnable {
        override fun run() {
            harvestTicking = false
            val game = harvestGame
            if (game != null) {
                if (game.done && System.currentTimeMillis() - game.doneAt > 420L) harvestGame = null
                else { harvestTicking = true; postOnAnimation(this) }
            }
            invalidate()
        }
    }
    private fun ensureHarvestTicking() { if (!harvestTicking) { harvestTicking = true; postOnAnimation(harvestTick) } }
    /** Consumes the whole gesture once started on a ripe cell, mirroring the debris/watering handlers. */
    private fun handleHarvestTouch(event: MotionEvent): Boolean {
        if (region != FarmRegion.HOME || wateringMode) return false
        val x = (event.x - cameraX) / zoom; val y = (event.y - cameraY) / zoom
        val now = System.currentTimeMillis()
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cell = cells.indexOfFirst { it.contains(x, y) }
                if (cell < 0) return false
                val p = state.plots[cell]
                if (!state.parcels[FarmLayout.parcelOf(cell)].unlocked || p.crop == null || p.progress(now) < 1f) return false
                harvestGame = HarvestGauge(cell, now, x, y, p.crop, p.variant, p.critical)
                ensureHarvestTicking(); invalidate(); true
            }
            MotionEvent.ACTION_MOVE -> {
                val game = harvestGame ?: return false
                if (!game.done && game.tryPull(game.downY - y, now)) {
                    // One pull reaps whatever the basket reaches: this cell, its row, or the parcel.
                    val (amount, count) = state.harvestMany(state.harvestTargets(game.cell), now)
                    onHarvested(game.cell, amount, count)
                }
                invalidate(); ensureHarvestTicking(); true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val game = harvestGame ?: return true
                if (!game.done) harvestGame = null
                invalidate(); true
            }
            else -> true
        }
    }
    /** Keeps redrawing, frame by frame, only while at least one ripe critical plant needs its pulsing aura. */
    private var auraTicking = false
    private val auraTick = Runnable { auraTicking = false; invalidate() }
    private fun ensureAuraTicking() { if (!auraTicking) { auraTicking = true; postOnAnimation(auraTick) } }
    var wateringMode = false
        set(value) { field = value; wateringGame = null; invalidate() }
    private var wateringGame: WateringGauge? = null
    private data class WaterBurst(val cell: Int, val startAt: Long)
    private val waterBursts = mutableListOf<WaterBurst>()
    private var wateringTicking = false
    private val wateringTick = object : Runnable {
        override fun run() {
            wateringTicking = false
            val now = System.currentTimeMillis()
            val game = wateringGame
            if (game != null && ((game.done && now - game.doneAt > 260L) || (!game.done && now - game.startTime > 20000L)))
                wateringGame = null
            waterBursts.removeAll { now - it.startAt > 520L }
            invalidate()
            if (wateringGame != null || waterBursts.isNotEmpty()) { wateringTicking = true; postOnAnimation(this) }
        }
    }
    private fun ensureWateringTicking() { if (!wateringTicking) { wateringTicking = true; postOnAnimation(wateringTick) } }
    private fun startWateringGauge(cell: Int) {
        wateringGame = WateringGauge(cell, state.wateringTargets(cell), System.currentTimeMillis(), state.wateringHitsNeeded())
        ensureWateringTicking(); invalidate()
    }
    private fun handleWateringTap(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val game = wateringGame
        // Once the gauge is swinging, a tap anywhere on screen is the timing attempt - no need to hit the tiny cell.
        if (game != null && !game.done) {
            if (game.attempt(now) && game.done) {
                val watered = state.waterMany(game.targets, now)
                watered.forEach { waterBursts.add(WaterBurst(it, now)) }
                if (watered.isNotEmpty()) onWatered(watered.size)
            }
            ensureWateringTicking(); invalidate(); return
        }
        val cell = cells.indexOfFirst { it.contains(x, y) }
        if (cell < 0) { onWatered(-1); return }
        val p = state.plots[cell]
        // A miss here is a silent trap otherwise: nothing to water means every other action (plant, harvest) also does nothing while the mode stays on.
        if (!state.parcels[FarmLayout.parcelOf(cell)].unlocked || p.crop == null || p.watered || p.progress(now) >= 1f) { onWatered(-1); return }
        startWateringGauge(cell)
    }
    private var debrisGame: DebrisGame? = null
    private var debrisTicking = false
    private val debrisTick = object : Runnable {
        override fun run() {
            debrisTicking = false
            val game = debrisGame
            if (game != null) when {
                game.done && System.currentTimeMillis() - game.doneAt > 480L -> {
                    state.clean(game.cell); debrisGame = null; onDebrisCleared(game.cell)
                }
                !game.done && System.currentTimeMillis() - game.lastActivityAt > 6000L -> debrisGame = null
                else -> { debrisTicking = true; postOnAnimation(this) }
            }
            invalidate()
        }
    }
    private fun ensureDebrisTicking() { if (!debrisTicking) { debrisTicking = true; postOnAnimation(debrisTick) } }
    private fun startDebrisGame(cell: Int): DebrisGame {
        val game = DebrisGame(cell, state.plots[cell].debris, System.currentTimeMillis())
        debrisGame = game; ensureDebrisTicking(); invalidate()
        return game
    }
    private fun angleOf(cell: RectF, x: Float, y: Float) =
        Math.toDegrees(kotlin.math.atan2((y - cell.centerY()).toDouble(), (x - cell.centerX()).toDouble())).toFloat()
    private fun nearestAnchor(game: DebrisGame, x: Float, y: Float): Int {
        val cell = cells[game.cell]
        var best = -1; var bestDist = 30f
        game.anchors.forEachIndexed { i, anchor ->
            if (game.plucked[i]) return@forEachIndexed
            val dist = kotlin.math.hypot((x - (cell.left + anchor.x * cell.width())).toDouble(), (y - (cell.top + anchor.y * cell.height())).toDouble()).toFloat()
            if (dist < bestDist) { best = i; bestDist = dist }
        }
        return best
    }
    /** Consumes the whole gesture while a debris mini-game is running, so panning/zoom never fights it. */
    private fun handleDebrisTouch(event: MotionEvent): Boolean {
        if (region != FarmRegion.HOME || wateringMode) return false
        val x = (event.x - cameraX) / zoom; val y = (event.y - cameraY) / zoom
        val now = System.currentTimeMillis()
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val existing = debrisGame
                val onExisting = existing != null && !existing.done &&
                    if (existing.rock) cells[existing.cell].contains(x, y) else nearestAnchor(existing, x, y) >= 0
                // A tap on a fresh debris cell always starts its own mini-game, even if another one sits unfinished elsewhere.
                val game = if (onExisting) existing!! else {
                    val cell = cells.indexOfFirst { it.contains(x, y) }
                    if (cell < 0 || state.plots[cell].debris == 0 || !state.parcels[FarmLayout.parcelOf(cell)].unlocked) return false
                    startDebrisGame(cell)
                }
                game.lastActivityAt = now
                if (game.rock) game.lastAngle = angleOf(cells[game.cell], x, y)
                else { val anchor = nearestAnchor(game, x, y); game.dragAnchor = anchor
                    if (anchor >= 0) { game.dragFromX = x; game.dragFromY = y; game.dragX = x; game.dragY = y } }
                invalidate(); true
            }
            MotionEvent.ACTION_MOVE -> {
                val game = debrisGame ?: return false
                if (game.done) return true
                game.lastActivityAt = now
                if (game.rock) {
                    val angle = angleOf(cells[game.cell], x, y)
                    if (!game.lastAngle.isNaN()) {
                        var delta = angle - game.lastAngle
                        if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
                        game.rotation += kotlin.math.abs(delta); game.checkDone(now)
                    }
                    game.lastAngle = angle
                } else if (game.dragAnchor >= 0) {
                    game.dragX = x; game.dragY = y
                    val cell = cells[game.cell]; val anchor = game.anchors[game.dragAnchor]
                    val ax = cell.left + anchor.x * cell.width(); val ay = cell.top + anchor.y * cell.height()
                    if (kotlin.math.hypot((x - ax).toDouble(), (y - ay).toDouble()) > 26.0) {
                        game.plucked[game.dragAnchor] = true
                        game.burstAt = now; game.burstX = ax; game.burstY = ay
                        game.dragAnchor = -1; game.checkDone(now)
                    }
                }
                invalidate(); ensureDebrisTicking(); true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val game = debrisGame ?: return false
                game.lastAngle = Float.NaN; game.dragAnchor = -1; invalidate(); true
            }
            else -> debrisGame != null
        }
    }
    private val sprites = FarmSprites(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scenery = FarmScenery(sprites)
    private var zoom = 1f
    private var cameraX = 0f
    private var cameraY = 0f
    private var multiTouch = false
    var dismissBubble: (() -> Boolean)? = null
    private var dismissGesture = false
    private val regionScenery = FarmRegionScenery(sprites)
    private val livestockScene = LivestockScene(context, sprites, state.livestock)
    var onLivestockPen: ((LivestockKind, Boolean) -> Unit)? = null
    var region = FarmRegion.HOME
        private set
    var onRegionTap: (() -> Unit)? = null
    var onBushBonus: ((Long) -> Unit)? = null
    // Tucked in the gap between the house and parcel 1, centred over its gate; a coin pile only
    // shows through it - and only gets a tap - while the once-a-day bonus hasn't been claimed yet.
    private val treasureBush = RectF(965f, 15f, 1095f, 145f)
    private data class Camera(val zoom: Float, val x: Float, val y: Float)
    private val cameras = mutableMapOf<FarmRegion, Camera>()
    private val worldWidth get() = if (region == FarmRegion.HOME) FarmLayout.worldWidth else 1600f
    private val worldHeight get() = if (region == FarmRegion.HOME) FarmLayout.worldHeight else 1500f
    private val worldTop get() = if (region == FarmRegion.HOME) FarmLayout.worldTop else 0f
    private val lands = FarmLayout.lands.map { RectF(it.x, it.y, it.x + it.width, it.y + it.height) }
    private val cells = List(FarmLayout.cellCount) { i ->
        val parcel = FarmLayout.parcelOf(i)
        val land = lands[parcel]
        val spec = FarmLayout.lands[parcel]
        val local = FarmLayout.localCell(i)
        val x = land.left + 28 + local % spec.columns * 78
        val y = land.top + 72 + local / spec.columns * 74
        RectF(x, y, x + 70, y + 66)
    }
    fun parcelScreenPosition(index: Int) = PointF(
        cameraX + lands[index].centerX() * zoom, cameraY + lands[index].centerY() * zoom)
    private fun mapTop() = 80f * resources.displayMetrics.density
    fun switchRegion(next: FarmRegion) {
        if (next == region) return
        cameras[region] = Camera(zoom, cameraX, cameraY)
        region = next
        contentDescription = context.getString(next.label)
        val camera = cameras[next]
        if (camera != null) {
            zoom = camera.zoom.coerceIn(minimumZoom(), maximumZoom())
            cameraX = camera.x; cameraY = camera.y
        } else {
            zoom = minimumZoom()
            cameraX = (width - worldWidth * zoom) / 2
            cameraY = mapTop() + (height - mapTop() - (worldHeight - worldTop) * zoom) / 2 - worldTop * zoom
        }
        constrain(); invalidate()
    }
    private fun minimumZoom() = minOf(width / worldWidth, (height - mapTop()).coerceAtLeast(1f) / (worldHeight - worldTop)).coerceAtLeast(.01f)
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { multiTouch = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val x = (detector.focusX - cameraX) / zoom
            val y = (detector.focusY - cameraY) / zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(minimumZoom(), maximumZoom())
            cameraX = detector.focusX - x * zoom; cameraY = detector.focusY - y * zoom
            constrain(); invalidate(); return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!multiTouch && !scale.isInProgress) {
                cameraX -= distanceX; cameraY -= distanceY; constrain(); invalidate()
            }
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (multiTouch) return true
            performClick()
            if (region == FarmRegion.LIVESTOCK) {
                LivestockScene.hit((e.x - cameraX) / zoom, (e.y - cameraY) / zoom)?.let { onLivestockPen?.invoke(it, false) }
                return true
            }
            if (region != FarmRegion.HOME) { onRegionTap?.invoke(); return true }
            val x = (e.x - cameraX) / zoom; val y = (e.y - cameraY) / zoom
            if (state.bushBonusReady() && treasureBush.contains(x, y)) {
                val gained = state.claimBushBonus()
                if (gained > 0) { onBushBonus?.invoke(gained); invalidate() }
                return true
            }
            if (wateringMode) { handleWateringTap(x, y); return true }
            val parcel = lands.indexOfFirst { x >= it.left && x <= it.right && y >= it.top - 12 && y <= it.bottom + 20 }
            if (parcel < 0) return true
            if (!state.parcels[parcel].unlocked) onParcel(parcel)
            else {
                val cell = cells.indexOfFirst { it.contains(x, y) }
                if (cell >= 0) onPlant(cell) else onParcel(parcel)
            }
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (region == FarmRegion.LIVESTOCK && !multiTouch) {
                LivestockScene.hit((e.x - cameraX) / zoom, (e.y - cameraY) / zoom)?.let { onLivestockPen?.invoke(it, true) }
                return
            }
            if (region != FarmRegion.HOME || multiTouch || wateringMode) return
            val x = (e.x - cameraX) / zoom; val y = (e.y - cameraY) / zoom
            val cell = cells.indexOfFirst { it.contains(x, y) }
            if (cell < 0 || !state.parcels[FarmLayout.parcelOf(cell)].unlocked) return
            val p = state.plots[cell]
            if (p.crop != null) onRemove(cell)
            // A tap held a touch too long lands here instead of onSingleTapUp; an empty cell has
            // nothing to remove, so treat it as the plant tap it was almost certainly meant to be.
            else if (p.debris == 0) onPlant(cell)
        }
        // SimpleOnGestureListener also implements OnDoubleTapListener, which GestureDetector picks up
        // automatically; that silently swallows every other rapid tap while it waits to see if a pair
        // of nearby taps forms a double-tap. Nothing here ever wants that, so it's turned off below.
    }).apply { setOnDoubleTapListener(null) }
    init {
        isClickable = true
        contentDescription = context.getString(R.string.farm_map_description)
    }
    private fun maximumZoom() = maxOf(minimumZoom(), 2.5f * resources.displayMetrics.density)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) focusParcel(0) else {
            cameraX += (w - oldw) / 2f; cameraY += (h - oldh) / 2f
            zoom = zoom.coerceIn(minimumZoom(), maximumZoom()); constrain()
        }
    }
    fun focusParcel(index: Int) {
        val land = lands[index]
        zoom = minOf(width / (land.width() + 80f), (height - 100 * resources.displayMetrics.density).coerceAtLeast(1f) / (land.height() + 80f)).coerceIn(minimumZoom(), maximumZoom())
        cameraX = width / 2f - land.centerX() * zoom
        cameraY = height / 2f + 30 * resources.displayMetrics.density - land.centerY() * zoom
        constrain(); invalidate()
    }

    private fun constrain() {
        cameraX = if (worldWidth * zoom <= width) (width - worldWidth * zoom) / 2
            else cameraX.coerceIn(width - worldWidth * zoom, 0f)
        val mapHeight = (worldHeight - worldTop) * zoom
        cameraY = if (mapHeight <= height - mapTop())
            mapTop() + (height - mapTop() - mapHeight) / 2 - worldTop * zoom
        else cameraY.coerceIn(height - worldHeight * zoom, mapTop() - worldTop * zoom)
    }
    /** Which raw handler owns the CURRENT gesture, decided once at ACTION_DOWN and never re-decided mid-gesture. */
    private enum class TouchClaim { NONE, DEBRIS, HARVEST }
    private var touchClaim = TouchClaim.NONE
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) dismissGesture = dismissBubble?.invoke() == true
        // Dismissing a card must not also buy land, plant or water underneath it.
        if (dismissGesture) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiTouch = false; touchClaim = TouchClaim.NONE; parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event.pointerCount > 1) multiTouch = true
        if (multiTouch) {
            scale.onTouchEvent(event); gestures.onTouchEvent(event)
        } else when (touchClaim) {
            // A gesture claimed by a mini-game at its DOWN stays with that same handler for its
            // whole life; an unclaimed gesture goes to the normal detectors for its whole life too.
            // Re-deciding per event (the old `handleDebrisTouch(event) || handleHarvestTouch(event)`
            // dispatch) let a leftover or unconditionally-true handler swallow an UP that belonged
            // to the other path, so onSingleTapUp never fired and quick taps silently did nothing.
            TouchClaim.DEBRIS -> handleDebrisTouch(event)
            TouchClaim.HARVEST -> handleHarvestTouch(event)
            TouchClaim.NONE -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchClaim = if (handleDebrisTouch(event)) TouchClaim.DEBRIS
                        else if (handleHarvestTouch(event)) TouchClaim.HARVEST else TouchClaim.NONE
                }
                if (touchClaim == TouchClaim.NONE) { scale.onTouchEvent(event); gestures.onTouchEvent(event) }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
            parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(87, 133, 57))
        canvas.save(); canvas.translate(cameraX, cameraY); canvas.scale(zoom, zoom)
        val visible = RectF(-cameraX / zoom, -cameraY / zoom, (width - cameraX) / zoom, (height - cameraY) / zoom)
        for (row in kotlin.math.floor(visible.top / 80).toInt().coerceAtLeast(kotlin.math.floor(worldTop / 80).toInt())..(visible.bottom / 80).toInt().coerceAtMost((worldHeight / 80).toInt()))
            for (col in (visible.left / 80).toInt().coerceAtLeast(0)..(visible.right / 80).toInt().coerceAtMost((worldWidth / 80).toInt())) {
            sprites.grass(canvas, RectF(col * 80f, row * 80f, col * 80f + 80, row * 80f + 80), col, row)
        }
        if (region != FarmRegion.HOME) {
            if (region == FarmRegion.LIVESTOCK) livestockScene.draw(canvas, visible)
            else regionScenery.draw(canvas, region, visible)
            canvas.restore(); return
        }
        scenery.ground(canvas)

        val now = System.currentTimeMillis()
        if (state.plots.any { it.critical && it.crop != null }) ensureAuraTicking()
        lands.forEachIndexed { index, land ->
            if (land.right + 20 < visible.left || land.left - 20 > visible.right ||
                land.bottom + 20 < visible.top || land.top - 20 > visible.bottom) return@forEachIndexed
            val unlocked = state.parcels[index].unlocked
            val spec = FarmLayout.lands[index]
            val fenceWidth = land.width() / spec.columns
            paint.color = Color.argb(24, 83, 102, 40)
            canvas.drawRoundRect(land, 22f, 22f, paint)
            for (col in 0 until spec.columns) sprites.environment(canvas, 0, 2,
                RectF(land.left + col * fenceWidth, land.top + 14, land.left + (col + 1) * fenceWidth, land.top + 64))
            for (row in 0 until spec.rows + 1) {
                sprites.environment(canvas, 1, 2, RectF(land.left - 9, land.top + 40 + row * (land.height() - 40) / (spec.rows + 1), land.left + 12, land.top + 40 + (row + 1) * (land.height() - 40) / (spec.rows + 1)))
                sprites.environment(canvas, 1, 2, RectF(land.right - 12, land.top + 40 + row * (land.height() - 40) / (spec.rows + 1), land.right + 9, land.top + 40 + (row + 1) * (land.height() - 40) / (spec.rows + 1)))
            }
            for (i in FarmLayout.cells(index)) {
                val cell = cells[i]; val p = state.plots[i]
                if (p.debris != 0) {
                    val game = debrisGame?.takeIf { it.cell == i }
                    if (game != null) drawDebrisGame(canvas, cell, p.debris, game, now)
                    else if (p.debris == 3) sprites.environment(canvas, 3, 0, cell)
                    else sprites.environment(canvas, if (p.debris == 1) 2 else 3, 3, cell)
                } else {
                    // Manured ground reads as a darker, richer earth - the bonus has to be visible
                    // from the moment the seed goes in, not only on the harvest total.
                    paint.color = when {
                        p.rich && p.watered -> Color.rgb(58, 39, 25)
                        p.rich -> Color.rgb(104, 68, 37)
                        p.watered -> Color.rgb(94, 65, 44)
                        else -> Color.rgb(151, 104, 60)
                    }
                    canvas.drawRoundRect(RectF(cell.left, cell.top + 34, cell.right, cell.bottom), 5f, 5f, paint)
                    paint.color = if (p.rich) Color.rgb(76, 47, 26) else Color.rgb(112, 70, 43)
                    for (r in 0..2) canvas.drawRect(cell.left + 5, cell.top + 40 + r * 9, cell.right - 5, cell.top + 42 + r * 9, paint)
                    val crop = p.crop
                    val grip = harvestGame?.takeIf { it.cell == i && !it.done && now - it.startTime >= HarvestGauge.GRIP_DELAY }
                    if (crop != null) {
                        val shakeX = grip?.let { (kotlin.math.sin((now - it.startTime) / 28.0) * 4).toFloat() } ?: 0f
                        canvas.save(); canvas.translate(shakeX, 0f)
                        sprites.crop(canvas, crop, p.variant, p.stage(now), RectF(cell.left - 2, cell.top - 9, cell.right + 2, cell.bottom - 5))
                        canvas.restore()
                        paint.color = Color.rgb(48, 66, 36)
                        canvas.drawRect(cell.left + 8, cell.bottom - 3, cell.right - 8, cell.bottom + 1, paint)
                        paint.color = if (p.progress(now) >= 1) Color.rgb(255, 224, 91) else Color.rgb(95, 201, 222)
                        canvas.drawRect(cell.left + 8, cell.bottom - 3, cell.left + 8 + (cell.width() - 16) * p.progress(now), cell.bottom + 1, paint)
                        if (p.progress(now) >= 1) label(canvas, context.getString(R.string.farm_ready), cell.centerX(), cell.top + 8, 10f)
                        if (p.critical && p.progress(now) >= 1) drawCriticalAura(canvas, cell, now)
                        waterBursts.firstOrNull { it.cell == i }?.let { drawWaterBurst(canvas, cell, it, now) }
                    }
                    wateringGame?.takeIf { it.cell == i }?.let { drawWateringGauge(canvas, cell, it, now) }
                    harvestGame?.takeIf { it.cell == i }?.let { drawHarvestGauge(canvas, cell, it, now) }
                }
            }
            // The gate stands open while some ground here is still idle, and shuts once every cell
            // is growing. It is the one piece of state the map shows from across the farm, zoomed out,
            // without a badge or a number: which fields still want you.
            val gateOpen = state.parcelHasIdleGround(index)
            for (col in 0 until spec.columns) sprites.environment(canvas,
                if (col == 1 && gateOpen) 1 else 0, if (col == 1) 3 else 2,
                RectF(land.left + col * fenceWidth, land.bottom - 27, land.left + (col + 1) * fenceWidth, land.bottom + 20))
            if (!unlocked) {
                paint.color = Color.argb(145, 30, 49, 27); canvas.drawRect(land, paint)
                val price = java.text.NumberFormat.getIntegerInstance().format(state.unlockCost(index).toLong())
                label(canvas, context.getString(R.string.farm_locked_price, price), land.centerX(), land.centerY(), 20f)
            }
            paint.color = Color.rgb(61, 76, 40)
            canvas.drawRoundRect(RectF(land.left + 45, land.top - 12, land.right - 45, land.top + 20), 8f, 8f, paint)
            label(canvas, context.getString(R.string.farm_parcel_label, index + 1, context.getString(state.parcels[index].use.label)), land.centerX(), land.top + 10, 15f)
        }
        scenery.objects(canvas, visible)
        if (RectF.intersects(treasureBush, visible)) drawTreasureBush(canvas)
        canvas.restore()
    }
    /** A coin pile only peeks out from under the bush - with a quiet, static glint - while unclaimed. */
    private fun drawTreasureBush(canvas: Canvas) {
        if (state.bushBonusReady()) {
            val cx = treasureBush.centerX(); val cy = treasureBush.bottom - 22
            paint.color = Color.rgb(196, 137, 44)
            canvas.drawOval(cx - 20, cy - 5, cx + 20, cy + 11, paint)
            paint.color = Color.rgb(241, 196, 90)
            for ((dx, dy) in listOf(-9f to 0f, 8f to -4f, 1f to 5f)) canvas.drawCircle(cx + dx, cy + dy, 8f, paint)
            paint.color = Color.rgb(255, 226, 150); paint.strokeWidth = 2f
            for ((dx, dy) in listOf(-9f to 0f, 8f to -4f, 1f to 5f)) canvas.drawCircle(cx + dx - 2, cy + dy - 2, 3f, paint)
        }
        sprites.environment(canvas, 2, 3, treasureBush)
        if (state.bushBonusReady()) {
            val sx = treasureBush.right - 14; val sy = treasureBush.top + 20
            paint.color = Color.argb(150, 255, 250, 214); paint.strokeWidth = 2.5f
            canvas.drawLine(sx - 8, sy, sx + 8, sy, paint); canvas.drawLine(sx, sy - 8, sx, sy + 8, paint)
            paint.color = Color.argb(90, 255, 250, 214)
            canvas.drawLine(sx - 5, sy - 5, sx + 5, sy + 5, paint); canvas.drawLine(sx - 5, sy + 5, sx + 5, sy - 5, paint)
        }
    }
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
        paint.color = Color.rgb(255, 245, 208); paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y, paint)
    }
    private fun drawDebrisGame(canvas: Canvas, cell: RectF, debris: Int, game: DebrisGame, now: Long) {
        if (game.rock) {
            val fade = if (game.done) (1f - (now - game.doneAt) / 480f).coerceIn(0f, 1f) else 1f
            canvas.save()
            canvas.translate(cell.centerX(), cell.centerY())
            canvas.rotate(game.rotation % 360f)
            canvas.scale(fade, fade)
            canvas.translate(-cell.centerX(), -cell.centerY())
            paint.alpha = (fade * 255).toInt()
            sprites.environment(canvas, 3, 3, cell)
            paint.alpha = 255
            canvas.restore()
            if (!game.done) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.rgb(255, 210, 90)
                val r = cell.width() * .58f
                canvas.drawArc(RectF(cell.centerX() - r, cell.centerY() - r, cell.centerX() + r, cell.centerY() + r),
                    -90f, 360f * game.progress(), false, paint)
                paint.style = Paint.Style.FILL
            } else {
                val t = ((now - game.doneAt) / 480f).coerceIn(0f, 1f)
                paint.color = Color.rgb(150, 150, 150)
                game.angles.forEach { a ->
                    val rad = a * kotlin.math.PI / 180
                    val dx = (kotlin.math.cos(rad) * t * 42).toFloat(); val dy = (kotlin.math.sin(rad) * t * 42 - t * 14).toFloat()
                    paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cell.centerX() + dx, cell.centerY() + dy, (3.5f * (1f - t)).coerceAtLeast(0f), paint)
                }
            }
        } else {
            val fade = if (game.done) (1f - (now - game.doneAt) / 400f).coerceIn(0f, 1f) else 1f
            game.anchors.forEachIndexed { i, anchor ->
                if (game.plucked[i]) return@forEachIndexed
                val ax = cell.left + anchor.x * cell.width(); val ay = cell.top + anchor.y * cell.height()
                val dragging = game.dragAnchor == i
                canvas.save()
                if (dragging) canvas.translate((game.dragX - game.dragFromX) * .5f, (game.dragY - game.dragFromY) * .5f)
                paint.alpha = (fade * 255).toInt()
                sprites.environment(canvas, if (debris == 1) 2 else 3, 3, RectF(ax - 12f, ay - 16f, ax + 12f, ay + 12f))
                paint.alpha = 255
                canvas.restore()
                if (dragging) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Color.argb(160, 255, 248, 225)
                    canvas.drawLine(ax, ay, game.dragX, game.dragY, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            if (now - game.burstAt < 400) {
                val t = (now - game.burstAt) / 400f
                paint.color = Color.rgb(126, 168, 88)
                game.angles.take(4).forEach { a ->
                    val rad = a * kotlin.math.PI / 180
                    val dx = (kotlin.math.cos(rad) * t * 30).toFloat(); val dy = (kotlin.math.sin(rad) * t * 30 - t * 20).toFloat()
                    paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(game.burstX + dx, game.burstY + dy, (3f * (1f - t)).coerceAtLeast(0f), paint)
                }
            }
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
    }
    /** A shallow arc above the plant, like a rainbow gauge: the marker sweeps it, a band marks the zone to hit. */
    private fun drawWateringGauge(canvas: Canvas, cell: RectF, game: WateringGauge, now: Long) {
        val fade = if (game.done) (1f - (now - game.doneAt) / 260f).coerceIn(0f, 1f) else 1f
        val cx = cell.centerX(); val cy = cell.top + 26f
        val r = cell.width() * .42f
        val sweepStart = 200f; val sweepTotal = 140f
        val bounds = RectF(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb((170 * fade).toInt().coerceIn(0, 170), 35, 70, 100)
        canvas.drawArc(bounds, sweepStart, sweepTotal, false, paint)
        paint.color = Color.argb((235 * fade).toInt().coerceIn(0, 235), 80, 190, 240)
        canvas.drawArc(bounds, sweepStart + game.targetStart * sweepTotal, game.targetWidth * sweepTotal, false, paint)
        if (!game.done) {
            if (now - game.missAt < 160) {
                paint.color = Color.argb((150 * (1 - (now - game.missAt) / 160f)).toInt().coerceIn(0, 150), 220, 70, 60)
                canvas.drawArc(bounds, sweepStart, sweepTotal, false, paint)
            }
            if (now - game.hitAt < 200) {
                paint.color = Color.argb((200 * (1 - (now - game.hitAt) / 200f)).toInt().coerceIn(0, 200), 140, 230, 150)
                canvas.drawArc(bounds, sweepStart, sweepTotal, false, paint)
            }
            paint.style = Paint.Style.FILL
            val angle = Math.toRadians((sweepStart + game.pos(now) * sweepTotal).toDouble())
            val px = cx + (r * kotlin.math.cos(angle)).toFloat(); val py = cy + (r * kotlin.math.sin(angle)).toFloat()
            paint.color = Color.rgb(240, 250, 255); canvas.drawCircle(px, py, 5.5f, paint)
            paint.color = Color.rgb(120, 200, 232); canvas.drawCircle(px, py, 3f, paint)
            paint.color = Color.rgb(255, 248, 225)
            for (dot in 0 until game.needed) {
                paint.alpha = if (dot < game.hits) 255 else 90
                canvas.drawCircle(cx - (game.needed - 1) * 5f + dot * 10f, cy - r - 10f, 3f, paint)
            }
            paint.alpha = 255
        } else {
            paint.color = Color.argb((230 * fade).toInt().coerceIn(0, 230), 140, 230, 150)
            canvas.drawArc(bounds, sweepStart, sweepTotal, false, paint)
            paint.style = Paint.Style.FILL
        }
    }
    private fun drawWaterBurst(canvas: Canvas, cell: RectF, burst: WaterBurst, now: Long) {
        val t = now - burst.startAt
        paint.color = Color.rgb(120, 200, 235)
        for (i in 0..2) {
            val dt = t - i * 90
            if (dt < 0 || dt > 380) continue
            val f = dt / 380f
            val yPos = cell.top - 6 + f * (cell.height() * .55f)
            paint.alpha = ((1f - f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cell.centerX() - 8 + i * 8, yPos, 3.5f, paint)
        }
        val splash = t - 300
        if (splash >= 0 && splash <= 220) {
            val f = splash / 220f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
            paint.color = Color.argb(((1f - f) * 180).toInt().coerceIn(0, 180), 150, 220, 245)
            canvas.drawOval(cell.centerX() - 14 * f - 6, cell.bottom - 10, cell.centerX() + 14 * f + 6, cell.bottom + 2, paint)
            paint.style = Paint.Style.FILL
        }
        paint.alpha = 255
    }
    private fun drawHarvestGauge(canvas: Canvas, cell: RectF, game: HarvestGauge, now: Long) {
        if (game.done) {
            val t = ((now - game.doneAt) / 420f).coerceIn(0f, 1f)
            game.cropAtStart?.let { crop ->
                paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                val rect = RectF(cell.left - 2, cell.top - 9 - t * 60f, cell.right + 2, cell.bottom - 5 - t * 60f)
                sprites.crop(canvas, crop, game.variant, 4, rect)
                paint.alpha = 255
            }
            paint.color = if (game.critical) Color.rgb(255, 150, 60) else Color.rgb(255, 224, 91)
            val spread = if (game.critical) 46f else 34f
            game.angles.forEach { a ->
                val rad = a * kotlin.math.PI / 180
                val dx = (kotlin.math.cos(rad) * t * spread).toFloat(); val dy = (kotlin.math.sin(rad) * t * spread - t * 28).toFloat()
                paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(cell.centerX() + dx, cell.top + dy, (3f * (1f - t)).coerceAtLeast(0f), paint)
            }
            paint.alpha = 255
            return
        }
        if (now - game.startTime < HarvestGauge.GRIP_DELAY) return
        val barLeft = cell.right + 4f; val barRight = cell.right + 11f
        val barTop = cell.top - 2f; val barBottom = cell.bottom - 12f
        paint.color = Color.argb(170, 35, 70, 100)
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 3f, 3f, paint)
        paint.color = if (game.critical) Color.argb(235, 255, 140, 60) else Color.argb(230, 255, 214, 110)
        val zoneHeight = (barBottom - barTop) * (1f - game.zoneStart)
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + zoneHeight), 3f, 3f, paint)
        if (now - game.failedAt < 160) {
            paint.color = Color.argb((150 * (1 - (now - game.failedAt) / 160f)).toInt().coerceIn(0, 150), 220, 70, 60)
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 3f, 3f, paint)
        }
        val my = barBottom - game.phase(now) * (barBottom - barTop)
        paint.color = Color.rgb(240, 250, 255); canvas.drawCircle((barLeft + barRight) / 2, my, 5f, paint)
        paint.color = Color.rgb(120, 200, 232); canvas.drawCircle((barLeft + barRight) / 2, my, 2.6f, paint)
    }
    /** A pulsing gold halo with orbiting sparkles, so a ripe critical plant reads as special at a glance. */
    private fun drawCriticalAura(canvas: Canvas, cell: RectF, now: Long) {
        val pulse = (0.5f + 0.5f * kotlin.math.sin(now / 260.0)).toFloat()
        val cx = cell.centerX(); val cy = cell.centerY() - 14f
        paint.style = Paint.Style.STROKE
        for (ring in 0..1) {
            paint.strokeWidth = 2.5f
            paint.color = Color.argb((110 - ring * 40 + (pulse * 40).toInt()).coerceIn(0, 170), 255, 190, 70)
            canvas.drawCircle(cx, cy, cell.width() * (.42f + ring * .1f) + pulse * 4f, paint)
        }
        paint.style = Paint.Style.FILL
        for (i in 0..2) {
            val angle = (now / 6.0 + i * 120) % 360.0
            val rad = angle * kotlin.math.PI / 180
            val sx = cx + (kotlin.math.cos(rad) * cell.width() * .5f).toFloat()
            val sy = cy + (kotlin.math.sin(rad) * cell.width() * .5f).toFloat()
            paint.color = Color.argb((160 + pulse * 60).toInt().coerceIn(0, 255), 255, 224, 120)
            canvas.drawCircle(sx, sy, 2.2f, paint)
        }
    }
}
