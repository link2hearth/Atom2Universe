package com.Atom2Universe.app.games.caves.world

import kotlin.math.*
import kotlin.random.Random

/** Surface plants and trees, sampled from the same terrain as their supporting blocks. */
internal class CozyLandscape(private val seed: Long,
                             private val nearCave: (Int, Int) -> Boolean,
                             private val natural: NaturalTerrain,
                             private val reserved: (Int, Int, Int) -> Boolean) {
    private val cold by lazy {
        ColdLandscape(seed, { x, z -> height(x.toDouble(), z.toDouble()).toInt() },
            { x, z -> natural.biomeIdAt(x.toDouble(), z.toDouble()) },
            { x, z ->
                val id = natural.biomeIdAt(x.toDouble(), z.toDouble())
                val b = BiomeRegistry.surfaceBiomes.first { it.id == id }
                topBlock(b, x.toDouble(), z.toDouble(), height(x.toDouble(), z.toDouble()).toInt()) in shortArrayOf(SNOW, DIRT_SNOW)
            },
            { x, z -> reserved(x,z,10) || nearCave(x,z) })
    }
    private val offset = (seed and 0xFFFFF) * 0.0001
    private fun random(x: Int, z: Int, salt: Long) =
        Random(seed xor (x.toLong() * 341873128712L) xor (z.toLong() * 132897987541L) xor salt)

    fun height(x: Double, z: Double) = natural.height(x, z)
    fun topBlock(b: SurfaceBiomeDef, x: Double, z: Double, h: Int) = natural.topBlock(b, x, z, h, natural.waterLevelAt(x,z))

    private fun put(c: Chunk, x: Int, y: Int, z: Int, block: Short, onlyAir: Boolean = false, meta: Byte = 0) {
        val lx = x - c.worldX; val ly = y - c.worldY; val lz = z - c.worldZ
        if (lx in 0..15 && ly in 0..15 && lz in 0..15 && (!onlyAir || c.blockAt(lx, ly, lz) == AIR)) {
            c.setBlock(lx, ly, lz, block)
            c.setMeta(lx, ly, lz, meta)
        }
    }

    fun decorate(c: Chunk, heights: IntArray, tops: ShortArray, biomes: IntArray) {
        if (c.worldY > heights.max() + TreeShape.HEIGHT || c.worldY + 16 <= heights.min() - 3) return
        trees(c)
        cold.decorate(c)
        for (z in 0..15) for (x in 0..15) {
            val i = z * 16 + x; val h = heights[i]
            val y = h + 1 - c.worldY
            if (y !in 0..15 || c.blockAt(x, y, z) != AIR || h < NaturalTerrain.SEA_LEVEL) continue
            // Check the actual carved ground; never suspend plants over cave mouths.
            if (y > 0 && c.blockAt(x, y - 1, z) != tops[i]) continue
            val wx = c.worldX + x; val wz = c.worldZ + z
            if (y == 0 && nearCave(wx, wz)) continue
            if (cold.reserves(wx, wz) || reserved(wx,wz,0)) continue
            val rng = random(wx, wz, 71893L)
            val biome = BiomeRegistry.surfaceBiomes[biomes[i]]
            val patch = SimplexNoise.noise(wx * .042 + offset + 49, wz * .042)
            val block: Short = when {
                biome.id == "flower_meadow" && rng.nextFloat() < .38f ->
                    (7040 + Math.floorMod((wx / 7) + (wz / 9), 6)).toShort()
                biome.id in setOf("steppe", "dry_scrub", "heathland") && rng.nextFloat() < .20f ->
                    if (rng.nextBoolean()) GRASS_TAN else GRASS_BROWN
                biome.id in setOf("willow_marsh", "tropical_marsh") && rng.nextFloat() < .10f -> 7052
                h == 74 && rng.nextFloat() < .18f -> 7052
                (natural.temperature(wx.toDouble(), wz.toDouble()) > .63) &&
                    tops[i] in shortArrayOf(SAND, REDSAND) && h > 76 && rng.nextFloat() < .006f -> CACTUS
                tops[i] in shortArrayOf(GRASS, FOREST_FLOOR, MOSS) && rng.nextFloat() < .18 + max(0.0, patch) * .08 -> {
                    when {
                        h < 77 && rng.nextFloat() < .20f -> 7052 // occasional reeds along the water
                        biome.treeDensityBase > .06f && patch < .05 && rng.nextFloat() < .18f ->
                            if (rng.nextFloat() < .7f) 7046 else 7050 + rng.nextInt(2)
                        patch > .20 && rng.nextFloat() < .12f ->
                            7040 + floor((SimplexNoise.noise(wx * .012 + 913, wz * .012) + 1) * 3).toInt().coerceIn(0, 5)
                        rng.nextFloat() < .025f -> 7054 + rng.nextInt(2) // roses / berry bushes
                        else -> GRASS_TUFT_SHORT + rng.nextInt(3)
                    }.toShort()
                }
                tops[i] == SAND && h > 76 && rng.nextFloat() < .018f -> 7053
                tops[i] != GRASS && biome.decorationBlocks.isNotEmpty() && rng.nextFloat() < biome.decorationDensity ->
                    biome.decorationBlocks[rng.nextInt(biome.decorationBlocks.size)]
                tops[i] in shortArrayOf(GRASS, FOREST_FLOOR, MOSS) && rng.nextFloat() < .005f -> ROCK_MOSS
                else -> continue
            }
            if (BlockPlacement.supported(block, wx, h + 1, wz, natural::groundAt))
                put(c, wx, h + 1, wz, block, true)
        }
    }

    private fun trees(c: Chunk) {
        for (gz in Math.floorDiv(c.worldZ - TreeShape.REACH, 7)..Math.floorDiv(c.worldZ + 15 + TreeShape.REACH, 7))
            for (gx in Math.floorDiv(c.worldX - TreeShape.REACH, 7)..Math.floorDiv(c.worldX + 15 + TreeShape.REACH, 7)) {
                val rng = random(gx, gz, 44281L)
                val x = gx * 7 + 1 + rng.nextInt(5); val z = gz * 7 + 1 + rng.nextInt(5)
                val biome = BiomeRegistry.surfaceBiomes.first { it.id == natural.biomeIdAt(x.toDouble(), z.toDouble()) }
                val arid = biome.id in setOf("desert", "red_desert")
                if (biome.treeType == "none" && !arid) continue
                val grove = SimplexNoise.noise(x * .018 + offset + 83, z * .018)
                val density = if (arid) .025 else (biome.treeDensityBase * 12 + grove * .25).coerceIn(.025, .78)
                if (rng.nextFloat() > density) continue
                val variant = random(gx, gz, 91283L)
                val spacious = Math.floorMod(gx, 3) == 0 && Math.floorMod(gz, 3) == 0
                val treeType = when {
                    arid -> if (variant.nextInt(3) == 0) "acacia" else "baobab"
                    biome.id == "savanna" && variant.nextBoolean() -> "acacia"
                    biome.id == "wetlands" && variant.nextInt(3) == 0 -> "willow"
                    biome.treeType == "redwood" && spacious -> "giant_redwood"
                    biome.treeType == "sapin" && spacious -> "giant_pine"
                    biome.treeType in setOf("oak", "darkwood") && variant.nextInt(5) == 0 -> "broad_oak"
                    else -> biome.treeType
                }
                if (nearCave(x, z) || cold.reserves(x, z, TreeShape.REACH) || reserved(x,z,TreeShape.REACH)) continue
                val y = height(x.toDouble(), z.toDouble()).toInt()
                val soil = topBlock(biome, x.toDouble(), z.toDouble(), y)
                // Un lac ou étang surélevé garde h > 75 : vérifier aussi son niveau d'eau local,
                // sinon un arbre pourrait pousser au fond, sous la surface.
                if (y <= 75 || (natural.waterLevelAt(x.toDouble(), z.toDouble()) > y) ||
                    (soil !in shortArrayOf(GRASS, DIRT_SNOW, SNOW, FOREST_FLOOR, MOSS) &&
                        !(treeType in setOf("baobab", "acacia") && soil in shortArrayOf(SAND, REDSAND)))) continue
                // Wide trunks need a stable footprint, not a cliff edge or a cave mouth.
                val footprint = when (treeType) {
                    "baobab" -> -2..2
                    "giant_redwood", "giant_pine", "broad_oak", "willow" -> 0..1
                    else -> 0..0
                }
                if (footprint.any { dx -> footprint.any { dz ->
                    val ground = height((x + dx).toDouble(), (z + dz).toDouble()).toInt()
                    ground !in y - 2..y || nearCave(x + dx, z + dz)
                } }) continue
                if (c.worldY > y + TreeShape.HEIGHT || c.worldY + 16 <= y) continue
                TreeShape.generate(treeType, rng) { dx, dy, dz, block, onlyAir ->
                    if (dy == 1 && com.Atom2Universe.app.games.caves.node.BlockRegistry.isWood(block)) {
                        val ground = height((x + dx).toDouble(), (z + dz).toDouble()).toInt()
                        for (rootY in ground + 1..y) put(c, x + dx, rootY, z + dz, block, true)
                    }
                    put(c, x + dx, y + dy, z + dz, block, onlyAir,
                        if (isLeaf(block)) TreeSpecies.leafMeta(treeType) else 0)
                }
            }
    }

}
