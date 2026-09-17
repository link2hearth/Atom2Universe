package com.Atom2Universe.app.games.roguelike

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.roundToInt
import kotlin.random.Random

/** Implémenté par les enums dont le nom affiché passe par les ressources strings. */
interface Labeled {
    @get:StringRes val labelRes: Int
}

// ─── Stats ──────────────────────────────────────────────────────────────────────

/** Les six caractéristiques D&D, plus les bonus que donnent les objets. */
enum class StatType(@StringRes override val labelRes: Int, val isPercent: Boolean) : Labeled {
    STR          (R.string.roguelike_attr_str,             false),
    DEX          (R.string.roguelike_attr_dex,             false),
    CON          (R.string.roguelike_attr_con,             false),
    INT          (R.string.roguelike_attr_int,             false),
    WIS          (R.string.roguelike_attr_wis,             false),
    CHA          (R.string.roguelike_attr_cha,             false),
    ARMOR        (R.string.roguelike_stattype_armor,       false),
    MAX_HP       (R.string.roguelike_stattype_maxhp,       false),
    WEAPON_DMG   (R.string.roguelike_stattype_weapon_dmg,  false),
    SPELL_DMG    (R.string.roguelike_stattype_spell_dmg,   true),
    CRIT_CHANCE  (R.string.roguelike_stattype_crit_chance, true),
    CRIT_DAMAGE  (R.string.roguelike_stattype_crit_damage, true),
    LIFE_STEAL   (R.string.roguelike_stattype_life_steal,  true);

    companion object {
        val ATTRIBUTES = listOf(STR, DEX, CON, INT, WIS, CHA)
    }
}

data class StatRoll(val type: StatType, val value: Float) {
    fun display(context: Context): String {
        val label = context.getString(type.labelRes)
        return if (type.isPercent)
            context.getString(R.string.roguelike_stat_roll_percent, (value * 100).roundToInt(), label)
        else
            context.getString(R.string.roguelike_stat_roll_flat, value.roundToInt(), label)
    }
}

// ─── Rareté ────────────────────────────────────────────────────────────────────

/** Légendaires et sets viendront plus tard (voir DONJON.md). */
enum class Rarity(
    @StringRes override val labelRes: Int, val colorArgb: Int,
    val minAffixes: Int, val maxAffixes: Int, val sellMult: Float,
) : Labeled {
    NORMAL(R.string.roguelike_rarity_normal, 0xFFBDBDBD.toInt(), 0, 0, 1f),
    MAGIC (R.string.roguelike_rarity_magic,  0xFF6E9BFF.toInt(), 1, 2, 2f),
    RARE  (R.string.roguelike_rarity_rare,   0xFFFFD54F.toInt(), 3, 4, 4f),
}

// ─── Emplacements ──────────────────────────────────────────────────────────────

enum class EquipSlot(@StringRes override val labelRes: Int) : Labeled {
    WEAPON (R.string.roguelike_slot_weapon), OFFHAND(R.string.roguelike_slot_offhand),
    HELMET(R.string.roguelike_slot_helmet), CHEST(R.string.roguelike_slot_chest), BOOTS(R.string.roguelike_slot_boots),
    AMULET(R.string.roguelike_slot_amulet), RING(R.string.roguelike_slot_ring)
}

// ─── Matières ──────────────────────────────────────────────────────────────────

/**
 * Une seule échelle de matières, 5 tiers chacune. Le tier 5 d'une matière a la même
 * puissance que le tier 1 de la suivante : on trouve du Cuir 5 aux mêmes étages que du
 * Cuivre 1. Noms provisoires. [weaponRes] : le nom pour les armes et bijoux (une épée
 * « de cuir » n'existe pas, le premier palier est en bois).
 */
enum class Material(@StringRes val armorRes: Int, @StringRes val weaponRes: Int) {
    LEATHER   (R.string.roguelike_mat_leather,    R.string.roguelike_mat_wood),
    COPPER    (R.string.roguelike_mat_copper,     R.string.roguelike_mat_copper),
    BRONZE    (R.string.roguelike_mat_bronze,     R.string.roguelike_mat_bronze),
    IRON      (R.string.roguelike_mat_iron,       R.string.roguelike_mat_iron),
    STEEL     (R.string.roguelike_mat_steel,      R.string.roguelike_mat_steel),
    MITHRIL   (R.string.roguelike_mat_mithril,    R.string.roguelike_mat_mithril),
    OBSIDIAN  (R.string.roguelike_mat_obsidian,   R.string.roguelike_mat_obsidian),
    ADAMANTIUM(R.string.roguelike_mat_adamantium, R.string.roguelike_mat_adamantium),
    ORICHALCUM(R.string.roguelike_mat_orichalcum, R.string.roguelike_mat_orichalcum),
    ASTRALITE (R.string.roguelike_mat_astralite,  R.string.roguelike_mat_astralite);

