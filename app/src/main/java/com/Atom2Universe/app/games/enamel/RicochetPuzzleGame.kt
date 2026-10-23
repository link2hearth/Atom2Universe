package com.Atom2Universe.app.games.enamel

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Règles indépendantes du mode fusion. Dimensions fixes pour comparer une même seed. */
object RicochetPuzzleRules {
    const val VERSION = 1
    const val MAX_SEED = 999_999_999
    const val WIDTH = 10f
    const val HEIGHT = 14f
    const val BASE = HEIGHT - EnamelRules.BASE_DEPTH
    const val RADIUS = .55f
    const val PROJECTILE_RADIUS = .46f
    const val DRAG = 3.8f
    const val MAX_SPEED = 19f
    const val TURN_LIMIT = 10f
    const val WALL_Y = 6f
    const val WALL_HALF_WIDTH = 1.4f
    const val WALL_HALF_HEIGHT = .18f
    const val WALL_SPEED = 1.1f
    const val WALL_MIN_X = 3f
    const val WALL_MAX_X = 7f
}

class PuzzlePuck(val color: Int, var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f) {
    val projectile get() = color == -1
    val radius get() = if (projectile) RicochetPuzzleRules.PROJECTILE_RADIUS else RicochetPuzzleRules.RADIUS
    val mass get() = if (projectile) .6f else radius * radius
    var entered = !projectile
}

data class PuzzlePairEvent(val color: Int, val x: Float, val y: Float)

class RicochetPuzzleGame(seed: Int = 1, hard: Boolean = false) {
    var seed = seed; private set
    var hard = hard; private set
    var shots = 0; private set
    var moving = false; private set
    var wallX = 5f; private set
    var wallVelocity = RicochetPuzzleRules.WALL_SPEED; private set
    private var elapsed = 0f
    private var quiet = 0f
    val pucks = ArrayList<PuzzlePuck>()
    val events = ArrayList<PuzzlePairEvent>()
    val pairsLeft get() = pucks.count { !it.projectile } / 2
    val complete get() = shots > 0 && pairsLeft == 0 && !moving
    val canLaunch get() = !moving && !complete
    val recordKey get() = recordKey(seed, hard)

    companion object {
        fun recordKey(seed: Int, hard: Boolean) = "best_${RicochetPuzzleRules.VERSION}_${if (hard) 1 else 0}_$seed"
    }

    init { reset(seed, hard) }

    fun reset(seed: Int = this.seed, hard: Boolean = this.hard) {
        require(seed in 0..RicochetPuzzleRules.MAX_SEED)
        this.seed = seed; this.hard = hard
        shots = 0; moving = false; elapsed = 0f; quiet = 0f
        pucks.clear(); events.clear()
        val random = Random(seed)
        // Une grille mélangée garantit des pièces séparées et un couloir libre pour le mur.
        val slots = (0 until 5).flatMap { row -> (0 until 5).map { col -> col to row } }.shuffled(random)
        val colors = (0..5).flatMap { listOf(it, it) }.shuffled(random)
        val rows = floatArrayOf(1.2f, 3f, 4.6f, 7.6f, 9.5f)
        colors.forEachIndexed { i, color ->
            val (col, row) = slots[i]
            pucks.add(PuzzlePuck(color, 1f + 2f * col + (random.nextFloat() - .5f) * .3f,
                rows[row] + (random.nextFloat() - .5f) * .3f))
        }
        wallX = 3f + random.nextFloat() * 4f
        wallVelocity = if (random.nextBoolean()) RicochetPuzzleRules.WALL_SPEED else -RicochetPuzzleRules.WALL_SPEED
    }

    fun isLaunchClear(x: Float, y: Float): Boolean {
        val r = RicochetPuzzleRules.PROJECTILE_RADIUS
        return x.isFinite() && y.isFinite() && x in r..10f - r &&
            y in RicochetPuzzleRules.BASE + r..RicochetPuzzleRules.HEIGHT - r &&
            pucks.none { hypot(it.x - x, it.y - y) < it.radius + r + .02f }
    }

    fun shotSpeed(y: Float, dx: Float, dy: Float, power: Float): Float? {
        if (!y.isFinite() || !dx.isFinite() || !dy.isFinite() || !power.isFinite()) return null
        val length = hypot(dx, dy)
        if (!length.isFinite() || length < .001f || dy / length > -.15f || power < .025f) return null
        val minimum = sqrt(2 * RicochetPuzzleRules.DRAG * max(.1f,
            y - RicochetPuzzleRules.BASE + RicochetPuzzleRules.PROJECTILE_RADIUS + .16f)) / (-dy / length)
        if (minimum > RicochetPuzzleRules.MAX_SPEED) return null
        return minimum + power.coerceIn(0f, 1f) * (RicochetPuzzleRules.MAX_SPEED - minimum)
    }

