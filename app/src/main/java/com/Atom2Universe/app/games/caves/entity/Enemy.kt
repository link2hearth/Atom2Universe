package com.Atom2Universe.app.games.caves.entity

internal enum class EnemyType(
    val maxHp: Int,
    val speed: Float,
    val damage: Int,
    val attackRange: Double,
    val detectRange: Double,
    val eyeHeight: Float,
    val radius: Float,
    val flies: Boolean,
    val spriteWidth: Float,
    val spriteHeight: Float
) {
    ZOMBIE  (6, 2.8f, 1, 1.6, 12.0, 1.7f, 0.4f, false, 1.0f, 2.0f),
    SKELETON(4, 4.0f, 1, 1.4, 16.0, 1.7f, 0.35f, false, 0.9f, 2.0f),
    BAT     (3, 5.5f, 1, 1.2, 10.0, 0.4f, 0.35f, true,  1.6f, 0.8f)
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

    /** PV max scalés selon le niveau. */
    val maxHp get() = type.maxHp * level

    /** Dégâts scalés : +1 toutes les 3 niveaux. */
    val scaledDamage get() = type.damage + (level - 1) / 3

    /** Vitesse scalée : +5% par niveau, plafonnée à ×2. */
    val scaledSpeed get() = type.speed * (1f + (level - 1) * 0.05f).coerceAtMost(2f)

    var hp: Int = type.maxHp   // initialisé avant que le level soit fixé, recalculé dans EnemyManager
    var state = EnemyState.WANDER
    var velY = 0.0
    var onGround = false
    var wanderTimer = 0f
    var wanderDirX = 0f
    var wanderDirZ = 0f
    var attackCooldown = 0f
    var yaw = 0f
    var hitFlash = 0f
}
