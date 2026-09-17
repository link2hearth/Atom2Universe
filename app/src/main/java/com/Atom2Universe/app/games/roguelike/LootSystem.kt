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

// ─── Rareté ────────────────────────────────────────────────────────────────────
enum class Rarity(@StringRes override val labelRes: Int, val colorArgb: Int, val statCount: Int, val mult: Float) : Labeled {
    COMMON   (R.string.roguelike_rarity_common,     0xFFAAAAAA.toInt(), 1, 1.00f),
    UNCOMMON (R.string.roguelike_rarity_uncommon,   0xFF66BB6A.toInt(), 2, 1.15f),
    RARE     (R.string.roguelike_rarity_rare,       0xFF42A5F5.toInt(), 3, 1.35f),
    EPIC     (R.string.roguelike_rarity_epic,       0xFFCE93D8.toInt(), 4, 1.60f),
    LEGENDARY(R.string.roguelike_rarity_legendary,  0xFFFFB74D.toInt(), 4, 2.20f),
}

// ─── Stats ──────────────────────────────────────────────────────────────────────
/** Les six caractéristiques D&D, plus les bonus d'équipement. */
enum class StatType(@StringRes override val labelRes: Int, val isPercent: Boolean) : Labeled {
    STR          (R.string.roguelike_attr_str,        false),
    DEX          (R.string.roguelike_attr_dex,        false),
    CON          (R.string.roguelike_attr_con,        false),
    INT          (R.string.roguelike_attr_int,        false),
    WIS          (R.string.roguelike_attr_wis,        false),
    CHA          (R.string.roguelike_attr_cha,        false),
    ARMOR        (R.string.roguelike_stattype_armor,  false),
    MAX_HP       (R.string.roguelike_stattype_maxhp,  false),
    WEAPON_DMG   (R.string.roguelike_stattype_weapon_dmg, false),
    SPELL_DMG    (R.string.roguelike_stattype_spell_dmg,  true),
}

// ─── Matière ────────────────────────────────────────────────────────────────────
enum class EquipMaterial(@StringRes override val labelRes: Int, val tierMult: Float, val minFloor: Int) : Labeled {
    IRON  (R.string.roguelike_material_iron,   1.0f,  1),
    GOLD  (R.string.roguelike_gold,             1.3f,  5),
    ICE   (R.string.roguelike_material_ice,    1.8f, 15),
    UNIQUE(R.string.roguelike_material_unique, 2.5f, 25),
}

// ─── Slots ──────────────────────────────────────────────────────────────────────
enum class EquipSlot(@StringRes override val labelRes: Int) : Labeled {
    WEAPON (R.string.roguelike_slot_weapon), CHEST(R.string.roguelike_slot_chest), HELMET(R.string.roguelike_slot_helmet),
    BOOTS(R.string.roguelike_slot_boots), OFFHAND(R.string.roguelike_slot_offhand), AMULET(R.string.roguelike_slot_amulet), RING(R.string.roguelike_slot_ring)
}

// ─── Data classes ───────────────────────────────────────────────────────────────
data class StatRoll(val type: StatType, val value: Float) {
    fun display(context: Context): String {
        val label = context.getString(type.labelRes)
        return if (type.isPercent)
            context.getString(R.string.roguelike_stat_roll_percent, (value * 100).roundToInt(), label)
        else
            context.getString(R.string.roguelike_stat_roll_flat, value.roundToInt(), label)
    }
}

data class Equipment(
    val slot: EquipSlot,
    val material: EquipMaterial,
    val rarity: Rarity,
    val stats: List<StatRoll>,
    val spriteRow: Int,
    val spriteCol: Int,
)

// ─── Système de loot ────────────────────────────────────────────────────────────
object LootSystem {

    // Le premier élément de chaque liste est la stat « de base » de l'emplacement :
    // une arme a toujours des dégâts, une armure toujours de l'armure.
    private val attributes = listOf(StatType.STR, StatType.DEX, StatType.CON, StatType.INT, StatType.WIS, StatType.CHA)

