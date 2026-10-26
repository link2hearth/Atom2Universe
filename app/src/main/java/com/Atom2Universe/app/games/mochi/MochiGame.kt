package com.Atom2Universe.app.games.mochi

import kotlin.math.*
import kotlin.random.Random

/**
 * Monde en unités logiques, Y vers le haut. Aucune dépendance Android.
 *
 * Une seule boucle : tirer, lire l'arc, relâcher. La pression vient des fleurs
 * qui fanent sous Mochi ; la seconde chance vient d'une bulle de rosée visible,
 * gagnée aux fleurs d'or et en ramassant des pêches. Aucun menu en cours de partie.
 */
class MochiGame(val seed: Long = Random.nextLong()) {
    enum class Phase { ATTACHED, FLYING, OVER }
    enum class Danger { THORN, BEE }
    enum class End { FALL, THORN, BEE }
    enum class FlowerKind { NORMAL, SPRING, DRIFT, SANCTUARY }
    enum class Effect { CATCH, PERFECT, SWEET, GOLD, REST, RESCUE, WILT, POP, BOUNCE, BUBBLE }
    enum class Outcome { NONE, LAND, DANGER, FALL }
    data class Flower(val id: Int, val x: Float, val y: Float, var visited: Boolean = false,
        val kind: FlowerKind = FlowerKind.NORMAL, val offset: Float = 0f,
        var life: Float = -1f, var alive: Boolean = true) {
        val row get() = abs(id)
        val bonus get() = id < 0
        val wilts get() = kind != FlowerKind.SANCTUARY
        val travel get() = if (kind == FlowerKind.DRIFT) 22f else 0f
        fun at(time: Float) = x + sin(time * 1.4f + offset) * travel
    }
    data class Sweet(val x: Float, val y: Float, var taken: Boolean = false, val golden: Boolean = false)
    data class Hazard(val x: Float, val y: Float, val kind: Danger, val phase: Float) {
        val radius get() = if (kind == Danger.BEE) 15f else 20f
        val travel get() = if (kind == Danger.BEE) 48f else 0f
        fun at(time: Float) = x + sin(time * 1.8f + phase) * travel
    }
    data class Point(val x: Float, val y: Float)
    data class Spark(val x: Float, val y: Float, val effect: Effect, val points: Int = 0, val bounces: Int = 0)
    /** L'arc complet jusqu'au premier contact, et ce qui l'attend au bout. */
    data class Preview(val points: List<Point>, val outcome: Outcome, val flowerId: Int) {
        companion object { val EMPTY = Preview(emptyList(), Outcome.NONE, 0) }
    }
    private data class Landing(val flower: Flower, val fraction: Float, val error: Float)
    internal data class FlowerContact(val fraction: Float, val error: Float)
    private data class Trace(val motion: MochiFlight.Result, val limit: Float,
        val landing: Landing?, val danger: Hazard?, val fallen: Boolean)

    val flowers = ArrayList<Flower>()
    val sweets = ArrayList<Sweet>()
    val hazards = ArrayList<Hazard>()
    val walls = ArrayList<MochiWall>()
    val events = ArrayList<Spark>()
    var phase = Phase.ATTACHED; private set
    var end = End.FALL; private set
    var x = WIDTH / 2; private set
    var y = START_Y; private set
    var vx = 0f; private set
    var vy = 0f; private set
    var bottom = 0f; private set
    var time = 0f; private set
    var score = 0; private set
    var caught = 0; private set
    var treats = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var perfects = 0; private set
    var attachedId = 0; private set
    var flightBounces = 0; private set
    var bankShots = 0; private set
    var maxHeight = START_Y; private set
    var shots = 0; private set
    /** Une seule bulle portée ; les pêches en préparent une autre. */
    var bubbles = 0; private set
    var peaches = 0; private set
    var bubbleLife = 0f; private set
    var detours = 0; private set
    var sanctuaries = 0; private set
    val inBubble get() = phase == Phase.ATTACHED && attachedId == BUBBLE_ID
    val chapter get() = sanctuaryCountThrough(highestFlower)
    val biome get() = chapter % 3
    val attachedFlower get() = flowers.firstOrNull { it.id == attachedId }
    val catchRadius get() = CATCH_RADIUS
    val launchPower get() = if (attachedFlower?.kind == FlowerKind.SPRING) 1.2f else 1f
    val heightMetres get() = ((maxHeight - START_Y) / 10f).toInt().coerceAtLeast(0)
    /** Temps restant sur la prise actuelle, de 1 à 0 ; négatif quand rien ne presse. */
    val wiltFraction: Float get() {
        if (phase != Phase.ATTACHED) return -1f
        if (inBubble) return (bubbleLife / BUBBLE_TIME).coerceIn(0f, 1f)
        val flower = attachedFlower ?: return -1f
        if (!flower.wilts || flower.life < 0f) return -1f
        return (flower.life / wiltTime(flower.row)).coerceIn(0f, 1f)
    }

