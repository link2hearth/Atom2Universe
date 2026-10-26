package com.Atom2Universe.app.games.mochi

import kotlin.math.*

/** Obstacle plein, en coordonnées du jardin (Y vers le haut). */
data class MochiWall(
    val id: Int,
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
    val material: Material = Material.STONE
) {
    enum class Material(val restitution: Float) { STONE(.72f), RUBBER(.94f) }
}

/**
 * Vol balistique et collisions continues d'un cercle avec des rectangles.
 * Les faces sont résolues analytiquement ; les quarts de cercle des coins par
 * isolation des racines de la distance balistique. Aucun rectangle agrandi
 * ne transforme les coins en obstacles invisibles.
 *
 * Le jeu, le guide et le générateur partagent ce même calcul. Les segments
 * temporels permettent d'arrêter le vol à une fleur ou à une corniche AVANT
 * un contact plus tardif, et de ramasser les fruits le long du véritable arc.
 */
internal object MochiFlight {
    data class State(val x: Float, val y: Float, val vx: Float, val vy: Float)
    data class Segment(val ax: Float, val ay: Float, val bx: Float, val by: Float,
        val startFraction: Float, val endFraction: Float)
    /** x/y désignent le centre de Mochi ; speed est la vitesse normale avant impact. */
    data class Hit(val x: Float, val y: Float, val nx: Float, val ny: Float,
        val wallId: Int, val material: MochiWall.Material, val speed: Float, val fraction: Float)
    data class Result(val state: State, val segments: List<Segment>, val hits: List<Hit>)

    private data class Contact(val time: Double, val nx: Double, val ny: Double,
        val id: Int, val material: MochiWall.Material, val restitution: Double)

    fun overlaps(wall: MochiWall, x: Float, y: Float, radius: Float = 17f): Boolean {
        val dx = x - x.coerceIn(wall.left, wall.right)
        val dy = y - y.coerceIn(wall.bottom, wall.top)
        return dx * dx + dy * dy < radius * radius - .001f
    }

    /**
     * [minBounce] > 0 : tout obstacle renvoie Mochi avec au moins cette vitesse normale,
     * et un sommet plat le pousse de côté. Il ne peut alors ni se poser ni rester coincé.
     */
    fun step(state: State, seconds: Float, walls: List<MochiWall>, gravity: Float = 760f,
        radius: Float = 17f, width: Float = 420f, minBounce: Float = 0f): Result {
        if (seconds <= 0f || !seconds.isFinite() || !gravity.isFinite() ||
            !state.x.isFinite() || !state.y.isFinite() || !state.vx.isFinite() || !state.vy.isFinite()) {
            return Result(state, emptyList(), emptyList())
        }
        val duration = seconds.toDouble()
        val r = radius.toDouble()
        var x = state.x.toDouble().coerceIn(r, width - r)
        var y = state.y.toDouble()
        var vx = state.vx.toDouble()
        var vy = state.vy.toDouble()
        var acceleration = gravity.toDouble()
        var elapsed = 0.0
        val segments = ArrayList<Segment>(3)
        val hits = ArrayList<Hit>(2)

        // Résolution défensive d'un départ déjà pénétrant (arrondi des coordonnées
        // ou outils d'édition). Elle retire seulement la vitesse dirigée dans le mur.
        for (wall in walls) {
            if (wall.right <= wall.left || wall.top <= wall.bottom) continue
            val closestX = x.coerceIn(wall.left.toDouble(), wall.right.toDouble())
            val closestY = y.coerceIn(wall.bottom.toDouble(), wall.top.toDouble())
            val dx = x - closestX; val dy = y - closestY
            val distance = hypot(dx, dy)
            if (distance >= r - .00001) continue
            val nx: Double
            val ny: Double
            if (distance > .000001) {
                nx = dx / distance; ny = dy / distance
                x = closestX + nx * r; y = closestY + ny * r
            } else {
                val depths = doubleArrayOf(x - wall.left, wall.right - x, y - wall.bottom, wall.top - y)
                when (depths.indices.minBy { depths[it] }) {
                    0 -> { nx = -1.0; ny = 0.0; x = wall.left - r }
                    1 -> { nx = 1.0; ny = 0.0; x = wall.right + r }
                    2 -> { nx = 0.0; ny = -1.0; y = wall.bottom - r }
                    else -> { nx = 0.0; ny = 1.0; y = wall.top + r }
                }
            }
            val inward = vx * nx + vy * ny
            if (inward < 0) { vx -= inward * nx; vy -= inward * ny }
        }

        repeat(6) {
            val remaining = duration - elapsed
            if (remaining <= 1e-10) return Result(State(x.toFloat(), y.toFloat(), vx.toFloat(), vy.toFloat()), segments, hits)
            val contact = firstContact(x, y, vx, vy, remaining, acceleration, r, width.toDouble(), walls)
            val dt = contact?.time ?: remaining
            val oldX = x; val oldY = y
            x += vx * dt
            y += vy * dt - .5 * acceleration * dt * dt
            vy -= acceleration * dt
            val startFraction = (elapsed / duration).toFloat()
            elapsed += dt
            segments += Segment(oldX.toFloat(), oldY.toFloat(), x.toFloat(), y.toFloat(),
                startFraction, (elapsed / duration).toFloat())
            if (contact == null) return Result(State(x.toFloat(), y.toFloat(), vx.toFloat(), vy.toFloat()), segments, hits)
            val normalSpeed = vx * contact.nx + vy * contact.ny
            hits += Hit(x.toFloat(), y.toFloat(), contact.nx.toFloat(), contact.ny.toFloat(),
                contact.id, contact.material, max(0.0, -normalSpeed).toFloat(), (elapsed / duration).toFloat())
            if (normalSpeed < 0) {
                vx -= (1.0 + contact.restitution) * normalSpeed * contact.nx
                vy -= (1.0 + contact.restitution) * normalSpeed * contact.ny
            }
            if (minBounce > 0f && contact.id >= 0) {
                val outgoing = vx * contact.nx + vy * contact.ny
                if (outgoing < minBounce) {
                    vx += (minBounce - outgoing) * contact.nx
                    vy += (minBounce - outgoing) * contact.ny
                }
                if (contact.ny > .999 && abs(vx) < 40.0) {
                    val wall = walls.firstOrNull { it.id == contact.id }
                    val side = if (wall == null || x >= (wall.left + wall.right) * .5) 1.0 else -1.0
                    vx = side * 60.0
                }
            } else if (contact.ny > .999 && abs(vy) < 2.0 && acceleration > 0.0) {
                // Un repos infinitésimal ne déclenche pas une infinité de rebonds à t=0.
                vy = 0.0
                acceleration = 0.0
            }
        }
        // Budget atteint dans un angle pathologique : ne jamais avancer à travers
        // une paroi. Le temps résiduel reste sur place, avec une vitesse annulée.
        if (elapsed < duration) {
            segments += Segment(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(),
                (elapsed / duration).toFloat(), 1f)
            vx = 0.0; vy = 0.0
        }
        return Result(State(x.toFloat(), y.toFloat(), vx.toFloat(), vy.toFloat()), segments, hits)
    }

