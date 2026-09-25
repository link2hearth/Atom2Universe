package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*

class PhysicsNode(private val blockAt: (Int, Int, Int) -> Short) {
    /** Obstacle mobile optionnel : centre X/Z, pieds, hauteur totale du joueur. */
    var dynamicCollision: ((Double, Double, Double, Double) -> Boolean)? = null
    /** Static map props, kept separate from the moving soldiers' collision callback. */
    var decorCollision: ((Double, Double, Double, Double) -> Boolean)? = null

    var metaAt: (Int, Int, Int) -> Byte = { _, _, _ -> 0 }
    var sampleWaterCurrent: (Double, Double, Double, DoubleArray) -> Unit = { _, _, _, out -> out.fill(0.0) }
    private val current = DoubleArray(4)
    private var driftX = 0.0
    private var driftZ = 0.0
    var waterContainsPoint: (Double, Double, Double) -> Boolean = { x, y, z ->
        isWater(blockAt(floor(x).toInt(), floor(y).toInt(), floor(z).toInt()))
    }
    var velocityY = 0.0
    var onGround  = false

    var isSprinting = false
    var isCrouching = false
        private set
    val eyeDrop: Double get() = if (isCrouching) 0.6 else 0.0
    val heightAbove: Double get() = PLAYER_H_ABOVE - eyeDrop

    // Keep the standing eye reference: posture does not move feet or saved positions.
    fun updateCrouch(pressed: Boolean, x: Double, y: Double, z: Double) {
        val wasCrouching = isCrouching
        val blockedStanding = collidesAt(x, y, z, PLAYER_H_ABOVE)
        isCrouching = pressed || (blockedStanding &&
            (isCrouching || !collidesAt(x, y, z, PLAYER_H_ABOVE - 0.6)))
        if (isCrouching) {
            isSprinting = false
            if (!wasCrouching) stepUpRemaining = 0.0
        }
    }

    // Recul horizontal (knockback) quand le joueur est touché — vitesse amortie.
    private var knockX = 0.0
    private var knockZ = 0.0

    /** Donne une impulsion de recul (unités/s) ; [vx]/[vz] = direction × force. */
    fun applyKnockback(vx: Double, vz: Double) { knockX = vx; knockZ = vz }

    private var prevJumpPressed = false
    private var coyoteTimer     = 0f
    private var stepUpRemaining = 0.0