    fun catchRadiusFor(flower: Flower) = catchRadius + when {
        flower.kind == FlowerKind.SANCTUARY -> 4f
        flower.bonus -> -2f
        else -> 0f
    }

    private val garden = MochiGarden(seed)
    private var accumulator = 0f
    private var flightTime = 0f
    private var departure = 0
    private var departureY = START_Y
    private var highestFlower = 0
    /** La fleur de départ attend le premier tir ; elle ne fane qu'une fois revisitée. */
    private var frontier = Flower(0, x, y, true)

    init {
        flowers += frontier
        extend()
    }

    /** Le déplacement du doigt est en coordonnées écran : tirer vers le bas lance vers le haut. */
    fun velocityForDrag(dx: Float, dy: Float): Point {
        if (!dx.isFinite() || !dy.isFinite()) return Point(0f, 0f)
        val ux = -dx.coerceIn(-10000f, 10000f) * DRAG_POWER
        val uy = dy.coerceIn(-10000f, 10000f) * DRAG_POWER
        val speed = hypot(ux, uy)
        val factor = if (speed > MAX_SPEED) MAX_SPEED / speed else 1f
        return Point(ux * factor * launchPower, uy * factor * launchPower)
    }

    fun launch(dx: Float, dy: Float): Boolean {
        if (phase != Phase.ATTACHED || !dx.isFinite() || !dy.isFinite()) return false
        val v = velocityForDrag(dx, dy)
        if (hypot(v.x, v.y) < MIN_LAUNCH) return false
        vx = v.x; vy = v.y
        departure = attachedId
        departureY = y
        flightBounces = 0
        flightTime = 0f
        shots++
        phase = Phase.FLYING
        return true
    }

    /** Même intégration et mêmes contacts que le vol, sans modifier le monde. */
    fun preview(dx: Float, dy: Float): Preview {
        if (phase != Phase.ATTACHED || !dx.isFinite() || !dy.isFinite()) return Preview.EMPTY
        val v = velocityForDrag(dx, dy)
        if (hypot(v.x, v.y) < MIN_LAUNCH) return Preview.EMPTY
        var state = MochiFlight.State(x, y, v.x, v.y)
        var previewBottom = bottom
        val points = ArrayList<Point>(PREVIEW_STEPS / PREVIEW_EVERY + 2)
        var outcome = Outcome.NONE
        var flowerId = 0
        for (i in 1..PREVIEW_STEPS) {
            val future = time + i * STEP
            val trace = traceFlight(state, future, attachedId, i * STEP, max(20f, previewBottom - RADIUS))
            val segments = travelled(trace)
            val endpoint = segments.lastOrNull()?.let { Point(it.bx, it.by) } ?: Point(state.x, state.y)
            state = trace.motion.state
            previewBottom = max(previewBottom, min(endpoint.y - 330f, y - 70f))
            if (i % PREVIEW_EVERY == 0) points += endpoint
            when {
                trace.landing != null -> { outcome = Outcome.LAND; flowerId = trace.landing.flower.id }
                trace.danger != null -> outcome = Outcome.DANGER
                trace.fallen -> outcome = Outcome.FALL
            }
            if (outcome != Outcome.NONE) {
                if (points.lastOrNull() != endpoint) points += endpoint
                break
            }
        }
        return Preview(points, outcome, flowerId)
    }

