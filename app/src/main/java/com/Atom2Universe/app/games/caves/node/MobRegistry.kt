package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import org.json.JSONObject
import kotlin.math.min

internal object MobRegistry {

    private val defs = mutableMapOf<String, MobDef>()

    fun load(assets: AssetManager) {
        if (defs.isNotEmpty()) return

        // ── 1. Génération procédurale depuis Cave World/Pokemon/ ──────────────
        val sprites = try { assets.list("Cave World/Pokemon") ?: emptyArray() }
                      catch (_: Exception) { emptyArray() }
        for (file in sprites) {
            if (!file.endsWith(".png") || file.endsWith("s.png")) continue
            val sheet   = file.removeSuffix(".png")
            val baseNum = sheet.substringBefore(".").toIntOrNull() ?: continue
            val def     = buildDef(sheet, baseNum)
            defs[def.id] = def
        }

        // ── 2. Overrides JSON (caves/mobs/*.json) — priorité absolue ─────────
        val files = try { assets.list("caves/mobs") ?: emptyArray() }
                    catch (_: Exception) { emptyArray() }
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/mobs/$file").bufferedReader().readText()
            val def  = MobDef.fromJson(JSONObject(json))
            defs[def.id] = def
        }
    }

    /** Génère un MobDef par défaut depuis le numéro de sprite sheet. */
    private fun buildDef(sheet: String, n: Int): MobDef = MobDef(
        id                 = "pk_${sheet.replace(".", "_")}",
        hpBase             = 5,          // HP = 5 × level² → ~500 HP au level 10
        damageBase         = maxOf(1, n / 30),
        speed              = (2.5 + min(n * 0.001, 0.8)).toFloat(),
        attackRange        = 1.6,
        detectRange        = 12.0,
        eyeHeight          = 1.7f,
        radius             = 0.5f,
        spriteScale        = 0.85f,
        hpScalePerLevel    = 1.0,        // inutilisé (formule quadratique)
        hpScaleCap         = 1.0,        // inutilisé
        damageScalePer3Lvl = 1,
        speedScalePerLevel = 0.08f,
        biomes             = listOf("any"),
        spriteSheet        = sheet,
        spawnZoneMin       = maxOf(1, n - 1),
        spawnZoneMax       = n + 1,
        lootTable          = tierLootTable(maxOf(1, n - 1)),
        behavior           = "aggressive",
        bossEligible       = true,
        xpBase             = n
    )

    private fun tierLootTable(zone: Int) = when {
        zone < 5  -> "loot_t1"
        zone < 10 -> "loot_t2"
        zone < 15 -> "loot_t3"
        zone < 20 -> "loot_t4"
        else      -> "loot_t5"
    }

    fun get(id: String): MobDef =
        defs[id] ?: error("MobDef '$id' introuvable")

    fun allEligibleFor(biome: String, zone: Int): List<MobDef> =
        defs.values.filter { def ->
            (def.biomes.isEmpty() || "any" in def.biomes || biome in def.biomes)
                && zone >= def.spawnZoneMin && zone <= def.spawnZoneMax
        }

    fun all(): Collection<MobDef> = defs.values
}
