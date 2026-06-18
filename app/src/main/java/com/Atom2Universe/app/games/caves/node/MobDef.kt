package com.Atom2Universe.app.games.caves.node

import org.json.JSONObject

internal data class MobDef(
    val id: String,
    val hpBase: Int,
    val damageBase: Int,
    val speed: Float,
    val attackRange: Double,
    val detectRange: Double,
    val eyeHeight: Float,
    val radius: Float,
    val spriteScale: Float,
    val hpScalePerLevel: Double,
    val hpScaleCap: Double,
    val damageScalePer3Lvl: Int,
    val speedScalePerLevel: Float,
    val biomes: List<String>,
    /** Id du modèle voxel à rendre (voir [com.Atom2Universe.app.games.caves.render.MobModels]) : "zombie", "skeleton"… */
    val model: String,
    val spawnZoneMin: Int,
    val spawnZoneMax: Int,
    val lootTable: String,
    val behavior: String,
    val bossEligible: Boolean,
    val xpBase: Int
) {
    companion object {
        fun fromJson(j: JSONObject): MobDef {
            val biomes = mutableListOf<String>()
            val biomesArr = j.getJSONArray("biomes")
            for (i in 0 until biomesArr.length()) biomes.add(biomesArr.getString(i))
            return MobDef(
                id                 = j.getString("id"),
                hpBase             = j.getInt("hp_base"),
                damageBase         = j.getInt("damage_base"),
                speed              = j.getDouble("speed").toFloat(),
                attackRange        = j.getDouble("attack_range"),
                detectRange        = j.getDouble("detect_range"),
                eyeHeight          = j.getDouble("eye_height").toFloat(),
                radius             = j.getDouble("radius").toFloat(),
                spriteScale        = j.getDouble("sprite_scale").toFloat(),
                hpScalePerLevel    = j.optDouble("hp_scale_per_level", 1.4),
                hpScaleCap         = j.optDouble("hp_scale_cap", 20.0),
                damageScalePer3Lvl = j.optInt("damage_scale_per_3lvl", 1),
                speedScalePerLevel = j.optDouble("speed_scale_per_level", 0.10).toFloat(),
                biomes             = biomes,
                model              = j.optString("model", "slime"),
                spawnZoneMin       = j.optInt("spawn_zone_min", 1),
                spawnZoneMax       = j.optInt("spawn_zone_max", Int.MAX_VALUE),
                lootTable          = j.optString("loot_table", "default_loot"),
                behavior           = j.optString("behavior", "aggressive"),
                bossEligible       = j.optBoolean("boss_eligible", true),
                xpBase             = j.optInt("xp_base", 1)
            )
        }
    }
}
