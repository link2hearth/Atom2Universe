package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonWorld.Lane
import com.Atom2Universe.app.games.glutton.GluttonWorld.Orbit
import com.Atom2Universe.app.games.glutton.GluttonWorld.Ring
import kotlin.math.*
import kotlin.random.Random

/**
 * Fixed-step rules in metres (doubles). Anything smaller than you is swallowed: it vanishes into your
 * mouth and its area goes to your stomach, which you digest at a steady pace, so however much you gulp
 * at once you never balloon in a blink. The journey runs from a crumb in your kitchen to the whole
 * universe; VOYAGE has no clock (your time is the record), FRINGALE a fuse that growing feeds a little
 * less than it burns, so the run always ends and the score is how far you got.
 */
class GluttonGame(val seed: Long = Random.nextLong(), val mode: Mode = Mode.VOYAGE) {
    enum class Mode(val budget: Float) { VOYAGE(0f), FRINGALE(120f) }
    class Item(val id: Int, val prop: Prop, var x: Double, var y: Double, val angle: Float, val radius: Double, val ring: Ring, val flip: Boolean = false) {
        var active = true
        var vx = 0.0; var vy = 0.0
        var heading = 0.0; var clock = 0f; var stunned = 0f
        /** Seconds before a knocked-loose item can be swallowed again. */
        var cooldown = 0f
        /** Seconds a chaser keeps its distance after a bump, so it never pins you down. */
        var rest = 0f
        var alert = false
        var lane: Lane? = null; var laneAt = 0.0; var laneDirection = 1.0
        var box: DoubleArray? = null
        var orbit: Orbit? = null; var orbitParent: Item? = null
        /** Your house, the Earth, the Milky Way: one big thing standing for every ring inside it. */
        var cover = false
        internal var stamp = 0
        internal var filedX = 0.0; internal var filedY = 0.0
        val moves get() = prop.moves
    }
    /** What was swallowed last, so a bump can make you spit a little of it back out. */
    class Bite(val item: Item, val gain: Double)
    enum class Effect { EAT, KNOCK, BURST, BONUS, MILESTONE, RING, FINISH }
    class Event(val effect: Effect, val x: Double, val y: Double, val item: Item? = null, val value: Int = 0)
    /** The live things of one ring: still ones in a grid, movers in a list. */
    class Layer(val ring: Ring) {
        internal val grid = GluttonGrid<Item>(ring.half, 64)
        val statics = ArrayList<Item>()
        val movers = ArrayList<Item>()
    }

    val world = GluttonWorld(seed)
    val layers = arrayOfNulls<Layer>(world.rings.size)
    val covers = ArrayList<Item>(3)
    /** Rings hidden under a cover that has been reached: not drawn, nothing in them any more. */
    val covered = BooleanArray(world.rings.size)
    val recent = ArrayDeque<Bite>(MAX_RECENT + 1)
    /** Spat-out things flying back to the ground. */
    val loose = ArrayList<Item>()
    val events = ArrayList<Event>(24)
    var x = world.startX; private set
    var y = world.startY; private set
    var vx = 0.0; private set
    var vy = 0.0; private set
    var spin = 0.0; private set
    var area = START_RADIUS * START_RADIUS; private set
    /** Swallowed area not digested yet. */
    var stomach = 0.0; private set
    var elapsed = 0f; private set
    var remaining = mode.budget; private set
    var bonusTime = 0f; private set
    var eaten = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var charge = 1f; private set
    var burst = 0f; private set
    var invulnerable = 0f; private set
    var finished = false; private set
    /** The last ring is swallowed: the universe is gone and the journey is over. */
    var cleared = false; private set
    var ringReached = 0; private set
    var ring: Ring = world.rings[0]; private set
    val radius get() = sqrt(area)
    val diameterMetres get() = radius * 2.0
    val suctionRadius get() = radius * if (burst > 0f) 2.2 else 1.35
    /** Brisk when tiny (the rooms are vast then), about four body radii per second once huge. */
    val moveSpeed get() = radius * 3.5 + .2 * min(1.0, 1.0 / (radius * 4))
    /** Your house, swallowed or not (null before its suburb is built: never). */
    val house: Item get() = covers.first { it.prop === GluttonCatalog.house }
    val houseEaten get() = covered[0] && !house.active
    private val random = Random(seed xor 0x6A09E667L)
    private var nextId = 0
    private var stamp = 0
    private var inputX = 0.0; private var inputY = 0.0
    private var accumulator = 0.0
    private var comboTime = 0f
    private var nextMilestone = 0
    private var burstEvent = false
    private val laneSpot = DoubleArray(4)
    private val laid = HashSet<Item>()

