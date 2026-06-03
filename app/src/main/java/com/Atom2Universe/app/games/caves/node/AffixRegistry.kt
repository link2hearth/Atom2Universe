package com.Atom2Universe.app.games.caves.node

import kotlin.random.Random

internal data class AffixDef(
    val id: String,
    val minByTier: IntArray,   // valeur min pour chaque tier 1-5 (index 0 = tier 1)
    val maxByTier: IntArray    // valeur max pour chaque tier 1-5
) {
    fun roll(tier: Int, rng: Random): Int {
        val t = (tier - 1).coerceIn(0, 4)
        val lo = minByTier[t]; val hi = maxByTier[t]
        return if (lo == hi) lo else lo + rng.nextInt(hi - lo + 1)
    }
}

internal object AffixRegistry {

    // Tous les affixes disponibles sur les armes
    val all: List<AffixDef> = listOf(
        AffixDef("crit_chance",   intArrayOf( 4,  6,  8, 10, 14), intArrayOf(10, 15, 20, 28, 40)),
        AffixDef("crit_dmg",      intArrayOf(20, 30, 40, 55, 70), intArrayOf(40, 60, 80,110,150)),
        AffixDef("attack_speed",  intArrayOf( 5,  8, 10, 15, 20), intArrayOf(10, 15, 22, 30, 45)),
        AffixDef("life_steal",    intArrayOf( 2,  3,  4,  6,  8), intArrayOf( 5,  7, 10, 14, 20)),
        AffixDef("bleed_chance",  intArrayOf(10, 15, 20, 28, 35), intArrayOf(22, 30, 40, 55, 70)),
        AffixDef("shock_chance",  intArrayOf( 8, 12, 15, 20, 28), intArrayOf(18, 25, 35, 45, 60)),
        AffixDef("execute",       intArrayOf( 3,  5,  7, 10, 14), intArrayOf( 8, 12, 18, 25, 35)),
        AffixDef("aoe_splash",    intArrayOf(10, 15, 20, 28, 35), intArrayOf(22, 30, 40, 55, 70)),
        AffixDef("thorns",        intArrayOf( 5,  8, 10, 14, 18), intArrayOf(12, 18, 25, 35, 50)),
        AffixDef("fire_chance",   intArrayOf( 8, 12, 16, 22, 30), intArrayOf(20, 28, 38, 50, 65))
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): AffixDef? = byId[id]

    // Nombre d'affixes bonus selon la rareté (en plus des stats de base de la def)
    fun affixCount(rarity: ItemRarity): Int = when (rarity) {
        ItemRarity.COMMON    -> 0
        ItemRarity.MAGIC     -> 1
        ItemRarity.RARE      -> 2
        ItemRarity.EPIC      -> 3
        ItemRarity.LEGENDARY -> 5
    }

    // Multiplicateur de valeur pour LEGENDARY (affixes plus forts)
    fun legendaryBoost(rarity: ItemRarity): Float = if (rarity == ItemRarity.LEGENDARY) 1.25f else 1.0f
}
