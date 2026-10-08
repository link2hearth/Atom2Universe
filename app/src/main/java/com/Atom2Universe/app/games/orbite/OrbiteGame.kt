package com.Atom2Universe.app.games.orbite

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Logique pure du jeu Orbite : un électron tourne en continu autour d'un noyau.
 *
 * Le joueur n'a qu'une action — inverser le sens de rotation [reverse] — pour
 * esquiver les astéroïdes attirés vers le noyau et balayer les quanta d'énergie
 * qui apparaissent sur l'anneau orbital.
 *
 * Les coordonnées sont en pixels, centrées sur le noyau ([cx], [cy]) ; la vue
 * appelle [configure] avec ses dimensions puis [update] à chaque frame.
 */
class OrbiteGame {

    // ── Géométrie du monde (renseignée par la vue) ──────────────────────────
    var cx = 0f; private set
    var cy = 0f; private set
    var orbitR = 0f; private set
    var nucleusR = 0f; private set
    var electronR = 0f; private set
    var asteroidR = 0f; private set
    private var spawnR = 0f
    private var minDim = 0f

    // ── État de l'électron ──────────────────────────────────────────────────
    var angle = -PI.toFloat() / 2f; private set
    var dir = 1; private set
    private var omega = BASE_OMEGA

    // ── État de partie ──────────────────────────────────────────────────────
    var isRunning = false; private set
    var isGameOver = false; private set
    var score = 0; private set
    var best = 0
    var combo = 0; private set
    var elapsedMs = 0L; private set

    // ── Entités ─────────────────────────────────────────────────────────────
    class Asteroid(var angle: Float, var dist: Float, var speed: Float, val radius: Float, val seed: Long)
    class Quantum(val angle: Float, var ageMs: Long, val lifeMs: Long)

    val asteroids = ArrayList<Asteroid>()
    val quanta = ArrayList<Quantum>()

    private var asteroidTimer = 0f
    private var quantumTimer = 0f

    // ── Callbacks d'effets visuels (consommés par la vue, même thread) ───────
    /** (x, y) du quantum capturé, et points gagnés. */
    var onCollect: ((Float, Float, Int) -> Unit)? = null
    /** (x, y) du noyau lorsqu'un astéroïde y est absorbé. */
    var onAbsorb: ((Float, Float) -> Unit)? = null
    /** (x, y) du point d'impact mortel. */
    var onDeath: ((Float, Float) -> Unit)? = null

    fun configure(w: Int, h: Int) {
        cx = w / 2f
        cy = h / 2f
        minDim = minOf(w, h).toFloat()
        orbitR = minDim * 0.30f
        nucleusR = minDim * 0.072f
        electronR = minDim * 0.028f
        asteroidR = minDim * 0.030f
        spawnR = hypot(w.toFloat(), h.toFloat()) / 2f + asteroidR * 2f
    }

    fun start() {
        isRunning = true
        isGameOver = false
        score = 0
        combo = 0
        elapsedMs = 0L
        angle = -PI.toFloat() / 2f
        dir = 1
        omega = BASE_OMEGA
        asteroids.clear()
        quanta.clear()
        asteroidTimer = FIRST_SPAWN_DELAY
        quantumTimer = 1.0f
    }

    /** Action unique du joueur : inverse le sens de rotation. */
    fun reverse() {
        if (isRunning && !isGameOver) dir = -dir
    }

    fun electronX() = cx + orbitR * Math.cos(angle.toDouble()).toFloat()
    fun electronY() = cy + orbitR * Math.sin(angle.toDouble()).toFloat()

