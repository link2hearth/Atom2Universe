package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import org.json.JSONObject

internal object ItemRegistry {

    private val defs = mutableMapOf<String, ItemDef>()

    fun load(assets: AssetManager) {
        if (defs.isNotEmpty()) return
        val files = assets.list("caves/items") ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/items/$file").bufferedReader().readText()
            val def = ItemDef.fromJson(JSONObject(json))
            defs[def.id] = def
        }
    }

    fun get(id: String): ItemDef? = defs[id]

    fun rollInstance(defId: String, rng: kotlin.random.Random, mobLevel: Int = 1, mobMaxHp: Int = 0): ItemInstance? {
        val def = defs[defId] ?: return null
        val rarity = rollRarity(def.rarityWeights, rng)
        val rarityMult = rarityMultiplier(rarity)
        val damage = if (def.type == "weapon" && def.damageBase != null) {
            if (mobMaxHp > 0) {
                // Dégâts = HP du mob / roll(20..50) × rarityMult
                val divisor = 20 + rng.nextInt(31)   // 20 à 50
                (mobMaxHp / divisor.toFloat() * rarityMult).toInt().coerceAtLeast(1)
            } else {
                // Fallback (ne devrait pas arriver en jeu normal)
                val levelMult = 1f + (mobLevel - 1) * 0.08f
                (def.damageBase.roll(rng) * levelMult * rarityMult).toInt().coerceAtLeast(1)
            }
        } else null
        val stats = def.stats.mapValues { (_, range) ->
            val base = range.roll(rng)
            if (def.type == "weapon") (base * rarityMult).toInt().coerceAtLeast(range.min)
            else base
        }.toMutableMap()

        // Affixes bonus selon la rareté
        if (def.type == "weapon") {
            val count = AffixRegistry.affixCount(rarity)
            if (count > 0) {
                val boost = AffixRegistry.legendaryBoost(rarity)
                val pool = AffixRegistry.all.filter { it.id !in stats }.shuffled(rng)
                for (i in 0 until minOf(count, pool.size)) {
                    val affix = pool[i]
                    val value = (affix.roll(def.tier.coerceAtLeast(1), rng) * boost).toInt().coerceAtLeast(1)
                    stats[affix.id] = value
                }
            }
        }

        return ItemInstance(defId, rarity, damage, stats, tier = def.tier)
    }

    private fun rarityMultiplier(rarity: ItemRarity) = when (rarity) {
        ItemRarity.COMMON    -> 1.0f
        ItemRarity.MAGIC     -> 1.3f
        ItemRarity.RARE      -> 1.6f
        ItemRarity.EPIC      -> 2.0f
        ItemRarity.LEGENDARY -> 2.5f
    }

    private fun rollRarity(weights: Map<ItemRarity, Int>, rng: kotlin.random.Random): ItemRarity {
        val total = weights.values.sum().coerceAtLeast(1)
        var roll = rng.nextInt(total)
        for ((rarity, w) in weights) {
            roll -= w
            if (roll < 0) return rarity
        }
        return ItemRarity.COMMON
    }
}
