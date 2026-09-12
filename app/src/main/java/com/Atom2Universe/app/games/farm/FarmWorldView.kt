package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.Atom2Universe.app.R

/** Pulls needed to tear a bush out. One volume comes off each time; the last stub goes with the burst. */
private const val BUSH_PULLS = 4
/** How far the finger must travel from where it pressed for one pull to count, in world units. */
private const val PULL_THRESHOLD = 26f

/**
 * A quick tactile mini-game before a debris cell actually clears.
 * Rock: circle a finger around it to roll it away (rotation accumulates from the finger's angle).
 * Bush: grab it anywhere and pull - each pull rips off one of the volumes it is built from, so the
 *   bush visibly comes apart in your hand. Drag back towards where you pressed to take another.
 * Cluttered ground: four-odd weed clumps and small stones lie about; drag each one out past a
 *   short threshold. Their placement comes from FarmScenery.rubbleLayout, the same list the art
 *   reads, so a piece is always grabbed exactly where it is drawn.
 */
private class DebrisGame(val cell: Int, debris: Int, val startTime: Long,
                        val rubble: List<FarmScenery.Rubble> = emptyList()) {
    val rock = debris == 2
    val bush = debris == 1
    /** Volumes torn off so far, and whether the finger has come back far enough to take another. */
    var torn = 0
    var armed = true
    var pulling = false
    var pullFromX = 0f
    var pullFromY = 0f
    val plucked = BooleanArray(rubble.size)
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
    fun progress() = when {
        rock -> (rotation / 720f).coerceIn(0f, 1f)
        bush -> torn / BUSH_PULLS.toFloat()
        rubble.isEmpty() -> 1f
        else -> plucked.count { it } / rubble.size.toFloat()
    }
    fun checkDone(now: Long) { if (!done && progress() >= 1f) { done = true; doneAt = now } }
}

/**
 * A watering-can timing challenge: a marker swings back and forth like a metronome pendulum
 * (easing at the ends, fast through the middle); tapping the cell while it sits in the
 * highlighted zone scores a hit. Several hits in a row are needed - more for a bigger job -
 * and the zone narrows after each one. A miss never costs progress, only another try.
 * [zoneScale] is the bought aim assist: it stretches every zone, early and late alike.
 */
