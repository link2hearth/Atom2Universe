package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonWorld.Cell
import com.Atom2Universe.app.games.glutton.GluttonWorld.Kind
import kotlin.math.*
import kotlin.random.Random

/**
 * Fixed-step rules in metres. Anything smaller than you is swallowed and its area joins yours; swallowed
 * things stay stuck on your body and bumps knock a few loose. The clock is the only limit: three fixed
 * minutes (CHRONO) or a short fuse that every bite and hourglass feeds (FRINGALE). Score = final size.
 */
class GluttonGame(val seed: Long = Random.nextLong(), val mode: Mode = Mode.CHRONO) {
    enum class Mode(val budget: Float) { CHRONO(180f), FRINGALE(45f) }
    class Item(val id: Int, val prop: Prop, var x: Float, var y: Float, val angle: Float, val radius: Float, val cell: Cell?) {
        var active = true
        var vx = 0f; var vy = 0f
        var heading = 0f; var clock = 0f; var stunned = 0f
        /** Seconds before a knocked-loose item can be swallowed again. */
        var cooldown = 0f
        /** Seconds a chaser keeps its distance after a bump, so it never pins you down. */
        var rest = 0f
        var alert = false
        val moves get() = prop.moves
    }
    class Stuck(val item: Item, val angle: Float, val gain: Float)
    enum class Effect { EAT, KNOCK, BURST, FEVER, BONUS, MILESTONE, RING, FINISH }
    class Event(val effect: Effect, val x: Float, val y: Float, val item: Item? = null, val value: Int = 0)

    val world = GluttonWorld(seed)
    val items = ArrayList<Item>(2600)
    val stuck = ArrayDeque<Stuck>(MAX_STUCK + 1)
    val events = ArrayList<Event>(24)
    var x = world.startX; private set
    var y = world.startY; private set
    var vx = 0f; private set
    var vy = 0f; private set
    var spin = 0f; private set
    var area = START_RADIUS * START_RADIUS; private set
    var elapsed = 0f; private set
    var remaining = mode.budget; private set
    var bonusTime = 0f; private set
    var eaten = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var charge = 1f; private set
    var burst = 0f; private set
    var fever = 0f; private set
    var invulnerable = 0f; private set
    var finished = false; private set
    var ringReached = 0; private set
    var cell: Cell = world.cellAt(x, y)!!; private set
    /** Things still out there to swallow (your house included); when it hits zero the run is won early. */
    var foodLeft = 0; private set
    var cleared = false; private set
    /** Your own house: hidden while you are inside, then just another thing to swallow once you are big enough. */
    val house: Item
    val houseEaten get() = ringReached > 0 && !house.active
    val radius get() = sqrt(area)
    val diameterMetres get() = radius * 2f
    val multiplier get() = (1 + combo / 6).coerceAtMost(5)
    val suctionRadius get() = radius * if (burst > 0f) 2.2f else if (fever > 0f) 1.75f else 1.35f
    /** Brisk when tiny (the rooms are vast then), about four body radii per second once huge: rolling, not racing. */
    val moveSpeed get() = (radius * 3.5f + .2f) * (if (fever > 0f) 1.15f else 1f)
    private val random = Random(seed xor 0x6A09E667L)
    private var nextId = 0
    private var inputX = 0f; private var inputY = 0f
    private var accumulator = 0.0
    private var comboTime = 0f; private var feverAt = 20
    private var nextMilestone = 0
    private var burstEvent = false
    init {
        for (p in world.placements) items += Item(nextId++, p.prop, p.x, p.y, random.nextFloat() * 360f - 180f, p.radius, p.cell)
        for (p in world.movers) items += Item(nextId++, p.prop, p.x, p.y, 0f, p.radius, p.cell).also {
            it.heading = if (p.prop.motion == Motion.PATROL) (if (random.nextBoolean()) 0f else PI.toFloat() / 2f) else random.nextFloat() * 2f * PI.toFloat()
        }
        if (mode == Mode.FRINGALE) for (p in world.hourglasses) items += Item(nextId++, p.prop, p.x, p.y, 0f, p.radius, p.cell)
        val suburb = world.rings[1]
        house = Item(nextId++, GluttonCatalog.house, world.startX, world.startY, 0f, world.rings[0].half * 1.05f,
            suburb.cell(suburb.count / 2, suburb.count / 2)).also { it.active = false; items += it }
        invulnerable = 1f
        foodLeft = items.count { !it.prop.bonus }
        nextMilestone = MILESTONES.indexOfFirst { it > diameterMetres }.let { if (it < 0) MILESTONES.size else it }
    }

