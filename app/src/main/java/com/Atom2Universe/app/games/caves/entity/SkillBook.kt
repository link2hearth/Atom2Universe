package com.Atom2Universe.app.games.caves.entity

import kotlin.math.sqrt

class SkillBook {

    var athleticsXp:  Int = 0  // +1 par saut → charge height
    var speedXp:      Int = 0  // +XP distance au sol → sprint speed
    var enduranceXp:  Int = 0  // +XP dégâts/soins → maxHp
    var acrobaticsXp: Int = 0  // +XP mètres chute survivés → fall threshold

    // Athletics : niveau 1000 à 50 000 sauts (linéaire : xp / 50)
    val athleticsLevel: Int get() = (athleticsXp / 50).coerceAtMost(1000)

    // Speed / Acrobatics : courbe n²×5, cap 100
    val speedLevel:      Int get() = xpToLevel100(speedXp)
    val acrobaticsLevel: Int get() = xpToLevel100(acrobaticsXp)

    // Endurance : cap 10 000, linéaire (xp / 10)
    val enduranceLevel: Int get() = (enduranceXp / 10).coerceAtMost(10_000)

    // Sprint speed : base 6.5 + 0.10/niveau (6.5 → 16.5 m/s)
    val sprintSpeed: Double get() = BASE_SPRINT_SPEED + speedLevel * 0.10

    // HP calculé par la formule pure : 50 × 1.1^niveau (sans cap externe)
    // Le plafonnement par le dernier mob tué est appliqué dans CaveRenderer.
    val computedMaxHp: Int get() {
        val raw = 50.0 * Math.pow(1.1, enduranceLevel.toDouble())
        return if (raw.isInfinite() || raw >= Int.MAX_VALUE) Int.MAX_VALUE
               else raw.toInt().coerceAtLeast(50)
    }

    // Seuil de chute sûre : base 3 blocs + 0.05/niveau acrobatics (3.0 → 8.0)
    val fallSafeBlocks: Double get() = BASE_FALL_SAFE + acrobaticsLevel * 0.05

    // Vitesse de saut maximale selon niveau athletics (utilisée par PhysicsNode)
    // niveau 0 → 1 bloc, niveau 1000 → 20 blocs ; vitesse = sqrt(2 × G × blocs)
    fun maxJumpBlocks(): Double {
        val ratio = (athleticsLevel / 1000.0).coerceIn(0.0, 1.0)
        return 1.0 + ratio * 19.0
    }

    companion object {
        const val BASE_WALK_SPEED   = 3.5
        const val BASE_SPRINT_SPEED = 6.5
        const val BASE_FALL_SAFE    = 3.0
        const val GRAVITY           = 24.0  // doit rester synchro avec PhysicsNode

        // Courbe n²×5 pour Speed/Acrobatics (cap 100)
        fun xpToLevel100(xp: Int): Int {
            var lv = 1
            while (lv < 100 && lv * lv * 5 <= xp) lv++
            return lv
        }

        // Vitesse de saut pour une hauteur cible en blocs
        fun jumpVyForBlocks(blocks: Double) = sqrt(2.0 * GRAVITY * blocks)
    }
}
