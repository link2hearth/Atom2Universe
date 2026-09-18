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
        /**
         * Les PV de base du héros nu. Ils sont volontairement bas : l'essentiel du sac de
         * PV vient des pièces défensives, qui en donnent proportionnellement à leur armure
         * (voir [LootSystem.HP_PER_ARMOR_BASE]). Sans ça, les PV grandissaient bien moins
         * vite que les dégâts des monstres et on finissait par mourir en deux coups à
         * l'étage 100 — voir DONJON.md, « Ce que les PV devaient rattraper ».
         */
        const val BASE_HP        = 28
        const val HP_PER_CON     = 4
        /** Sans arme, on se bat à mains nues. */
        const val FIST_MIN       = 2
        const val FIST_MAX       = 4
        const val MAX_POTIONS    = 5
        const val POTION_HEAL    = 0.40f   // part des PV max rendue par une potion
        const val BASE_CRIT_MULT = 2f
        /**
         * Reliques portées en même temps. Le combat a quatre boutons, façon Pokémon :
         * l'attaque (fixe), deux reliques, et un sort spécial (à venir).
         */
        const val RELIC_SLOTS    = 2
        /**
         * La recharge des reliques ne repart pas à zéro à chaque combat : elle avance d'un
         * tour par tour de combat, par tour de repos, et tous les [RELIC_WALK_STEPS] pas.
         * Sans ça, chaque relique sortait au moins une fois par combat et sa recharge ne
         * coûtait rien (voir DONJON.md, « Les reliques »). Le repos devient la façon de
         * recharger ses sorts — et il reste risqué.
         */
        const val RELIC_WALK_STEPS = 8

        /** Un héros neuf : une épée de bois toute simple, et aucune relique — elles se trouvent. */
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

    /** Les reliques trouvées, dans l'ordre de découverte. On n'en perd jamais. */
    val relics = mutableListOf<Relic>()
    /** Celles qui sont portées : ce sont elles qui ont un bouton en combat. */
    val relicSlots = arrayOfNulls<Relic>(RELIC_SLOTS)
    /** Tours de recharge restants, gardés d'un combat à l'autre. Absent = prête. */
    val relicCooldowns = mutableMapOf<Relic, Int>()
    private var walkSteps = 0

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

    /**
     * La puissance d'un sort : l'épée de référence de la puissance de l'arme portée,
     * multipliée par INT et les bonus des objets. Un coefficient de relique de 1 frappe
     * donc comme une épée normale : le sort suit l'équipement sans table à part.
     */
    val spellPower get() = AffixBudget.refWeaponDamage(equipped[EquipSlot.WEAPON]?.power ?: 1) * spellMult

    /**
     * Le DD des sorts de contrôle, façon D&D : 11 + modificateur d'INT ((INT − 10) / 2) +
     * maîtrise (qui suit la puissance de l'arme portée). Voir [SpellSave].
     */
    val spellDc get() = SpellSave.DC_BASE + Math.floorDiv(attribute(StatType.INT) - BASE_ATTRIBUTE, 2) +
        SpellSave.proficiency(equipped[EquipSlot.WEAPON]?.power ?: 1)

    /** Fourchette de dégâts d'une relique, avant critique. */
    fun relicDamage(relic: Relic): Pair<Int, Int> {
        val lo = (relic.minCoef * spellPower).roundToInt().coerceAtLeast(1)
        val hi = (relic.maxCoef * spellPower).roundToInt().coerceAtLeast(lo)
        return lo to hi
    }

    /** Ce qu'une dose de poison de cette relique ronge par tour. */
    fun poisonDose(relic: Relic) = (relic.doseCoef * spellPower).roundToInt().coerceAtLeast(1)

    /** Chance de critique : 5 % + 1 % par point de DEX + bonus des objets. */
    val critChance get() = (0.05f + 0.01f * bonus(StatType.DEX) + equipSum(StatType.CRIT_CHANCE)).coerceIn(0f, 0.6f)
    val critMult get() = BASE_CRIT_MULT + equipSum(StatType.CRIT_DAMAGE)

    /** Part des dégâts infligés à l'épée rendue en PV. */
    val lifeSteal get() = equipSum(StatType.LIFE_STEAL)

    /** Recharge des sorts : SAG retire un tour tous les 6 points. */
    fun spellCooldown(base: Int) = (base - bonus(StatType.WIS) / 6).coerceAtLeast(1)

    /**
     * La classe d'armure, façon D&D : 10 + maîtrise (celle des pièces d'armure portées) +
     * les bonus des pièces (léger, bouclier) + le modificateur de DEX — sauf si une pièce
     * lourde est portée : la DEX ne compte plus. Voir [ArmorClass].
     */
    val armorClass: Int get() {
        val pieces = listOf(EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS).mapNotNull { equipped[it] }
        val power = if (pieces.isEmpty()) 1 else Math.round(pieces.map { it.power }.average()).toInt()
        val dexCounts = pieces.none { it.weight?.dexCounts == false }
        val dex = if (dexCounts) Math.floorDiv(attribute(StatType.DEX) - BASE_ATTRIBUTE, 2) else 0
        return ArmorClass.BASE + SpellSave.proficiency(power) + equipped.values.sumOf { it.acBonus } + dex
    }

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

    // ── Reliques ────────────────────────────────────────────────────────────────

    /** Une relique trouvée : elle prend un emplacement libre s'il y en a un. Vrai si portée. */
    fun addRelic(relic: Relic): Boolean {
        if (relic !in relics) relics += relic
        if (relic in relicSlots) return true
        val free = relicSlots.indexOfFirst { it == null }
        if (free < 0) return false
        relicSlots[free] = relic
        return true
    }

    fun relicCooldown(relic: Relic) = relicCooldowns[relic] ?: 0

    /** Toutes les recharges avancent de [turns] tours. */
    fun tickRelics(turns: Int = 1) {
        val it = relicCooldowns.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.setValue(e.value - turns)
            if (e.value <= 0) it.remove()
        }
    }

    /** Un pas sur la carte : la recharge avance, lentement. */
    fun walkRelics() {
        if (++walkSteps >= RELIC_WALK_STEPS) { walkSteps = 0; tickRelics() }
    }

    val relicsRecharging get() = relicSlots.any { it != null && relicCooldown(it) > 0 }

    enum class RelicToggle { EQUIPPED, REMOVED, SLOTS_FULL }

    /** Porter ou ranger une relique. Pleins, les emplacements refusent : on range d'abord. */
    fun toggleRelic(relic: Relic): RelicToggle {
        val at = relicSlots.indexOf(relic)
        if (at >= 0) { relicSlots[at] = null; return RelicToggle.REMOVED }
        if (relic !in relics) return RelicToggle.SLOTS_FULL
        val free = relicSlots.indexOfFirst { it == null }
        if (free < 0) return RelicToggle.SLOTS_FULL
        relicSlots[free] = relic
        return RelicToggle.EQUIPPED
    }

    // ── Équipement ──────────────────────────────────────────────────────────────

    /** Équipe [item] ; ce qui était porté part dans le sac. */
    fun equip(item: Equipment) {
        equipped.put(item.slot, item)?.let { bag += it }
        hp = hp.coerceAtMost(maxHp)
    }
}