    /** Test hook: jump straight to a size without eating. */
    fun forceRadius(r: Float) { area = r * r }
    /** Test hook: how much is left after a test has emptied the world by hand. */
    fun forceFoodLeft(count: Int) { foodLeft = count }
    fun steer(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) { inputX = 0f; inputY = 0f; return }
        val length = hypot(dx, dy).coerceAtLeast(1f); inputX = dx / length; inputY = dy / length
    }
    fun canEat(item: Item) = item.active && item.cooldown <= 0f && (item.prop.bonus || item.radius <= radius * EDIBLE)
    /**
     * Area gained: the item's own area, capped at [CAP] of yours so anything from about 40 % of your
     * radius upwards is a full bite and growth stays a steady climb instead of exploding. Past the size
     * that leaves the house every bite counts less ([DAMP]): a giant sweeps up hundreds of things at once,
     * and without the brake the whole city is gone in two minutes.
     */
    fun growth(item: Item): Float {
        if (item.prop.bonus) return 0f
        val ratio = item.radius / radius
        return min(ratio * ratio * GROWTH, CAP) * area * damping
    }
    val damping get() = (world.rings[1].from / radius).coerceAtMost(1f).pow(DAMP)
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
    private fun tick(dt: Float) {
        val r = radius
        if (burstEvent) {
            burstEvent = false; events += Event(Effect.BURST, x, y)
            val reach = suctionRadius + r
            for (item in items) if (item.active && item.moves && hypot(item.x - x, item.y - y) < reach) item.stunned = 1.8f
        }
        elapsed += dt; remaining = (remaining - dt).coerceAtLeast(0f)
        burst = (burst - dt).coerceAtLeast(0f); fever = (fever - dt).coerceAtLeast(0f)
        invulnerable = (invulnerable - dt).coerceAtLeast(0f); comboTime -= dt
        if (comboTime <= 0f) { combo = 0; feverAt = 20 }
        charge = (charge + dt * .015f).coerceAtMost(1f)
        val response = 1f - exp(-dt * if (inputX == 0f && inputY == 0f) 19f else 14f)
        vx += (inputX * moveSpeed - vx) * response; vy += (inputY * moveSpeed - vy) * response
        x += vx * dt; y += vy * dt
        spin += (vx + vy) * dt / (r * 1.4f)
        repeat(2) { collideItems(); collideWalls() }
        val now = world.cellAt(x, y)
        if (now != null) {
            cell = now
            if (now.ring.index > ringReached) {
                ringReached = now.ring.index; events += Event(Effect.RING, x, y, value = ringReached)
                if (ringReached == 1) house.active = true
            }
        }
        for (item in items) {
            if (!item.active) continue
            if (item.cooldown > 0f) {
                item.cooldown = (item.cooldown - dt).coerceAtLeast(0f)
                item.x += item.vx * dt; item.y += item.vy * dt
                item.vx *= 1f - dt * 4f; item.vy *= 1f - dt * 4f
                continue
            }
            if (item.moves) updateMover(item, dt)
            if (!canEat(item)) continue
            val dx = x - item.x; val dy = y - item.y; val reach = suctionRadius + item.radius * .25f
            if (abs(dx) > reach || abs(dy) > reach) continue
            val d2 = dx * dx + dy * dy
            if (d2 > reach * reach) continue
            val d = sqrt(d2); val mouth = r * .7f + item.radius * .3f
            val pull = (if (burst > 0f) 20f else 8f) * r * dt
            if (d <= mouth + pull) consume(item)
            else { item.x += dx / d * pull; item.y += dy / d * pull }
        }
        if (remaining <= 0f) end()
    }
    private fun collideItems() {
        val r = radius
        for (item in items) {
            // Your house never pushes: its rooms and walls do that job while you are inside, and its roof is just a hill.
            if (!item.active || item.prop.bonus || item === house || canEat(item)) continue
            val dx = x - item.x; val dy = y - item.y; val contact = r * .8f + item.radius * .78f
            if (abs(dx) > contact || abs(dy) > contact) continue
            val d2 = dx * dx + dy * dy
            if (d2 >= contact * contact) continue
            val d = sqrt(d2); val nx = if (d > 1e-6f) dx / d else 1f; val ny = if (d > 1e-6f) dy / d else 0f
            x = item.x + nx * contact; y = item.y + ny * contact
            val inward = vx * nx + vy * ny
            if (inward < 0f) {
                vx -= inward * nx; vy -= inward * ny
                // Head-on pushes roll off to the side you were already leaning to, like a ball would.
                val tangential = -vx * ny + vy * nx
                if (abs(tangential) < moveSpeed * .25f && inputX * nx + inputY * ny < -.6f) {
                    val side = if (tangential >= 0f) 1f else -1f
                    vx += -ny * side * moveSpeed * .3f; vy += nx * side * moveSpeed * .3f
                }
            }
        }
    }
    /** Cliffs and ring boundaries block until you are tall enough to roll over them; stairs and exits always let you through. */
    private fun collideWalls() {
        val here = world.cellAt(x, y)
        if (here == null) {
            val outer = world.rings.last()
            val limit = outer.half - radius * .85f
            x = x.coerceIn(-limit, limit); y = y.coerceIn(-limit, limit); return
        }
        val ring = here.ring; val r = radius; val t = ring.thickness
        for (side in 0..3) {
            val to = world.neighbour(here, side)
            val edge = world.edge(here, side)
            val horizontal = side == 0 || side == 2
            val line = when (side) { 0 -> here.y0; 1 -> here.x1; 2 -> here.y1; else -> here.x0 }
            val from = if (horizontal) here.x0 else here.y0
            val until = if (horizontal) here.x1 else here.y1
            if (to == null) {
                // Boundary of the ring: the way up into the next ring, with two stairways.
                if (r >= world.exitClimb(here)) continue
                var gapFrom = 0f; var gapTo = 0f
                for ((exitSide, offset) in ring.exits) if (exitSide == side) {
                    val centre = -ring.half + offset * ring.side
                    gapFrom = centre - ring.exitHalf; gapTo = centre + ring.exitHalf
                }
                blockEdge(horizontal, line, from, until, gapFrom, gapTo, t)
                continue
            }
            val climb = world.climb(here, to, edge)
            if (r >= climb || edge == null) continue
            if (edge.kind == Kind.CLIFF) blockEdge(horizontal, line, from, until, 0f, 0f, t)
            else {
                val centre = from + edge.offset * ring.cell
                blockEdge(horizontal, line, from, until, centre - edge.width / 2f, centre + edge.width / 2f, t)
            }
        }
    }
    private fun blockEdge(horizontal: Boolean, line: Float, from: Float, until: Float, gapFrom: Float, gapTo: Float, thickness: Float) {
        // A gap narrower than your body is simply wall: no squeezing through dead centre.
        if (gapTo > gapFrom && gapTo - gapFrom >= 2f * (radius * .85f + thickness)) {
            if (gapFrom > from) blockSegment(horizontal, line, from, gapFrom, thickness)
            if (gapTo < until) blockSegment(horizontal, line, gapTo, until, thickness)
        } else blockSegment(horizontal, line, from, until, thickness)
    }
    private fun blockSegment(horizontal: Boolean, line: Float, from: Float, until: Float, thickness: Float) {
        val along = (if (horizontal) x else y).coerceIn(from, until)
        val px = if (horizontal) along else line; val py = if (horizontal) line else along
        val dx = x - px; val dy = y - py; val contact = radius * .85f + thickness
        val d2 = dx * dx + dy * dy
        if (d2 >= contact * contact) return
        val d = sqrt(d2)
        val nx: Float; val ny: Float
        if (d > 1e-6f) { nx = dx / d; ny = dy / d } else if (horizontal) { nx = 0f; ny = if (vy > 0f) -1f else 1f } else { nx = if (vx > 0f) -1f else 1f; ny = 0f }
        x = px + nx * contact; y = py + ny * contact
        val inward = vx * nx + vy * ny
        if (inward < 0f) { vx -= inward * nx; vy -= inward * ny }
    }
    private fun updateMover(item: Item, dt: Float) {
        val prop = item.prop; val home = item.cell ?: return
        val dx = x - item.x; val dy = y - item.y; val distance = hypot(dx, dy).coerceAtLeast(1e-6f)
        val edible = item.radius <= radius * EDIBLE
        item.clock -= dt; item.alert = false
        if (item.stunned > 0f) {
            item.stunned = (item.stunned - dt).coerceAtLeast(0f); item.vx = 0f; item.vy = 0f; return
        }
        var speed: Float
        if (prop.motion == Motion.PATROL) speed = prop.speed
        else {
            if (item.rest > 0f) {
                item.rest -= dt; item.heading = atan2(-dy, -dx); speed = prop.speed * .7f
            } else if (prop.motion == Motion.CHASE && !edible && distance < radius * 5f + item.radius * 5f) {
                item.heading = atan2(dy, dx); speed = min(prop.speed, moveSpeed * .85f); item.alert = true
            } else {
                if (item.clock <= 0f) { item.heading = random.nextFloat() * 2f * PI.toFloat(); item.clock = .8f + random.nextFloat() * 1.8f }
                speed = prop.speed * .3f
            }
        }
        val hx = cos(item.heading); val hy = sin(item.heading)
        val ease = 1f - exp(-dt * 6f)
        item.vx += (hx * speed - item.vx) * ease; item.vy += (hy * speed - item.vy) * ease
        item.x += item.vx * dt; item.y += item.vy * dt
        // Movers keep to their own cell of the maze.
        val margin = home.ring.thickness + item.radius
        val x0 = home.x0 + margin; val x1 = home.x1 - margin; val y0 = home.y0 + margin; val y1 = home.y1 - margin
        if (item.x < x0 || item.x > x1) { item.heading = PI.toFloat() - item.heading; item.vx = -item.vx; item.x = item.x.coerceIn(x0, x1); item.clock = 1f }
        if (item.y < y0 || item.y > y1) { item.heading = -item.heading; item.vy = -item.vy; item.y = item.y.coerceIn(y0, y1); item.clock = 1f }
        if (!edible && (prop.motion == Motion.CHASE || prop.motion == Motion.PATROL) &&
            invulnerable <= 0f && distance < radius * .8f + item.radius * .8f) knock(item)
    }
    /** A bump never costs time: it shakes a few swallowed things loose, and they can be eaten again. */
    private fun knock(by: Item) {
        val r = radius
        val dx = x - by.x; val dy = y - by.y; val d = hypot(dx, dy).coerceAtLeast(1e-6f)
        val nx = dx / d; val ny = dy / d
        vx = nx * r * 12f + by.vx * .5f; vy = ny * r * 12f + by.vy * .5f; x += nx * r * .4f; y += ny * r * .4f
        combo = 0; comboTime = 0f; invulnerable = 1.4f
        if (by.prop.motion == Motion.CHASE) by.rest = 6f
        repeat(min(KNOCK_LOSS, stuck.size)) {
            val lost = stuck.removeLast()
            area = max(area - lost.gain, START_RADIUS * START_RADIUS)
            val item = lost.item; val a = random.nextFloat() * 2f * PI.toFloat()
            item.active = true; item.cooldown = 1.1f; foodLeft++
            item.x = x + cos(a) * (r + item.radius + r * .3f); item.y = y + sin(a) * (r + item.radius + r * .3f)
            item.vx = cos(a) * r * 8f; item.vy = sin(a) * r * 8f
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
        area += gain
        eaten++; foodLeft--; combo++; comboTime = 2.35f; bestCombo = max(bestCombo, combo)
        charge = (charge + if (big) .05f else .02f).coerceAtMost(1f)
        events += Event(Effect.EAT, item.x, item.y, item)
        if (stuck.size >= MAX_STUCK) stuck.removeFirst()
        stuck.addLast(Stuck(item, atan2(item.y - y, item.x - x) - spin, gain))
        if (mode == Mode.FRINGALE) {
            val bonus = if (big) BIG_BITE_TIME else BITE_TIME
            remaining = min(remaining + bonus, FRINGALE_MAX); bonusTime += bonus
        }
        if (combo >= feverAt) { feverAt += 30; fever = 5f; events += Event(Effect.FEVER, x, y) }
        while (nextMilestone < MILESTONES.size && diameterMetres >= MILESTONES[nextMilestone]) {
            nextMilestone++; events += Event(Effect.MILESTONE, x, y, value = nextMilestone - 1)
        }
        if (foodLeft <= 0) { cleared = true; end() }
    }
    private fun end() {
        if (finished) return
        finished = true; steer(0f, 0f); events += Event(Effect.FINISH, x, y)
    }
    /** Hint target: the closest thing worth a bite, favouring pieces near your own size. */
    fun nearestFood(): Item? {
        var best: Item? = null; var cost = Float.MAX_VALUE
        val reach = radius * 40f
        for (item in items) if (!item.moves && !item.prop.bonus && canEat(item)) {
            val ratio = item.radius / radius
            if (ratio < .12f) continue
            val dx = item.x - x; val dy = item.y - y
            if (abs(dx) > reach || abs(dy) > reach) continue
            val distance = hypot(dx, dy) / (ratio + .05f)
            if (distance < cost) { cost = distance; best = item }
        }
        return best
    }
    companion object {
        const val STEP = 1.0 / 120.0
        const val MAX_STUCK = 16
        const val KNOCK_LOSS = 3
        /** Starting radius in metres (a 3 cm gobbler). */
        const val START_RADIUS = .015f
        /** An item must be smaller than this share of your radius to fit in your mouth. */
        const val EDIBLE = .8f
        const val BIG_BITE = .55f
        const val GROWTH = .45f
        const val CAP = .06f
        /** Growth brake past the house-leaving size: gain × (0.6 m / radius)^DAMP. */
        const val DAMP = .3f
        /** FRINGALE: the faster you eat and the bigger the bites, the more time you earn, so a good run snowballs. */
        const val HOURGLASS = 8f
        /** The hungry clock never banks more than this: the pressure stays on however big you get. */
        const val FRINGALE_MAX = 120f
        const val BITE_TIME = .35f
        const val BIG_BITE_TIME = 1f
        val MILESTONES = floatArrayOf(.05f, .1f, .25f, .5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f)
    }
}
