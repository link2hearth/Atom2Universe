package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*

class PhysicsNode(private val blockAt: (Int, Int, Int) -> Short) {

    var velocityY = 0.0
    var onGround  = false

    var isSprinting = false

    var skillBook: com.Atom2Universe.app.games.caves.entity.SkillBook? = null

    // Callbacks
    var onJumped:            (() -> Unit)? = null
    var onFallLanded:        ((fallBlocks: Double) -> Unit)? = null
    // ratio 0→1 pendant la charge, 0 quand relâché/sauté
    var onJumpChargeChanged: ((ratio: Float) -> Unit)? = null

    private var prevJumpPressed = false
    private var coyoteTimer     = 0f
    private var stepUpRemaining = 0.0
    private var lastGroundY     = 0.0
    private var prevOnGround    = false

    // Charge du saut
    private var jumpHoldTime = 0f

    companion object {
        private const val GRAVITY        = 24.0   // m/s²
        private const val TERM_VEL       = 40.0   // m/s vitesse terminale chute
        private const val AIR_SPEED      = 4.0    // m/s en l'air
        private const val WATER_SPEED    = 3.0    // m/s dans l'eau
        private const val WATER_GRAVITY  = 6.0    // m/s² gravité réduite sous l'eau
        private const val WATER_MAX_VY   = 3.0    // m/s vitesse max nage verticale
        private const val COYOTE_SEC     = 0.12f  // s après quitter un bord
        private const val STEP_MAX       = 1.0    // max step-up en blocs
        private const val STEP_RATE      = 12.0   // blocs/s montée step-up
        private const val PLAYER_W       = 0.30
        private const val PLAYER_WI      = 0.29
        private const val PLAYER_H_BELOW = 1.62
        private const val PLAYER_H_ABOVE = 0.18
        const val CHARGE_MAX_SEC         = 1.5f   // durée pour charge max
    }

