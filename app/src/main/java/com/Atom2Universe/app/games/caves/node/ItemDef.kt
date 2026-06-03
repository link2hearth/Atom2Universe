package com.Atom2Universe.app.games.caves.node

import org.json.JSONObject

internal enum class ItemRarity { COMMON, MAGIC, RARE, EPIC, LEGENDARY }

internal data class ItemStatRange(val min: Int, val max: Int) {
    fun roll(rng: kotlin.random.Random): Int = if (min == max) min else min + rng.nextInt(max - min + 1)
}

internal data class ItemDef(
    val id: String,
    val type: String,                           // "weapon", "armor", "consumable", "material"
    val rarityWeights: Map<ItemRarity, Int>,
    val damageBase: ItemStatRange?,
    val stats: Map<String, ItemStatRange>,
    val flags: List<String>,
    val sprite: String,
    val tier: Int = 0,                          // 1-5 : iron/bronze/silver/gold/diamond
    val weaponType: String? = null,             // "sword","axe","hammer","flail","hoe"
    val attackSpeedMs: Int = 0                  // cooldown attaque en ms (0 = non-arme)
) {
    companion object {
        fun fromJson(j: JSONObject): ItemDef {
            val rarityWeights = mutableMapOf<ItemRarity, Int>()
            val rw = j.optJSONObject("rarity_weights")
            if (rw != null) {
                for (key in rw.keys()) {
                    val r = runCatching { ItemRarity.valueOf(key.uppercase()) }.getOrNull() ?: continue
                    rarityWeights[r] = rw.getInt(key)
                }
            }
            if (rarityWeights.isEmpty()) rarityWeights[ItemRarity.COMMON] = 100

            val dmgBase = j.optJSONArray("damage_base")?.let {
                ItemStatRange(it.getInt(0), it.getInt(1))
            }

            val stats = mutableMapOf<String, ItemStatRange>()
            val statsJson = j.optJSONObject("stats")
            if (statsJson != null) {
                for (key in statsJson.keys()) {
                    val arr = statsJson.optJSONArray(key)
                    if (arr != null) stats[key] = ItemStatRange(arr.getInt(0), arr.getInt(1))
                }
            }

            val flags = mutableListOf<String>()
            val flagsArr = j.optJSONArray("flags")
            if (flagsArr != null) {
                for (i in 0 until flagsArr.length()) flags.add(flagsArr.getString(i))
            }

            return ItemDef(
                id            = j.getString("id"),
                type          = j.optString("type", "material"),
                rarityWeights = rarityWeights,
                damageBase    = dmgBase,
                stats         = stats,
                flags         = flags,
                sprite        = j.optString("sprite", j.getString("id")),
                tier          = j.optInt("tier", 0),
                weaponType    = j.optString("weapon_type").takeIf { it.isNotBlank() },
                attackSpeedMs = j.optInt("attack_speed_ms", 0)
            )
        }
    }
}

internal data class ItemInstance(
    val defId: String,
    val rarity: ItemRarity,
    val rolledDamage: Int?,
    val rolledStats: Map<String, Int>,
    val count: Int = 1,
    val tier: Int = 0
)
