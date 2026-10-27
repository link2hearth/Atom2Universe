package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Family
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Scene
import kotlin.math.*
import kotlin.random.Random

/**
 * Fixed-step rules. Size is continuous: anything smaller than you is swallowed and its area is added
 * to yours; swallowed things stay stuck on your body and bumps knock a few of them loose again.
 * Three scenes chain without a menu; each ends when its landmark is swallowed.
 */
class GluttonGame(val seed: Long = Random.nextLong(), startScene: Int = 0) {
    class Item(val id: Int, val prop: Prop, var x: Float, var y: Float, val angle: Float) {
        var active = true
        var vx = 0f; var vy = 0f
        var heading = 0f; var clock = 0f; var stunned = 0f; var dir = 1f
        /** Seconds before a knocked-loose item can be swallowed again. */
        var cooldown = 0f
        /** Seconds a chaser keeps its distance after a bump, so it never pins you down. */
        var rest = 0f
        var alert = false
        val radius get() = prop.radius
        val moves get() = prop.moves
    }
    class Stuck(val item: Item, val angle: Float, val gain: Float)
    enum class Effect { EAT, KNOCK, BURST, FEVER, TREASURE, PREY, STREAK, MILESTONE, SCENE_CLEAR, FINISH }
    class Event(val effect: Effect, val x: Float, val y: Float, val item: Item? = null, val value: Int = 0, val family: Family? = null)

    val items = ArrayList<Item>(MAX_ITEMS)
    val stuck = ArrayDeque<Stuck>(MAX_STUCK + 1)
    val events = ArrayList<Event>(24)
    val medals = IntArray(3)
    val completionTimes = FloatArray(3)
    var sceneIndex = startScene.coerceIn(0, 2); private set
    val scene: Scene get() = Scene.entries[sceneIndex]
    var x = 0f; private set
    var y = 0f; private set
    var vx = 0f; private set
    var vy = 0f; private set
    var spin = 0f; private set
    var area = 0f; private set
    var sceneStartRadius = 1f; private set
    var elapsed = 0f; private set
    var sceneElapsed = 0f; private set
    var remaining = 0f; private set
    var score = 0; private set
    var eaten = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var treasures = 0; private set
    var totalTreasures = 0; private set
    var charge = 1f; private set
    var burst = 0f; private set
    var fever = 0f; private set
    var invulnerable = 0f; private set
    var finished = false; private set
    var won = false; private set
    var sceneCleared = false; private set
    var streakFamily: Family? = null; private set
    var streakCount = 0; private set
    lateinit var goal: Item; private set
    val radius get() = sqrt(area)
    val diameterMetres get() = radius * 2f / scene.scale
    /** Diameter needed before the landmark fits in your mouth. */
    val targetMetres get() = goal.radius / EDIBLE * 2f / scene.scale
    val targetRadius get() = goal.radius / EDIBLE
    val progress get() = (ln(radius / sceneStartRadius) / ln(targetRadius / sceneStartRadius)).coerceIn(0f, 1f)
    val multiplier get() = (1 + combo / 6).coerceAtMost(5)
    val suctionRadius get() = radius + if (burst > 0f) radius * .9f + 40f else if (fever > 0f) radius * .45f + 16f else radius * .3f + 10f
    /** About eight body radii per second when tiny, three when huge: rolling, not racing. */
    val moveSpeed get() = (90f + radius * 2.6f) * (if (fever > 0f) 1.15f else 1f)
    private var random = Random(seed)
    private var nextId = 0
    private var inputX = 0f; private var inputY = 0f
    private var accumulator = 0.0
    private var comboTime = 0f; private var restockAt = 6f; private var feverAt = 20
    private var nextMilestone = 0
    private var burstEvent = false
    private var lanesX = FloatArray(2); private var lanesY = FloatArray(2)
    init { populate() }

