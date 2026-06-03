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

    fun rollInstance(defId: String, rng: kotlin.random.Random, mobLevel: Int = 1): ItemInstance? {
        val def = defs[defId] ?: return null
        val rarity = rollRarity(def.rarityWeights, rng)
        val rarityMult = rarityMultiplier(rarity)
        val levelMult  = 1f + (mobLevel - 1) * 0.08f
        val damage = def.damageBase?.let { range ->
            val base = range.roll(rng)
            (base * levelMult * rarityMult).toInt().coerceAtLeast(1)
        }
        val stats = def.stats.mapValues { (_, range) ->
            val base = range.roll(rng)
            if (def.type == "weapon") (base * rarityMult).toInt().coerceAtLeast(range.min)
            else base
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