    fun updateWalk(
        dt: Float,
        px: Double, py: Double, pz: Double,
        fwdX: Float, fwdZ: Float, rgtX: Float, rgtZ: Float,
        moveForward: Float, moveRight: Float,
        jumpPressed: Boolean
    ): Triple<Double, Double, Double> {

        var x = px; var y = py; var z = pz
        val inWater = isBodyInWater(x, y, z)
        val sb = skillBook

        // ── Coyote time ───────────────────────────────────────────────────────
        if (onGround) {
            coyoteTimer = COYOTE_SEC
            lastGroundY = y
        } else {
            coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
        }

        val jumpJustReleased = !jumpPressed && prevJumpPressed
        prevJumpPressed = jumpPressed

        // ── Mouvement horizontal ──────────────────────────────────────────────
        val groundSpeed = if (isSprinting) sb?.sprintSpeed ?: com.Atom2Universe.app.games.caves.entity.SkillBook.BASE_SPRINT_SPEED
                          else             com.Atom2Universe.app.games.caves.entity.SkillBook.BASE_WALK_SPEED
        val hSpeed = when {
            inWater  -> WATER_SPEED * dt
            onGround -> groundSpeed * dt
            else     -> AIR_SPEED   * dt
        }
        val dx = (fwdX * moveForward - rgtX * moveRight) * hSpeed
        val dz = (fwdZ * moveForward - rgtZ * moveRight) * hSpeed

        val newX = x + dx
        if (!collidesAt(newX, y, z)) {
            x = newX
        } else if ((onGround || inWater) && dx != 0.0 &&
                   !collidesAt(newX, y + STEP_MAX, z)) {
            if (stepUpRemaining == 0.0) stepUpRemaining = STEP_MAX
        }

        val newZ = z + dz
        if (!collidesAt(x, y, newZ)) {
            z = newZ
        } else if ((onGround || inWater) && dz != 0.0 &&
                   !collidesAt(x, y + STEP_MAX, newZ)) {
            if (stepUpRemaining == 0.0) stepUpRemaining = STEP_MAX
        }

        // ── Phase verticale ───────────────────────────────────────────────────
        when {
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

            inWater -> {
                if (jumpPressed) velocityY = minOf(velocityY + WATER_SPEED * dt * 8, WATER_MAX_VY)
                velocityY = (velocityY - WATER_GRAVITY * dt).coerceAtLeast(-WATER_MAX_VY)
                val dy = velocityY * dt
                if (!collidesAt(x, y + dy, z)) {
                    y += dy
                } else {
                    if (dy < 0) { y = binarySearchFloor(x, y, z, dy); velocityY = 0.0 }
                    if (dy > 0) velocityY = 0.0
                }
                onGround    = false
                coyoteTimer = 0f
                // Annule la charge si on entre dans l'eau
                if (jumpHoldTime > 0f) { jumpHoldTime = 0f; onJumpChargeChanged?.invoke(0f) }
            }

            else -> {
                val canJump = coyoteTimer > 0f && velocityY <= 0.5

                if (canJump && jumpPressed) {
                    // Accumulation de charge
                    val prevRatio = jumpHoldTime / CHARGE_MAX_SEC
                    jumpHoldTime = (jumpHoldTime + dt).coerceAtMost(CHARGE_MAX_SEC)
                    val newRatio = jumpHoldTime / CHARGE_MAX_SEC
                    if (abs(newRatio - prevRatio) > 0.005f) onJumpChargeChanged?.invoke(newRatio)
                }

                val shouldFire = canJump && jumpHoldTime > 0f &&
                    (jumpJustReleased || (jumpHoldTime >= CHARGE_MAX_SEC) || (coyoteTimer <= dt && !onGround))
                if (shouldFire) {
                    fireJump()
                } else if (!canJump && jumpHoldTime > 0f) {
                    // Coyote expiré sans pouvoir sauter → réinitialise
                    jumpHoldTime = 0f
                    onJumpChargeChanged?.invoke(0f)
                }

                velocityY = (velocityY - GRAVITY * dt).coerceAtLeast(-TERM_VEL)
                val dy = velocityY * dt

                if (!collidesAt(x, y + dy, z)) {
                    y += dy
                    onGround = false
                } else if (dy < 0) {
                    y = binarySearchFloor(x, y, z, dy)
                    velocityY = 0.0
                    onGround  = true
                    if (!prevOnGround) {
                        val fallBlocks = lastGroundY - y
                        if (fallBlocks > 0.5) onFallLanded?.invoke(fallBlocks)
                    }
                } else {
                    velocityY = 0.0
                }
            }
        }

        prevOnGround = onGround
        return Triple(x, y, z)
    }

    private fun fireJump() {
        val chargeRatio = (jumpHoldTime / CHARGE_MAX_SEC).coerceIn(0f, 1f)
        val sb = skillBook
        val maxBlocks  = sb?.maxJumpBlocks() ?: 1.0
        val minBlocks  = 1.0
        val blocks     = minBlocks + chargeRatio * (maxBlocks - minBlocks)
        velocityY      = com.Atom2Universe.app.games.caves.entity.SkillBook.jumpVyForBlocks(blocks)
        jumpHoldTime   = 0f
        coyoteTimer    = 0f
        onGround       = false
        jumpHoldTime   = 0f
        onJumped?.invoke()
        onJumpChargeChanged?.invoke(0f)
    }

    private fun binarySearchFloor(x: Double, y: Double, z: Double, dy: Double): Double {
        var lo = y + dy; var hi = y
        repeat(8) {
            val mid = (lo + hi) * 0.5
            if (collidesAt(x, mid, z)) lo = mid else hi = mid
        }
        return hi
    }

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

    fun reset() {
        velocityY       = 0.0
        onGround        = false
        prevOnGround    = false
        prevJumpPressed = false
        coyoteTimer     = 0f
        stepUpRemaining = 0.0
        lastGroundY     = 0.0
        jumpHoldTime    = 0f
        onJumpChargeChanged?.invoke(0f)
    }

    // Ratio de charge courant (0→1), utile pour affichage HUD externe
    val jumpChargeRatio: Float get() = (jumpHoldTime / CHARGE_MAX_SEC).coerceIn(0f, 1f)
}
