package com.Atom2Universe.app.games.caves.node

internal class PlayerNode(maxHp: Int = 50, maxShield: Int = 0) {

    var hp: Int = maxHp
    var maxHp: Int = maxHp
    var shield: Int = 0
    var maxShield: Int = maxShield
    var shieldRechargeTimer: Float = 0f

    var onHpChanged:     ((hp: Int, maxHp: Int) -> Unit)? = null
    var onShieldChanged: ((current: Int, max: Int) -> Unit)? = null
    var onEnduranceXp:   ((xp: Int) -> Unit)? = null

    val isAlive: Boolean get() = hp > 0

    fun applyDamage(damage: Int): Boolean {
        val shAbsorb = minOf(shield, damage)
        shield -= shAbsorb
        val hpDmg = damage - shAbsorb
        if (hpDmg > 0) hp = (hp - hpDmg).coerceAtLeast(0)
        if (shAbsorb > 0 || hpDmg > 0) {
            shieldRechargeTimer = SHIELD_RECHARGE_DELAY
            onShieldChanged?.invoke(shield, maxShield)
        }
        onHpChanged?.invoke(hp, maxHp)
        if (damage > 0) onEnduranceXp?.invoke(damage)
        return hp <= 0
    }

    fun applyHeal(amount: Int) {
        val actual = (maxHp - hp).coerceAtLeast(0).coerceAtMost(amount)
        hp = (hp + actual).coerceAtMost(maxHp)
        onHpChanged?.invoke(hp, maxHp)
        if (actual > 0) onEnduranceXp?.invoke(actual)
    }

    fun tickShield(dt: Float) {
        if (maxShield <= 0 || shield >= maxShield) return
        shieldRechargeTimer -= dt
        if (shieldRechargeTimer <= 0f) {
            shield = maxShield
            onShieldChanged?.invoke(shield, maxShield)
        }
    }

    fun setMaxHp(value: Int, healDelta: Int = 0) {
        maxHp = value
        hp = (hp + healDelta).coerceAtMost(maxHp)
        onHpChanged?.invoke(hp, maxHp)
    }

    fun setMaxShield(value: Int, rechargeDelta: Int = 0) {
        maxShield = value
        shield = (shield + rechargeDelta).coerceAtMost(maxShield)
        onShieldChanged?.invoke(shield, maxShield)
    }

    companion object {
        const val SHIELD_RECHARGE_DELAY = 10f
    }
}
