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

    fun rollInstance(defId: String, rng: kotlin.random.Random): ItemInstance? {
        val def = defs[defId] ?: return null
        val rarity = rollRarity(def.rarityWeights, rng)
        val damage = def.damageBase?.roll(rng)
        val stats  = def.stats.mapValues { (_, range) -> range.roll(rng) }
        return ItemInstance(defId, rarity, damage, stats)
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