    fun launch(x: Float, y: Float, dx: Float, dy: Float, power: Float): Boolean {
        if (!canLaunch || !isLaunchClear(x, y)) return false
        val speed = shotSpeed(y, dx, dy, power) ?: return false
        val length = hypot(dx, dy)
        pucks.add(PuzzlePuck(-1, x, y, dx / length * speed, dy / length * speed))
        shots++; moving = true; elapsed = 0f; quiet = 0f
        return true
    }

    fun step(dt: Float) {
        if (complete || !dt.isFinite() || dt <= 0f) return
        val frame = dt.coerceAtMost(.05f)
        val fastest = pucks.maxOfOrNull { hypot(it.vx, it.vy) } ?: 0f
        val count = max(ceil(frame * 120).toInt(), ceil((fastest + 2f) * frame / .1f).toInt()).coerceIn(1, 64)
        val h = frame / count
        repeat(count) {
            advanceWall(h)
            if (!moving) return@repeat
            pucks.forEach { p ->
                val speed = hypot(p.vx, p.vy)
                val factor = if (speed > 0) max(0f, speed - RicochetPuzzleRules.DRAG * h) / speed else 0f
                p.vx *= factor; p.vy *= factor
                p.x += p.vx * h; p.y += p.vy * h
                bounds(p)
                if (hard) collideWall(p)
            }
            removePairs()
            repeat(6) {
                for (i in pucks.indices) for (j in i + 1 until pucks.size) collide(pucks[i], pucks[j])
                pucks.forEach { if (hard) collideWall(it); bounds(it) }
                removePairs()
            }
            elapsed += h
            if (pucks.all { hypot(it.vx, it.vy) < .04f }) quiet += h else quiet = 0f
            // Le mur apporte de l'énergie : un tour a une durée bornée, même s'il continue à pousser.
            if (quiet >= .3f || elapsed >= RicochetPuzzleRules.TURN_LIMIT || pairsLeft == 0) {
                pucks.removeAll { it.projectile }
                pucks.forEach { it.vx = 0f; it.vy = 0f }
                moving = false
                return
            }
        }
    }

    private fun advanceWall(dt: Float) {
        if (!hard) return
        val old = wallX
        val proposed = wallX + wallVelocity * dt
        if (proposed < RicochetPuzzleRules.WALL_MIN_X || proposed > RicochetPuzzleRules.WALL_MAX_X) wallVelocity = -wallVelocity
        wallX = (old + wallVelocity * dt).coerceIn(RicochetPuzzleRules.WALL_MIN_X, RicochetPuzzleRules.WALL_MAX_X)
        // Entre deux tirs, le mur fait demi-tour au contact sans jouer à la place du joueur.
        if (!moving && pucks.any { touchesWall(it.x, it.y, it.radius + .003f) }) {
            wallX = old; wallVelocity = -wallVelocity
        }
    }

    fun touchesWall(x: Float, y: Float, radius: Float): Boolean {
        if (!hard) return false
        val px = x.coerceIn(wallX - RicochetPuzzleRules.WALL_HALF_WIDTH, wallX + RicochetPuzzleRules.WALL_HALF_WIDTH)
        val py = y.coerceIn(RicochetPuzzleRules.WALL_Y - RicochetPuzzleRules.WALL_HALF_HEIGHT,
            RicochetPuzzleRules.WALL_Y + RicochetPuzzleRules.WALL_HALF_HEIGHT)
        return hypot(x - px, y - py) < radius
    }

    private fun collideWall(p: PuzzlePuck) {
        val dx = p.x - p.x.coerceIn(wallX - RicochetPuzzleRules.WALL_HALF_WIDTH, wallX + RicochetPuzzleRules.WALL_HALF_WIDTH)
        val dy = p.y - p.y.coerceIn(RicochetPuzzleRules.WALL_Y - RicochetPuzzleRules.WALL_HALF_HEIGHT,
            RicochetPuzzleRules.WALL_Y + RicochetPuzzleRules.WALL_HALF_HEIGHT)
        val d = hypot(dx, dy)
        if (d >= p.radius) return
        var nx: Float; var ny: Float; val penetration: Float
        if (d > .00001f) {
            nx = dx / d; ny = dy / d; penetration = p.radius - d
        } else {
            // Centre dans la barre : sortie par la face la plus proche.
            val side = RicochetPuzzleRules.WALL_HALF_WIDTH - abs(p.x - wallX)
            val vertical = RicochetPuzzleRules.WALL_HALF_HEIGHT - abs(p.y - RicochetPuzzleRules.WALL_Y)
            if (side < vertical) {
                nx = if (p.x < wallX) -1f else 1f; ny = 0f; penetration = p.radius + side
            } else {
                nx = 0f; ny = if (p.y < RicochetPuzzleRules.WALL_Y) -1f else 1f; penetration = p.radius + vertical
            }
        }
        p.x += nx * (penetration + .0001f); p.y += ny * (penetration + .0001f)
        val relative = (p.vx - wallVelocity) * nx + p.vy * ny
        if (relative < 0) { p.vx -= 1.7f * relative * nx; p.vy -= 1.7f * relative * ny }
    }