    init {
        for (ring in world.rings) if (ring.built) addLayer(ring)
        invulnerable = 1f
        nextMilestone = MILESTONE_FIRST
        while (10.0.pow(nextMilestone) <= diameterMetres) nextMilestone++
    }

    private fun addLayer(ring: Ring) {
        val layer = Layer(ring)
        val made = HashMap<GluttonWorld.Placement, Item>()
        fun item(p: GluttonWorld.Placement) = Item(nextId++, p.prop, p.x, p.y, p.angle, p.radius, ring, p.flip).also { made[p] = it }
        for (p in ring.placements) file(layer, item(p))
        if (mode == Mode.FRINGALE) for (p in ring.hourglasses) file(layer, item(p))
        for (p in ring.movers) layer.movers += item(p).also {
            it.lane = p.lane; it.laneAt = p.laneAt; it.box = p.box; it.orbit = p.orbit
            it.laneDirection = if (random.nextBoolean()) 1.0 else -1.0
            it.heading = random.nextDouble() * 2 * PI
        }
        for (it in layer.movers) it.orbit?.parent?.let { parent -> it.orbitParent = made[parent] }
        layers[ring.index] = layer
        ring.cover?.let { prop ->
            covers += Item(nextId++, prop, 0.0, 0.0, 0f, ring.coverRadius, ring).also { it.cover = true; it.active = false }
        }
    }

    private fun file(layer: Layer, item: Item) {
        layer.statics += item; layer.grid.add(item, item.x, item.y, item.radius); item.filedX = item.x; item.filedY = item.y
    }

    /** Test hook: jump straight to a size without eating. */
    fun forceRadius(r: Double) { area = r * r; stomach = 0.0 }
    /** Test hook: move the gobbler (and reach the rings on the way). */
    fun teleport(tx: Double, ty: Double) { x = tx; y = ty; vx = 0.0; vy = 0.0; update(1f / 120) }
    /** Test hook: empties every ring so a test can stage exactly what it needs. */
    fun clearItems() { for (layer in layers) layer?.let { it.statics.clear(); it.movers.clear(); it.grid.clear() }; loose.clear() }
    /** Test hook: adds a thing at its spot. */
    fun add(prop: Prop, x: Double, y: Double, scale: Double = 1.0): Item {
        val ring = world.ringAt(x, y) ?: world.rings.last()
        val layer = layers[ring.index] ?: Layer(ring).also { layers[ring.index] = it }
        val item = Item(nextId++, prop, x, y, 0f, prop.radius * scale, ring)
        if (prop.moves) { layer.movers += item; item.box = doubleArrayOf(x - 50 * item.radius, y - 50 * item.radius, x + 50 * item.radius, y + 50 * item.radius) }
        else file(layer, item)
        return item
    }
    /** Every live thing (tests and the robot). */
    val items: List<Item> get() = layers.filterNotNull().flatMap { it.statics + it.movers }.filter { it.active } + loose

    fun steer(dx: Double, dy: Double) {
        if (!dx.isFinite() || !dy.isFinite()) { inputX = 0.0; inputY = 0.0; return }
        val length = hypot(dx, dy).coerceAtLeast(1.0); inputX = dx / length; inputY = dy / length
    }
    fun canEat(item: Item) = item.active && item.cooldown <= 0f && (item.prop.bonus || item.radius <= radius * EDIBLE)
    /** Area gained: the item's own area, capped at [CAP] of yours, so anything from about 40 % of your radius is a full bite. */
    fun growth(item: Item): Double {
        if (item.prop.bonus) return 0.0
        val ratio = item.radius / radius
        return min(ratio * ratio * GROWTH, CAP) * area
    }
    fun isBigBite(item: Item) = !item.prop.bonus && item.radius >= radius * BIG_BITE
    fun activateBurst(): Boolean {
        if (finished || charge < .999f || burst > 0f) return false
        charge = 0f; burst = 1f; burstEvent = true; return true
    }
    fun update(seconds: Float) {
        events.clear()
        if (finished || !seconds.isFinite() || seconds <= 0f) return
        accumulator += seconds.coerceAtMost(.1f).toDouble()
        while (accumulator + 1e-9 >= STEP && !finished) { tick(STEP.toFloat()); accumulator -= STEP }
    }

