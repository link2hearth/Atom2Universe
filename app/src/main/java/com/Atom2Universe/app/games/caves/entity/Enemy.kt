package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.MobDef
import kotlin.math.pow

internal enum class EnemyState { WANDER, CHASE, ATTACK }

internal class Enemy(
    val id: Int,
    val def: MobDef,
    var x: Double,
    var y: Double,
    var z: Double
) {
    var level: Int = 1
    var isBoss: Boolean = false
    var animTime: Float = 0f

    // HP = hpBase × level² : linéaire au carré, sans cap, calibré à ~500 HP à level 10 (hpBase=5)
    private fun scaledHp(): Int = (def.hpBase.toLong() * level * level).toInt().coerceAtLeast(1)
    val maxHp get() = if (isBoss) scaledHp() * BOSS_HP_MULT else scaledHp()
    val scaledDamage get() = (if (isBoss) def.damageBase * 3 else def.damageBase) + (level - 1) / 3
    val scaledSpeed get() = def.speed * (1f + (level - 1) * def.speedScalePerLevel).coerceAtMost(if (isBoss) 2.0f else 3.0f)
    val baseScale get() = if (isBoss) def.spriteScale * BOSS_SPRITE_SCALE else def.spriteScale

    var hp: Int = def.hpBase
    var state = EnemyState.WANDER
    var velY = 0.0
    var onGround = false
    var wanderTimer = 0f
    var wanderDirX = 0f
    var wanderDirZ = 0f
    var attackCooldown = 0f
    var yaw = 0f
    var hitFlash = 0f
    var stuckTimer = 0f

    // Recul horizontal quand le joueur inflige des dégâts — vitesse amortie.
    var knockX = 0.0
    var knockZ = 0.0

    // Effets de statut appliqués par les affixes d'arme
    // Saignement : jauge qui monte à chaque proc et explose en gros dégâts à 100
    // (façon Dark Souls), puis redescend seule si le mob n'est plus touché.
    var bleedBuildup: Float = 0f
    var bleedDecayGrace: Float = 0f

    var freezeTimer: Float = 0f      // gel : immobilisation totale, aucune IA
    var confusionTimer: Float = 0f   // électrique : attaque ses alliés au lieu du joueur

    var poisonTimer: Float = 0f
    var poisonDamage: Int = 0
    var poisonTickTimer: Float = 0f

    var fireTimer: Float = 0f
    var fireDamage: Int = 0
    var fireTickTimer: Float = 0f

    var alertPlayed: Boolean = false

    companion object {
        const val BOSS_SPRITE_SCALE = 2.2f
        const val BOSS_HP_MULT      = 6
        const val BLEED_BURST_THRESHOLD = 100f
        const val BLEED_BURST_FRACTION  = 0.30f   // % des PV max infligés d'un coup
        const val BLEED_DECAY_PER_SEC   = 12f     // vitesse à laquelle la jauge redescend
    }
}
