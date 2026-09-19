package com.Atom2Universe.app.games.roguelike

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * L'archétype, façon Diablo : **le stuff fait la classe**. Il vient de l'armure portée —
 * [Hero.ARCHETYPE_PIECES] pièces du même poids sur trois (casque, armure, bottes) — en
 * attendant les sets. Il donne un bonus sur la parade parfaite et le bouton « Spécial »
 * du combat (voir [Combat]) :
 *  - Guerrier (lourd) : **blocage** au bouclier, et **Garde** (fenêtre de parade doublée) ;
 *  - Voleur (léger) : **esquive + riposte**, et **Coup mortel** sur une cible exposée ;
 *  - Mage (tissu) : **contresort** (une recharge de relique gagnée), et **Image miroir**.
 */
enum class Archetype(
    @StringRes override val labelRes: Int,
    @StringRes val specialRes: Int,
    val weight: ArmorWeight,
    val color: Int,
) : Labeled {
    WARRIOR(R.string.roguelike_archetype_warrior, R.string.roguelike_special_guard,  ArmorWeight.HEAVY, 0xFF8D6E63.toInt()),
    ROGUE  (R.string.roguelike_archetype_rogue,   R.string.roguelike_special_deadly, ArmorWeight.LIGHT, 0xFF546E7A.toInt()),
    MAGE   (R.string.roguelike_archetype_mage,    R.string.roguelike_special_mirror, ArmorWeight.CLOTH, 0xFF5E35B1.toInt()),
}