    fun update(dtMs: Long) {
        if (!isRunning || isGameOver) return
        val dt = (dtMs.coerceAtMost(50L)) / 1000f
        elapsedMs += dtMs

        // Montée en difficulté progressive.
        val ramp = (elapsedMs / 1000f)
        omega = BASE_OMEGA + (ramp * 0.028f).coerceAtMost(1.5f)
        val asteroidSpeed = spawnR / (CROSS_TIME_START - (ramp * 0.02f).coerceAtMost(CROSS_TIME_START - CROSS_TIME_MIN))
        val spawnInterval = (SPAWN_INTERVAL_START - ramp * 0.012f).coerceAtLeast(SPAWN_INTERVAL_MIN)

        // Rotation de l'électron.
        angle += dir * omega * dt
        if (angle > PI.toFloat()) angle -= 2f * PI.toFloat()
        if (angle < -PI.toFloat()) angle += 2f * PI.toFloat()

        // Apparition des astéroïdes.
        asteroidTimer -= dt
        if (asteroidTimer <= 0f && asteroids.size < MAX_ASTEROIDS) {
            asteroidTimer = spawnInterval
            spawnAsteroid(asteroidSpeed)
            // Salve double une fois la partie bien lancée.
            if (elapsedMs > 28_000L && Random.nextFloat() < 0.30f && asteroids.size < MAX_ASTEROIDS) {
                spawnAsteroid(asteroidSpeed)
            }
        }

        // Apparition des quanta d'énergie.
        quantumTimer -= dt
        if (quantumTimer <= 0f && quanta.size < MAX_QUANTA) {
            quantumTimer = QUANTUM_INTERVAL
            quanta.add(Quantum(randomAngleAwayFromElectron(), 0L, QUANTUM_LIFE_MS))
        }

        updateAsteroids(dt)
        updateQuanta(dtMs)
    }

    private fun spawnAsteroid(speed: Float) {
        // On évite de viser pile l'angle courant de l'électron pour rester jouable.
        var a: Float
        var tries = 0
        do {
            a = Random.nextFloat() * 2f * PI.toFloat() - PI.toFloat()
            tries++
        } while (tries < 6 && angularDist(a, angle) < 0.35f)
        asteroids.add(Asteroid(a, spawnR, speed, asteroidR, Random.nextLong()))
    }

    private fun updateAsteroids(dt: Float) {
        val band = electronR + asteroidR
        val angHalf = (electronR + asteroidR) / orbitR + 0.04f
        val it = asteroids.iterator()
        while (it.hasNext()) {
            val a = it.next()
            a.dist -= a.speed * dt

            // Collision lorsque l'astéroïde traverse l'anneau au même angle.
            if (abs(a.dist - orbitR) <= band && angularDist(a.angle, angle) <= angHalf) {
                isGameOver = true
                isRunning = false
                if (score > best) best = score
                onDeath?.invoke(electronX(), electronY())
                return
            }

            // Absorbé par le noyau → astéroïde esquivé.
            if (a.dist <= nucleusR * 0.55f) {
                it.remove()
                score += DODGE_POINTS
                onAbsorb?.invoke(cx, cy)
            }
        }
    }

    private fun updateQuanta(dtMs: Long) {
        val angHalf = (electronR + electronR * 0.8f) / orbitR + 0.05f
        val it = quanta.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.ageMs += dtMs

            if (angularDist(q.angle, angle) <= angHalf) {
                // Capturé : combo qui grimpe → points croissants.
                combo++
                val pts = QUANTUM_POINTS + (combo - 1).coerceAtMost(MAX_COMBO_BONUS)
                score += pts
                val qx = cx + orbitR * Math.cos(q.angle.toDouble()).toFloat()
                val qy = cy + orbitR * Math.sin(q.angle.toDouble()).toFloat()
                onCollect?.invoke(qx, qy, pts)
                it.remove()
                continue
            }

            if (q.ageMs >= q.lifeMs) {
                it.remove()
                combo = 0 // un quantum raté casse le combo
            }
        }
    }

    private fun randomAngleAwayFromElectron(): Float {
        var a: Float
        var tries = 0
        do {
            a = Random.nextFloat() * 2f * PI.toFloat() - PI.toFloat()
            tries++
        } while (tries < 8 && angularDist(a, angle) < 0.6f)
        return a
    }

    companion object {
        private const val BASE_OMEGA = 2.3f          // rad/s
        private const val CROSS_TIME_START = 2.3f     // s pour traverser l'écran au début
        private const val CROSS_TIME_MIN = 1.05f
        private const val SPAWN_INTERVAL_START = 1.15f
        private const val SPAWN_INTERVAL_MIN = 0.50f
        private const val FIRST_SPAWN_DELAY = 1.1f
        private const val QUANTUM_INTERVAL = 1.6f
        private const val QUANTUM_LIFE_MS = 4200L
        private const val MAX_ASTEROIDS = 6
        private const val MAX_QUANTA = 3

        private const val DODGE_POINTS = 1
        private const val QUANTUM_POINTS = 3
        private const val MAX_COMBO_BONUS = 9

        /** Plus court écart angulaire entre deux angles (en valeur absolue). */
        fun angularDist(a: Float, b: Float): Float {
            var d = abs(a - b) % (2f * PI.toFloat())
            if (d > PI.toFloat()) d = 2f * PI.toFloat() - d
            return d
        }
    }
}
