package com.Atom2Universe.app.games.bigger

import kotlin.math.*

val VALUE_ORDER = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)
val SPAWN_VALUES = intArrayOf(1, 2, 4, 8, 16)
const val MAX_VALUE = 1024
const val QUEUE_LENGTH = 3

private val AREA_GROWTH = sqrt(1.75f)
val BALL_SIZE_STEPS: FloatArray by lazy {
    val s = mutableListOf(0.68f, 0.76f)
    while (s.size < VALUE_ORDER.size) s.add((s.last() * AREA_GROWTH * 1000).toInt() / 1000f)
    s.toFloatArray()
}

private const val GRAVITY        = 1600f
private const val WALL_BOUNCE    = 0.55f
private const val FLOOR_BOUNCE   = 0.68f
private const val BALL_BOUNCE    = 0.38f
private const val RESTING_VEL    = 24f
private const val DAMPING        = 0.995f
private const val STATIC_FRIC    = 0.32f
private const val DYN_FRIC       = 0.08f
private const val MERGE_EPS      = 2f
const val  DEFEAT_MARGIN         = 96f

fun getTier(value: Int): Int {
    val i = VALUE_ORDER.indexOf(value)
    return if (i >= 0) i else min(VALUE_ORDER.size - 1, log2(value.toFloat()).roundToInt().coerceAtLeast(0))
}

fun getDiameterMultiplier(value: Int) =
    BALL_SIZE_STEPS[getTier(value).coerceIn(0, BALL_SIZE_STEPS.size - 1)]

class Ball(val id: Int, var value: Int, var x: Float, var y: Float) {
    var vx = 0f; var vy = 0f
    var radius = 0f; var mass = 1f
    var overflowTime = 0f
    val invMass get() = 1f / mass
}

data class Stats(var turns: Int = 0, var merges: Int = 0, var largest: Int = 0)

enum class GameResult { NONE, VICTORY, DEFEAT }

class BiggerGame {
    val balls    = ArrayList<Ball>()
    val queue    = ArrayList<Int>()
    var current  = 0
    var stats    = Stats()
    var result   = GameResult.NONE
    var victoryAchieved = false

    var cellSize    = 40f
    var boardWidth  = 400f
    var boardHeight = 700f

    private var idCounter = 1
    private val contacts      = HashMap<Long, Boolean>()
    private val pendingMerges = ArrayList<Pair<Int, Int>>()

    val isGameOver get() = result != GameResult.NONE

    fun resize(cz: Float, bw: Float, bh: Float) {
        val scale = if (cellSize > 0f) cz / cellSize else 1f
        cellSize = cz; boardWidth = bw; boardHeight = bh
        if (scale != 1f) for (b in balls) {
            b.x *= scale; b.y *= scale
            b.vx *= scale; b.vy *= scale
            b.radius = radius(b.value); b.mass = b.radius * b.radius
        }
    }

    fun reset() {
        balls.clear(); queue.clear()
        current = 0; stats = Stats(); result = GameResult.NONE
        victoryAchieved = false; idCounter = 1
        contacts.clear(); pendingMerges.clear()
        fillQueue(); ensureCurrent()
    }

    private fun fillQueue() { while (queue.size < QUEUE_LENGTH) queue.add(SPAWN_VALUES.random()) }

    fun ensureCurrent() {
        fillQueue()
        if (!VALUE_ORDER.contains(current)) current = if (queue.isNotEmpty()) queue.removeAt(0) else SPAWN_VALUES[0]
        fillQueue()
    }

    fun radius(value: Int): Float {
        val mult = getDiameterMultiplier(value)
        return maxOf(14f, cellSize * mult / 2f)
    }

    fun drop(normalizedX: Float): Ball? {
        if (isGameOver) return null
        ensureCurrent()
        val value = current
        if (!VALUE_ORDER.contains(value)) return null
        val r = radius(value)
        val cx = (normalizedX * boardWidth).coerceIn(r, boardWidth - r)
        current = 0; ensureCurrent()
        stats.turns++; stats.largest = maxOf(stats.largest, value)
        return Ball(idCounter++, value, cx, -DEFEAT_MARGIN * 0.5f).also {
            it.radius = r; it.mass = r * r; balls.add(it)
        }
    }

