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
enum class StatType(@StringRes override val labelRes: Int, val isPercent: Boolean) : Labeled {
    ATK          (R.string.roguelike_stattype_atk,      false),
    DEF          (R.string.roguelike_stattype_def,      false),
    MAX_HP       (R.string.roguelike_stattype_maxhp,    false),
    CRIT_CHANCE  (R.string.roguelike_stattype_crit,     true),
    CRIT_DMG     (R.string.roguelike_stattype_critdmg,  true),
    EVASION      (R.string.roguelike_stattype_evasion,  true),
    BLOCK        (R.string.roguelike_stattype_block,    true),
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
            "+${(value * 100).roundToInt()}% $label"
        else
            "+${value.roundToInt()} $label"
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

    private val slotStats = mapOf(
        EquipSlot.WEAPON  to listOf(StatType.ATK, StatType.CRIT_CHANCE, StatType.CRIT_DMG),
        EquipSlot.CHEST   to listOf(StatType.MAX_HP, StatType.DEF, StatType.BLOCK),
        EquipSlot.HELMET  to listOf(StatType.MAX_HP, StatType.DEF, StatType.EVASION),
        EquipSlot.BOOTS   to listOf(StatType.EVASION, StatType.DEF, StatType.MAX_HP),
        EquipSlot.OFFHAND to listOf(StatType.DEF, StatType.BLOCK, StatType.MAX_HP),
        EquipSlot.AMULET  to StatType.values().toList(),
        EquipSlot.RING    to StatType.values().toList(),
    )

    // Stats de base réduites — la progression est lente par design
    private val statBase = mapOf(
        StatType.ATK         to (1f to 3f),
        StatType.DEF         to (1f to 2f),
        StatType.MAX_HP      to (4f to 10f),
        StatType.CRIT_CHANCE to (0.02f to 0.06f),
        StatType.CRIT_DMG    to (0.08f to 0.20f),
        StatType.EVASION     to (0.02f to 0.06f),
        StatType.BLOCK       to (0.03f to 0.10f),
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

    fun tryDrop(floor: Int, rng: Random = Random): Equipment? {
        val chance = (0.18f + floor * 0.004f).coerceAtMost(0.38f)
        if (rng.nextFloat() > chance) return null
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

    private fun pickMaterial(floor: Int, rng: Random): EquipMaterial = weighted(listOf(
        EquipMaterial.IRON   to maxOf(0f, 65f - floor * 1.8f),
        EquipMaterial.GOLD   to if (floor >= 5)  minOf(50f, (floor - 4) * 4f)  else 0f,
        EquipMaterial.ICE    to if (floor >= 15) minOf(40f, (floor - 14) * 3f) else 0f,
        EquipMaterial.UNIQUE to if (floor >= 25) minOf(30f, (floor - 24) * 2f) else 0f,
    ), rng)

    private fun pickRarity(floor: Int, rng: Random): Rarity = weighted(listOf(
        Rarity.COMMON    to maxOf(0f, 55f - floor * 1.3f),
        Rarity.UNCOMMON  to 25f,
        Rarity.RARE      to minOf(28f, 4f + floor * 0.8f),
        Rarity.EPIC      to minOf(18f, maxOf(0f, floor * 0.4f - 3f)),
        Rarity.LEGENDARY to minOf(7f,  maxOf(0f, floor * 0.18f - 4f)),
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
        val pool      = slotStats[slot] ?: StatType.values().toList()
        // +1.5% par étage au lieu de +7% — progression lente et satisfaisante
        val floorMult = 1f + floor * 0.015f
        return pool.shuffled(rng).take(rar.statCount).map { stat ->
            val (lo, hi) = statBase[stat] ?: (1f to 2f)
            val raw      = lo + rng.nextFloat() * (hi - lo)
            val value    = raw * mat.tierMult * rar.mult * floorMult
            val rounded  = if (stat.isPercent)
                value.coerceAtMost(0.75f)
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