    companion object {
        const val TIERS = 5
        /** Puissance gagnée d'une matière à la suivante (4 : le tier 5 chevauche le tier 1 suivant). */
        const val STEP = 4
        val MAX_POWER = (entries.size - 1) * STEP + TIERS
    }
}

// ─── Bases d'objets ────────────────────────────────────────────────────────────

/**
 * Le type d'objet fixe sa base. Une arme donne toujours la caractéristique de son type
 * ([attribute]) ; une pièce d'armure ou un bijou tire une caractéristique au hasard
 * ([attribute] = null).
 */
enum class ItemBase(
    @StringRes val nounRes: Int,
    val slot: EquipSlot,
    val attribute: StatType?,
    val damageMult: Float,
    val armorBase: Float,
    val spellBonus: Float,
    val usesWeaponMaterial: Boolean,
) {
    SWORD  (R.string.roguelike_base_sword,   EquipSlot.WEAPON,  StatType.STR, 1.00f, 0f, 0f,     true),
    AXE    (R.string.roguelike_base_axe,     EquipSlot.WEAPON,  StatType.STR, 1.25f, 0f, 0f,     true),
    DAGGER (R.string.roguelike_base_dagger,  EquipSlot.WEAPON,  StatType.DEX, 0.80f, 0f, 0f,     true),
    MACE   (R.string.roguelike_base_mace,    EquipSlot.WEAPON,  StatType.CON, 1.00f, 0f, 0f,     true),
    STAFF  (R.string.roguelike_base_staff,   EquipSlot.WEAPON,  StatType.INT, 0.60f, 0f, 0.10f,  true),
    SCEPTER(R.string.roguelike_base_scepter, EquipSlot.WEAPON,  StatType.WIS, 0.70f, 0f, 0.05f,  true),
    SHIELD (R.string.roguelike_base_shield,  EquipSlot.OFFHAND, StatType.CON, 0f,    4f, 0f,     true),
    ORB    (R.string.roguelike_base_orb,     EquipSlot.OFFHAND, StatType.INT, 0f,    0f, 0.08f,  true),
    HELMET (R.string.roguelike_base_helmet,  EquipSlot.HELMET,  null,         0f,    3f, 0f,     false),
    ARMOR  (R.string.roguelike_base_armor,   EquipSlot.CHEST,   null,         0f,    6f, 0f,     false),
    BOOTS  (R.string.roguelike_base_boots,   EquipSlot.BOOTS,   null,         0f,    3f, 0f,     false),
    AMULET (R.string.roguelike_base_amulet,  EquipSlot.AMULET,  null,         0f,    0f, 0f,     true),
    RING   (R.string.roguelike_base_ring,    EquipSlot.RING,    null,         0f,    0f, 0f,     true),
}

// ─── Objet ─────────────────────────────────────────────────────────────────────

data class Equipment(
    val base: ItemBase,
    val material: Material,
    val tier: Int,
    val rarity: Rarity,
    /** Dégâts de l'arme (0 pour ce qui n'en est pas une). */
    val damageMin: Int,
    val damageMax: Int,
    val armor: Int,
    /** Stats de base, fixées par le type d'objet. */
    val implicits: List<StatRoll>,
    /** Stats aléatoires, selon la rareté. */
    val affixes: List<StatRoll>,
    val spriteRow: Int,
    val spriteCol: Int,
    /** Ordre de ramassage, pour trier « dernier looté en premier ». */
    val lootId: Long,
) {
    val slot get() = base.slot
    val power get() = material.ordinal * Material.STEP + tier
    val allStats get() = implicits + affixes
    fun sum(type: StatType) = allStats.filter { it.type == type }.sumOf { it.value.toDouble() }.toFloat()
}

// ─── Génération ────────────────────────────────────────────────────────────────

object LootSystem {

    /** Chance qu'un ennemi vaincu lâche un équipement. */
    const val DROP_CHANCE = 0.20f

    /** Multiplicateur de base d'une puissance donnée : +45 % par cran. */
    fun scale(power: Int) = 1f + 0.45f * (power - 1)

    /** Puissance typique d'un étage : 1 à l'étage 1, ~41 à l'étage 100. */
    fun powerCenter(floor: Int) = 1f + (floor - 1) * 0.4f