    /** Calls [visit] once for every live thing whose box meets the given one, in every ring that is still around. */
    internal inline fun near(x0: Double, y0: Double, x1: Double, y1: Double, visit: (Item) -> Unit) {
        val s = nextStamp()
        for (layer in layers) {
            if (layer == null) continue
            val h = layer.ring.half
            if (x1 < -h || y1 < -h || x0 > h || y0 > h) continue
            layer.grid.query(x0, y0, x1, y1) { item ->
                if (item.stamp != s && item.active) { item.stamp = s; if (item.x + item.radius >= x0 && item.x - item.radius <= x1 && item.y + item.radius >= y0 && item.y - item.radius <= y1) visit(item) }
            }
            for (i in layer.movers.indices) {
                val item = layer.movers[i]
                if (item.active && item.x + item.radius >= x0 && item.x - item.radius <= x1 && item.y + item.radius >= y0 && item.y - item.radius <= y1) visit(item)
            }
        }
        for (i in loose.indices) { val item = loose[i]; if (item.active && item.x + item.radius >= x0 && item.x - item.radius <= x1 && item.y + item.radius >= y0 && item.y - item.radius <= y1) visit(item) }
    }
    @PublishedApi internal fun nextStamp() = ++stamp

    private fun tick(dt: Float) {
        val r = radius
        if (burstEvent) {
            burstEvent = false; events += Event(Effect.BURST, x, y)
            val reach = suctionRadius + r
            for (layer in layers) layer?.movers?.forEach { if (it.active && hypot(it.x - x, it.y - y) < reach + it.radius) it.stunned = 1.8f }
        }
        elapsed += dt
        if (mode == Mode.FRINGALE) remaining = (remaining - dt).coerceAtLeast(0f)
        burst = (burst - dt).coerceAtLeast(0f)
        invulnerable = (invulnerable - dt).coerceAtLeast(0f); comboTime -= dt
        if (comboTime <= 0f) combo = 0
        charge = (charge + dt * .015f).coerceAtMost(1f)
        if (stomach > 0.0) {
            val digested = min(stomach, area * DIGEST * ring.pace * dt)
            if (mode == Mode.FRINGALE) {
                val bonus = (FRINGALE_GROWTH * digested / area).toFloat()
                remaining = min(remaining + bonus, FRINGALE_MAX); bonusTime += bonus
            }
            area += digested; stomach -= digested
            while (nextMilestone <= MILESTONE_LAST && diameterMetres >= 10.0.pow(nextMilestone)) {
                events += Event(Effect.MILESTONE, x, y, value = nextMilestone); nextMilestone++
            }
        }
        val speed = moveSpeed
        val response = 1.0 - exp(-dt * if (inputX == 0.0 && inputY == 0.0) 19.0 else 14.0)
        vx += (inputX * speed - vx) * response; vy += (inputY * speed - vy) * response
        x += vx * dt; y += vy * dt
        spin += (vx + vy) * dt / (r * 1.4)
        repeat(2) { collideItems(); collideWalls() }
        val now = world.ringAt(x, y) ?: world.rings.last()
        ring = now
        if (now.index > ringReached) reach(now.index)
        // A bump can spit a swallowed mover out of its list: walk the list by hand.
        for (layer in layers) if (layer != null) { var i = 0; while (i < layer.movers.size) { val m = layer.movers[i++]; if (m.active) updateMover(m, dt) } }
        updateLoose(dt)
        swallow(dt)
        if (mode == Mode.FRINGALE && remaining <= 0f) end()
        if (radius >= world.rings.last().to) { cleared = true; end() }
    }

    /** A new ring: build the next one, lay the covers over what is now behind you, forget what is far too small to matter. */
    private fun reach(index: Int) {
        ringReached = index; events += Event(Effect.RING, x, y, value = index)
        for (ring in world.ensure(index + 1)) addLayer(ring)
        for (cover in covers) {
            if (cover.ring.index > index || !laid.add(cover)) continue
            cover.active = true
            for (i in cover.ring.coverFrom until cover.ring.index) { covered[i] = true; layers[i] = null }
            // A smaller cover under the new one goes with everything else it hid.
            for (other in covers) if (other !== cover && other.ring.index in cover.ring.coverFrom until cover.ring.index) other.active = false
        }
        for (i in 0..index - 4) layers[i] = null
    }

