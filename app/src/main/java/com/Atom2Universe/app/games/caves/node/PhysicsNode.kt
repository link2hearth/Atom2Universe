package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*

/**
 * Physique joueur en mode WALK.
 *
 * Améliorations vs l'ancienne implémentation inline dans CaveRenderer :
 *   - Coyote time  : 0.12 s après avoir quitté un bord, le saut reste valide.
 *   - Jump buffer  : saut pressé ≤ 0.10 s avant l'atterrissage se déclenche à l'impact.
 *   - Collision plafond : toucher un plafond en montant annule immédiatement la vitesse Y.
 *   - Eau améliorée : accélération progressive, décélération douce à la surface.
 *   - Step-up propre : montée douce sans accumuler de jank.
 */
class PhysicsNode(private val blockAt: (Int, Int, Int) -> Short) {

    var velocityY = 0.0
    var onGround  = false

    private var prevJumpPressed  = false
    private var coyoteTimer      = 0f
    private var jumpBuffer       = 0f
    private var stepUpRemaining  = 0.0

    companion object {
        private const val GRAVITY        = 24.0   // m/s²
        private const val JUMP_VY        = 10.5   // m/s vitesse initiale saut
        private const val TERM_VEL       = 40.0   // m/s vitesse terminale chute
        private const val WALK_SPEED     = 7.0    // m/s au sol
        private const val AIR_SPEED      = 4.0    // m/s en l'air
        private const val WATER_SPEED    = 3.0    // m/s dans l'eau
        private const val WATER_GRAVITY  = 6.0    // m/s² gravité réduite sous l'eau
        private const val WATER_MAX_VY   = 3.0    // m/s vitesse max nage verticale
        private const val COYOTE_SEC     = 0.12f  // s après quitter un bord
        private const val JUMP_BUF_SEC   = 0.10f  // s buffer avant atterrissage
        private const val STEP_MAX       = 1.0    // max step-up en blocs
        private const val STEP_RATE      = 12.0   // blocs/s montée step-up
        private const val PLAYER_W       = 0.30   // demi-largeur
        private const val PLAYER_WI      = 0.29   // demi-largeur interne (évite coins)
        private const val PLAYER_H_BELOW = 1.62   // hauteur sous l'œil (pieds → œil)
        private const val PLAYER_H_ABOVE = 0.18   // hauteur au-dessus de l'œil
    }

    /**
     * Exécute un tick de physique WALK.
     * Retourne la nouvelle position (x, y, z) du joueur.
     */
    fun updateWalk(
        dt: Float,
        px: Double, py: Double, pz: Double,
        fwdX: Float, fwdZ: Float, rgtX: Float, rgtZ: Float,
        moveForward: Float, moveRight: Float,
        jumpPressed: Boolean
    ): Triple<Double, Double, Double> {

        var x = px; var y = py; var z = pz
        val inWater = isBodyInWater(x, y, z)

        // ── Coyote time ───────────────────────────────────────────────────────
        if (onGround) coyoteTimer = COYOTE_SEC
        else          coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)

        // ── Jump buffer ───────────────────────────────────────────────────────
        val jumpJustPressed = jumpPressed && !prevJumpPressed
        prevJumpPressed = jumpPressed
        if (jumpJustPressed) jumpBuffer = JUMP_BUF_SEC
        else                 jumpBuffer = (jumpBuffer - dt).coerceAtLeast(0f)

        // ── Mouvement horizontal ──────────────────────────────────────────────
        val hSpeed = when {
            inWater  -> WATER_SPEED * dt
            onGround -> WALK_SPEED  * dt
            else     -> AIR_SPEED   * dt
        }
        val dx = (fwdX * moveForward - rgtX * moveRight) * hSpeed
        val dz = (fwdZ * moveForward - rgtZ * moveRight) * hSpeed

        // Axe X — tentative step-up si bloqué
        val newX = x + dx
        if (!collidesAt(newX, y, z)) {
            x = newX
        } else if ((onGround || inWater) && dx != 0.0 &&
                   !collidesAt(newX, y + STEP_MAX, z)) {
            if (stepUpRemaining == 0.0) stepUpRemaining = STEP_MAX
        }

        // Axe Z — tentative step-up si bloqué
        val newZ = z + dz
        if (!collidesAt(x, y, newZ)) {
            z = newZ
        } else if ((onGround || inWater) && dz != 0.0 &&
                   !collidesAt(x, y + STEP_MAX, newZ)) {
            if (stepUpRemaining == 0.0) stepUpRemaining = STEP_MAX
        }