    private val affixPools: Map<EquipSlot, List<StatType>> = run {
        val attrs = StatType.ATTRIBUTES
        val armorPiece = attrs + listOf(StatType.ARMOR, StatType.MAX_HP)
        mapOf(
            EquipSlot.WEAPON  to attrs + listOf(StatType.WEAPON_DMG, StatType.SPELL_DMG, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.LIFE_STEAL),
            EquipSlot.OFFHAND to attrs + listOf(StatType.ARMOR, StatType.MAX_HP, StatType.SPELL_DMG, StatType.CRIT_CHANCE),
            EquipSlot.HELMET  to armorPiece,
            EquipSlot.CHEST   to armorPiece,
            EquipSlot.BOOTS   to armorPiece,
            EquipSlot.AMULET  to attrs + listOf(StatType.MAX_HP, StatType.SPELL_DMG, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.LIFE_STEAL),
            EquipSlot.RING    to attrs + listOf(StatType.MAX_HP, StatType.SPELL_DMG, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.LIFE_STEAL),
        )
    }

    fun tryDrop(floor: Int, lootId: Long, rng: Random = Random): Equipment? {
        if (rng.nextFloat() > DROP_CHANCE) return null
        return generate(floor, lootId, rng)
    }

    fun generate(floor: Int, lootId: Long = 0, rng: Random = Random): Equipment {
        // Puissance : autour de celle de l'étage, un peu en dessous le plus souvent
        val power = (powerCenter(floor) + rng.nextFloat() * 3f - 2f).roundToInt().coerceIn(1, Material.MAX_POWER)
        val (material, tier) = pickGrade(power, rng)
        return create(pickBase(rng), material, tier, pickRarity(floor, rng), lootId, rng)
    }

    /** Toutes les façons d'écrire une puissance : Cuir 5 ou Cuivre 1, au hasard. */
    private fun pickGrade(power: Int, rng: Random): Pair<Material, Int> {
        val options = Material.entries.mapNotNull { m ->
            val tier = power - m.ordinal * Material.STEP
            if (tier in 1..Material.TIERS) m to tier else null
        }
        return options.random(rng)
    }

    fun create(base: ItemBase, material: Material, tier: Int, rarity: Rarity, lootId: Long, rng: Random): Equipment {
        val power = material.ordinal * Material.STEP + tier
        val s = scale(power)

        val isWeapon = base.damageMult > 0f
        val dmgMin = if (isWeapon) (4f * s * base.damageMult).roundToInt().coerceAtLeast(1) else 0
        val dmgMax = if (isWeapon) (7f * s * base.damageMult).roundToInt().coerceAtLeast(dmgMin + 1) else 0
        val armor  = (base.armorBase * s).roundToInt()

        val implicits = mutableListOf<StatRoll>()
        val attr = base.attribute ?: StatType.ATTRIBUTES.random(rng)
        val attrValue = if (base.attribute != null) 2f + 0.6f * (power - 1) else 1f + 0.4f * (power - 1)
        implicits += StatRoll(attr, attrValue.roundToInt().toFloat())
        if (base.spellBonus > 0f) implicits += StatRoll(StatType.SPELL_DMG, base.spellBonus * (1f + 0.1f * (power - 1)))

        val count = rng.nextInt(rarity.minAffixes, rarity.maxAffixes + 1)
        val affixes = affixPools.getValue(base.slot).shuffled(rng).take(count).map { rollAffix(it, power, rng) }

        val (row, col) = pickSprite(base, material, rng)
        return Equipment(base, material, tier, rarity, dmgMin, dmgMax, armor, implicits, affixes, row, col, lootId)
    }

    private fun rollAffix(type: StatType, power: Int, rng: Random): StatRoll {
        fun between(lo: Float, hi: Float) = lo + rng.nextFloat() * (hi - lo)
        val s = scale(power)
        val pct = 1f + 0.05f * (power - 1)
        val value = when (type) {
            StatType.STR, StatType.DEX, StatType.CON,
            StatType.INT, StatType.WIS, StatType.CHA -> between(1f, 3f) * (1f + 0.5f * (power - 1))
            StatType.ARMOR       -> between(1f, 4f) * s
            StatType.MAX_HP      -> between(3f, 8f) * s
            StatType.WEAPON_DMG  -> between(1f, 2f) * s
            StatType.SPELL_DMG   -> between(0.04f, 0.10f) * pct
            StatType.CRIT_CHANCE -> between(0.01f, 0.04f)
            StatType.CRIT_DAMAGE -> between(0.05f, 0.20f) * pct
            StatType.LIFE_STEAL  -> between(0.01f, 0.03f)
        }
        return StatRoll(type, if (type.isPercent) value else value.roundToInt().coerceAtLeast(1).toFloat())
    }