/**
 * Le héros : ce qui survit d'une partie à l'autre (équipement, sac, or, reliques) et ses
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
        /** Ce que rapporte un point au-dessus de 10 (l'inventaire affiche ces mêmes chiffres). */
        const val STR_DAMAGE_PER_POINT = 0.04f
        const val RELIC_DAMAGE_PER_POINT = 0.05f
        const val WIS_POINTS_PER_TURN = 6
        const val GOLD_PER_CHA = 0.03f
        /** Le critique de départ, et ce que rapporte un point de DEX. */
        const val BASE_CRIT = 0.05f
        const val CRIT_PER_DEX = 0.01f
        /** Ce qu'un point de DEX ajoute à la fenêtre de parade, en millisecondes. */
        const val PARRY_MS_PER_DEX = 4
        /** Sans arme, on se bat à mains nues. */
        const val FIST_MIN       = 2
        const val FIST_MAX       = 4
        const val BASE_CRIT_MULT = 2f
        /**
         * Reliques portées en même temps, au plus. Le combat a six boutons : l'attaque et le
         * Spécial (fixes), et quatre reliques. Le premier emplacement est là dès le départ (il
         * se remplit avec la première relique trouvée), les autres s'ouvrent en descendant :
         * voir [RELIC_SLOT_FLOORS] et DONJON.md, « Plus de potion ».
         */
        const val RELIC_SLOTS    = 4
        /** L'étage qu'il faut avoir atteint une fois pour ouvrir le 2ᵉ, le 3ᵉ et le 4ᵉ emplacement. */
        val RELIC_SLOT_FLOORS = intArrayOf(50, 100, 500)
        /**
         * La recharge des reliques ne repart pas à zéro à chaque combat : elle avance d'un
         * tour par tour de combat, par tour de repos, et tous les [RELIC_WALK_STEPS] pas.
         * Sans ça, chaque relique sortait au moins une fois par combat et sa recharge ne
         * coûtait rien (voir DONJON.md, « Les reliques »). Le repos devient la façon de
         * recharger ses sorts — et il reste risqué.
         */
        const val RELIC_WALK_STEPS = 8
        /** Pièces d'armure du même poids qu'il faut porter pour avoir un archétype. */
        const val ARCHETYPE_PIECES = 2
        /** Recharge du bouton « Spécial », gardée d'un combat à l'autre comme les reliques. */
        const val SPECIAL_COOLDOWN = 5
        /** L'orbe est la main gauche de celui qui tue vite : tous les dégâts qu'il inflige, arme et sorts, montent de cette part. */
        const val ORB_DAMAGE_SHARE = 0.20f
        /** La vitesse ne descend jamais sous ça, quoi qu'on porte. */
        const val MIN_SPEED = 0.5f
        /** La chance de critique réelle ne dépasse jamais ça : les objets ne doivent pas y suffire seuls. */
        const val MAX_CRIT = 0.6f

        fun affinityKey(type: MonsterType, element: Element) = "${type.name}:${element.name}"

        /** Un héros neuf : une épée d'Hydrogène toute simple, et aucune relique — elles se trouvent. */
        fun starter(): Hero = Hero().apply {
            val sword = LootSystem.create(ItemBase.SWORD, 1, Rarity.NORMAL, nextLootId++, Random(0))
            equipped[EquipSlot.WEAPON] = sword
            healFull()
        }
    }

    var hp      = BASE_HP
    var gold    = 0
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
    /** Tours avant que le « Spécial » soit prêt. */
    var specialCooldown = 0
    /** Les résonances déjà portées une fois : l'inventaire les liste, les autres restent cachées. */
    val knownResonances = mutableSetOf<Resonance>()
    /**
     * Ce que le lexique ne montre qu'une fois vu : les réactions déclenchées, et les affinités
     * des monstres (« TYPE:ELEMENT ») testées d'un sort. Rien ne les annonce en jeu, le lexique
     * ne doit pas les révéler d'avance.
     */
    val knownReactions = mutableSetOf<Reaction>()
    val knownAffinities = mutableSetOf<String>()
    /** Les sets d'isotope dont une pièce est déjà tombée (leur numéro atomique) : le lexique ne les montre qu'alors. */
    val knownSets = mutableSetOf<Int>()

    /** Un sort vient de frapper [type] : on retient l'affinité testée et les réactions vues. */
    fun discover(type: MonsterType, element: Element, reactions: Collection<Reaction>) {
        knownAffinities += affinityKey(type, element)
        knownReactions += reactions
    }

    /** L'étage le plus profond jamais atteint : il ouvre les emplacements de relique. On ne le perd pas en mourant. */
    var deepestFloor = 1

    /** Les emplacements de relique ouverts : un au départ, puis aux étages [RELIC_SLOT_FLOORS]. */
    val unlockedRelicSlots: Int get() = 1 + RELIC_SLOT_FLOORS.count { deepestFloor >= it }

    /** Le premier emplacement ouvert et vide, ou -1. */
    private fun freeRelicSlot() = (0 until unlockedRelicSlots).firstOrNull { relicSlots[it] == null } ?: -1

    /** Les résonances des reliques portées (voir [Resonance]). */
    val resonances: List<Resonance> get() = Resonance.active(relicSlots.filterNotNull())

    /** L'archétype que donne l'armure portée, ou null sans majorité. */
    val archetype: Archetype? get() {
        val weights = listOf(EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS).mapNotNull { equipped[it]?.weight }
        return Archetype.entries.firstOrNull { a -> weights.count { it == a.weight } >= ARCHETYPE_PIECES }
    }

    /**
     * L'archétype du set d'isotope porté : les trois pièces (casque, armure, bottes) sont des pièces de
     * sets du **même archétype**, même de sets différents. Null sinon. Il améliore le Spécial
     * ([specialBoosted]) et donne la stat de base de l'archétype (voir [IsotopeSets]).
     */
    val setArchetype: Archetype? get() {
        val sets = IsotopeSets.SLOTS.map { equipped[it]?.isotopeSet ?: return null }
        val a = sets.first().archetype
        return if (sets.all { it.archetype == a }) a else null
    }

    /** Le Spécial de cet archétype est-il amélioré par le set porté ? */
    fun specialBoosted(a: Archetype) = setArchetype == a

    val hasShield get() = equipped[EquipSlot.OFFHAND]?.base == ItemBase.SHIELD

    private fun equipSum(type: StatType): Float = equipped.values.sumOf { it.sum(type).toDouble() }.toFloat()

    // ── Caractéristiques D&D ────────────────────────────────────────────────────

    /** La caractéristique : la base, l'équipement, et les résonances des reliques portées. */
    fun attribute(type: StatType): Int = BASE_ATTRIBUTE + equipSum(type).roundToInt() +
        Resonance.BONUS * resonances.count { it.attribute == type }

    /** Points au-dessus de 10. */
    private fun bonus(type: StatType) = attribute(type) - BASE_ATTRIBUTE

    /**
     * L'étage où se trouve le héros (le combat le règle aussi) : ses monstres résistent à ses
     * caractéristiques, voir [LootSystem.attributeWeight].
     */
    var floor = 1

    /** Ce que vaut un point de caractéristique à cet étage : 1 jusqu'à l'étage 100, moins au-delà. */
    val attributeWeight get() = LootSystem.attributeWeightAt(floor)

    /**
     * Les points au-dessus de 10 **qui comptent à cet étage** : pour tous les effets d'une
     * caractéristique (dégâts, jets, critique, parade, recharges, or), sauf les PV de la CON,
     * qui sont des PV comme les autres.
     */
    private fun effective(type: StatType) = bonus(type) * attributeWeight

    // ── Stats dérivées ──────────────────────────────────────────────────────────

    val maxHp: Int get() {
        val plain = BASE_HP + HP_PER_CON * bonus(StatType.CON) + equipSum(StatType.MAX_HP).roundToInt()
        return (plain * (1f + if (specialBoosted(Archetype.WARRIOR)) IsotopeSets.HP_SHARE else 0f)).roundToInt()
    }

    /** La puissance moyenne du casque, de l'armure et des bottes portés (1 sans armure). */
    private val armorPower: Int get() {
        val pieces = listOf(EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS).mapNotNull { equipped[it] }
        return if (pieces.isEmpty()) 1 else Math.round(pieces.map { it.power }.average()).toInt()
    }

    val armor get() = equipped.values.sumOf { it.armor } + equipSum(StatType.ARMOR).roundToInt()

    /** Dégâts de l'arme portée (ou des poings), plus les bonus, puis FOR : +4 % par point. */
    val weaponMin get() = (((equipped[EquipSlot.WEAPON]?.damageMin ?: FIST_MIN) + equipSum(StatType.WEAPON_DMG)) * strMult * orbMult).roundToInt()
    val weaponMax get() = (((equipped[EquipSlot.WEAPON]?.damageMax ?: FIST_MAX) + equipSum(StatType.WEAPON_DMG)) * strMult * orbMult).roundToInt()
    private val orbMult get() = if (equipped[EquipSlot.OFFHAND]?.base == ItemBase.ORB) 1f + ORB_DAMAGE_SHARE else 1f
    private val strMult get() = 1f + STR_DAMAGE_PER_POINT * effective(StatType.STR)

    /**
     * Le multiplicateur d'une relique : **sa** caractéristique ([Relic.attribute] — INT
     * pour le mage, DEX pour le Venin du voleur) ajoute 5 % par point, puis les bonus
     * « dégâts des sorts » des objets.
     */
    fun relicMult(relic: Relic) = orbMult * (1f + RELIC_DAMAGE_PER_POINT * effective(relic.attribute)) * (1f + equipSum(StatType.SPELL_DMG) + if (specialBoosted(Archetype.MAGE)) IsotopeSets.SPELL_SHARE else 0f)

    /**
     * La puissance d'une relique : l'épée de référence de la puissance de l'arme portée,
     * multipliée par [relicMult]. Un coefficient de relique de 1 frappe donc comme une épée
     * normale : le sort suit l'équipement sans table à part.
     */
    fun relicPower(relic: Relic) = AffixBudget.refWeaponDamage(equipped[EquipSlot.WEAPON]?.power ?: 1) * relicMult(relic)

    /**
     * Le DD d'une relique, façon D&D : 11 + modificateur de **sa** caractéristique
     * ((carac − 10) / 2) + maîtrise (qui suit la puissance de l'arme portée). Voir [SpellSave].
     */
    fun spellDc(relic: Relic) = SpellSave.DC_BASE + modifier(relic.attribute) +
        SpellSave.proficiency(equipped[EquipSlot.WEAPON]?.power ?: 1)

    /** Fourchette de dégâts d'une relique, avant critique. */
    fun relicDamage(relic: Relic): Pair<Int, Int> {
        val power = relicPower(relic)
        val lo = (relic.minCoef * power).roundToInt().coerceAtLeast(1)
        val hi = (relic.maxCoef * power).roundToInt().coerceAtLeast(lo)
        return lo to hi
    }

    /** Ce qu'une dose de poison de cette relique ronge par tour. */
    fun poisonDose(relic: Relic) = (relic.doseCoef * relicPower(relic)).roundToInt().coerceAtLeast(1)

    /** Ce que le saignement de cette relique ronge à chaque attaque de la cible. */
    fun bleedDamage(relic: Relic) = (RelicBudget.bleedCoef(relic) * relicPower(relic)).roundToInt().coerceAtLeast(1)

    /**
     * La quantité propre à une relique, celle qu'affiche sa description : la barrière du
     * Bouclier arcanique et le soin par tour de la Régénération (en part des PV max, relevés
     * par la caractéristique), le saignement, les épines en %.
     */
    fun relicAmount(relic: Relic): Int = when (relic.effect) {
        RelicEffect.BARRIER -> (maxHp * Relic.BARRIER_SHARE * relicMult(relic)).roundToInt().coerceAtLeast(1)
        RelicEffect.REGEN   -> (maxHp * Relic.REGEN_SHARE * relicMult(relic)).roundToInt().coerceAtLeast(1)
        RelicEffect.HEAL    -> (maxHp * Relic.HEAL_SHARE).roundToInt().coerceAtLeast(1)
        RelicEffect.BLEED, RelicEffect.BLEED_ON_CRIT -> bleedDamage(relic)
        RelicEffect.STONESKIN -> (Relic.THORNS_SHARE * 100).roundToInt()
        else -> 0
    }

    /**
     * Le critique du héros, sans plafond : 5 % + 1 % par point de DEX + bonus des objets. En
     * profondeur, il peut dépasser 100 % : les monstres y résistent (voir [critChance]).
     */
    val critRating get() = BASE_CRIT + CRIT_PER_DEX * effective(StatType.DEX) + equipSum(StatType.CRIT_CHANCE)

    /**
     * La vraie chance de critique à l'étage [floor] : le critique du héros moins la résistance
     * des monstres de l'étage (nulle jusqu'à l'étage 100 environ), bornée à [MAX_CRIT].
     */
    fun critChance(floor: Int) = (critRating -
        AffixBudget.critResistance(LootSystem.powerCenter(floor).roundToInt())).coerceIn(0f, MAX_CRIT)
    val critMult get() = BASE_CRIT_MULT + equipSum(StatType.CRIT_DAMAGE)

    /** Part des dégâts infligés à l'épée rendue en PV. */
    val lifeSteal get() = equipSum(StatType.LIFE_STEAL)

    /** Recharge des sorts : SAG retire un tour tous les 6 points. */
    fun spellCooldown(base: Int) = (base - (effective(StatType.WIS) / WIS_POINTS_PER_TURN).toInt()).coerceAtLeast(1)

    /**
     * La recharge d'une relique après son lancer : la SAG la raccourcit, jamais sous
     * [Relic.minCooldown] (le Soin garde au moins 3 tours, sinon une grosse SAG le relancerait à
     * chaque tour et le héros ne mourrait plus).
     */
    fun castCooldown(relic: Relic) = spellCooldown(relic.cooldown).coerceAtLeast(relic.minCooldown)

    /** Le modificateur de D&D, (carac − 10) / 2 arrondi vers le bas, sur les points qui comptent à cet étage. */
    private fun modifier(type: StatType) = kotlin.math.floor(effective(type) / 2f).toInt()

    /**
     * La classe d'armure, façon D&D : 10 + maîtrise (celle des pièces d'armure portées) +
     * les bonus des pièces (léger, bouclier) + le modificateur de DEX — sauf si une pièce
     * lourde est portée : la DEX ne compte plus. Voir [ArmorClass].
     */
    val armorClass: Int get() {
        val pieces = listOf(EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS).mapNotNull { equipped[it] }
        val dexCounts = pieces.none { it.weight?.dexCounts == false }
        val dex = if (dexCounts) modifier(StatType.DEX) else 0
        return ArmorClass.BASE + SpellSave.proficiency(armorPower) + equipped.values.sumOf { it.acBonus } + dex
    }

    /**
     * Ce que le joueur lit à la place de la CA (jargon de D&D) : la chance que les monstres
     * de cet étage le ratent. Un point de CA vaut 5 points d'esquive.
     */
    fun dodgeChance(floor: Int) = 1f - ArmorClass.hitChance(armorClass, ArmorClass.monsterAttack(floor))

    /** La parade s'élargit de 4 ms par point de DEX. */
    val parryBonusMs get() = (PARRY_MS_PER_DEX * effective(StatType.DEX)).roundToInt()

    /**
     * La vitesse du héros : ce que sa jauge gagne par unité de temps (1 : normale). Les
     * affixes « Vitesse » s'ajoutent, l'armure lourde retire 5 % par pièce, la légère en
     * ajoute 5. Bornée pour qu'aucun empilement ne fige le jeu (voir [MIN_SPEED]).
     */
    val speed get() = (1f + equipSum(StatType.SPEED) + equipped.values.sumOf { it.weightSpeed.toDouble() }.toFloat() +
        if (specialBoosted(Archetype.ROGUE)) IsotopeSets.SPEED_BONUS else 0f)
        .coerceAtLeast(MIN_SPEED)

    /** Or gagné : +3 % par point de CHA. */
    val goldMult get() = 1f + GOLD_PER_CHA * effective(StatType.CHA)

    /**
     * Dégâts réellement subis après armure. L'armure se mesure à l'étage : la même armure
     * protège moins face à des monstres plus profonds. [armorMult] : la Peau de pierre la double.
     */
    fun mitigate(raw: Float, floor: Int, armorMult: Float = 1f): Float {
        val k = 50f * LootSystem.scale(LootSystem.powerCenter(floor).roundToInt())
        return raw * k / (k + armor * armorMult)
    }

    fun healFull() { hp = maxHp }
    fun heal(amount: Int) { hp = (hp + amount).coerceAtMost(maxHp) }

    // ── Reliques ────────────────────────────────────────────────────────────────

    /** Une relique trouvée : elle prend un emplacement libre s'il y en a un. Vrai si portée. */
    fun addRelic(relic: Relic): Boolean {
        if (relic !in relics) relics += relic
        if (relic in relicSlots) return true
        val free = freeRelicSlot()
        if (free < 0) return false
        relicSlots[free] = relic
        return true
    }

    /**
     * Les résonances portées pour la première fois : elles rejoignent le carnet. À appeler
     * après chaque changement de reliques portées ; renvoie celles qu'on vient de découvrir.
     */
    fun discoverResonances(): List<Resonance> = resonances.filter { knownResonances.add(it) }

    fun relicCooldown(relic: Relic) = relicCooldowns[relic] ?: 0

    /**
     * Toutes les recharges avancent de [turns] tours, « Spécial » compris sauf si
     * [includeSpecial] est faux (le contresort du mage ne recharge que les reliques), et
     * sauf [except] (le Bouclier arcanique ne se recharge pas lui-même).
     */
    fun tickRelics(turns: Int = 1, includeSpecial: Boolean = true, except: Relic? = null) {
        if (includeSpecial) specialCooldown = (specialCooldown - turns).coerceAtLeast(0)
        val it = relicCooldowns.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key == except) continue
            e.setValue(e.value - turns)
            if (e.value <= 0) it.remove()
        }
    }

    /** Un pas sur la carte : la recharge avance, lentement. */
    fun walkRelics() {
        if (++walkSteps >= RELIC_WALK_STEPS) { walkSteps = 0; tickRelics() }
    }

    /** Une relique portée ou le « Spécial » se recharge : le repos a une utilité. */
    val relicsRecharging get() = specialCooldown > 0 || relicSlots.any { it != null && relicCooldown(it) > 0 }

    enum class RelicToggle { EQUIPPED, REMOVED, SLOTS_FULL }

    /** Porter ou ranger une relique. Pleins, les emplacements refusent : on range d'abord. */
    fun toggleRelic(relic: Relic): RelicToggle {
        val at = relicSlots.indexOf(relic)
        if (at >= 0) {
            relicSlots[at] = null
            // Une résonance peut donner de la CON : sans elle, les PV max baissent
            hp = hp.coerceAtMost(maxHp)
            return RelicToggle.REMOVED
        }
        if (relic !in relics) return RelicToggle.SLOTS_FULL
        val free = freeRelicSlot()
        if (free < 0) return RelicToggle.SLOTS_FULL
        relicSlots[free] = relic
        return RelicToggle.EQUIPPED
    }

    /**
     * Un nouvel étage atteint. S'il ouvre un emplacement de relique, les reliques trouvées et
     * pas encore portées le remplissent (dans l'ordre de découverte). Vrai si un emplacement s'ouvre.
     */
    fun reachFloor(floor: Int): Boolean {
        if (floor <= deepestFloor) return false
        val before = unlockedRelicSlots
        deepestFloor = floor
        if (unlockedRelicSlots == before) return false
        for (relic in relics) if (relic !in relicSlots) addRelic(relic)
        discoverResonances()
        return true
    }

    // ── Équipement ──────────────────────────────────────────────────────────────

    /** Équipe [item] ; ce qui était porté part dans le sac. */
    fun equip(item: Equipment) {
        equipped.put(item.slot, item)?.let { bag += it }
        hp = hp.coerceAtMost(maxHp)
    }
}