    companion object {
        private const val GRAVITY        = 24.0   // m/s²
        const val WALK_SPEED             = 3.5    // m/s
        const val SPRINT_SPEED           = 6.5    // m/s
        // Hauteur du saut, fixe. 1 bloc pile ne dépasse jamais le bloc (la gravité s'applique
        // dès la première image) : ni marche franchie, ni bloc posé sous soi.
        const val JUMP_BLOCKS            = 1.25
        private val JUMP_VY              = sqrt(2.0 * GRAVITY * JUMP_BLOCKS)
        private const val TERM_VEL       = 40.0   // m/s vitesse terminale chute
        private const val AIR_SPEED      = 4.0    // m/s en l'air
        private const val WATER_SPEED    = 3.0    // m/s dans l'eau
        private const val WATER_GRAVITY  = 6.0    // m/s² gravité réduite sous l'eau
        private const val WATER_MAX_VY   = 3.0    // m/s vitesse max nage verticale
        private const val COYOTE_SEC     = 0.12f  // s après quitter un bord
        private const val STEP_MAX       = 1.0    // max step-up en blocs
        private const val STEP_RATE      = 12.0   // blocs/s montée step-up
        private const val GROUND_FOLLOW  = 0.125  // small lips follow the floor without an auto-climb
        private const val PLAYER_W       = 0.30
        private const val PLAYER_WI      = 0.29
        private const val PLAYER_H_BELOW = 1.62
        private const val PLAYER_H_ABOVE = 0.18
        private const val KNOCK_DAMP     = 8.0    // amortissement du recul (par seconde)
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
        if (inWater) sampleWaterCurrent(x, y - 0.9, z, current) else current.fill(0.0)
        val response = 1.0 - exp(-4.0 * dt)
        if (inWater) {
            driftX += (current[0] - driftX) * response
            driftZ += (current[2] - driftZ) * response
        } else { driftX = 0.0; driftZ = 0.0 }
        // ── Coyote time ───────────────────────────────────────────────────────
        if (onGround) {
            coyoteTimer = COYOTE_SEC
        } else {
            coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
        }

        val jumpJustPressed = jumpPressed && !prevJumpPressed

        // ── Mouvement horizontal ──────────────────────────────────────────────
        val groundSpeed = if (isSprinting) SPRINT_SPEED else WALK_SPEED
        val hSpeed = (when {
            inWater  -> WATER_SPEED * dt
            onGround -> groundSpeed * dt
            else     -> AIR_SPEED   * dt
        }) * if (isCrouching) 0.35 else 1.0
        val dx = (fwdX * moveForward - rgtX * moveRight) * hSpeed + driftX * dt
        val dz = (fwdZ * moveForward - rgtZ * moveRight) * hSpeed + driftZ * dt

        val newX = x + dx
        if (!collidesAt(newX, y, z)) {
            x = newX
        } else if (onGround && !inWater && !jumpPressed && velocityY <= 0.0 &&
            stepUpRemaining == 0.0 && !collidesAt(x, y + GROUND_FOLLOW, z) &&
            !collidesAt(newX, y + GROUND_FOLLOW, z)) {
            y = smallStepHeight(newX, y, z)
            x = newX
        } else if ((onGround || inWater) && dx != 0.0 && (moveForward != 0f || moveRight != 0f) &&
                   (!collidesAt(newX, y + .5, z) || (!isCrouching && !collidesAt(newX, y + STEP_MAX, z)))) {
            if (stepUpRemaining == 0.0) stepUpRemaining = if (!collidesAt(newX, y + .5, z)) .5 else STEP_MAX
        }

        val newZ = z + dz
        if (!collidesAt(x, y, newZ)) {
            z = newZ
        } else if (onGround && !inWater && !jumpPressed && velocityY <= 0.0 &&
            stepUpRemaining == 0.0 && !collidesAt(x, y + GROUND_FOLLOW, z) &&
            !collidesAt(x, y + GROUND_FOLLOW, newZ)) {
            y = smallStepHeight(x, y, newZ)
            z = newZ
        } else if ((onGround || inWater) && dz != 0.0 && (moveForward != 0f || moveRight != 0f) &&
                   (!collidesAt(x, y + .5, newZ) || (!isCrouching && !collidesAt(x, y + STEP_MAX, newZ)))) {
            if (stepUpRemaining == 0.0) stepUpRemaining = if (!collidesAt(x, y + .5, newZ)) .5 else STEP_MAX
        }

        // ── Recul (knockback) ─────────────────────────────────────────────────
        if (knockX != 0.0 || knockZ != 0.0) {
            val kx = knockX * dt
            if (!collidesAt(x + kx, y, z)) x += kx
            val kz = knockZ * dt
            if (!collidesAt(x, y, z + kz)) z += kz
            val damp = (KNOCK_DAMP * dt).coerceAtMost(1.0)
            knockX -= knockX * damp
            knockZ -= knockZ * damp
            if (abs(knockX) < 0.1 && abs(knockZ) < 0.1) { knockX = 0.0; knockZ = 0.0 }
        }

        // Stay supported on shallow descents as well. Never snap during a jump, swimming,
        // an auto-climb or a real fall, and never bridge a drop larger than GROUND_FOLLOW.
        if (onGround && !inWater && !jumpPressed && velocityY <= 0.0 && stepUpRemaining == 0.0 &&
            !collidesAt(x, y - .002, z) && collidesAt(x, y - GROUND_FOLLOW, z)) {
            y = binarySearchFloor(x, y, z, -GROUND_FOLLOW)
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
                // Freine aussi une chute rapide à l'entrée dans l'eau. Le courant
                // descendant gêne la remontée, mais le joueur peut encore nager.
                velocityY += (current[1] - velocityY) * response
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
            }

            else -> {
                val canJump = coyoteTimer > 0f && velocityY <= 0.5

                if (canJump && jumpPressed && (jumpJustPressed || onGround)) {
                    velocityY = JUMP_VY; coyoteTimer = 0f; onGround = false
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
                } else {
                    velocityY = 0.0
                }
            }
        }