    fun update(seconds: Float) {
        events.clear()
        if (phase == Phase.OVER || !seconds.isFinite() || seconds <= 0) return
        // Une interruption de l'UI ne se transforme pas en chute instantanée à la reprise.
        accumulator += seconds.coerceAtMost(.1f)
        while (accumulator + .000001f >= STEP && phase != Phase.OVER) {
            accumulator -= STEP
            tick()
        }
    }

    /** Accroche à la fleur entière, quelle que soit la direction du vol. */
    private fun landingBetween(ax: Float, ay: Float, bx: Float, by: Float, atTime: Float,
        origin: Int, elapsed: Float, duration: Float = STEP): Landing? {
        return flowers.asSequence().filter {
            it.alive && (it.id != origin || elapsed > .25f)
        }.mapNotNull { flower ->
            flowerContact(flower, ax, ay, bx, by, atTime - duration, atTime, catchRadiusFor(flower))
                ?.let { Landing(flower, it.fraction, it.error) }
        }.minByOrNull { it.fraction }
    }

    /** Contacts dans l'ordre du vol, y compris de part et d'autre d'un ricochet. */
    private fun traceFlight(state: MochiFlight.State, atTime: Float, origin: Int, elapsed: Float,
        floor: Float = max(20f, bottom - RADIUS)): Trace {
        val motion = MochiFlight.step(state, STEP, walls, minBounce = MIN_BOUNCE)
        var limit = 1f
        var landing: Landing? = null
        var danger: Hazard? = null
        var fallen = false
        for (segment in motion.segments) {
            if (segment.startFraction > limit) break
            val span = segment.endFraction - segment.startFraction
            if (span <= 0f) continue
            val segmentEnd = atTime - STEP + segment.endFraction * STEP
            val candidate = landingBetween(segment.ax, segment.ay, segment.bx, segment.by, segmentEnd,
                origin, elapsed - STEP + segment.endFraction * STEP, span * STEP)
            if (candidate != null) {
                val fraction = segment.startFraction + candidate.fraction * span
                if (fraction <= limit) { limit = fraction; landing = candidate; danger = null; fallen = false }
            }
            for (hazard in hazards) {
                val fraction = hazardFraction(hazard, segment, atTime) ?: continue
                val hitTime = segment.startFraction + span * fraction
                if (hitTime <= limit) { limit = hitTime; danger = hazard; landing = null; fallen = false }
            }
            if (segment.by < floor && segment.by < segment.ay) {
                val fraction = segment.startFraction + span * ((segment.ay - floor) / (segment.ay - segment.by)).coerceIn(0f, 1f)
                if (fraction <= limit) { limit = fraction; fallen = true; danger = null; landing = null }
            }
        }
        return Trace(motion, limit, landing, danger, fallen)
    }

    private fun hazardFraction(hazard: Hazard, segment: MochiFlight.Segment, atTime: Float): Float? {
        val ax = segment.ax - hazard.at(atTime - STEP + segment.startFraction * STEP)
        val ay = segment.ay - hazard.y
        val dx = segment.bx - hazard.at(atTime - STEP + segment.endFraction * STEP) - ax
        val dy = segment.by - hazard.y - ay
        val radius = RADIUS + hazard.radius
        val c = ax * ax + ay * ay - radius * radius
        if (c <= 0f) return 0f
        val a = dx * dx + dy * dy
        if (a < .000001f) return null
        val b = 2f * (ax * dx + ay * dy)
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null
        val t = (-b - sqrt(discriminant)) / (2f * a)
        return t.takeIf { it in 0f..1f }
    }