    private fun swallow(dt: Float) {
        val r = radius
        val reach = suctionRadius
        val pullSpeed = (if (burst > 0f) 20.0 else 8.0) * r * dt
        near(x - reach * 1.3, y - reach * 1.3, x + reach * 1.3, y + reach * 1.3) { item ->
            if (!canEat(item)) return@near
            val dx = x - item.x; val dy = y - item.y
            val d = sqrt(dx * dx + dy * dy)
            if (d > reach + item.radius * .25) return@near
            val mouth = r * .7 + item.radius * .3
            if (d <= mouth + pullSpeed) consume(item)
            else {
                item.x += dx / d * pullSpeed; item.y += dy / d * pullSpeed
                if (!item.moves && hypot(item.x - item.filedX, item.y - item.filedY) > item.radius * .5) {
                    layers[item.ring.index]?.grid?.add(item, item.x, item.y, item.radius); item.filedX = item.x; item.filedY = item.y
                }
            }
        }
        for (cover in covers) if (cover.active && canEat(cover) && hypot(cover.x - x, cover.y - y) < r + cover.radius * .3) consume(cover)
    }

    private fun collideItems() {
        val r = radius
        val reach = r * 2
        near(x - reach, y - reach, x + reach, y + reach) { item ->
            if (item.prop.bonus || item.cover || canEat(item)) return@near
            val dx = x - item.x; val dy = y - item.y; val contact = r * .8 + item.radius * .78
            val d2 = dx * dx + dy * dy
            if (d2 >= contact * contact) return@near
            val d = sqrt(d2); val nx = if (d > 1e-12 * r) dx / d else 1.0; val ny = if (d > 1e-12 * r) dy / d else 0.0
            x = item.x + nx * contact; y = item.y + ny * contact
            val inward = vx * nx + vy * ny
            if (inward < 0.0) {
                vx -= inward * nx; vy -= inward * ny
                // Head-on pushes roll off to the side you were already leaning to, like a ball would.
                val tangential = -vx * ny + vy * nx
                if (abs(tangential) < moveSpeed * .25 && inputX * nx + inputY * ny < -.6) {
                    val side = if (tangential >= 0.0) 1.0 else -1.0
                    vx += -ny * side * moveSpeed * .3; vy += nx * side * moveSpeed * .3
                }
            }
        }
    }

    /** The house walls stop you until you are round enough to roll over them; the universe has an edge. */
    private fun collideWalls() {
        val outer = world.rings.last()
        val limit = outer.half - radius
        x = x.coerceIn(-limit, limit); y = y.coerceIn(-limit, limit)
        val house = world.rings[0]
        if (covered[0] || radius >= GluttonLayout.WALL_HEIGHT || abs(x) > house.half + radius || abs(y) > house.half + radius) return
        for (wall in house.walls) blockSegment(wall.horizontal, wall.line, wall.from, wall.until, wall.thickness)
    }
    private fun blockSegment(horizontal: Boolean, line: Double, from: Double, until: Double, thickness: Double) {
        val along = (if (horizontal) x else y).coerceIn(from, until)
        val px = if (horizontal) along else line; val py = if (horizontal) line else along
        val dx = x - px; val dy = y - py; val contact = radius * .85 + thickness
        val d2 = dx * dx + dy * dy
        if (d2 >= contact * contact) return
        val d = sqrt(d2)
        val nx: Double; val ny: Double
        if (d > 1e-9) { nx = dx / d; ny = dy / d } else if (horizontal) { nx = 0.0; ny = if (vy > 0.0) -1.0 else 1.0 } else { nx = if (vx > 0.0) -1.0 else 1.0; ny = 0.0 }
        x = px + nx * contact; y = py + ny * contact
        val inward = vx * nx + vy * ny
        if (inward < 0.0) { vx -= inward * nx; vy -= inward * ny }
    }