        prevJumpPressed = jumpPressed
        return Triple(x, y, z)
    }

    /** Lowest collision-free height at the destination, rather than a half-block overshoot. */
    private fun smallStepHeight(x: Double, y: Double, z: Double): Double {
        var blocked = y
        var clear = y + GROUND_FOLLOW
        repeat(12) {
            val mid = (blocked + clear) * .5
            if (collidesAt(x, mid, z)) blocked = mid else clear = mid
        }
        return clear
    }

    private fun binarySearchFloor(x: Double, y: Double, z: Double, dy: Double): Double {
        var lo = y + dy; var hi = y
        repeat(8) {
            val mid = (lo + hi) * 0.5
            if (collidesAt(x, mid, z)) lo = mid else hi = mid
        }
        return hi
    }

    fun collidesAt(px: Double, py: Double, pz: Double): Boolean = collidesAt(px, py, pz, heightAbove)

    private fun collidesAt(px: Double, py: Double, pz: Double, top: Double): Boolean {
        if (decorCollision?.invoke(px, py - PLAYER_H_BELOW, pz, PLAYER_H_BELOW + top) == true) return true
        if (dynamicCollision?.invoke(px, py - PLAYER_H_BELOW, pz, PLAYER_H_BELOW + top) == true) return true
        val x0 = floor(px - PLAYER_W).toInt();  val x1 = floor(px + PLAYER_WI).toInt()
        val y0 = floor(py - PLAYER_H_BELOW).toInt(); val y1 = floor(py + top).toInt()
        val z0 = floor(pz - PLAYER_W).toInt();  val z1 = floor(pz + PLAYER_WI).toInt()
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1) {
            val b = blockAt(bx, by, bz)
            if (b != AIR && !isDecoration(b) && !isWater(b)) {
                if (BlockRegistry.get(b)?.stairs != true && BlockRegistry.get(b)?.slab != true && (BlockRegistry.get(b)?.blockHeight ?: 1f) >= 1f) return true
                if (PartialBlockModel.boxes(metaAt(bx, by, bz), BlockRegistry.get(b)?.slab == true, BlockRegistry.get(b)?.blockHeight ?: 1f, StairConnections.maskAt(bx, by, bz, blockAt, metaAt)).any { box ->
                    px + PLAYER_WI > bx + box.x && px - PLAYER_W < bx + box.x + box.width &&
                    py + top > by + box.y && py - PLAYER_H_BELOW < by + box.y + box.height &&
                    pz + PLAYER_WI > bz + box.z && pz - PLAYER_W < bz + box.z + box.depth
                }) return true
            }
        }
        return false
    }

    fun isBodyInWater(px: Double, py: Double, pz: Double): Boolean =
        waterContainsPoint(px, py - 0.9, pz)

    fun isHeadInWater(px: Double, py: Double, pz: Double): Boolean =
        waterContainsPoint(px, py - eyeDrop, pz)

    fun reset() {
        isCrouching    = false
        isSprinting    = false
        velocityY       = 0.0
        knockX          = 0.0
        knockZ          = 0.0
        onGround        = false
        prevJumpPressed = false
        coyoteTimer     = 0f
        stepUpRemaining = 0.0
    }
}