    fun steer(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) { inputX = 0f; inputY = 0f; return }
        val length = hypot(dx, dy).coerceAtLeast(1f); inputX = dx / length; inputY = dy / length
    }
    fun canEat(item: Item) = item.active && item.cooldown <= 0f && item.radius <= radius * EDIBLE
    /**
     * Area gained. Anything from about 40 % of your radius upwards is a full bite worth [CAP] of your
     * current area, so growth stays a steady climb instead of exploding; crumbs add a percent or two.
     */
    fun growth(item: Item): Float {
        val ratio = item.radius / radius
        val t = ((ratio - .15f) / .5f).coerceIn(0f, 1f)
        return min(ratio * ratio * GROWTH * (.5f + 1.5f * t), CAP) * area
    }
    fun points(item: Item) = ((5f + 25f * (item.radius / sceneStartRadius).pow(1.2f)) * (sceneIndex + 1)).roundToInt()
    fun isBigBite(item: Item) = item.radius >= radius * BIG_BITE
    fun activateBurst(): Boolean {
        if (finished || sceneCleared || charge < .999f || burst > 0f) return false
        charge = 0f; burst = 1f; burstEvent = true; return true
    }
    /** Called once the clear animation has played; size in metres carries over into the larger scene. */
    fun advanceScene(): Boolean {
        if (!sceneCleared || sceneIndex == 2 || finished) return false
        val metres = diameterMetres
        val carry = min(20f, remaining * .5f)
        sceneIndex++; sceneCleared = false; populate()
        area = (metres / 2f * scene.scale).pow(2); sceneStartRadius = radius
        remaining += carry; events.clear(); accumulator = 0.0
        return true
    }
    fun update(seconds: Float) {
        events.clear()
        if (finished || sceneCleared || !seconds.isFinite() || seconds <= 0f) return
        accumulator += seconds.coerceAtMost(.1f).toDouble()
        while (accumulator + 1e-9 >= STEP && !finished && !sceneCleared) { tick(STEP.toFloat()); accumulator -= STEP }
    }
    private fun tick(dt: Float) {
        if (burstEvent) {
            burstEvent = false; events += Event(Effect.BURST, x, y)
            val reach = suctionRadius + 40f
            for (item in items) if (item.active && item.moves && hypot(item.x - x, item.y - y) < reach) item.stunned = 1.8f
        }
        elapsed += dt; sceneElapsed += dt; remaining = (remaining - dt).coerceAtLeast(0f)
        burst = (burst - dt).coerceAtLeast(0f); fever = (fever - dt).coerceAtLeast(0f)
        invulnerable = (invulnerable - dt).coerceAtLeast(0f); comboTime -= dt
        if (comboTime <= 0f) { combo = 0; feverAt = 20 }
        charge = (charge + dt * .015f).coerceAtMost(1f)
        val response = 1f - exp(-dt * if (inputX == 0f && inputY == 0f) 19f else 14f)
        vx += (inputX * moveSpeed - vx) * response; vy += (inputY * moveSpeed - vy) * response
        x += vx * dt; y += vy * dt
        spin += (vx + vy) * dt / (radius * 1.4f)
        constrain()
        repeat(2) {
            for (item in items) {
                if (!item.active || canEat(item)) continue
                val dx = x - item.x; val dy = y - item.y; val contact = radius * .8f + item.radius * .78f
                val d2 = dx * dx + dy * dy
                if (d2 >= contact * contact) continue
                val d = sqrt(d2); val nx = if (d > .001f) dx / d else 1f; val ny = if (d > .001f) dy / d else 0f
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
        constrain()
        for (item in items) {
            if (!item.active) continue
            if (item.cooldown > 0f) {
                item.cooldown = (item.cooldown - dt).coerceAtLeast(0f)
                item.x += item.vx * dt; item.y += item.vy * dt
                item.vx *= 1f - dt * 4f; item.vy *= 1f - dt * 4f
                keepInside(item); continue
            }
            if (item.moves) updateMover(item, dt)
            if (!canEat(item)) continue
            val dx = x - item.x; val dy = y - item.y; val reach = suctionRadius + item.radius * .25f
            val d2 = dx * dx + dy * dy
            if (d2 > reach * reach) continue
            val d = sqrt(d2); val mouth = radius * .7f + item.radius * .3f
            val pull = (if (burst > 0f) 500f + radius * 2f else 120f + radius) * dt
            if (d <= mouth + pull) {
                consume(item); if (sceneCleared || finished) return
            } else { item.x += dx / d * pull; item.y += dy / d * pull }
        }
        constrain()
        if (sceneElapsed >= restockAt) { restockAt += 6f; restock() }
        if (remaining <= 0f) end(false)
    }
    private fun updateMover(item: Item, dt: Float) {
        val prop = item.prop
        val dx = x - item.x; val dy = y - item.y; val distance = hypot(dx, dy).coerceAtLeast(.001f)
        val edible = item.radius <= radius * EDIBLE
        item.clock -= dt; item.alert = false
        if (item.stunned > 0f) {
            item.stunned = (item.stunned - dt).coerceAtLeast(0f); item.vx = 0f; item.vy = 0f; return
        }
        var speed = 0f; var hx = 0f; var hy = 0f
        when (prop.motion) {
            Motion.PATROL_H -> { hx = item.dir; speed = prop.speed }
            Motion.PATROL_V -> { hy = item.dir; speed = prop.speed }
            else -> {
                val sight = radius * 7f + 160f
                if (item.rest > 0f) {
                    item.rest -= dt; item.heading = atan2(-dy, -dx); speed = prop.speed * .7f
                } else if (prop.motion != Motion.WANDER && edible && distance < sight) {
                    // Prey: run straight away from the mouth with a little zigzag.
                    item.heading = atan2(-dy, -dx) + sin(elapsed * 5f + item.id) * .5f
                    speed = min(prop.speed, moveSpeed * .72f); item.alert = true
                } else if (prop.motion == Motion.CHASE && !edible && distance < radius * 5f + 150f) {
                    // Hunters are a little slower than you, so moving away always works.
                    item.heading = atan2(dy, dx); speed = min(prop.speed, moveSpeed * .85f); item.alert = true
                } else {
                    if (item.clock <= 0f) { item.heading = random.nextFloat() * 2f * PI.toFloat(); item.clock = .8f + random.nextFloat() * 1.8f }
                    speed = prop.speed * .3f
                }
                hx = cos(item.heading); hy = sin(item.heading)
            }
        }
        val ease = 1f - exp(-dt * 6f)
        item.vx += (hx * speed - item.vx) * ease; item.vy += (hy * speed - item.vy) * ease
        item.x += item.vx * dt; item.y += item.vy * dt
        val margin = WALL + item.radius
        if (prop.motion == Motion.PATROL_H && (item.x < margin || item.x > scene.width - margin)) item.dir = -item.dir
        if (prop.motion == Motion.PATROL_V && (item.y < margin || item.y > scene.height - margin)) item.dir = -item.dir
        if (prop.motion != Motion.PATROL_H && prop.motion != Motion.PATROL_V &&
            (item.x < margin || item.x > scene.width - margin || item.y < margin || item.y > scene.height - margin)) {
            item.heading += PI.toFloat() * .75f; item.clock = 1f
        }
        keepInside(item)
        if (!edible && (prop.motion == Motion.CHASE || prop.motion == Motion.PATROL_H || prop.motion == Motion.PATROL_V) &&
            invulnerable <= 0f && distance < radius * .8f + item.radius * .8f) knock(item)
    }
    /** A bump never costs time: it shakes a few swallowed things loose, and they can be eaten again. */
    private fun knock(by: Item) {
        val dx = x - by.x; val dy = y - by.y; val d = hypot(dx, dy).coerceAtLeast(.001f)
        val nx = dx / d; val ny = dy / d
        vx = nx * 380f + by.vx * .5f; vy = ny * 380f + by.vy * .5f; x += nx * 12f; y += ny * 12f
        combo = 0; comboTime = 0f; invulnerable = 1.4f; streakCount = 0
        if (by.prop.motion == Motion.CHASE) by.rest = 6f
        repeat(min(KNOCK_LOSS, stuck.size)) {
            val lost = stuck.removeLast()
            area = max(area - lost.gain, (sceneStartRadius * .8f).pow(2))
            val item = lost.item; val a = random.nextFloat() * 2f * PI.toFloat()
            item.active = true; item.cooldown = 1.1f
            item.x = x + cos(a) * (radius + item.radius + 10f); item.y = y + sin(a) * (radius + item.radius + 10f)
            item.vx = cos(a) * 260f; item.vy = sin(a) * 260f
            keepInside(item)
        }
        events += Event(Effect.KNOCK, x, y, by)
    }
    private fun consume(item: Item) {
        val gain = growth(item); val big = isBigBite(item)
        item.active = false; item.cooldown = 0f; area += gain
        eaten++; combo++; comboTime = 2.35f; bestCombo = max(bestCombo, combo)
        score += points(item) * multiplier * if (big) 2 else 1
        charge = (charge + if (big) .05f else .02f).coerceAtMost(1f)
        events += Event(Effect.EAT, item.x, item.y, item, points(item) * multiplier * if (big) 2 else 1)
        if (item.prop.treasure) {
            treasures++; totalTreasures++; charge = 1f; remaining += 3f
            events += Event(Effect.TREASURE, item.x, item.y, item)
        } else {
            if (stuck.size >= MAX_STUCK) stuck.removeFirst()
            stuck.addLast(Stuck(item, atan2(item.y - y, item.x - x) - spin, gain))
        }
        if (item.moves) { remaining += 2f; events += Event(Effect.PREY, item.x, item.y, item) }
        if (item.prop.family == streakFamily) streakCount++ else { streakFamily = item.prop.family; streakCount = 1 }
        if (streakCount > 0 && streakCount % STREAK == 0) {
            score += 40 * (sceneIndex + 1) * multiplier
            events += Event(Effect.STREAK, x, y, family = item.prop.family, value = streakCount)
        }
        if (combo >= feverAt) { feverAt += 30; fever = 5f; events += Event(Effect.FEVER, x, y) }
        while (nextMilestone < MILESTONES.size && diameterMetres >= MILESTONES[nextMilestone]) {
            nextMilestone++; events += Event(Effect.MILESTONE, x, y, value = nextMilestone - 1)
        }
        if (item.prop.goal) {
            medals[sceneIndex] = 1 + (if (treasures == 3) 1 else 0) + (if (sceneElapsed <= scene.par) 1 else 0)
            completionTimes[sceneIndex] = sceneElapsed
            sceneCleared = true; steer(0f, 0f); vx = 0f; vy = 0f
            events += Event(Effect.SCENE_CLEAR, x, y, value = medals[sceneIndex]); if (sceneIndex == 2) end(true)
        }
    }
    private fun end(victory: Boolean) {
        if (finished) return
        finished = true; won = victory; steer(0f, 0f); events += Event(Effect.FINISH, x, y)
    }
    private fun constrain() {
        val margin = radius * .8f + WALL
        x = x.coerceIn(margin, scene.width - margin); y = y.coerceIn(margin, scene.height - margin)
    }
    private fun keepInside(item: Item) {
        val margin = WALL + item.radius * .6f
        item.x = item.x.coerceIn(margin, scene.width - margin); item.y = item.y.coerceIn(margin, scene.height - margin)
    }
    /** Hint target: the closest thing worth a bite, favouring pieces near your own size. */
    fun nearestFood(): Item? {
        var best: Item? = null; var cost = Float.MAX_VALUE
        for (item in items) if (canEat(item) && !item.moves) {
            val ratio = item.radius / radius
            if (ratio < .12f) continue
            val distance = hypot(item.x - x, item.y - y) / (ratio + .05f)
            if (distance < cost) { cost = distance; best = item }
        }
        return best ?: goal.takeIf { canEat(it) }
    }
    private fun populate() {
        random = Random(seed xor (sceneIndex.toLong() * 0x5DEECE66DL)); items.clear(); stuck.clear(); nextId = 0
        x = scene.width * .2f; y = scene.height * .8f; vx = 0f; vy = 0f; spin = 0f
        area = (scene.startMetres / 2f * scene.scale).pow(2); sceneStartRadius = radius
        steer(0f, 0f); remaining = scene.budget; sceneElapsed = 0f; restockAt = 6f
        treasures = 0; burst = 0f; fever = 0f; combo = 0; comboTime = 0f; feverAt = 20; charge = 1f
        streakFamily = null; streakCount = 0
        invulnerable = 1f; burstEvent = false
        nextMilestone = MILESTONES.indexOfFirst { it > scene.startMetres }.let { if (it < 0) MILESTONES.size else it }
        lanesX = floatArrayOf(scene.width * .33f, scene.width * .67f); lanesY = floatArrayOf(scene.height * .3f, scene.height * .7f)
        val props = GluttonCatalog.forScene(scene).sortedWith(compareBy({ !it.goal }, { !it.treasure }, { -it.radius }))
        for (prop in props) repeat(prop.count) { i -> place(prop, i) }
        goal = items.first { it.prop.goal }
        // A handful of snacks around the start so the very first second already feeds you.
        val snacks = GluttonCatalog.forScene(scene).filter { !it.moves && it.radius in radius * .2f..radius * .6f }
        repeat(12) { i ->
            val a = i * .9f; val r = radius * 2.2f + i * 7f
            items += Item(nextId++, snacks[i % snacks.size], x + cos(a) * r, y + sin(a) * r, i * 37f)
        }
    }
    private fun place(prop: Prop, index: Int): Boolean {
        if (prop.motion == Motion.PATROL_H || prop.motion == Motion.PATROL_V) {
            val horizontal = prop.motion == Motion.PATROL_H
            val lane = (if (horizontal) lanesY else lanesX)[index % 2]
            val along = WALL + prop.radius + random.nextFloat() * ((if (horizontal) scene.width else scene.height) - 2 * (WALL + prop.radius))
            val item = Item(nextId++, prop, if (horizontal) along else lane, if (horizontal) lane else along, 0f)
            item.dir = if (random.nextBoolean()) 1f else -1f; items += item
            return true
        }
        val margin = WALL + prop.radius + 12f
        val cellW = scene.width / 3f; val cellH = scene.height / 3f
        repeat(120) { attempt ->
            val px: Float; val py: Float
            if (prop.where == GluttonCatalog.ANY || attempt >= 45) {
                px = margin + random.nextFloat() * (scene.width - 2 * margin); py = margin + random.nextFloat() * (scene.height - 2 * margin)
            } else {
                val cx = (prop.where % 3 + .5f) * cellW; val cy = (prop.where / 3 + .5f) * cellH
                px = (cx + (random.nextFloat() - .5f) * cellW * 1.1f).coerceIn(margin, scene.width - margin)
                py = (cy + (random.nextFloat() - .5f) * cellH * 1.1f).coerceIn(margin, scene.height - margin)
            }
            if (prop.radius > sceneStartRadius * 1.6f && hypot(px - scene.width * .2f, py - scene.height * .8f) < 260f + prop.radius) return@repeat
            // Lanes stay open so patrols can drive and you always have an escape route.
            if (prop.radius > 14f) {
                val clearance = prop.radius + 60f
                if (abs(px - lanesX[0]) < clearance || abs(px - lanesX[1]) < clearance ||
                    abs(py - lanesY[0]) < clearance || abs(py - lanesY[1]) < clearance) return@repeat
            }
            var free = true
            for (other in items) {
                if (!other.active || other.moves) continue
                val gap = prop.radius + other.radius + if (prop.radius < 10f) 2f else 8f
                if ((px - other.x).pow(2) + (py - other.y).pow(2) < gap * gap) { free = false; break }
            }
            if (free) {
                val angle = if (prop.radius > 40f) random.nextFloat() * 20f - 10f else random.nextFloat() * 180f - 90f
                items += Item(nextId++, prop, px, py, angle); return true
            }
        }
        return false
    }
    /** Keeps a stock of bites near your current size once the small stuff is gone. */
    private fun restock() {
        items.removeAll { candidate -> !candidate.active && stuck.none { it.item === candidate } }
        var useful = 0
        for (item in items) if (canEat(item) && !item.moves && item.radius >= radius * .3f) useful++
        if (useful >= 16) return
        val candidates = GluttonCatalog.forScene(scene).filter { !it.moves && !it.goal && !it.treasure && it.radius in radius * .35f..radius * .75f }
        if (candidates.isEmpty()) return
        repeat(min(12, MAX_ITEMS - items.size)) { i -> place(candidates[random.nextInt(candidates.size)], i) }
    }
    companion object {
        const val WALL = 30f
        const val STEP = 1.0 / 120.0
        const val MAX_ITEMS = 560
        const val MAX_STUCK = 16
        const val KNOCK_LOSS = 3
        const val STREAK = 5
        /** An item must be smaller than this share of your radius to fit in your mouth. */
        const val EDIBLE = .8f
        const val BIG_BITE = .55f
        const val GROWTH = .3f
        const val CAP = .06f
        val MILESTONES = floatArrayOf(.15f, .25f, .5f, 1f, 2f, 4f, 8f, 15f, 30f, 60f, 100f)
    }
}