    private fun travelled(trace: Trace): List<MochiFlight.Segment> = trace.motion.segments.mapNotNull { segment ->
        if (segment.startFraction > trace.limit) null
        else if (segment.endFraction <= trace.limit) segment
        else {
            val t = ((trace.limit - segment.startFraction) / (segment.endFraction - segment.startFraction)).coerceIn(0f, 1f)
            segment.copy(bx = segment.ax + (segment.bx - segment.ax) * t,
                by = segment.ay + (segment.by - segment.ay) * t, endFraction = trace.limit)
        }
    }

    private fun tick() {
        time += STEP
        if (phase == Phase.ATTACHED) {
            if (inBubble) {
                bubbleLife -= STEP
                y += BUBBLE_RISE * STEP
                if (bubbleLife <= 0f) {
                    drop()
                    events += Spark(x, y, Effect.POP)
                }
            } else attachedFlower?.let { flower ->
                x = flower.at(time)
                if (flower.wilts && flower.life >= 0f) {
                    flower.life -= STEP
                    if (flower.life <= 0f) {
                        flower.alive = false
                        drop()
                        events += Spark(x, y, Effect.WILT)
                    }
                }
            }
        }
        if (phase != Phase.FLYING) return
        flightTime += STEP
        val trace = traceFlight(MochiFlight.State(x, y, vx, vy), time, departure, flightTime)
        val segments = travelled(trace)
        x = segments.lastOrNull()?.bx ?: x
        y = segments.lastOrNull()?.by ?: y
        vx = trace.motion.state.vx; vy = trace.motion.state.vy
        val terminal = trace.landing != null || trace.danger != null || trace.fallen
        for (hit in trace.motion.hits) if ((if (terminal) hit.fraction < trace.limit - .00001f else hit.fraction <= trace.limit) && hit.speed >= 25f) {
            if (hit.wallId >= 0) flightBounces++
            events += Spark(hit.x - hit.nx * RADIUS, hit.y - hit.ny * RADIUS, Effect.BOUNCE)
        }
        maxHeight = max(maxHeight, segments.maxOfOrNull { max(it.ay, it.by) } ?: y)
        // La caméra garde la prise de départ : un surdosage peut encore se rattraper.
        bottom = max(bottom, min(y - 330f, departureY - 70f))

        for (sweet in sweets) {
            if (!sweet.taken && segments.any { distanceToSegment(sweet.x, sweet.y, it.ax, it.ay, it.bx, it.by) < PICKUP_RADIUS }) {
                sweet.taken = true
                treats++
                val points = if (sweet.golden) GOLD_POINTS else PEACH_POINTS
                score += points
                events += Spark(sweet.x, sweet.y, if (sweet.golden) Effect.GOLD else Effect.SWEET, points)
                if (sweet.golden) gainBubble() else {
                    peaches = min(peaches + 1, PEACHES_PER_BUBBLE)
                    ripenBubble()
                }
            }
        }
        when {
            trace.fallen -> fail(End.FALL)
            trace.danger != null -> fail(if (trace.danger.kind == Danger.BEE) End.BEE else End.THORN, trace.danger)
            trace.landing != null -> land(trace.landing)
        }
        if (phase == Phase.FLYING && y < max(20f, bottom - RADIUS)) fail(End.FALL)
        extend()
        flowers.removeAll { (it.y < bottom - 100 || !it.alive) && it.id != attachedId && it.id != departure }
        sweets.removeAll { it.y < bottom - 100 }
        hazards.removeAll { it.y < bottom - 100 }
        walls.removeAll { it.top < bottom - 100 }
    }

    /** La prise cède : Mochi tombe sans élan, la série est perdue. */
    private fun drop() {
        departure = attachedId
        departureY = y
        flightTime = 0f
        flightBounces = 0
        vx = 0f; vy = -30f
        combo = 0
        phase = Phase.FLYING
    }