        // ── Phase verticale ───────────────────────────────────────────────────
        when {
            // Step-up en cours : monter progressivement
            stepUpRemaining > 0.0 -> {
                val rise = minOf(stepUpRemaining, STEP_RATE * dt)
                if (!collidesAt(x, y + rise, z)) {
                    y += rise
                    stepUpRemaining -= rise
                } else {
                    stepUpRemaining = 0.0
                }
                velocityY = 0.0
                onGround  = (stepUpRemaining == 0.0)
            }

            // Sous l'eau
            inWater -> {
                if (jumpPressed) velocityY = minOf(velocityY + WATER_SPEED * dt * 8, WATER_MAX_VY)
                velocityY = (velocityY - WATER_GRAVITY * dt).coerceAtLeast(-WATER_MAX_VY)

                val dy = velocityY * dt
                if (!collidesAt(x, y + dy, z)) {
                    y += dy
                } else {
                    if (dy < 0) {
                        y = binarySearchFloor(x, y, z, dy)
                        velocityY = 0.0
                    }
                    // Plafond dans l'eau : ne pas progresser, vitesse annulée
                    if (dy > 0) velocityY = 0.0
                }
                onGround    = false
                coyoteTimer = 0f
            }

            // Gravité standard
            else -> {
                // Saut autorisé par coyote time + buffer
                val canJump = coyoteTimer > 0f && jumpBuffer > 0f && velocityY <= 0.5
                if (canJump) {
                    velocityY   = JUMP_VY
                    coyoteTimer = 0f
                    jumpBuffer  = 0f
                    onGround    = false
                }

                velocityY = (velocityY - GRAVITY * dt).coerceAtLeast(-TERM_VEL)
                val dy = velocityY * dt

                if (!collidesAt(x, y + dy, z)) {
                    y += dy
                    onGround = false
                } else if (dy < 0) {
                    // Atterrissage : recherche binaire de la position exacte
                    y = binarySearchFloor(x, y, z, dy)
                    if (jumpBuffer > 0f) {
                        // Saut bufferisé : se déclenche à l'impact
                        velocityY  = JUMP_VY
                        jumpBuffer = 0f
                        onGround   = false
                    } else {
                        velocityY = 0.0
                        onGround  = true
                    }
                } else {
                    // Impact plafond : annule la vitesse ascendante immédiatement
                    velocityY = 0.0
                }
            }
        }

        return Triple(x, y, z)
    }

    /** Recherche binaire pour la position Y exacte au sol (sans pénétrer). */
    private fun binarySearchFloor(x: Double, y: Double, z: Double, dy: Double): Double {
        var lo = y + dy; var hi = y
        repeat(8) {
            val mid = (lo + hi) * 0.5
            if (collidesAt(x, mid, z)) lo = mid else hi = mid
        }
        return hi
    }

    /** AABB du joueur en position (px, py, pz) : collision avec un bloc solide ? */
    fun collidesAt(px: Double, py: Double, pz: Double): Boolean {
        val x0 = floor(px - PLAYER_W).toInt();  val x1 = floor(px + PLAYER_WI).toInt()
        val y0 = floor(py - PLAYER_H_BELOW).toInt(); val y1 = floor(py + PLAYER_H_ABOVE).toInt()
        val z0 = floor(pz - PLAYER_W).toInt();  val z1 = floor(pz + PLAYER_WI).toInt()
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1) {
            val b = blockAt(bx, by, bz)
            if (b != AIR && !isDecoration(b) && !isWater(b)) return true
        }
        return false
    }

    fun isBodyInWater(px: Double, py: Double, pz: Double): Boolean =
        isWater(blockAt(floor(px).toInt(), floor(py - 0.9).toInt(), floor(pz).toInt()))

    fun isHeadInWater(px: Double, py: Double, pz: Double): Boolean =
        isWater(blockAt(floor(px).toInt(), floor(py - 0.1).toInt(), floor(pz).toInt()))

    /** Remet l'état physique à zéro (ex. : changement de mode SPECTATOR→WALK). */
    fun reset() {
        velocityY       = 0.0
        onGround        = false
        prevJumpPressed = false
        coyoteTimer     = 0f
        jumpBuffer      = 0f
        stepUpRemaining = 0.0
    }
}