    private val slotStats = mapOf(
        EquipSlot.WEAPON  to listOf(StatType.WEAPON_DMG, StatType.STR, StatType.DEX, StatType.SPELL_DMG),
        EquipSlot.CHEST   to listOf(StatType.ARMOR, StatType.MAX_HP, StatType.CON),
        EquipSlot.HELMET  to listOf(StatType.ARMOR, StatType.INT, StatType.WIS),
        EquipSlot.BOOTS   to listOf(StatType.ARMOR, StatType.DEX, StatType.CON),
        EquipSlot.OFFHAND to listOf(StatType.ARMOR, StatType.CON, StatType.STR),
        EquipSlot.AMULET  to listOf(StatType.SPELL_DMG) + attributes + StatType.MAX_HP,
        EquipSlot.RING    to attributes + listOf(StatType.MAX_HP, StatType.SPELL_DMG),
    )

    // Valeurs à l'étage 1 ; le niveau d'objet (= étage) les fait grimper
    private val statBase = mapOf(
        StatType.STR         to (1f to 3f),
        StatType.DEX         to (1f to 3f),
        StatType.CON         to (1f to 3f),
        StatType.INT         to (1f to 3f),
        StatType.WIS         to (1f to 3f),
        StatType.CHA         to (1f to 3f),
        StatType.ARMOR       to (2f to 5f),
        StatType.MAX_HP      to (4f to 10f),
        StatType.WEAPON_DMG  to (1f to 3f),
        StatType.SPELL_DMG   to (0.05f to 0.12f),
    )

    private val spritePools: Map<Pair<EquipSlot, EquipMaterial>, List<Pair<Int, Int>>> = buildMap {
        fun rows(vararg rs: Int) = rs.flatMap { r -> (0..15).map { c -> r to c } }

        put(EquipSlot.WEAPON to EquipMaterial.IRON,    rows(90, 91, 92, 93, 94))
        put(EquipSlot.WEAPON to EquipMaterial.GOLD,    rows(95, 96, 97, 98, 99))
        put(EquipSlot.WEAPON to EquipMaterial.ICE,     rows(100, 101, 102, 103, 104))
        put(EquipSlot.WEAPON to EquipMaterial.UNIQUE,  rows(105, 106, 107, 108, 109, 110, 111, 112))

        put(EquipSlot.CHEST to EquipMaterial.IRON,     rows(116, 117, 118))
        put(EquipSlot.CHEST to EquipMaterial.GOLD,     rows(120, 123))
        put(EquipSlot.CHEST to EquipMaterial.ICE,      rows(126))
        put(EquipSlot.CHEST to EquipMaterial.UNIQUE,   rows(130, 131))

        put(EquipSlot.HELMET to EquipMaterial.IRON,    rows(119))
        put(EquipSlot.HELMET to EquipMaterial.GOLD,    rows(122))
        put(EquipSlot.HELMET to EquipMaterial.ICE,     rows(125))
        put(EquipSlot.HELMET to EquipMaterial.UNIQUE,  rows(125))

        put(EquipSlot.BOOTS to EquipMaterial.IRON,     rows(121))
        put(EquipSlot.BOOTS to EquipMaterial.GOLD,     rows(124))
        put(EquipSlot.BOOTS to EquipMaterial.ICE,      rows(127))
        put(EquipSlot.BOOTS to EquipMaterial.UNIQUE,   rows(127))

        val shields = rows(132, 133)
        val orbs    = rows(10)
        for (mat in EquipMaterial.values()) {
            put(EquipSlot.OFFHAND to mat, shields)
            put(EquipSlot.AMULET  to mat, orbs)
            put(EquipSlot.RING    to mat, orbs)
        }
    }

    // ── API publique ────────────────────────────────────────────────────────────

    /** Chance qu'un ennemi vaincu lâche un équipement. */
    const val DROP_CHANCE = 0.20f

    fun tryDrop(floor: Int, rng: Random = Random): Equipment? {
        if (rng.nextFloat() > DROP_CHANCE) return null
        return generate(floor, rng)
    }

    fun generate(floor: Int, rng: Random = Random): Equipment {
        val material = pickMaterial(floor, rng)
        val rarity   = pickRarity(floor, rng)
        val slot     = pickSlot(rng)
        val stats    = rollStats(slot, material, rarity, floor, rng)
        val (row, col) = pickSprite(slot, material, rng)
        return Equipment(slot, material, rarity, stats, row, col)
    }

