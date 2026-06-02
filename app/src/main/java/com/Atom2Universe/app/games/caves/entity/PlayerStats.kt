package com.Atom2Universe.app.games.caves.entity

import kotlin.math.pow

internal enum class WeaponColor { WHITE, BLUE, ORANGE, RED }
internal enum class WeaponVariant { SQUARE, SWIRL }

internal class WeaponDef(val color: WeaponColor, val variant: WeaponVariant) {
    val assetFile get() = "${variant.name.lowercase()}_${color.name.lowercase()}.png"
    val texIndex  get() = color.ordinal * 2 + variant.ordinal
}

internal class PlayerStats {

    var level    = 1
    var xp       = 0
    var xpToNext = xpRequired(1)

    var maxHp  = 20
    var shield = 0

    fun xpRequired(lvl: Int): Int = (8.0 * lvl.toDouble().pow(1.25)).toInt().coerceAtLeast(1)

    fun addXp(amount: Int) {
        xp += amount
        if (xp >= xpToNext) {
            xp -= xpToNext
            level++
            xpToNext = xpRequired(level)
        }
    }
}
