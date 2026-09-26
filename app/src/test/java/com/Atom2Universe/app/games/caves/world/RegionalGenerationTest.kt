package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class RegionalGenerationTest {
    private fun profiles(): List<NaturalBiomeProfile> {
        val text = File("src/main/assets/caves/natural_generation.json").readText()
        return Regex("\\{[^{}]*\"id\"[^{}]*\\}").findAll(text).map { m ->
            fun number(key: String) = Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(m.value)?.groupValues?.get(1)?.toDouble() ?: 0.0
            NaturalBiomeProfile(Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(m.value)!!.groupValues[1],
                number("base"), number("amplitude"), number("temperature"), number("humidity"), number("rarity"))
        }.toList()
    }

    @Test fun climatesSeparateSnowAndHotDesertsAndMostLandStaysLow() {
        val definitions = profiles()
        val hot = setOf("desert", "red_desert", "sandstone_badlands", "dry_scrub", "baobab_savanna", "rainforest")
        val snow = setOf("tundra", "snowy_taiga", "frost_plains", "iceberg")
        var land = 0; var high = 0
        val seen = mutableSetOf<String>()
        for (seed in listOf(7L, 42L, 123456789L)) {
            val terrain = NaturalTerrain(seed, definitions)
            for (x in -12000..12000 step 192) for (z in -12000..12000 step 192) {
                val id = terrain.biomeIdAt(x.toDouble(), z.toDouble()); seen += id
                val temperature = terrain.temperature(x.toDouble(), z.toDouble())
                if (id in hot) assertTrue("Hot biome $id at $temperature", temperature > .63)
                if (id in snow) assertTrue("Snow biome $id at $temperature", temperature < .25)
                val h = terrain.height(x.toDouble(), z.toDouble())
                if (h >= NaturalTerrain.SEA_LEVEL) { land++; if (h > NaturalTerrain.SEA_LEVEL + 50) high++ }
                assertTrue("Extreme altitude: $h", h < NaturalTerrain.SEA_LEVEL + 300)
                if (id in hot) for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1))
                    assertFalse(terrain.biomeIdAt((x + dx).toDouble(), (z + dz).toDouble()) in snow)
            }
        }
        assertTrue("Relief too high: $high/$land", high < land * .04)
        assertTrue("Not enough new biomes encountered: $seen", seen.count { RegionalBiomes.variant(it) != null } >= 12)
    }

    @Test fun villagePathsReachEveryHouseAndMillWithoutCrossingWallsOrWater() {
        for (seed in 0..23) for (climate in 0..2) {
            val plan = RegionalSettlements.village(Random(seed), climate == 2, climate == 1, false) { _, _ -> 0 }
            fun block(x: Int, y: Int, z: Int) = plan[Triple(x, y, z)]?.id ?: if (y <= 0) STONE else AIR
            // Only planned ground counts: no shortcut through unplanned terrain.
            fun walkable(x: Int, z: Int) = plan.containsKey(Triple(x, 0, z)) &&
                block(x, 0, z) != WATER && block(x, 0, z) != AIR &&
                block(x, 1, z) == AIR && block(x, 2, z) == AIR
            val start = 44 to 44
            assertTrue(walkable(start.first, start.second))
            val visited = mutableSetOf(start); val queue = ArrayDeque<Pair<Int, Int>>(); queue += start
            while (queue.isNotEmpty()) {
                val (x, z) = queue.removeFirst()
                for (p in listOf(x + 1 to z, x - 1 to z, x to z + 1, x to z - 1))
                    if (walkable(p.first, p.second) && visited.add(p)) queue += p
            }
            val cookers = plan.filterValues { it.id == F.COOKER }
            assertTrue(cookers.size in 3..4)
            for (p in cookers.keys + plan.filterValues { it.id == F.MILL }.keys) {
                assertTrue("Unreachable building: seed $seed climate $climate $p",
                    listOf(p.first + 1 to p.third, p.first - 1 to p.third, p.first to p.third + 1, p.first to p.third - 1).any { it in visited })
            }
            val head = plan.filterValues { it.id == F.WINDMILL }.keys.single()
            assertFalse(plan.values.any { it.id == F.SAIL })
            for (s in RegionalSettlements.sails())
                assertEquals(AIR, block(head.first + s[0], head.second + s[1], head.third + s[2]))
        }
    }

    @Test fun cavesAreDeterministicAcrossNegativeCoordinatesAndKeepLakeBedsClosed() {
        val a = NaturalTerrain(42, profiles())
        val b = NaturalTerrain(42, profiles())
        for (x in -64..64 step 4) for (z in -64..64 step 4) {
            val h = a.height(x.toDouble(), z.toDouble()).toInt()
            val water = a.waterLevelAt(x.toDouble(), z.toDouble())
            for (y in h - 80..h + 4 step 4) assertEquals(a.caveField(x.toDouble(), y.toDouble(), z.toDouble()),
                b.caveField(x.toDouble(), y.toDouble(), z.toDouble()), 0.0)
            if (h <= water) for (y in h - 7..h) assertFalse(a.caveAt(x, y, z))
        }
    }
}