private class WateringGauge(val cell: Int, val targets: List<Int>, val startTime: Long, val needed: Int,
                            val zoneScale: Float = 1f) {
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
        targetWidth = (.34f - .2f * (hits.toFloat() / (needed - 1).coerceAtLeast(1))) * zoneScale
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

private const val HARVEST_FLY_DURATION = 900L
/** Width, in world units, of each tappable arrow at either end of a parcel's banner. */
private const val ARROW_ZONE = 34f
/** Grass tiles baked beyond the world bounds, for the regions whose map does not fill the screen. */
private const val MEADOW_MARGIN_TILES = 3
/**
 * How many grass tiles may be on screen before the loose tufts stop being drawn blade by blade and
 * the baked layer takes over. Roughly a third of the map at once; below that the live draw is a few
 * hundred stamps, which is what the meadow always cost before it was baked, and above it the count
 * climbs into the thousands - the very thing the bake exists to prevent.
 */
private const val LIVE_TUFT_TILES = 420

/** Coordinates stay in world units; drawing and hit testing use the same transform. */
class FarmWorldView(context: Context, private val state: FarmState,
                    private val onPlant: (Int) -> Unit, private val onParcel: (Int) -> Unit,
                    private val onRemove: (Int) -> Unit, private val onDebrisCleared: (Int) -> Unit,
                    private val onWatered: (Int) -> Unit,
                    private val onHarvested: (cell: Int, result: FarmHarvestResult) -> Unit) : View(context) {
    var harvestTarget: (() -> PointF?)? = null
    private var harvestGame: HarvestGauge? = null
    private var harvestTicking = false
    private val harvestTick = object : Runnable {
        override fun run() {
            harvestTicking = false
            val game = harvestGame
            val now = System.currentTimeMillis()
            if (game != null) {
                if (game.done && now - game.doneAt > HARVEST_FLY_DURATION) harvestGame = null
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
                    val result = state.harvestMany(state.harvestTargets(game.cell), now)
                    onHarvested(game.cell, result)
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
    /** The shared wind clock and each parcel's gate never stop animating while the map is drawn. */
    private var lastDecorFrameNanos = 0L
    private var windTime = 0f
    private var decorTicking = false
    private val decorTick = Runnable { decorTicking = false; invalidate() }
    private fun ensureDecorTicking() { if (!decorTicking) { decorTicking = true; postOnAnimation(decorTick) } }
    // NaN means "never drawn yet": the gate then snaps straight to its real state instead of
    // swinging open from closed the first time a parcel scrolls into view.
    private val gateOpening = FloatArray(FarmLayout.lands.size) { Float.NaN }
    private fun updateGateOpening(index: Int, open: Boolean, dt: Float): Float {
        val goal = if (open) 1f else 0f
        var value = gateOpening[index]
        value = if (value.isNaN()) goal else {
            val step = 1.1f * dt
            value + (goal - value).coerceIn(-step, step)
        }
        gateOpening[index] = value
        if (value != goal) ensureDecorTicking()
        return value
    }
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
        wateringGame = WateringGauge(cell, state.wateringTargets(cell), System.currentTimeMillis(),
            state.wateringHitsNeeded(), state.wateringZoneScale())
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
                game.done && System.currentTimeMillis() - game.doneAt > 480L -> finishDebrisGame(game)
                !game.done && System.currentTimeMillis() - game.lastActivityAt > 6000L -> debrisGame = null
                else -> { debrisTicking = true; postOnAnimation(this) }
            }
            invalidate()
        }
    }
    private fun ensureDebrisTicking() { if (!debrisTicking) { debrisTicking = true; postOnAnimation(debrisTick) } }
    /** Settles a won game now instead of at the end of its fade - see [startDebrisGame]. */
    private fun finishDebrisGame(game: DebrisGame) {
        state.clean(game.cell); debrisGame = null; onDebrisCleared(game.cell)
    }
    private fun startDebrisGame(cell: Int): DebrisGame {
        // A won game stays here for the length of its fade, and only then does its cell get cleaned.
        // Replacing it without settling it first drops that clean-up on the floor, and the cell that
        // was just cleared comes straight back with all its debris.
        debrisGame?.takeIf { it.done }?.let { finishDebrisGame(it) }
        val game = DebrisGame(cell, state.plots[cell].debris, System.currentTimeMillis(),
            scenery.rubbleLayout(cells[cell]))
        debrisGame = game; ensureDebrisTicking(); invalidate()
        return game
    }
    /**
     * The angle of the finger about the rock's OWN centre. It used to be measured about the centre
     * of the cell, which sits well above the rock - a rock stands on the bottom of its square - so
     * circling the stone turned it about a point hanging in the air and it swung instead of rolling.
     */
    private fun angleOf(cell: RectF, x: Float, y: Float): Float {
        val pivot = scenery.rockCenter(cell)
        return Math.toDegrees(kotlin.math.atan2((y - pivot.y).toDouble(), (x - pivot.x).toDouble())).toFloat()
    }
    private fun nearestAnchor(game: DebrisGame, x: Float, y: Float): Int {
        val cell = cells[game.cell]
        var best = -1; var bestDist = 30f
        game.rubble.forEachIndexed { i, item ->
            if (game.plucked[i]) return@forEachIndexed
            val dist = kotlin.math.hypot((x - (cell.left + item.fx * cell.width())).toDouble(),
                (y - (cell.top + item.fy * cell.height())).toDouble()).toFloat()
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
                // A cell that has just been won still shows its debris for another instant, while
                // the pieces fade out and before state.clean() runs. Pressing it again inside that
                // window used to fall through and open a BRAND NEW game on the very same cell, with
                // its pull count back at zero - so one pull too many put back everything that had
                // just been torn out. The cell is finished: swallow the press.
                if (existing != null && existing.done && cells[existing.cell].contains(x, y)) return true
                val onExisting = existing != null && !existing.done &&
                    if (existing.rock || existing.bush) cells[existing.cell].contains(x, y)
                    else nearestAnchor(existing, x, y) >= 0
                // A tap on a fresh debris cell always starts its own mini-game, even if another one sits unfinished elsewhere.
                val game = if (onExisting) existing!! else {
                    val cell = cells.indexOfFirst { it.contains(x, y) }
                    if (cell < 0 || state.plots[cell].debris == 0 || !state.parcels[FarmLayout.parcelOf(cell)].unlocked) return false
                    startDebrisGame(cell)
                }
                game.lastActivityAt = now
                if (game.rock) game.lastAngle = angleOf(cells[game.cell], x, y)
                else if (game.bush) {
                    game.pulling = true; game.armed = true
                    game.pullFromX = x; game.pullFromY = y; game.dragX = x; game.dragY = y
                } else { val anchor = nearestAnchor(game, x, y); game.dragAnchor = anchor
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
                } else if (game.bush) {
                    game.dragX = x; game.dragY = y
                    val pulled = kotlin.math.hypot((x - game.pullFromX).toDouble(), (y - game.pullFromY).toDouble()).toFloat()
                    if (game.armed && pulled > PULL_THRESHOLD) {
                        game.torn++
                        // The volumes are stripped from the top down, so the one that just came off
                        // is whichever was highest a moment ago - that is where the leaves scatter.
                        val burst = scenery.bushClumpCenter(cells[game.cell], FarmScenery.BUSH_CLUMPS - game.torn)
                        game.burstAt = now; game.burstX = burst.x; game.burstY = burst.y
                        game.armed = false; game.checkDone(now)
                    } else if (!game.armed && pulled < 10f) game.armed = true
                } else if (game.dragAnchor >= 0) {
                    game.dragX = x; game.dragY = y
                    val cell = cells[game.cell]; val item = game.rubble[game.dragAnchor]
                    val ax = cell.left + item.fx * cell.width(); val ay = cell.top + item.fy * cell.height()
                    if (kotlin.math.hypot((x - ax).toDouble(), (y - ay).toDouble()) > PULL_THRESHOLD) {
                        game.plucked[game.dragAnchor] = true
                        game.burstAt = now; game.burstX = ax; game.burstY = ay
                        game.dragAnchor = -1; game.checkDone(now)
                    }
                }
                invalidate(); ensureDebrisTicking(); true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val game = debrisGame ?: return false
                game.lastAngle = Float.NaN; game.dragAnchor = -1; game.pulling = false; invalidate(); true
            }
            else -> debrisGame != null
        }
    }
    private val sprites = FarmSprites(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scenery = FarmScenery(sprites)
    // The meadow is baked once per region into a single bitmap laid out in world coordinates (see
    // FarmSprites.bakeMeadow) and stamped back with one drawBitmap. Nothing here is ever repainted:
    // the ground is static, so following the camera with a screen-sized cache - which is what this
    // used to do - only bought a 48 MB texture upload several times a second and the stutter that
    // came with it. Nearest-neighbour on purpose: these are art pixels, they must stay square.
    private class Meadow(val columnStart: Int, val rowStart: Int, val columns: Int, val rows: Int,
                        val bed: Bitmap, val tufts: Bitmap, val mask: FarmSprites.TuftMask)
    private val meadows = mutableMapOf<FarmRegion, Meadow>()
    private val meadowsBaking = mutableSetOf<FarmRegion>()
    private val meadowPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    // Scratch rectangles for the per-cell draws below. Every visible cell used to allocate two of
    // these per frame, and at 120 Hz over a whole parcel that is pure garbage for the collector.
    private val soilRect = RectF()
    private val cropRect = RectF()
    /** Built once: asking ICU for a formatter every frame, for every locked parcel, is not free. */
    private val priceFormat = java.text.NumberFormat.getIntegerInstance()
    private var zoom = 1f
    private var cameraX = 0f
    private var cameraY = 0f
    private var multiTouch = false
    /**
     * Set when a one-finger gesture starts on a planting cell of an unlocked parcel. Those cells
     * answer to gestures of their own - pull to harvest, tug to clear, tap to water - and a drag
     * that begins on one is meant for the plant, not for the camera. Panning would otherwise slide
     * the whole farm out from under the finger on the first pixel of movement. Taps and long
     * presses are untouched: the detector still sees every event, only the scroll is refused.
     */
    private var cellHeld = false
    var dismissBubble: (() -> Boolean)? = null
    private var dismissGesture = false
    private val regionScenery = FarmRegionScenery(sprites)
    private val livestockScene = LivestockScene(context, sprites, state.livestock)
    var onLivestockPen: ((LivestockKind, Boolean) -> Unit)? = null
    var region = FarmRegion.HOME
        private set
    var onRegionTap: (() -> Unit)? = null
    var onBushBonus: ((Long) -> Unit)? = null
    // Tucked in the grass just above parcel 1; a coin pile only shows through it - and only gets a
    // tap - while the once-a-day bonus hasn't been claimed yet. FarmLayout places it, which is how
    // the scenery knows to keep its trees and rocks off it.
    private val treasureBush = FarmLayout.treasure.let { RectF(it.left, it.top, it.right, it.bottom) }
    private data class Camera(val zoom: Float, val x: Float, val y: Float)
    private val cameras = mutableMapOf<FarmRegion, Camera>()
    private fun worldWidth(region: FarmRegion) = when (region) {
        FarmRegion.HOME -> FarmLayout.worldWidth
        FarmRegion.GREENHOUSE -> 900f
        else -> 1600f
    }
    private fun worldHeight(region: FarmRegion) = when (region) {
        FarmRegion.HOME -> FarmLayout.worldHeight
        FarmRegion.GREENHOUSE -> 1600f
        else -> 1500f
    }
    private val worldWidth get() = worldWidth(region)
    private val worldHeight get() = worldHeight(region)
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
            cameraY = mapTop() + (height - mapTop() - worldHeight * zoom) / 2
        }
        constrain(); invalidate()
    }
    private fun minimumZoom() = minOf(width / worldWidth, (height - mapTop()).coerceAtLeast(1f) / worldHeight).coerceAtLeast(.01f)
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
            if (cellHeld) return true
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
            // The parcel banner carries its own prev/next arrows, so hopping across the farm never
            // needs a drag or a pinch - only its own index moves, never a shared "current" pointer.
            val bannerParcel = lands.indexOfFirst { y >= it.top - 12 && y <= it.top + 20 && x >= it.left + 45 && x <= it.right - 45 }
            if (bannerParcel >= 0) {
                val banner = lands[bannerParcel]
                when {
                    x < banner.left + 45 + ARROW_ZONE -> focusParcel((bannerParcel - 1).coerceAtLeast(0))
                    x > banner.right - 45 - ARROW_ZONE -> focusParcel((bannerParcel + 1).coerceAtMost(lands.size - 1))
                    else -> onParcel(bannerParcel)
                }
                return true
            }
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
        val mapHeight = worldHeight * zoom
        cameraY = if (mapHeight <= height - mapTop())
            mapTop() + (height - mapTop() - mapHeight) / 2
        else cameraY.coerceIn(height - mapHeight, mapTop())
    }
    /** True where a one-finger drag must stay with the plant rather than move the camera. */
    private fun onPlantingCell(x: Float, y: Float): Boolean {
        if (region != FarmRegion.HOME) return false
        val cell = cells.indexOfFirst { it.contains(x, y) }
        return cell >= 0 && state.parcels[FarmLayout.parcelOf(cell)].unlocked
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
                if (touchClaim == TouchClaim.NONE) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN)
                        cellHeld = onPlantingCell((event.x - cameraX) / zoom, (event.y - cameraY) / zoom)
                    scale.onTouchEvent(event); gestures.onTouchEvent(event)
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cellHeld = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    /**
     * The tile range one region's meadow has to cover. Everywhere but the greenhouse the grass
     * stops at the world bounds, exactly as it did when it was tiled live; the greenhouse is
     * centred and can show margin beyond its own map, so its bake is widened to cover that.
     */
    private fun meadowTiles(region: FarmRegion): IntArray {
        val tile = FarmSprites.GRASS_TILE
        val margin = if (region == FarmRegion.GREENHOUSE) MEADOW_MARGIN_TILES else 0
        return intArrayOf(-margin, -margin,
            (worldWidth(region) / tile).toInt() + 1 + 2 * margin,
            (worldHeight(region) / tile).toInt() + 1 + 2 * margin)
    }
    /**
     * Bakes the current region's meadow if it is missing, off the UI thread - it is a few tens of
     * milliseconds of pure pixel work and it must not land inside a frame. Until it comes back the
     * map simply shows its flat grass backdrop, which is what the very first frames showed anyway.
     */
    private fun ensureMeadow() {
        val target = region
        if (meadows.containsKey(target) || !meadowsBaking.add(target)) return
        val (firstColumn, firstRow, columns, rows) = meadowTiles(target)
        Thread {
            val bed = sprites.bakeMeadowBed(firstColumn, firstRow, columns, rows)
            // A tile wider than the meadow on every side, because the bake itself reaches that far
            // for the tufts that lean in from just outside.
            val mask = sprites.tuftMask(firstColumn - 1, firstRow - 1, columns + 2, rows + 2) { x, y ->
                target != FarmRegion.HOME || scenery.tuftAllowed(x, y)
            }
            val tufts = sprites.bakeMeadowTufts(firstColumn, firstRow, columns, rows, mask)
            post {
                meadowsBaking.remove(target)
                meadows[target] = Meadow(firstColumn, firstRow, columns, rows, bed, tufts, mask)
                invalidate()
            }
        }.apply { name = "farm-meadow-bake"; priority = Thread.MIN_PRIORITY }.start()
    }
    /** Stamps one baked meadow layer back, in world units. Inside the camera transform. */
    private fun drawMeadowLayer(canvas: Canvas, meadow: Meadow, layer: Bitmap, sway: Float) {
        canvas.save()
        canvas.translate(meadow.columnStart * FarmSprites.GRASS_TILE + sway,
            meadow.rowStart * FarmSprites.GRASS_TILE.toFloat())
        canvas.scale(1f / FarmSprites.MEADOW_SCALE, 1f / FarmSprites.MEADOW_SCALE)
        canvas.drawBitmap(layer, 0f, 0f, meadowPaint)
        canvas.restore()
    }
    /**
     * The bed, then the tufts - and the tufts are where the wind lives.
     *
     * Close in, the visible ones are drawn blade by blade so each leans by the gust at its own x,
     * which is the travelling ripple the meadow always had. Far out there are far too many for
     * that, so the baked layer is stamped instead and slid bodily by a world unit or two: at that
     * distance a blade is barely two pixels tall, and a whole field breathing together reads the
     * same as a field rippling - what would show is a field standing perfectly still.
     */
    private fun drawMeadow(canvas: Canvas, visible: RectF) {
        ensureMeadow()
        val meadow = meadows[region] ?: return
        drawMeadowLayer(canvas, meadow, meadow.bed, 0f)
        val tile = FarmSprites.GRASS_TILE
        // A tile of bleed: a tuft stands about half a tile above its own base, so one rooted just
        // off screen still leans into view and has to be drawn.
        val firstColumn = (kotlin.math.floor(visible.left / tile).toInt() - 1).coerceAtLeast(meadow.columnStart)
        val firstRow = (kotlin.math.floor(visible.top / tile).toInt() - 1).coerceAtLeast(meadow.rowStart)
        val lastColumn = ((visible.right / tile).toInt() + 1).coerceAtMost(meadow.columnStart + meadow.columns - 1)
        val lastRow = ((visible.bottom / tile).toInt() + 1).coerceAtMost(meadow.rowStart + meadow.rows - 1)
        val columns = lastColumn - firstColumn + 1; val rows = lastRow - firstRow + 1
        if (columns <= 0 || rows <= 0) return
        if (columns.toLong() * rows <= LIVE_TUFT_TILES)
            sprites.tufts(canvas, firstColumn, firstRow, columns, rows, windTime, meadow.mask)
        else
            drawMeadowLayer(canvas, meadow, meadow.tufts, FarmSprites.gustAt(visible.centerX(), windTime) * 5f)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visible = RectF(-cameraX / zoom, -cameraY / zoom, (width - cameraX) / zoom, (height - cameraY) / zoom)
        // The wind field animates in every region the grass is drawn in, not only on the home map.
        val frameNanos = System.nanoTime()
        val dt = if (lastDecorFrameNanos == 0L) 0f else ((frameNanos - lastDecorFrameNanos) / 1_000_000_000f).coerceIn(0f, .1f)
        lastDecorFrameNanos = frameNanos
        windTime += dt
        ensureDecorTicking()
        canvas.drawColor(Color.rgb(87, 133, 57))
        canvas.save(); canvas.translate(cameraX, cameraY); canvas.scale(zoom, zoom)
        drawMeadow(canvas, visible)
        if (region != FarmRegion.HOME) {
            if (region == FarmRegion.LIVESTOCK) livestockScene.draw(canvas, visible)
            else regionScenery.draw(canvas, region, visible)
            canvas.restore(); return
        }
        // Trails and scattered decorations, live rather than cached: the trail network is already a
        // single world-sized bitmap of its own, and the decorations are a couple hundred stamps of
        // sprites that are cached per variant - both far below the cost of keeping a copy of them.
        scenery.ground(canvas)
        scenery.objects(canvas, visible, windTime)

        val now = System.currentTimeMillis()
        lands.forEachIndexed { index, land ->
            if (land.right + 20 < visible.left || land.left - 20 > visible.right ||
                land.bottom + 20 < visible.top || land.top - 20 > visible.bottom) return@forEachIndexed
            val unlocked = state.parcels[index].unlocked
            val spec = FarmLayout.lands[index]
            paint.color = Color.argb(24, 83, 102, 40)
            canvas.drawRoundRect(land, 22f, 22f, paint)
            scenery.fenceBack(canvas, land, spec.columns, spec.rows)
            for (i in FarmLayout.cells(index)) {
                val cell = cells[i]; val p = state.plots[i]
                if (p.debris != 0) {
                    val game = debrisGame?.takeIf { it.cell == i }
                    if (game != null) drawDebrisGame(canvas, cell, p.debris, game, now)
                    else if (p.debris == 3) scenery.rubble(canvas, cell, windTime)
                    else if (p.debris == 1) scenery.bush(canvas, cell, windTime)
                    else scenery.rock(canvas, cell)
                } else {
                    // Manured ground reads as a darker, richer earth - the bonus has to be visible
                    // from the moment the seed goes in, not only on the harvest total.
                    paint.color = when {
                        p.rich && p.watered -> Color.rgb(58, 39, 25)
                        p.rich -> Color.rgb(104, 68, 37)
                        p.watered -> Color.rgb(94, 65, 44)
                        else -> Color.rgb(151, 104, 60)
                    }
                    soilRect.set(cell.left, cell.top + 34, cell.right, cell.bottom)
                    canvas.drawRoundRect(soilRect, 5f, 5f, paint)
                    paint.color = if (p.rich) Color.rgb(76, 47, 26) else Color.rgb(112, 70, 43)
                    for (r in 0..2) canvas.drawRect(cell.left + 5, cell.top + 40 + r * 9, cell.right - 5, cell.top + 42 + r * 9, paint)
                    val crop = p.crop
                    val grip = harvestGame?.takeIf { it.cell == i && !it.done && now - it.startTime >= HarvestGauge.GRIP_DELAY }
                    if (crop != null) {
                        val shakeX = grip?.let { (kotlin.math.sin((now - it.startTime) / 28.0) * 4).toFloat() } ?: 0f
                        canvas.save(); canvas.translate(shakeX, 0f)
                        cropRect.set(cell.left - 2, cell.top - 9, cell.right + 2, cell.bottom - 5)
                        sprites.crop(canvas, crop, p.variant, p.stage(now), cropRect,
                            growth = p.progress(now), windTime = windTime, established = p.established)
                        canvas.restore()
                        FarmGrowthBar.draw(canvas, cell, p.progress(now), p.rich, p.critical)
                        if (p.progress(now) >= 1) label(canvas, context.getString(R.string.farm_ready), cell.centerX(), cell.top + 8, 10f)
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
            val opening = updateGateOpening(index, gateOpen, dt)
            scenery.fenceFront(canvas, land, spec.columns, opening)
            if (!unlocked) {
                paint.color = Color.argb(145, 30, 49, 27); canvas.drawRect(land, paint)
                val price = priceFormat.format(state.unlockCost(index).toLong())
                label(canvas, context.getString(R.string.farm_locked_price, price), land.centerX(), land.centerY(), 20f)
            }
            paint.color = Color.rgb(61, 76, 40)
            canvas.drawRoundRect(RectF(land.left + 45, land.top - 12, land.right - 45, land.top + 20), 8f, 8f, paint)
            label(canvas, "‹", land.left + 45 + ARROW_ZONE / 2, land.top + 10, 18f)
            label(canvas, context.getString(R.string.farm_parcel_label, index + 1), land.centerX(), land.top + 10, 15f)
            label(canvas, "›", land.right - 45 - ARROW_ZONE / 2, land.top + 10, 18f)
        }
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
        scenery.bush(canvas, treasureBush, windTime)
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
            // Everything here turns about the stone itself, never about the square it stands in.
            val pivot = scenery.rockCenter(cell)
            canvas.save()
            canvas.translate(pivot.x, pivot.y)
            canvas.rotate(game.rotation % 360f)
            canvas.scale(fade, fade)
            canvas.translate(-pivot.x, -pivot.y)
            scenery.rock(canvas, cell, (fade * 255).toInt())
            canvas.restore()
            if (!game.done) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.rgb(255, 210, 90)
                val r = cell.width() * .58f
                canvas.drawArc(RectF(pivot.x - r, pivot.y - r, pivot.x + r, pivot.y + r),
                    -90f, 360f * game.progress(), false, paint)
                paint.style = Paint.Style.FILL
            } else {
                val t = ((now - game.doneAt) / 480f).coerceIn(0f, 1f)
                paint.color = Color.rgb(150, 150, 150)
                game.angles.forEach { a ->
                    val rad = a * kotlin.math.PI / 180
                    val dx = (kotlin.math.cos(rad) * t * 42).toFloat(); val dy = (kotlin.math.sin(rad) * t * 42 - t * 14).toFloat()
                    paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(pivot.x + dx, pivot.y + dy, (3.5f * (1f - t)).coerceAtLeast(0f), paint)
                }
            }
        } else if (game.bush) {
            val fade = if (game.done) (1f - (now - game.doneAt) / 400f).coerceIn(0f, 1f) else 1f
            // The bush leans towards the pull, so the finger has something to fight against.
            val lean = if (game.pulling) .35f else 0f
            canvas.save()
            canvas.translate((game.dragX - game.pullFromX).coerceIn(-20f, 20f) * lean,
                (game.dragY - game.pullFromY).coerceIn(-20f, 20f) * lean)
            // The last stub is yanked down into the ground rather than simply blinking out.
            if (game.done) canvas.scale(fade, fade, cell.centerX(), cell.bottom)
            scenery.bushClumps(canvas, cell, FarmScenery.BUSH_CLUMPS - game.torn, (fade * 255).toInt())
            canvas.restore()
            if (now - game.burstAt < 400) drawLeafBurst(canvas, game, now)
        } else {
            val fade = if (game.done) (1f - (now - game.doneAt) / 400f).coerceIn(0f, 1f) else 1f
            game.rubble.forEachIndexed { i, item ->
                if (game.plucked[i]) return@forEachIndexed
                val ax = cell.left + item.fx * cell.width(); val ay = cell.top + item.fy * cell.height()
                val dragging = game.dragAnchor == i
                canvas.save()
                if (dragging) canvas.translate((game.dragX - game.dragFromX) * .5f, (game.dragY - game.dragFromY) * .5f)
                scenery.rubblePiece(canvas, cell, item, windTime, (fade * 255).toInt())
                canvas.restore()
                if (dragging) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Color.argb(160, 255, 248, 225)
                    canvas.drawLine(ax, ay, game.dragX, game.dragY, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            if (now - game.burstAt < 400) drawLeafBurst(canvas, game, now)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
    }
    /** Leaves thrown off wherever the last piece of greenery was torn away. */
    private fun drawLeafBurst(canvas: Canvas, game: DebrisGame, now: Long) {
        val t = (now - game.burstAt) / 400f
        paint.color = Color.rgb(126, 168, 88)
        game.angles.take(4).forEach { a ->
            val rad = a * kotlin.math.PI / 180
            val dx = (kotlin.math.cos(rad) * t * 30).toFloat(); val dy = (kotlin.math.sin(rad) * t * 30 - t * 20).toFloat()
            paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(game.burstX + dx, game.burstY + dy, (3f * (1f - t)).coerceAtLeast(0f), paint)
        }
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
            val t = ((now - game.doneAt) / HARVEST_FLY_DURATION.toFloat()).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val targetScreen = harvestTarget?.invoke()
            val targetX = targetScreen?.let { (it.x - cameraX) / zoom } ?: cell.centerX()
            val targetY = targetScreen?.let { (it.y - cameraY) / zoom } ?: (cell.top - 68f)
            val startCenterX = cell.centerX()
            val startCenterY = cell.centerY() - 12f
            val lift = kotlin.math.sin(eased * Math.PI).toFloat() * 90f
            val centerX = startCenterX + (targetX - startCenterX) * eased
            val centerY = startCenterY + (targetY - startCenterY) * eased - lift
            val scale = 1f - eased * .42f
            val w = (cell.width() + 4f) * scale
            val h = (cell.height() + 4f) * scale
            val fadeStart = .72f
            val alpha = if (t < fadeStart) 255 else ((1f - (t - fadeStart) / (1f - fadeStart)) * 255).toInt()
                .coerceIn(0, 255)
            game.cropAtStart?.let { crop ->
                paint.alpha = alpha
                val rect = RectF(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
                sprites.crop(canvas, crop, game.variant, 4, rect)
                paint.alpha = 255
            }
            paint.color = if (game.critical) Color.rgb(255, 150, 60) else Color.rgb(255, 224, 91)
            val spread = if (game.critical) 46f else 34f
            game.angles.forEach { a ->
                val rad = a * kotlin.math.PI / 180
                val dx = (kotlin.math.cos(rad) * t * spread).toFloat(); val dy = (kotlin.math.sin(rad) * t * spread - t * 28).toFloat()
                paint.alpha = alpha
                canvas.drawCircle(centerX + dx, centerY - h * .35f + dy, (3f * (1f - t)).coerceAtLeast(0f), paint)
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
}