    private fun land(landing: Landing) {
        val hit = landing.flower
        x = hit.at(time); y = hit.y
        maxHeight = max(maxHeight, y)
        bottom = max(bottom, y - 330f)
        vx = 0f; vy = 0f
        attachedId = hit.id
        phase = Phase.ATTACHED
        if (hit.life < 0f) hit.life = wiltTime(hit.row)
        if (hit.visited) {
            combo = 0
            return
        }
        hit.visited = true
        caught++
        val perfect = landing.error <= PERFECT_RADIUS && y > departureY + 10f && hit.row >= highestFlower
        combo = if (perfect) min(combo + 1, MAX_COMBO) else 0
        if (perfect) perfects++
        bestCombo = max(bestCombo, combo)
        highestFlower = max(highestFlower, hit.row)
        if (hit.bonus) detours++
        if (flightBounces > 0) bankShots++
        val points = (if (hit.bonus) BONUS_POINTS else LANDING_POINTS) +
            (if (perfect) PERFECT_POINTS * combo else 0) + min(flightBounces, 2) * BANK_POINTS
        score += points
        events += Spark(x, y, if (perfect) Effect.PERFECT else Effect.CATCH, points, flightBounces)
        if (hit.kind == FlowerKind.SANCTUARY) {
            sanctuaries++
            events += Spark(x, y, Effect.REST)
            gainBubble()
        }
    }

    /** Une bulle en main ; au-delà, les pêches restent prêtes pour la suivante. */
    internal fun gainBubble() {
        if (bubbles < 1) {
            bubbles = 1
            events += Spark(x, y, Effect.BUBBLE)
        } else peaches = PEACHES_PER_BUBBLE
    }

    private fun ripenBubble() {
        if (peaches >= PEACHES_PER_BUBBLE && bubbles < 1) {
            peaches = 0
            bubbles = 1
            events += Spark(x, y, Effect.BUBBLE)
        }
    }

    private fun fail(reason: End, hazard: Hazard? = null) {
        if (bubbles > 0) {
            bubbles--
            ripenBubble()
            val floor = max(20f, bottom - RADIUS)
            if (hazard != null) {
                // La bulle se forme à l'écart du danger : relancer ne retouche pas aussitôt l'épine.
                val hx = hazard.at(time)
                var nx = x - hx; var ny = y - hazard.y
                val length = hypot(nx, ny)
                if (length < .001f) { nx = 0f; ny = 1f } else { nx /= length; ny /= length }
                val distance = hazard.radius + RADIUS + BUBBLE_CLEARANCE
                x = hx + nx * distance
                y = hazard.y + ny * distance
            }
            y = max(y, floor + 50f)
            x = x.coerceIn(RADIUS + 6f, WIDTH - RADIUS - 6f)
            for (wall in walls) if (MochiFlight.overlaps(wall, x, y, RADIUS + 2f)) y = wall.top + RADIUS + 4f
            vx = 0f; vy = 0f
            combo = 0
            phase = Phase.ATTACHED
            // Une fleur vivante à portée : la bulle y dépose Mochi plutôt que de flotter dessus.
            val perch = flowers.firstOrNull { it.alive && hypot(it.at(time) - x, it.y - y) < catchRadiusFor(it) + RADIUS }
            if (perch != null) {
                x = perch.at(time); y = perch.y
                attachedId = perch.id
                if (perch.life < 0f) perch.life = wiltTime(perch.row)
            } else {
                attachedId = BUBBLE_ID
                bubbleLife = BUBBLE_TIME
            }
            events += Spark(x, y, Effect.RESCUE)
        } else {
            end = reason
            phase = Phase.OVER
        }
    }

