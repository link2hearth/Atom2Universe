package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import org.json.JSONObject

internal object MobRegistry {

    private val defs = mutableMapOf<String, MobDef>()

    fun load(assets: AssetManager) {
        if (defs.isNotEmpty()) return
        val files = assets.list("caves/mobs") ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/mobs/$file").bufferedReader().readText()
            val def = MobDef.fromJson(JSONObject(json))
            defs[def.id] = def
        }
    }

    fun get(id: String): MobDef =
        defs[id] ?: error("MobDef '$id' not found — vérifier caves/mobs/$id.json")

    fun allEligibleFor(biome: String, zone: Int): List<MobDef> =
        defs.values.filter { def ->
            (def.biomes.isEmpty() || "any" in def.biomes || biome in def.biomes)
                && zone >= def.spawnZoneMin && zone <= def.spawnZoneMax
        }

    fun all(): Collection<MobDef> = defs.values
}
