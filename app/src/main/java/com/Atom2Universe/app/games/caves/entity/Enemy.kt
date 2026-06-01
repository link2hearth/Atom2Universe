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
    var spriteFamily: String = ""
    var familyRow: Int = 0
    var isBoss: Boolean = false
    var animTime: Float = 0f

    private fun scaledHp(): Int {
        val mult = minOf(def.hpScalePerLevel.pow(level - 1), def.hpScaleCap)
        return ((def.hpBase * mult) + 0.5).toInt().coerceAtLeast(1)
    }
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

    companion object {
        const val BOSS_SPRITE_SCALE = 2.2f
        const val BOSS_HP_MULT      = 6
    }
}