    private fun firstContact(x: Double, y: Double, vx: Double, vy: Double, duration: Double,
        gravity: Double, radius: Double, width: Double, walls: List<MochiWall>): Contact? {
        var first: Contact? = null
        fun candidate(t: Double, nx: Double, ny: Double, id: Int, material: MochiWall.Material,
            restitution: Double = material.restitution.toDouble()) {
            if (t < -1e-9 || t > duration + 1e-9) return
            val time = t.coerceIn(0.0, duration)
            val normalSpeed = vx * nx + (vy - gravity * time) * ny
            if (normalSpeed >= -1e-7 && !(time <= 1e-9 && ny > .999 && gravity > 0.0 && abs(normalSpeed) < 1e-7)) return
            if (first == null || time < first!!.time - 1e-9) first = Contact(time, nx, ny, id, material, restitution)
        }
        if (vx < -1e-9) candidate((radius - x) / vx, 1.0, 0.0, -1, MochiWall.Material.STONE, .8)
        if (vx > 1e-9) candidate((width - radius - x) / vx, -1.0, 0.0, -2, MochiWall.Material.STONE, .8)

        val endX = x + vx * duration
        val endY = y + vy * duration - .5 * gravity * duration * duration
        var maxY = max(y, endY)
        var minY = min(y, endY)
        if (abs(gravity) > 1e-9) {
            val extremum = vy / gravity
            if (extremum in 0.0..duration) {
                val extremumY = y + vy * extremum - .5 * gravity * extremum * extremum
                maxY = max(maxY, extremumY); minY = min(minY, extremumY)
            }
        }
        for (wall in walls) {
            if (wall.right <= wall.left || wall.top <= wall.bottom ||
                max(x, endX) + radius < wall.left || min(x, endX) - radius > wall.right ||
                maxY + radius < wall.bottom || minY - radius > wall.top) continue
            if (abs(vx) > 1e-9) {
                val faceX = if (vx > 0) wall.left - radius else wall.right + radius
                val t = (faceX - x) / vx
                val py = y + vy * t - .5 * gravity * t * t
                if (py >= wall.bottom - 1e-7 && py <= wall.top + 1e-7) {
                    candidate(t, if (vx > 0) -1.0 else 1.0, 0.0, wall.id, wall.material)
                }
            }
            for (side in 0..1) {
                val faceY = if (side == 0) wall.bottom - radius else wall.top + radius
                for (t in quadraticRoots(-.5 * gravity, vy, y - faceY)) {
                    val px = x + vx * t
                    if (px >= wall.left - 1e-7 && px <= wall.right + 1e-7) {
                        candidate(t, 0.0, if (side == 0) -1.0 else 1.0, wall.id, wall.material)
                    }
                }
                if (abs(y - faceY) < 1e-7 && abs(vy) < 1e-7 && side == 1 &&
                    x >= wall.left && x <= wall.right) candidate(0.0, 0.0, 1.0, wall.id, wall.material)
            }

            // Chaque coin est uniquement son quart extérieur, pas le cercle entier.
            for (sideX in 0..1) for (sideY in 0..1) {
                val cx = (if (sideX == 0) wall.left else wall.right).toDouble()
                val cy = (if (sideY == 0) wall.bottom else wall.top).toDouble()
                if (max(x, endX) < cx - radius || min(x, endX) > cx + radius ||
                    maxY < cy - radius || minY > cy + radius) continue
                val limit = min(duration, first?.time ?: duration)
                val t = cornerTime(x - cx, y - cy, vx, vy, gravity, radius, limit) ?: continue
                val dx = x + vx * t - cx
                val dy = y + vy * t - .5 * gravity * t * t - cy
                if ((sideX == 0 && dx > 1e-6) || (sideX == 1 && dx < -1e-6) ||
                    (sideY == 0 && dy > 1e-6) || (sideY == 1 && dy < -1e-6)) continue
                val length = hypot(dx, dy)
                if (length > 1e-9) candidate(t, dx / length, dy / length, wall.id, wall.material)
            }
        }
        return first
    }

