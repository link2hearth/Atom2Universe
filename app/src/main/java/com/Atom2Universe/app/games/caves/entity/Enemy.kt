package com.Atom2Universe.app.games.caves.entity

import kotlin.math.pow

internal enum class EnemyType(
    val baseHp: Int,
    val speed: Float,
    val damage: Int,
    val attackRange: Double,
    val detectRange: Double,
    val eyeHeight: Float,
    val radius: Float,
    val flies: Boolean,
    val spriteScale: Float
) {
    ZOMBIE  (2, 2.8f, 1, 1.6, 12.0, 1.7f, 0.4f, false, 0.8f),
    SKELETON(2, 4.0f, 1, 1.4, 16.0, 1.7f, 0.35f, false, 0.8f),
    BAT     (1, 5.5f, 1, 1.2, 10.0, 0.4f, 0.35f, true,  0.8f)
}

internal enum class EnemyState { WANDER, CHASE, ATTACK }

internal class Enemy(
    val id: Int,
    val type: EnemyType,
    var x: Double,
    var y: Double,
    var z: Double
) {
    var level: Int = 1
    var spriteFamily: String = ""
    var familyRow: Int = 0
    var isBoss: Boolean = false
    var animTime: Float = 0f

    // Croissance x1.4 par niveau, plafonnée à ×20 (plateau vers level 10)
    private fun scaledHp(): Int {
        val mult = minOf(1.4.pow(level - 1), HP_CAP_MULT)
        return ((type.baseHp * mult) + 0.5).toInt().coerceAtLeast(1)
    }
    val maxHp get() = if (isBoss) scaledHp() * BOSS_HP_MULT else scaledHp()
    val scaledDamage get() = (if (isBoss) type.damage * 3 else type.damage) + (level - 1) / 3
    val scaledSpeed get() = type.speed * (1f + (level - 1) * 0.10f).coerceAtMost(if (isBoss) 2.0f else 3.0f)
    val baseScale get() = if (isBoss) type.spriteScale * BOSS_SPRITE_SCALE else type.spriteScale

    var hp: Int = type.baseHp
    var state = EnemyState.WANDER
    var velY = 0.0
    var onGround = false
    var wanderTimer = 0f
    var wanderDirX = 0f
    var wanderDirZ = 0f
    var attackCooldown = 0f
    var yaw = 0f
    var hitFlash = 0f

    companion object {
        const val BOSS_SPRITE_SCALE = 2.2f
        const val HP_CAP_MULT       = 20.0   // plateau à partir de ~level 10
        const val BOSS_HP_MULT      = 6      // boss = 6× HP d'un mob normal
    }
}
