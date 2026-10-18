package com.Atom2Universe.app.games.wavesurf

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** Physique arcade locale à Wave Surf, sans dépendance au rendu ni au moteur partagé. */
internal class WaveSurfPhysics(val terrain: WaveSurfTerrain = WaveSurfTerrain()) {
    companion object {
        const val STEP = 1f / 120f
        const val GRAVITY = 700f
        private const val HOLD_GRAVITY_MULTIPLIER = 7f
        private const val PRESS_GRAVITY_BOOST = 4f
        private const val PRESS_BOOST_DURATION = 0.14f
        private const val PRESS_DOWN_IMPULSE = 480f
        private const val DOWNHILL_CLICK_BOOST = 280f
        private const val DOWNHILL_CLICK_SPEED_RATIO = 0.18f
        private const val MAX_DOWNHILL_SPEED_BONUS = 420f
        private const val RELAUNCH_SPEED_LIMIT = 650f
        private const val RECOVERY_SPEED = 160f
        private const val START_SPEED = 410f
    }

    var x = 0f; private set
    var y = 0f; private set
    var vx = START_SPEED; private set
    var vy = 0f; private set
    var speed = START_SPEED; private set
    var grounded = true; private set
    var pressing = false; private set
    private var pressDuration = 0f
    val altitude get() = (terrain.height(x) - y).coerceAtLeast(0f)
    private val gravityAcceleration: Float get() {
        if (!pressing) return GRAVITY
        // Chaque clic recharge une pointe de gravité (11×), qui retombe à 7×
        // pendant le maintien. Relâcher revient immédiatement à la gravité libre.
        val pulse = (1f - pressDuration / PRESS_BOOST_DURATION).coerceIn(0f, 1f)
        return GRAVITY * (HOLD_GRAVITY_MULTIPLIER + PRESS_GRAVITY_BOOST * pulse)
    }

    fun reset() {
        terrain.reset()
        x = 0f; y = terrain.height(x)
        speed = START_SPEED; vx = speed; vy = 0f
        grounded = true; pressing = false; pressDuration = 0f
    }

    /** Appui = traction vers le sol ; relâchement = liberté de décoller avec l'élan. */
    fun setPress(pressed: Boolean) {
        if (!pressed && !pressing) return
        pressing = pressed
        pressDuration = 0f
        if (!pressed) {
            // Petit saut de relance après un clic en montée, même sans assez
            // d'élan pour décoller par la seule courbure. Un maintien ne le répète pas.
            if (grounded && speed < RELAUNCH_SPEED_LIMIT && terrain.sample(x).gradient < -0.10f) {
                alignVelocity()
                vy -= (180f + speed * 0.25f).coerceAtMost(320f)
                grounded = false
                y -= 0.5f
                speed = hypot(vx, vy)
            }
            return
        }
        if (grounded) {
            val gradient = terrain.sample(x).gradient
            // La traction verticale reste projetée sur la pente.
            val alongSlope = PRESS_DOWN_IMPULSE * gradient / sqrt(1f + gradient * gradient)
            // Au contact d'une descente, CHAQUE clic donne aussi une vraie poussée
            // dans le sens de la glisse, perceptible même sur une pente douce.
            // La part proportionnelle garde de l'effet quand on a déjà de l'élan ;
            // le maintien ne répète pas ce bonus, il module toujours la gravité.
            val downhillBoost = if (gradient > 0f) {
                DOWNHILL_CLICK_BOOST + (speed * DOWNHILL_CLICK_SPEED_RATIO).coerceAtMost(MAX_DOWNHILL_SPEED_BONUS)
            } else 0f
            speed = max(RECOVERY_SPEED, speed + alongSlope + downhillBoost)
            alignVelocity()
        } else {
            // Réaction immédiate en vol, puis traction continue tant qu'on appuie.
            vy += PRESS_DOWN_IMPULSE
            speed = hypot(vx, vy)
        }
    }

    /** Une pause ou un geste annulé ne doit pas déclencher un saut. */
    fun cancelPress() {
        pressing = false
        pressDuration = 0f
    }

    fun step(dt: Float = STEP) {
        terrain.ensure(x + 4000f)
        if (grounded) advanceGround(dt) else advanceAir(dt)
        if (pressing) pressDuration += dt
    }

    private fun alignVelocity() {
        val gradient = terrain.sample(x).gradient
        vx = speed / sqrt(1f + gradient * gradient)
        vy = vx * gradient
    }

    private fun advanceGround(dt: Float) {
        val before = terrain.sample(x)
        // Point milieu pour avancer le long de la courbe, pas selon sa corde.
        val midX = x + speed * dt * 0.5f / sqrt(1f + before.gradient * before.gradient)
        val middle = terrain.sample(midX)
        x += speed * dt / sqrt(1f + middle.gradient * middle.gradient)
        val after = terrain.sample(x)
        // Y va vers le bas : descendre ajoute de l'énergie, remonter en consomme.
        // Le bilan de hauteur évite d'inventer de l'élan à chaque raccord de pente.
        val squaredSpeed = speed * speed + 2f * gravityAcceleration * (after.height - y)
        // Faibles pertes de glisse. La régulation n'agit qu'à très grande vitesse,
        // pour conserver l'élan gagné en chargeant les longues descentes.
        val overspeed = max(0f, speed - 2200f) / 800f
        speed = max(RECOVERY_SPEED, sqrt(max(0f, squaredSpeed)) * exp(-(0.018f + 0.08f * overspeed * overspeed) * dt))
        y = after.height
        alignVelocity()

        val normalForce = GRAVITY / sqrt(1f + after.gradient * after.gradient) - speed * speed * after.curvature
        // L'appui plaque la balle au relief. Après relâchement, elle s'envole
        // quand la montée s'arrondit sous elle : aucune phase imposée ni saut ajouté.
        if (!pressing && after.gradient < 0f && speed > 300f && normalForce < -10f) {
            grounded = false
            y -= 0.5f
        }
    }

    private fun advanceAir(dt: Float) {
        val oldX = x
        val oldY = y
        val oldVy = vy
        val gravity = gravityAcceleration
        x += vx * dt
        y += oldVy * dt + 0.5f * gravity * dt * dt
        vy += gravity * dt
        if (y >= terrain.height(x)) {
            // Retrouve le contact dans le pas de temps pour éviter de traverser
            // la courbe à grande vitesse ou de téléporter la réception au creux.
            var lo = 0f
            var hi = dt
            repeat(10) {
                val t = (lo + hi) * 0.5f
                val gap = oldY + oldVy * t + 0.5f * gravity * t * t - terrain.height(oldX + vx * t)
                if (gap >= 0f) hi = t else lo = t
            }
            x = oldX + vx * hi
            y = terrain.height(x)
            vy = oldVy + gravity * hi
            val gradient = terrain.sample(x).gradient
            val incomingSpeed = hypot(vx, vy)
            val tangentSpeed = (vx + vy * gradient) / sqrt(1f + gradient * gradient)
            // Une réception dans le sens de la pente conserve presque tout l'élan.
            // Une mauvaise réception en conserve une partie pour pouvoir repartir.
            speed = max(RECOVERY_SPEED, max(tangentSpeed * 0.985f, incomingSpeed * 0.55f))
            grounded = true
            alignVelocity()
            if (dt - hi > 0f) advanceGround(dt - hi)
        } else {
            speed = hypot(vx, vy)
        }
    }
}