    private fun quadraticRoots(a: Double, b: Double, c: Double): DoubleArray {
        if (abs(a) < 1e-12) return if (abs(b) < 1e-12) doubleArrayOf() else doubleArrayOf(-c / b)
        val discriminant = b * b - 4.0 * a * c
        if (discriminant < 0.0) return doubleArrayOf()
        val root = sqrt(discriminant)
        // Cette forme évite la soustraction de deux valeurs presque identiques.
        val q = -.5 * (b + if (b >= 0) root else -root)
        if (abs(q) < 1e-15) return doubleArrayOf(-b / (2.0 * a))
        return doubleArrayOf(q / a, c / q)
    }

    /** Racines réelles, utilisées seulement pour les extrema de la distance au coin. */
    private fun cubicRoots(a: Double, b: Double, c: Double, d: Double): DoubleArray {
        if (abs(a) < 1e-12) return quadraticRoots(b, c, d)
        val aa = b / a; val bb = c / a; val cc = d / a
        val p = bb - aa * aa / 3.0
        val q = 2.0 * aa * aa * aa / 27.0 - aa * bb / 3.0 + cc
        val discriminant = q * q / 4.0 + p * p * p / 27.0
        if (discriminant >= 0.0) {
            val s = sqrt(discriminant)
            val u = Math.cbrt(-q / 2.0 + s)
            val v = Math.cbrt(-q / 2.0 - s)
            return if (discriminant < 1e-12) doubleArrayOf(u + v - aa / 3.0, -(u + v) / 2.0 - aa / 3.0)
                else doubleArrayOf(u + v - aa / 3.0)
        }
        val magnitude = 2.0 * sqrt(-p / 3.0)
        val angle = acos((-q / (2.0 * sqrt(-p * p * p / 27.0))).coerceIn(-1.0, 1.0))
        return DoubleArray(3) { magnitude * cos((angle + 2.0 * PI * it) / 3.0) - aa / 3.0 }
    }

    private fun cornerTime(dx: Double, dy: Double, vx: Double, vy: Double, gravity: Double,
        radius: Double, duration: Double): Double? {
        val a = .25 * gravity * gravity
        val b = -gravity * vy
        val c = vx * vx + vy * vy - gravity * dy
        val d = 2.0 * (dx * vx + dy * vy)
        val e = dx * dx + dy * dy - radius * radius
        fun value(t: Double) = (((a * t + b) * t + c) * t + d) * t + e
        if (e <= 1e-7 && d < -1e-7) return 0.0
        val splits = cubicRoots(4.0 * a, 3.0 * b, 2.0 * c, d)
            .filter { it > 0.0 && it < duration }.sorted() + duration
        var lower = 0.0
        var lowValue = e
        for (upper in splits) {
            val highValue = value(upper)
            if (lowValue > 0.0 && highValue <= 0.0) {
                var lo = lower; var hi = upper
                repeat(30) {
                    val middle = (lo + hi) * .5
                    if (value(middle) > 0.0) lo = middle else hi = middle
                }
                return (lo + hi) * .5
            }
            lower = upper; lowValue = highValue
        }
        return null
    }
}
