package com.Atom2Universe.app.games.roguelike

import kotlin.math.roundToInt

/**
 * Le héros : ce qui survit d'une partie à l'autre (équipement, or, potions) et ses
 * caractéristiques façon D&D. Toutes les formules de combat côté joueur vivent ici,
 * pour qu'on les retrouve et les règle au même endroit (voir DONJON.md).
 */
class Hero {

    companion object {
        const val BASE_ATTRIBUTE = 10
        const val BASE_HP        = 40
        const val HP_PER_CON     = 4
        const val SWORD_MIN      = 4
        const val SWORD_MAX      = 7
        const val MAX_POTIONS    = 5
        const val POTION_HEAL    = 0.40f   // part des PV max rendue par une potion
    }

    var hp      = BASE_HP
    var gold    = 0
    var potions = 2
    val equipped = mutableMapOf<EquipSlot, Equipment>()

    private fun equipSum(type: StatType): Float =
        equipped.values.sumOf { e -> e.stats.filter { it.type == type }.sumOf { it.value.toDouble() } }.toFloat()

    // ── Caractéristiques D&D ────────────────────────────────────────────────────

    fun attribute(type: StatType): Int = BASE_ATTRIBUTE + equipSum(type).roundToInt()

    /** Points au-dessus de 10 (jamais négatif pour l'instant : on part de 10). */
    private fun bonus(type: StatType) = attribute(type) - BASE_ATTRIBUTE

    val str get() = attribute(StatType.STR)
    val dex get() = attribute(StatType.DEX)
    val con get() = attribute(StatType.CON)
    val int get() = attribute(StatType.INT)
    val wis get() = attribute(StatType.WIS)
    val cha get() = attribute(StatType.CHA)

    // ── Stats dérivées ──────────────────────────────────────────────────────────

    val maxHp get() = BASE_HP + HP_PER_CON * bonus(StatType.CON) + equipSum(StatType.MAX_HP).roundToInt()
    val armor get() = equipSum(StatType.ARMOR).roundToInt()

    /** Épée : FOR ajoute 4 % de dégâts par point. */
    val swordMin get() = ((SWORD_MIN + equipSum(StatType.WEAPON_DMG)) * strMult).roundToInt()
    val swordMax get() = ((SWORD_MAX + equipSum(StatType.WEAPON_DMG)) * strMult).roundToInt()
    private val strMult get() = 1f + 0.04f * bonus(StatType.STR)

    /** Sorts : INT ajoute 5 % par point, plus les bonus « dégâts des sorts » de l'équipement. */
    val spellMult get() = (1f + 0.05f * bonus(StatType.INT)) * (1f + equipSum(StatType.SPELL_DMG))

    /** Chance de critique de base : 5 % + 1 % par point de DEX. */
    val critChance get() = (0.05f + 0.01f * bonus(StatType.DEX)).coerceIn(0f, 0.6f)

    /** Recharge des sorts : SAG retire un tour tous les 6 points. */
    fun spellCooldown(base: Int) = (base - bonus(StatType.WIS) / 6).coerceAtLeast(1)

    /** La parade s'élargit de 4 ms par point de DEX. */
    val parryBonusMs get() = 4 * bonus(StatType.DEX)

    /** Or gagné : +3 % par point de CHA. */
    val goldMult get() = 1f + 0.03f * bonus(StatType.CHA)

    /** Dégâts réellement subis après armure. */
    fun mitigate(raw: Float): Float = raw * 50f / (50f + armor)

    fun healFull() { hp = maxHp }
    fun heal(amount: Int) { hp = (hp + amount).coerceAtMost(maxHp) }
}
