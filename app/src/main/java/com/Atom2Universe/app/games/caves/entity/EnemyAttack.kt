package com.Atom2Universe.app.games.caves.entity

import kotlin.math.*

/** The warning and the hit use this same, locked volume. Coordinates are world coordinates. */
internal enum class AttackShape { SWEEP, SLAM, LUNGE, ARROW, VENOM, BEAM }

internal class EnemyAttack(
    val shape: AttackShape,
    val windup: Float,
    val recovery: Float,
    val range: Double,
    val halfAngle: Double = .9,
    val width: Double = .65,
) {
    var x = 0.0; var y = 0.0; var z = 0.0
    var targetX = 0.0; var targetY = 0.0; var targetZ = 0.0
    var yaw = 0.0
    var flash = 0f
    var lungeRemaining = 0f
    var lungeHit = false
    val ranged get() = shape == AttackShape.ARROW || shape == AttackShape.VENOM || shape == AttackShape.BEAM

    fun hits(px: Double, eyeY: Double, pz: Double, eyeDrop: Double = 0.0): Boolean {
        val dx = px - x; val dz = pz - z
        if (shape == AttackShape.BEAM) {
            // Closest points between the beam and the player's vertical body segment.
            val bx = targetX - x; val by = targetY - y; val bz = targetZ - z
            val horizontal = bx * bx + bz * bz
            var t = if (horizontal > 1e-6) ((dx * bx + dz * bz) / horizontal).coerceIn(0.0, 1.0) else 0.0
            val bodyY = (y + by * t).coerceIn(eyeY - 1.45, eyeY + .1 - eyeDrop)
            val length2 = horizontal + by * by
            if (length2 > 1e-6) t = ((dx * bx + (bodyY - y) * by + dz * bz) / length2).coerceIn(0.0, 1.0)
            return (dx - bx * t).pow(2) + (bodyY - y - by * t).pow(2) + (dz - bz * t).pow(2) <= (width + .25).pow(2)
        }
        // Ground attacks cannot hit a player on a different floor or jumping over a slam.
        val feet = eyeY - 1.6
        if (feet > y + (if (shape == AttackShape.SLAM) .65 else 1.3) || eyeY < y) return false
        val distance = hypot(dx, dz)
        if (shape == AttackShape.SLAM) return distance <= range
        val forward = dx * sin(yaw) + dz * cos(yaw)
        val side = dx * cos(yaw) - dz * sin(yaw)
        if (shape == AttackShape.LUNGE) return forward in 0.0..range && abs(side) <= width
        return distance <= range && (distance < .01 || forward / distance >= cos(halfAngle))
    }

    companion object {
        fun forEnemy(e: Enemy, distance: Double): EnemyAttack {
            val reach = max(e.def.radius + EnemyManager.PLAYER_STANDOFF, e.def.attackRange) + EnemyManager.ATTACK_REACH
            return when {
                e.def.id == "skeleton" && distance > 4 -> EnemyAttack(AttackShape.ARROW, .95f, .55f, 16.0)
                e.def.id == "spider" && distance > 5 -> EnemyAttack(AttackShape.VENOM, .95f, .65f, 10.0)
                (e.def.id == "imp" || e.def.id == "wraith") && distance > 3 -> EnemyAttack(AttackShape.BEAM, 1.25f, .85f, 14.0, width = .18)
                e.def.id == "golem" || e.def.id == "ogre" || e.def.id == "troll" -> EnemyAttack(AttackShape.SLAM, 1.05f, .8f, reach + .6)
                e.def.id == "slime" || e.def.id == "spider" -> EnemyAttack(AttackShape.LUNGE, .7f, .55f, reach + .6, width = .7)
                e.def.id == "goblin" -> EnemyAttack(AttackShape.SWEEP, .55f, .4f, reach, halfAngle = .7)
                e.def.id == "zombie" -> EnemyAttack(AttackShape.SWEEP, .85f, .7f, reach, halfAngle = 1.05)
                e.def.id == "mummy" -> EnemyAttack(AttackShape.SWEEP, .95f, .8f, reach, halfAngle = 1.2)
                e.def.id == "dwarf" -> EnemyAttack(AttackShape.SWEEP, .9f, .65f, reach, halfAngle = .8)
                else -> EnemyAttack(AttackShape.SWEEP, .65f, .5f, reach)
            }
        }
    }
}