    private fun updateMover(item: Item, dt: Float) {
        val prop = item.prop
        val dx = x - item.x; val dy = y - item.y; val distance = hypot(dx, dy).coerceAtLeast(1e-12)
        val edible = item.radius <= radius * EDIBLE
        // A thing drawn bigger than life moves faster, but never more than three of its own sizes a second.
        val pace = min(prop.speed * item.radius / prop.radius, max(prop.speed, item.radius * 3))
        item.clock -= dt; item.alert = false
        val orbit = item.orbit
        if (orbit != null) {
            val cx = item.orbitParent?.x ?: orbit.cx; val cy = item.orbitParent?.y ?: orbit.cy
            val a = orbit.phase + 2 * PI * elapsed / orbit.period
            val nx = cx + cos(a) * orbit.radius; val ny = cy + sin(a) * orbit.radius
            item.vx = (nx - item.x) / dt; item.vy = (ny - item.y) / dt; item.x = nx; item.y = ny
        } else if (item.stunned > 0f) {
            item.stunned = (item.stunned - dt).coerceAtLeast(0f); item.vx = 0.0; item.vy = 0.0; return
        } else if (item.lane != null) {
            val lane = item.lane!!
            item.laneAt += item.laneDirection * pace * dt
            if (lane.closed) item.laneAt = ((item.laneAt % lane.length) + lane.length) % lane.length
            else if (item.laneAt < 0.0 || item.laneAt > lane.length) { item.laneDirection = -item.laneDirection; item.laneAt = item.laneAt.coerceIn(0.0, lane.length) }
            lane.at(item.laneAt, laneSpot)
            // Keep to the right-hand side of the road.
            val side = item.radius * .6 * item.laneDirection
            val nx = laneSpot[0] - laneSpot[3] * side; val ny = laneSpot[1] + laneSpot[2] * side
            item.vx = (nx - item.x) / dt; item.vy = (ny - item.y) / dt; item.x = nx; item.y = ny
        } else {
            var speed: Double
            if (item.rest > 0f) {
                item.rest -= dt; item.heading = atan2(-dy, -dx); speed = pace * .7
            } else if (prop.motion == Motion.CHASE && !edible && distance < radius * 5 + item.radius * 5) {
                item.heading = atan2(dy, dx); speed = min(pace, moveSpeed * .85); item.alert = true
            } else {
                if (item.clock <= 0f) { item.heading = random.nextDouble() * 2 * PI; item.clock = .8f + random.nextFloat() * 1.8f }
                speed = pace * .3
            }
            val ease = 1.0 - exp(-dt * 6.0)
            item.vx += (cos(item.heading) * speed - item.vx) * ease; item.vy += (sin(item.heading) * speed - item.vy) * ease
            item.x += item.vx * dt; item.y += item.vy * dt
            item.box?.let { b ->
                val x0 = b[0] + item.radius; val x1 = b[2] - item.radius; val y0 = b[1] + item.radius; val y1 = b[3] - item.radius
                if (x1 > x0 && (item.x < x0 || item.x > x1)) { item.heading = PI - item.heading; item.vx = -item.vx; item.x = item.x.coerceIn(x0, x1); item.clock = 1f }
                if (y1 > y0 && (item.y < y0 || item.y > y1)) { item.heading = -item.heading; item.vy = -item.vy; item.y = item.y.coerceIn(y0, y1); item.clock = 1f }
            }
        }
        // Planets and moons only shove you aside (they come round again and again); vehicles and chasers knock.
        if (!edible && (prop.motion == Motion.CHASE || prop.motion == Motion.PATROL) && invulnerable <= 0f && distance < radius * .8 + item.radius * .8) knock(item)
    }

    private fun updateLoose(dt: Float) {
        val it = loose.iterator()
        while (it.hasNext()) {
            val item = it.next()
            item.cooldown = (item.cooldown - dt).coerceAtLeast(0f)
            item.x += item.vx * dt; item.y += item.vy * dt
            item.vx *= 1.0 - dt * 4.0; item.vy *= 1.0 - dt * 4.0
            if (!item.active) { it.remove(); continue }
            if (item.cooldown <= 0f) {
                it.remove(); item.vx = 0.0; item.vy = 0.0
                val layer = layers[item.ring.index]
                if (item.moves) layer?.movers?.add(item) else if (layer != null) { layer.grid.add(item, item.x, item.y, item.radius); item.filedX = item.x; item.filedY = item.y }
            }
        }
    }