    /** Nom affiché de l'équipement, ex. "Grand Fer Arme" / "Great Iron Weapon". */
    fun displayName(context: Context, e: Equipment): String =
        "${rarityPrefix(context, e.rarity)}${context.getString(e.material.labelRes)} ${context.getString(e.slot.labelRes)}"

    // ── Sélecteurs ──────────────────────────────────────────────────────────────

    // Paliers pensés pour 100 étages
    private fun pickMaterial(floor: Int, rng: Random): EquipMaterial = weighted(listOf(
        EquipMaterial.IRON   to maxOf(5f, 65f - floor * 0.8f),
        EquipMaterial.GOLD   to if (floor >= 10) minOf(50f, (floor - 9) * 3f)   else 0f,
        EquipMaterial.ICE    to if (floor >= 30) minOf(40f, (floor - 29) * 2f)  else 0f,
        EquipMaterial.UNIQUE to if (floor >= 60) minOf(30f, (floor - 59) * 1.5f) else 0f,
    ), rng)

    private fun pickRarity(floor: Int, rng: Random): Rarity = weighted(listOf(
        Rarity.COMMON    to maxOf(10f, 60f - floor * 0.8f),
        Rarity.UNCOMMON  to 28f,
        Rarity.RARE      to minOf(25f, 6f + floor * 0.4f),
        Rarity.EPIC      to minOf(15f, maxOf(0f, (floor - 5) * 0.3f)),
        Rarity.LEGENDARY to minOf(6f,  maxOf(0f, (floor - 15) * 0.1f)),
    ), rng)

    private fun pickSlot(rng: Random): EquipSlot = weighted(listOf(
        EquipSlot.WEAPON  to 28f,
        EquipSlot.CHEST   to 18f,
        EquipSlot.HELMET  to 13f,
        EquipSlot.BOOTS   to 13f,
        EquipSlot.OFFHAND to 12f,
        EquipSlot.AMULET  to 9f,
        EquipSlot.RING    to 7f,
    ), rng)

    private fun pickSprite(slot: EquipSlot, mat: EquipMaterial, rng: Random): Pair<Int, Int> =
        (spritePools[slot to mat] ?: listOf(0 to 0)).random(rng)

    // ── Calcul des stats ────────────────────────────────────────────────────────

    private fun rollStats(
        slot: EquipSlot, mat: EquipMaterial, rar: Rarity, floor: Int, rng: Random
    ): List<StatRoll> {
        val pool      = slotStats[slot] ?: StatType.entries
        // Niveau d'objet : +10 % par étage
        val floorMult = 1f + floor * 0.10f
        val picked    = (listOf(pool.first()) + pool.drop(1).shuffled(rng)).take(rar.statCount)
        return picked.map { stat ->
            val (lo, hi) = statBase[stat] ?: (1f to 2f)
            val raw      = lo + rng.nextFloat() * (hi - lo)
            val value    = raw * mat.tierMult * rar.mult * floorMult
            val rounded  = if (stat.isPercent)
                value
            else
                value.roundToInt().toFloat().coerceAtLeast(1f)
            StatRoll(stat, rounded)
        }
    }

    // ── Label ───────────────────────────────────────────────────────────────────

    private fun rarityPrefix(context: Context, rar: Rarity): String = when (rar) {
        Rarity.COMMON    -> ""
        Rarity.UNCOMMON  -> context.getString(R.string.roguelike_rarity_prefix_uncommon)
        Rarity.RARE      -> context.getString(R.string.roguelike_rarity_prefix_rare)
        Rarity.EPIC      -> context.getString(R.string.roguelike_rarity_prefix_epic)
        Rarity.LEGENDARY -> context.getString(R.string.roguelike_rarity_prefix_legendary)
    }

    private fun <T> weighted(list: List<Pair<T, Float>>, rng: Random): T {
        val total = list.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.001f)
        var r = rng.nextFloat() * total
        for ((item, w) in list) { r -= w; if (r <= 0f) return item }
        return list.last().first
    }
}
