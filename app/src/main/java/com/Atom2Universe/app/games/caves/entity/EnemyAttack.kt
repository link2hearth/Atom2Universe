package com.Atom2Universe.app.games.caves.entity

import kotlin.math.*

/** Attack volumes are readable through body movement, never previews on the ground. */
internal enum class AttackShape { SWEEP, SLAM, LUNGE, ARROW, VENOM, BEAM, SPIN, DOUBLE, FEINT, CHARGE }

internal class EnemyAttack(
    val shape: AttackShape,
    val windup: Float,
    val recovery: Float,
    val range: Double,
    val halfAngle: Double = .9,
    val width: Double = .65,
    val followUps: Int = 0,
) {
    var x = 0.0; var y = 0.0; var z = 0.0
    var targetX = 0.0; var targetY = 0.0; var targetZ = 0.0
    var yaw = 0.0
    var flash = 0f
    var lungeRemaining = 0f
    var lungeHit = false
    var activeRemaining = 0f
    var followUpIn = 0f
    var remainingHits = followUps
    var hitIndex = 0
    val rushing get() = shape == AttackShape.LUNGE || shape == AttackShape.CHARGE
    val rushDuration get() = if (shape == AttackShape.CHARGE) .5f else .24f
    val busy get() = lungeRemaining > 0f || activeRemaining > 0f || remainingHits > 0 && followUpIn > 0f
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
        if (shape == AttackShape.SLAM || shape == AttackShape.SPIN) return distance <= range
        val forward = dx * sin(yaw) + dz * cos(yaw)
        val side = dx * cos(yaw) - dz * sin(yaw)
        if (rushing) return forward in 0.0..range && abs(side) <= width
        return distance <= range && (distance < .01 || forward / distance >= cos(halfAngle))
    }

    companion object {
        fun forEnemy(e: Enemy, distance: Double): EnemyAttack {
            val reach = max(e.collisionRadius + EnemyManager.PLAYER_STANDOFF, e.def.attackRange) + EnemyManager.ATTACK_REACH
            val turn = Math.floorMod(e.attackSequence + e.id, 3)
            if (e.def.id == "skeleton" && distance > 4)
                return EnemyAttack(AttackShape.ARROW,.42f,.28f,16.0,followUps=if(turn==1) 1 else 0)
            if (e.def.id == "spider" && distance > 5)
                return EnemyAttack(AttackShape.VENOM,.38f,.3f,10.0,followUps=if(turn==2) 1 else 0)
            if ((e.def.id == "imp" || e.def.id == "wraith") && distance > 3)
                return EnemyAttack(AttackShape.BEAM,if(e.def.id=="imp") .48f else .65f,.35f,14.0,width=.18)
            val palette = when(e.def.id) {
                "goblin" -> listOf(AttackShape.SWEEP,AttackShape.FEINT,AttackShape.DOUBLE)
                "skeleton" -> listOf(AttackShape.SWEEP,AttackShape.LUNGE,AttackShape.DOUBLE)
                "zombie" -> listOf(AttackShape.DOUBLE,AttackShape.SWEEP,AttackShape.LUNGE)
                "mummy" -> listOf(AttackShape.FEINT,AttackShape.DOUBLE,AttackShape.SPIN)
                "dwarf" -> listOf(AttackShape.SWEEP,AttackShape.SPIN,AttackShape.SLAM)
                "golem" -> listOf(AttackShape.SLAM,AttackShape.DOUBLE,AttackShape.CHARGE)
                "ogre" -> listOf(AttackShape.CHARGE,AttackShape.SPIN,AttackShape.SLAM)
                "troll" -> listOf(AttackShape.DOUBLE,AttackShape.SLAM,AttackShape.SPIN)
                "slime" -> listOf(AttackShape.LUNGE,AttackShape.SLAM,AttackShape.CHARGE)
                "spider" -> listOf(AttackShape.LUNGE,AttackShape.DOUBLE,AttackShape.FEINT)
                "imp", "wraith" -> listOf(AttackShape.FEINT,AttackShape.SPIN,AttackShape.SWEEP)
                else -> listOf(AttackShape.SWEEP,AttackShape.DOUBLE,AttackShape.LUNGE)
            }
            return when(val shape=palette[turn]) {
                AttackShape.SLAM -> EnemyAttack(shape,.58f,.45f,reach+.5)
                AttackShape.SPIN -> EnemyAttack(shape,.46f,.35f,reach+.3)
                AttackShape.DOUBLE -> EnemyAttack(shape,.24f,.28f,reach,halfAngle=1.1,followUps=1)
                AttackShape.FEINT -> EnemyAttack(shape,.62f,.22f,reach+.2,halfAngle=.8)
                AttackShape.CHARGE -> EnemyAttack(shape,.55f,.4f,max(6.0,reach+2.0),width=e.collisionRadius+.3)
                AttackShape.LUNGE -> EnemyAttack(shape,.3f,.25f,reach+1.0,width=.7)
                else -> EnemyAttack(shape,if(e.def.id=="goblin") .18f else .28f,.22f,reach,halfAngle=1.0)
            }
        }
    }
}