    /** A bump never costs time: you spit out the last few things you swallowed, and they can be eaten again. */
    private fun knock(by: Item) {
        val r = radius
        val dx = x - by.x; val dy = y - by.y; val d = hypot(dx, dy).coerceAtLeast(1e-12)
        val nx = dx / d; val ny = dy / d
        vx = nx * r * 12 + by.vx * .5; vy = ny * r * 12 + by.vy * .5; x += nx * r * .4; y += ny * r * .4
        combo = 0; comboTime = 0f; invulnerable = 1.4f
        if (by.prop.motion == Motion.CHASE) by.rest = 6f
        repeat(min(KNOCK_LOSS, recent.size)) {
            val lost = recent.removeLast()
            val fromStomach = min(stomach, lost.gain)
            stomach -= fromStomach
            area = max(area - (lost.gain - fromStomach), START_RADIUS * START_RADIUS)
            val item = lost.item
            if (item.cover) return@repeat
            val a = random.nextDouble() * 2 * PI
            item.active = true; item.cooldown = 1.1f
            item.x = x + cos(a) * (r * 1.3 + item.radius); item.y = y + sin(a) * (r * 1.3 + item.radius)
            item.vx = cos(a) * r * 8; item.vy = sin(a) * r * 8
            layers[item.ring.index]?.let { layer -> layer.movers.remove(item) }
            loose += item
        }
        events += Event(Effect.KNOCK, x, y, by)
    }

    private fun consume(item: Item) {
        item.active = false; item.cooldown = 0f
        if (item.prop.bonus) {
            if (mode == Mode.FRINGALE) { remaining = min(remaining + HOURGLASS, FRINGALE_MAX); bonusTime += HOURGLASS }
            charge = 1f; events += Event(Effect.BONUS, item.x, item.y, item); return
        }
        val gain = growth(item); val big = isBigBite(item)
        stomach += gain
        eaten++; combo++; comboTime = 2.35f; bestCombo = max(bestCombo, combo)
        charge = (charge + if (big) .05f else .02f).coerceAtMost(1f)
        events += Event(Effect.EAT, item.x, item.y, item)
        if (recent.size >= MAX_RECENT) recent.removeFirst()
        recent.addLast(Bite(item, gain))
    }
    private fun end() {
        if (finished) return
        finished = true; steer(0.0, 0.0); events += Event(Effect.FINISH, x, y)
    }
    /** Hint target: the closest thing worth a bite, favouring pieces near your own size. */
    fun nearestFood(): Item? {
        var best: Item? = null; var cost = Double.MAX_VALUE
        val reach = radius * 25
        near(x - reach, y - reach, x + reach, y + reach) { item ->
            if (item.moves || item.prop.bonus || !canEat(item)) return@near
            val ratio = item.radius / radius
            if (ratio < .12) return@near
            val distance = hypot(item.x - x, item.y - y) / (ratio + .05)
            if (distance < cost) { cost = distance; best = item }
        }
        for (cover in covers) if (cover.active && canEat(cover)) {
            val distance = hypot(cover.x - x, cover.y - y) / (cover.radius / radius + .05)
            if (distance < cost) { cost = distance; best = cover }
        }
        return best
    }
    companion object {
        const val STEP = 1.0 / 120.0
        const val MAX_RECENT = 16
        const val KNOCK_LOSS = 3
        /** Starting radius in metres (a 3 cm gobbler). */
        const val START_RADIUS = .015
        /** An item must be smaller than this share of your radius to fit in your mouth. */
        const val EDIBLE = .8
        const val BIG_BITE = .55
        const val GROWTH = .45
        const val CAP = .06
        /** Share of your own area you can digest per second: the ceiling of growth, whatever you swallow. */
        const val DIGEST = .3
        /** FRINGALE: seconds earned per unit of ln(area) digested. Digesting flat out (DIGEST × pace) earns
         *  0.45 to 0.75 s per second: growing slows the clock down but never stops it; hourglasses are the savings. */
        const val FRINGALE_GROWTH = 2.5
        const val HOURGLASS = 5f
        /** The hungry clock never banks more than this. */
        const val FRINGALE_MAX = 120f
        /** Milestones at every power of ten of your diameter, from 10 cm (10^-1 m) to 10^25 m. */
        const val MILESTONE_FIRST = -1
        const val MILESTONE_LAST = 25
    }
}
