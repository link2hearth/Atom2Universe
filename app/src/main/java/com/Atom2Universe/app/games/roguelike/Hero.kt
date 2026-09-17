package com.Atom2Universe.app.games.roguelike

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Le héros : ce qui survit d'une partie à l'autre (équipement, sac, or, potions) et ses
 * caractéristiques façon D&D. Toutes les formules de combat côté joueur vivent ici,
 * pour qu'on les retrouve et les règle au même endroit (voir DONJON.md).
 */
class Hero {

    companion object {
        const val BASE_ATTRIBUTE = 10
        const val BASE_HP        = 40
        const val HP_PER_CON     = 4
        /** Sans arme, on se bat à mains nues. */
        const val FIST_MIN       = 2
        const val FIST_MAX       = 4
        const val MAX_POTIONS    = 5
        const val POTION_HEAL    = 0.40f   // part des PV max rendue par une potion
        const val BASE_CRIT_MULT = 2f

        /** Un héros neuf : une épée de bois toute simple. */
        fun starter(): Hero = Hero().apply {
            val sword = LootSystem.create(ItemBase.SWORD, Material.LEATHER, 1, Rarity.NORMAL, nextLootId++, Random(0))
            equipped[EquipSlot.WEAPON] = sword
            healFull()
        }
    }

    var hp      = BASE_HP
    var gold    = 0
    var potions = 2
    val equipped = mutableMapOf<EquipSlot, Equipment>()
    /** Le sac est infini : rien ne se perd, aucune gestion imposée. */
    val bag = mutableListOf<Equipment>()
    /** Compteur de ramassage, pour trier « dernier looté en premier ». */
    var nextLootId = 1L

    private fun equipSum(type: StatType): Float = equipped.values.sumOf { it.sum(type).toDouble() }.toFloat()

    // ── Caractéristiques D&D ────────────────────────────────────────────────────

    fun attribute(type: StatType): Int = BASE_ATTRIBUTE + equipSum(type).roundToInt()

    /** Points au-dessus de 10. */
    private fun bonus(type: StatType) = attribute(type) - BASE_ATTRIBUTE

    // ── Stats dérivées ──────────────────────────────────────────────────────────

    val maxHp get() = BASE_HP + HP_PER_CON * bonus(StatType.CON) + equipSum(StatType.MAX_HP).roundToInt()
    val armor get() = equipped.values.sumOf { it.armor } + equipSum(StatType.ARMOR).roundToInt()

    /** Dégâts de l'arme portée (ou des poings), plus les bonus, puis FOR : +4 % par point. */
    val weaponMin get() = (((equipped[EquipSlot.WEAPON]?.damageMin ?: FIST_MIN) + equipSum(StatType.WEAPON_DMG)) * strMult).roundToInt()
    val weaponMax get() = (((equipped[EquipSlot.WEAPON]?.damageMax ?: FIST_MAX) + equipSum(StatType.WEAPON_DMG)) * strMult).roundToInt()
    private val strMult get() = 1f + 0.04f * bonus(StatType.STR)

    /** Sorts : INT ajoute 5 % par point, plus les bonus « dégâts des sorts » des objets. */
    val spellMult get() = (1f + 0.05f * bonus(StatType.INT)) * (1f + equipSum(StatType.SPELL_DMG))

    /** Chance de critique : 5 % + 1 % par point de DEX + bonus des objets. */
    val critChance get() = (0.05f + 0.01f * bonus(StatType.DEX) + equipSum(StatType.CRIT_CHANCE)).coerceIn(0f, 0.6f)
    val critMult get() = BASE_CRIT_MULT + equipSum(StatType.CRIT_DAMAGE)

    /** Part des dégâts infligés à l'épée rendue en PV. */
    val lifeSteal get() = equipSum(StatType.LIFE_STEAL)

    /** Recharge des sorts : SAG retire un tour tous les 6 points. */
    fun spellCooldown(base: Int) = (base - bonus(StatType.WIS) / 6).coerceAtLeast(1)

    /** La parade s'élargit de 4 ms par point de DEX. */
    val parryBonusMs get() = 4 * bonus(StatType.DEX)

    /** Or gagné : +3 % par point de CHA. */
    val goldMult get() = 1f + 0.03f * bonus(StatType.CHA)

    /**
     * Dégâts réellement subis après armure. L'armure se mesure à l'étage : la même armure
     * protège moins face à des monstres plus profonds.
     */
    fun mitigate(raw: Float, floor: Int): Float {
        val k = 50f * LootSystem.scale(LootSystem.powerCenter(floor).roundToInt())
        return raw * k / (k + armor)
    }

    fun healFull() { hp = maxHp }
    fun heal(amount: Int) { hp = (hp + amount).coerceAtMost(maxHp) }

    // ── Équipement ──────────────────────────────────────────────────────────────

    /** Équipe [item] ; ce qui était porté part dans le sac. */
    fun equip(item: Equipment) {
        equipped.put(item.slot, item)?.let { bag += it }
        hp = hp.coerceAtMost(maxHp)
    }
}