    private fun bounds(p: PuzzlePuck) {
        val r = p.radius
        if (p.y + r <= RicochetPuzzleRules.BASE + .02f) p.entered = true
        if (p.x < r) { p.x = r; if (p.vx < 0) p.vx *= -.72f }
        if (p.x > 10 - r) { p.x = 10 - r; if (p.vx > 0) p.vx *= -.72f }
        if (p.y < r) { p.y = r; if (p.vy < 0) p.vy *= -.72f }
        val bottom = if (p.entered) RicochetPuzzleRules.BASE else RicochetPuzzleRules.HEIGHT
        if (p.y > bottom - r) { p.y = bottom - r; if (p.vy > 0) p.vy *= -.72f }
    }

    private fun collide(a: PuzzlePuck, b: PuzzlePuck) {
        val dx = b.x - a.x; val dy = b.y - a.y
        val reach = a.radius + b.radius; val d = hypot(dx, dy)
        if (d >= reach) return
        if (a.entered || b.entered) { a.entered = true; b.entered = true }
        val nx = if (d > .00001f) dx / d else 1f
        val ny = if (d > .00001f) dy / d else 0f
        val invA = 1 / a.mass; val invB = 1 / b.mass
        val correction = max(0f, reach - d - .0005f) / (invA + invB)
        a.x -= nx * correction * invA; a.y -= ny * correction * invA
        b.x += nx * correction * invB; b.y += ny * correction * invB
        val closing = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (closing < 0) {
            val impulse = -1.82f * closing / (invA + invB)
            a.vx -= impulse * nx * invA; a.vy -= impulse * ny * invA
            b.vx += impulse * nx * invB; b.vy += impulse * ny * invB
        }
    }

    private fun removePairs() {
        val used = HashSet<PuzzlePuck>()
        for (i in pucks.indices) {
            val a = pucks[i]
            if (a.projectile || a in used) continue
            for (j in i + 1 until pucks.size) {
                val b = pucks[j]
                if (b in used || a.color != b.color || hypot(a.x - b.x, a.y - b.y) > a.radius + b.radius + .005f) continue
                used.add(a); used.add(b)
                events.add(PuzzlePairEvent(a.color, (a.x + b.x) / 2, (a.y + b.y) / 2))
                break
            }
        }
        pucks.removeAll(used)
    }

    fun saveState(): String = listOf(RicochetPuzzleRules.VERSION, seed, if (hard) 1 else 0, shots,
        if (moving) 1 else 0, elapsed, quiet, wallX, wallVelocity,
        pucks.joinToString(";") { "${it.color},${it.x},${it.y},${it.vx},${it.vy},${if (it.entered) 1 else 0}" }).joinToString("|")

    /** La restauration est atomique, y compris la phase du mur et un projectile encore en vol. */
    fun restoreState(state: String): Boolean = try {
        val f = state.split('|'); require(f.size == 10 && f[0] == RicochetPuzzleRules.VERSION.toString())
        val seed = f[1].toInt(); require(seed in 0..RicochetPuzzleRules.MAX_SEED)
        require(f[2] in listOf("0", "1") && f[4] in listOf("0", "1"))
        val shots = f[3].toInt(); require(shots >= 0)
        val values = (5..8).map { f[it].toFloat().also { v -> require(v.isFinite()) } }
        require(values[0] in 0f..RicochetPuzzleRules.TURN_LIMIT + .05f && values[1] in 0f..1f)
        require(values[2] in RicochetPuzzleRules.WALL_MIN_X..RicochetPuzzleRules.WALL_MAX_X)
        require(abs(values[3]) == RicochetPuzzleRules.WALL_SPEED)
        val restored = if (f[9].isEmpty()) emptyList() else f[9].split(';').map { row ->
            val c = row.split(','); require(c.size == 6)
            val color = c[0].toInt(); require(color in -1..5 && c[5] in listOf("0", "1"))
            val v = (1..4).map { c[it].toFloat().also { n -> require(n.isFinite()) } }
            PuzzlePuck(color, v[0], v[1], v[2], v[3]).apply {
                entered = c[5] == "1"
                require(x in radius - .01f..10 - radius + .01f && y in radius - .01f..RicochetPuzzleRules.HEIGHT - radius + .01f)
                require(hypot(vx, vy) < 100f && (projectile || entered))
            }
        }
        require(restored.size <= 13)
        require((0..5).all { color -> restored.count { it.color == color } in listOf(0, 2) })
        val moving = f[4] == "1"
        require(restored.count { it.projectile } == if (moving) 1 else 0)
        require(!moving || shots > 0)
        require(shots > 0 || restored.size == 12)
        require(moving || restored.all { it.vx == 0f && it.vy == 0f })
        this.seed = seed; hard = f[2] == "1"; this.shots = shots; this.moving = moving
        elapsed = values[0]; quiet = values[1]; wallX = values[2]; wallVelocity = values[3]
        pucks.clear(); pucks.addAll(restored); events.clear()
        true
    } catch (_: RuntimeException) { false }
}
