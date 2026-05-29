package com.Atom2Universe.app.games.caves.entity

import kotlin.math.pow
import kotlin.random.Random

internal enum class WeaponColor { WHITE, BLUE, ORANGE, RED }
internal enum class WeaponVariant { SQUARE, SWIRL }

internal class WeaponDef(val color: WeaponColor, val variant: WeaponVariant) {
    val assetFile get() = "${variant.name.lowercase()}_${color.name.lowercase()}.png"
    val texIndex  get() = color.ordinal * 2 + variant.ordinal
}

internal enum class UpgradeType {
    DAMAGE, FIRE_RATE, MAX_HP, SHIELD,
    WEAPON_WHITE_SWIRL,
    WEAPON_BLUE_SQUARE, WEAPON_BLUE_SWIRL,
    WEAPON_ORANGE_SQUARE, WEAPON_ORANGE_SWIRL,
    WEAPON_RED_SQUARE, WEAPON_RED_SWIRL
}

internal class UpgradeOption(val type: UpgradeType, val isRare: Boolean)

internal class PlayerStats {

    var level    = 1
    var xp       = 0
    var xpToNext = xpRequired(1)

    var damage   = 2       // dégâts par projectile
    var fireRate = 1.5f    // secondes entre tirs par arme

    var maxHp  = 20
    var shield = 0         // points de bouclier max

    val weapons     = mutableListOf(WeaponDef(WeaponColor.WHITE, WeaponVariant.SQUARE))
    val shootTimers = mutableListOf(0f)

    fun xpRequired(lvl: Int): Int = (8.0 * lvl.toDouble().pow(1.25)).toInt().coerceAtLeast(1)

    /** Retourne true si level up. */
    fun addXp(amount: Int): Boolean {
        xp += amount
        if (xp >= xpToNext) {
            xp -= xpToNext
            level++
            xpToNext = xpRequired(level)
            return true
        }
        return false
    }

    fun applyUpgrade(type: UpgradeType) {
        when (type) {
            UpgradeType.DAMAGE         -> damage++
            UpgradeType.FIRE_RATE      -> fireRate = (fireRate * 0.80f).coerceAtLeast(0.30f)
            UpgradeType.MAX_HP         -> maxHp += 4
            UpgradeType.SHIELD         -> shield += 5
            UpgradeType.WEAPON_WHITE_SWIRL   -> unlock(WeaponColor.WHITE,  WeaponVariant.SWIRL)
            UpgradeType.WEAPON_BLUE_SQUARE   -> unlock(WeaponColor.BLUE,   WeaponVariant.SQUARE)
            UpgradeType.WEAPON_BLUE_SWIRL    -> unlock(WeaponColor.BLUE,   WeaponVariant.SWIRL)
            UpgradeType.WEAPON_ORANGE_SQUARE -> unlock(WeaponColor.ORANGE, WeaponVariant.SQUARE)
            UpgradeType.WEAPON_ORANGE_SWIRL  -> unlock(WeaponColor.ORANGE, WeaponVariant.SWIRL)
            UpgradeType.WEAPON_RED_SQUARE    -> unlock(WeaponColor.RED,    WeaponVariant.SQUARE)
            UpgradeType.WEAPON_RED_SWIRL     -> unlock(WeaponColor.RED,    WeaponVariant.SWIRL)
        }
    }

    private fun unlock(color: WeaponColor, variant: WeaponVariant) {
        if (weapons.none { it.color == color && it.variant == variant }) {
            weapons.add(WeaponDef(color, variant))
            shootTimers.add(0f)
        }
    }

    fun pickThreeUpgrades(rng: Random): List<UpgradeOption> {
        val pool = buildPool().toMutableList()
        val result = mutableListOf<UpgradeOption>()
        repeat(3) {
            if (pool.isEmpty()) return result
            val pick = pool[rng.nextInt(pool.size)]
            result.add(pick)
            pool.removeAll { it.type == pick.type }
        }
        return result
    }

    private fun buildPool(): List<UpgradeOption> {
        val pool = mutableListOf<UpgradeOption>()
        repeat(3) { pool.add(UpgradeOption(UpgradeType.DAMAGE,    false)) }
        repeat(3) { pool.add(UpgradeOption(UpgradeType.FIRE_RATE, false)) }
        repeat(3) { pool.add(UpgradeOption(UpgradeType.MAX_HP,    false)) }
        if (shield < 30) repeat(3) { pool.add(UpgradeOption(UpgradeType.SHIELD, false)) }
        val rares = listOf(
            UpgradeType.WEAPON_WHITE_SWIRL   to (WeaponColor.WHITE  to WeaponVariant.SWIRL),
            UpgradeType.WEAPON_BLUE_SQUARE   to (WeaponColor.BLUE   to WeaponVariant.SQUARE),
            UpgradeType.WEAPON_BLUE_SWIRL    to (WeaponColor.BLUE   to WeaponVariant.SWIRL),
            UpgradeType.WEAPON_ORANGE_SQUARE to (WeaponColor.ORANGE to WeaponVariant.SQUARE),
            UpgradeType.WEAPON_ORANGE_SWIRL  to (WeaponColor.ORANGE to WeaponVariant.SWIRL),
            UpgradeType.WEAPON_RED_SQUARE    to (WeaponColor.RED    to WeaponVariant.SQUARE),
            UpgradeType.WEAPON_RED_SWIRL     to (WeaponColor.RED    to WeaponVariant.SWIRL),
        )
        for ((type, cv) in rares) {
            if (weapons.none { it.color == cv.first && it.variant == cv.second })
                pool.add(UpgradeOption(type, true))
        }
        return pool
    }
}
