package com.Atom2Universe.app.games.roguelike

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Timing selects base damage; critical hits are rolled separately from hero statistics. */
internal object StrikeDamage {
    private const val GLANCING_DEFENSE_SHARE = 0.5f
    private const val BOSS_DEFENSE_MULT = 1.25f

    /** Only poorly executed blows face this guard; it never scales with the player's gear. */
    fun defense(type: MonsterType, floor: Int, boss: Boolean): Float {
        val power = LootSystem.powerCenter(floor.coerceAtLeast(1)).roundToInt()
        val toughness = sqrt(type.baseHp.toFloat() / MonsterType.SKELETON.baseHp)
        return AffixBudget.refWeaponDamage(power) * GLANCING_DEFENSE_SHARE * toughness *
            if (boss) BOSS_DEFENSE_MULT else 1f
    }

    fun base(minimum: Int, maximum: Int, timing: Timing): Float {
        val low = minimum.coerceAtLeast(0).toFloat()
        val high = maximum.coerceAtLeast(minimum).coerceAtLeast(0).toFloat()
        return when (timing) {
            Timing.PERFECT -> high
            Timing.GOOD -> low + (high - low) * 0.5f
            Timing.MISS -> low
        }
    }

    fun afterDefense(damage: Float, timing: Timing, defense: Float): Float =
        (damage - if (timing == Timing.MISS) defense else 0f).coerceAtLeast(0f)
}