    private fun extend() {
        while (frontier.y < bottom + HEIGHT + 200f) {
            val section = garden.next(frontier, walls, flowers)
            frontier = section.next
            flowers += section.next
            section.branch?.let { flowers += it }
            walls += section.walls
            sweets += section.sweets
            hazards += section.hazards
        }
    }
    companion object {
        /** Intersection continue avec la corolle mobile, également utilisée par le générateur. */
        internal fun flowerContact(flower: Flower, ax: Float, ay: Float, bx: Float, by: Float,
            startTime: Float, endTime: Float, radius: Float): FlowerContact? {
            val rx = ax - flower.at(startTime)
            val ry = ay - flower.y
            val dx = bx - flower.at(endTime) - rx
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val c = rx * rx + ry * ry - radius * radius
            val fraction = if (c <= 0f) 0f else {
                if (lengthSquared < .000001f) return null
                val b = 2f * (rx * dx + ry * dy)
                val discriminant = b * b - 4f * lengthSquared * c
                if (discriminant < 0f) return null
                ((-b - sqrt(discriminant)) / (2f * lengthSquared)).takeIf { it in 0f..1f } ?: return null
            }
            // La précision mesure la direction vers le cœur, pas le bord du cercle touché en premier.
            val error = if (lengthSquared > .000001f) abs(rx * dy - ry * dx) / sqrt(lengthSquared) else hypot(rx, ry)
            return FlowerContact(fraction, error)
        }

        const val WIDTH = 420f
        const val HEIGHT = 700f
        const val START_Y = 110f
        const val RADIUS = 17f
        const val CATCH_RADIUS = 18f
        const val PERFECT_RADIUS = 7f
        const val PICKUP_RADIUS = 26f
        const val GRAVITY = 760f
        const val MAX_SPEED = 650f
        const val MIN_LAUNCH = 65f
        const val DRAG_POWER = 4.6f
        const val STEP = 1f / 120f
        const val PREVIEW_STEPS = 300
        const val PREVIEW_EVERY = 4
        /** Les ronces renvoient toujours Mochi : aucune prise, aucun piège, aucun repos. */
        const val MIN_BOUNCE = 220f
        const val MAX_COMBO = 5
        const val BUBBLE_ID = Int.MIN_VALUE
        const val BUBBLE_TIME = 5f
        const val BUBBLE_RISE = 6f
        const val BUBBLE_CLEARANCE = 34f
        const val PEACHES_PER_BUBBLE = 6
        const val LANDING_POINTS = 10
        const val BONUS_POINTS = 20
        const val PERFECT_POINTS = 10
        const val BANK_POINTS = 10
        const val PEACH_POINTS = 5
        const val GOLD_POINTS = 25

        /** Généreux au départ, plus pressant en altitude, jamais injouable. */
        fun wiltTime(row: Int) = max(3.5f, 7f - row * .06f)

        internal fun restRowAfter(row: Int): Int = when {
            row < 10 -> 10
            row < 22 -> 22
            row < 36 -> 36
            row < 52 -> 52
            row < 70 -> 70
            else -> 90 + (row - 70) / 20 * 20
        }

        internal fun isSanctuaryRow(row: Int) = row > 0 && restRowAfter(row - 1) == row

        private fun sanctuaryCountThrough(row: Int): Int = when {
            row < 10 -> 0
            row < 22 -> 1
            row < 36 -> 2
            row < 52 -> 3
            row < 70 -> 4
            else -> 5 + (row - 70) / 20
        }

        /** Marge au sommet pour arriver à la descente, même sur les grandes diagonales. */
        internal fun routeDuration(from: Flower, to: Flower): Float {
            val dy = to.y - from.y
            return max(sqrt(2f * max(dy, 0f) / GRAVITY) + .24f,
                sqrt(2f * hypot(to.x - from.x, dy) / GRAVITY)).coerceAtLeast(.55f)
        }

        internal fun routeVelocity(from: Flower, to: Flower): Point {
            val duration = routeDuration(from, to)
            return Point((to.x - from.x) / duration,
                (to.y - from.y) / duration + .5f * GRAVITY * duration)
        }

        internal fun routePoint(from: Flower, velocity: Point, t: Float) =
            Point(from.x + velocity.x * t, from.y + velocity.y * t - .5f * GRAVITY * t * t)

        private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax; val dy = by - ay
            val length = dx * dx + dy * dy
            val t = if (length < .0001f) 0f else ((px - ax) * dx + (py - ay) * dy) / length
            return hypot(px - ax - dx * t.coerceIn(0f, 1f), py - ay - dy * t.coerceIn(0f, 1f))
        }
    }
}