    private fun pickBase(rng: Random): ItemBase = weighted(listOf(
        ItemBase.SWORD to 6f, ItemBase.AXE to 5f, ItemBase.DAGGER to 5f, ItemBase.MACE to 4f,
        ItemBase.STAFF to 5f, ItemBase.SCEPTER to 4f,
        ItemBase.SHIELD to 6f, ItemBase.ORB to 5f,
        ItemBase.HELMET to 10f, ItemBase.ARMOR to 12f, ItemBase.BOOTS to 10f,
        ItemBase.AMULET to 7f, ItemBase.RING to 8f,
    ), rng)

    private fun pickRarity(floor: Int, rng: Random): Rarity = weighted(listOf(
        Rarity.NORMAL to maxOf(25f, 55f - floor * 0.3f),
        Rarity.MAGIC  to 35f,
        Rarity.RARE   to minOf(35f, 10f + floor * 0.25f),
    ), rng)

    /** Icônes provisoires dans 64x64.png : la couleur suit la matière (brun, or, bleu). */
    private fun pickSprite(base: ItemBase, material: Material, rng: Random): Pair<Int, Int> {
        val shade = (material.ordinal * 3) / Material.entries.size   // 0, 1, 2
        val wo = shade * 5                                            // lignes des armes : +5 or, +10 glace
        fun pick(row: Int, cols: IntRange) = row to cols.random(rng)
        return when (base) {
            ItemBase.SWORD   -> pick(90 + wo, 4..9)
            ItemBase.DAGGER  -> pick(90 + wo, 0..3)
            ItemBase.AXE     -> pick(91 + wo, 5..9)
            ItemBase.MACE    -> pick(92 + wo, 0..3)
            ItemBase.STAFF   -> pick(93 + wo, 0..3)
            ItemBase.SCEPTER -> pick(93 + wo, 4..7)
            ItemBase.SHIELD  -> pick(134, 2..4)
            ItemBase.ORB     -> pick(133, 0..5)
            ItemBase.HELMET  -> pick(listOf(122, 119, 125)[shade], 0..15)
            ItemBase.ARMOR   -> pick(listOf(123, 120, 126)[shade], 0..15)
            ItemBase.BOOTS   -> pick(listOf(124, 121, 127)[shade], 0..15)
            ItemBase.AMULET  -> pick(115, 9..11)
            ItemBase.RING    -> pick(115, 2..8)
        }
    }

    // ── Valeur ──────────────────────────────────────────────────────────────────

    fun sellPrice(e: Equipment): Int = ((3 + 2 * e.power) * e.rarity.sellMult).roundToInt()

    /**
     * Note d'un objet, pour trier du meilleur au moins bon et comparer avec ce qu'on
     * porte. Une somme pondérée simple, affichée au joueur : pas de magie cachée.
     */
    fun rating(e: Equipment): Int {
        var r = (e.damageMin + e.damageMax) * 1.5f + e.armor * 1.5f
        for (s in e.allStats) r += when (s.type) {
            StatType.STR, StatType.DEX, StatType.CON, StatType.INT, StatType.WIS -> 2f * s.value
            StatType.CHA         -> 1f * s.value
            StatType.ARMOR       -> 1.5f * s.value
            StatType.MAX_HP      -> 0.5f * s.value
            StatType.WEAPON_DMG  -> 3f * s.value
            StatType.SPELL_DMG   -> 100f * s.value
            StatType.CRIT_CHANCE -> 200f * s.value
            StatType.CRIT_DAMAGE -> 60f * s.value
            StatType.LIFE_STEAL  -> 300f * s.value
        }
        return r.roundToInt()
    }

    // ── Affichage ───────────────────────────────────────────────────────────────

    /** « Épée de fer 3 » / « Iron Sword 3 ». */
    fun displayName(context: Context, e: Equipment): String {
        val mat = context.getString(if (e.base.usesWeaponMaterial) e.material.weaponRes else e.material.armorRes)
        return context.getString(R.string.roguelike_item_name, context.getString(e.base.nounRes), mat, e.tier)
    }

    /** Lignes de description : dégâts, armure, puis toutes les stats. */
    fun describe(context: Context, e: Equipment): List<String> = buildList {
        if (e.damageMax > 0) add(context.getString(R.string.roguelike_item_damage, e.damageMin, e.damageMax))
        if (e.armor > 0) add(context.getString(R.string.roguelike_item_armor, e.armor))
        e.implicits.forEach { add(it.display(context)) }
        e.affixes.forEach { add(it.display(context)) }
    }

    private fun <T> weighted(list: List<Pair<T, Float>>, rng: Random): T {
        val total = list.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.001f)
        var r = rng.nextFloat() * total
        for ((item, w) in list) { r -= w; if (r <= 0f) return item }
        return list.last().first
    }
}