    fun step(dt: Float) {
        if (isGameOver || balls.isEmpty()) return
        val dtc = dt.coerceAtMost(0.016f)
        // Damping indépendant du framerate : normalisé sur la référence 60fps (16ms) du JS
        val damp = DAMPING.pow(dtc / 0.016f)
        val active = HashSet<Long>(balls.size * balls.size)

        for (b in balls) {
            b.vy += GRAVITY * dtc
            b.vx *= damp; b.vy *= damp
            b.x += b.vx * dtc; b.y += b.vy * dtc

            if (b.x < b.radius) { b.x = b.radius; b.vx = abs(b.vx) * WALL_BOUNCE }
            else if (b.x > boardWidth - b.radius) { b.x = boardWidth - b.radius; b.vx = -abs(b.vx) * WALL_BOUNCE }

            val maxY = boardHeight - b.radius
            if (b.y > maxY) {
                b.y = maxY
                if (abs(b.vy) < 30f) { b.vy = 0f; b.vx *= 1f - STATIC_FRIC }
                else { b.vy = -b.vy * FLOOR_BOUNCE; b.vx *= 1f - DYN_FRIC }
            }
            if (b.y < -DEFEAT_MARGIN) { b.y = -DEFEAT_MARGIN; b.vy = maxOf(b.vy, 0f) }
            b.overflowTime = if (b.y - b.radius < 0f) b.overflowTime + dtc
                             else maxOf(0f, b.overflowTime - dtc * 0.5f)
        }

        val n = balls.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val a = balls[i]; val b = balls[j]
                val dx = b.x - a.x; val dy = b.y - a.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
                val minD = a.radius + b.radius
                val key = if (a.id < b.id) a.id.toLong() shl 32 or b.id.toLong()
                          else b.id.toLong() shl 32 or a.id.toLong()
                val close = dist <= minD + MERGE_EPS

                if (dist < minD) {
                    val ov = minD - dist; val nx = dx / dist; val ny = dy / dist
                    val invS = a.invMass + b.invMass
                    if (invS > 0f) {
                        a.x -= nx * ov * (a.invMass / invS); a.y -= ny * ov * (a.invMass / invS)
                        b.x += nx * ov * (b.invMass / invS); b.y += ny * ov * (b.invMass / invS)
                        val rv = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                        if (rv < 0f) {
                            val spd = abs(rv)
                            val rest = if (spd < RESTING_VEL) 0f else BALL_BOUNCE
                            val imp = (-(1f + rest) * rv) / invS
                            a.vx -= imp * nx * a.invMass; a.vy -= imp * ny * a.invMass
                            b.vx += imp * nx * b.invMass; b.vy += imp * ny * b.invMass
                            // Pas d'averaging global : il tue le glissement tangentiel et colle les billes
                        }
                    }
                }

                if (close) {
                    active.add(key)
                    val already = contacts[key] ?: false
                    if (a.value == b.value && !already) { pendingMerges.add(a.id to b.id); contacts[key] = true }
                    else if (a.value != b.value) contacts[key] = false
                } else contacts.remove(key)
            }
        }
        val toRemove = contacts.keys.filter { it !in active }
        toRemove.forEach { contacts.remove(it) }

        if (pendingMerges.isNotEmpty()) {
            val pairs = pendingMerges.toList(); pendingMerges.clear()
            for ((ia, ib) in pairs) {
                if (isGameOver) break
                val a = balls.find { it.id == ia } ?: continue
                val b = balls.find { it.id == ib } ?: continue
                if (a.value != b.value) continue
                merge(a, b)
            }
        }

        if (!isGameOver && balls.any { it.overflowTime > 0.75f }) result = GameResult.DEFEAT
    }

    private fun merge(a: Ball, b: Ball) {
        val tm = a.mass + b.mass
        val mx = (a.x * a.mass + b.x * b.mass) / tm
        val my = (a.y * a.mass + b.y * b.mass) / tm
        val mvx = (a.vx * a.mass + b.vx * b.mass) / tm
        val mvy = (a.vy * a.mass + b.vy * b.mass) / tm
        val nv = minOf(MAX_VALUE, a.value * 2)
        balls.remove(a); balls.remove(b)
        for (id in intArrayOf(a.id, b.id))
            contacts.keys.removeAll { k -> (k ushr 32).toInt() == id || k.toInt() == id }
        val merged = Ball(idCounter++, nv, mx, my).also {
            it.vx = mvx; it.vy = mvy; it.radius = radius(nv); it.mass = it.radius * it.radius
        }
        balls.add(merged)
        stats.merges++; stats.largest = maxOf(stats.largest, nv)
        if (!victoryAchieved && stats.largest >= MAX_VALUE) {
            victoryAchieved = true; result = GameResult.VICTORY
        }
    }
}
