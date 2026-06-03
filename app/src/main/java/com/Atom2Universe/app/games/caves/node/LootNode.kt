package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import org.json.JSONObject
import kotlin.random.Random

internal data class LootEntry(
    val itemId: String,
    val weight: Int,
    val countMin: Int,
    val countMax: Int
)

internal data class LootTable(
    val id: String,
    val rollsMin: Int,
    val rollsMax: Int,
    val entries: List<LootEntry>
) {
    fun roll(rng: Random, mobLevel: Int = 1): List<ItemInstance> {
        if (entries.isEmpty()) return emptyList()
        val rolls = rollsMin + rng.nextInt((rollsMax - rollsMin + 1).coerceAtLeast(1))
        val totalWeight = entries.sumOf { it.weight }.coerceAtLeast(1)
        val result = mutableListOf<ItemInstance>()
        repeat(rolls) {
            var pick = rng.nextInt(totalWeight)
            for (entry in entries) {
                pick -= entry.weight
                if (pick < 0) {
                    val count = entry.countMin + rng.nextInt((entry.countMax - entry.countMin + 1).coerceAtLeast(1))
                    val instance = ItemRegistry.rollInstance(entry.itemId, rng, mobLevel)
                    if (instance != null) result.add(instance.copy(count = count))
                    break
                }
            }
        }
        return result
    }
}

internal object LootTableRegistry {

    private val tables = mutableMapOf<String, LootTable>()

    fun load(assets: AssetManager) {
        if (tables.isNotEmpty()) return
        val files = assets.list("caves/loot") ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/loot/$file").bufferedReader().readText()
            val t = fromJson(JSONObject(json))
            tables[t.id] = t
        }
    }

    fun get(id: String): LootTable? = tables[id]

    private fun fromJson(j: JSONObject): LootTable {
        val rollsArr = j.optJSONArray("rolls")
        val rollsMin = rollsArr?.getInt(0) ?: j.optInt("rolls", 1)
        val rollsMax = rollsArr?.getInt(1) ?: rollsMin
        val entries = mutableListOf<LootEntry>()
        val arr = j.optJSONArray("entries")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val countArr = e.optJSONArray("count")
                val countMin = countArr?.getInt(0) ?: e.optInt("count", 1)
                val countMax = countArr?.getInt(1) ?: countMin
                entries.add(LootEntry(
                    itemId   = e.getString("item"),
                    weight   = e.optInt("weight", 1),
                    countMin = countMin,
                    countMax = countMax
                ))
            }
        }
        return LootTable(j.getString("id"), rollsMin, rollsMax, entries)
    }
}

internal class LootNode(
    private val bus: EventBus,
    private val rng: Random = Random.Default
) {
    var onItemsDropped: ((items: List<ItemInstance>) -> Unit)? = null

    init {
        bus.subscribe { event ->
            if (event !is GameEvent.MobDied) return@subscribe
            val mobDef  = MobRegistry.get(event.mobDefId)
            // Les boss utilisent boss_loot, avec fallback sur la table normale si absente
            val tableId = if (event.isBoss) "boss_loot" else mobDef.lootTable
            val table   = LootTableRegistry.get(tableId)
                       ?: LootTableRegistry.get(mobDef.lootTable)
                       ?: return@subscribe
            val drops = table.roll(rng, event.level)
            if (drops.isNotEmpty()) onItemsDropped?.invoke(drops)
        }
    }
}
