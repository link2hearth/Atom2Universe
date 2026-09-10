package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

/** Version 2 only. Every feature is a function of world coordinates, never chunk load order. */
internal class CozyLandscape(private val seed: Long,
                             private val nearCave: (Int, Int) -> Boolean = { _, _ -> false }) {
    private data class Relief(val base: Double, val amplitude: Double)
    private data class Site(val x: Int, val z: Int, val y: Int, val kind: Int)
    private val relief = ConcurrentHashMap<Long, Relief>()
    private val sites = ConcurrentHashMap<Long, Site>()
    private val offset = (seed and 0xFFFFF) * 0.0001
    private fun key(x: Int, z: Int) = (x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
    private fun random(x: Int, z: Int, salt: Long) =
        Random(seed xor (x.toLong() * 341873128712L) xor (z.toLong() * 132897987541L) xor salt)
    private fun smooth(t: Double): Double = t.coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
    private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun reliefAt(x: Int, z: Int): Relief = relief.getOrPut(key(x, z)) {
        if (relief.size > 4096) relief.clear()
        val biomes = BiomeRegistry.surfaceBiomes
        val weights = DoubleArray(biomes.size)
        BiomeMap.biomeWeights(x * 64.0, z * 64.0, seed, weights)
        var base = 0.0; var amplitude = 0.0
        for (i in biomes.indices) {
            base += weights[i] * biomes[i].heightBase.coerceIn(-26.0, 48.0)
            amplitude += weights[i] * biomes[i].heightAmplitude.coerceIn(6.0, 32.0)
        }
        Relief(base, amplitude)
    }

    /** Smooth biome interpolation removes the former two-block height discontinuities. */
    internal fun naturalHeight(x: Double, z: Double): Double {
        val gx = floor(x / 64).toInt(); val gz = floor(z / 64).toInt()
        val tx = smooth(x / 64 - gx); val tz = smooth(z / 64 - gz)
        val a = reliefAt(gx, gz); val b = reliefAt(gx + 1, gz)
        val c = reliefAt(gx, gz + 1); val d = reliefAt(gx + 1, gz + 1)
        val base = mix(mix(a.base, b.base, tx), mix(c.base, d.base, tx), tz)
        val amp = mix(mix(a.amplitude, b.amplitude, tx), mix(c.amplitude, d.amplitude, tx), tz)
        val rolling = SimplexNoise.noise(x * .0025 + offset, z * .0025)
        val hills = SimplexNoise.noise(x * .009 + offset + 120, z * .009)
        val detail = SimplexNoise.noise(x * .027 + offset + 240, z * .027)
        val land = 83.0 + base + rolling * amp * .52 + hills * 2.4 + detail * .35
        // Broad grassy riverbanks; highlands retain their own silhouette.
        val river = abs(SimplexNoise.noise(x * .0017 + offset + 712, z * .0017))
        val valley = (1.0 - smooth((river - .015) / .15)) * (1.0 - smooth((land - 88) / 16))
        return mix(land, min(land, 72.0), valley).coerceIn(25.0, 180.0)
    }

    private fun site(x: Int, z: Int): Site = sites.getOrPut(key(x, z)) {
        if (sites.size > 2048) sites.clear()
        val rng = random(x, z, 983741L)
        val sx = x * 128 + 24 + rng.nextInt(64)
        val sz = z * 128 + 24 + rng.nextInt(64)
        val y = naturalHeight(sx + 6.0, sz + 6.0).toInt()
        val biome = BiomeMap.surfaceBiomeAt(sx.toDouble(), sz.toDouble(), seed)
        val suitable = biome.surfaceBlocks.any { it.block == GRASS } && y >= 77 && !nearCave(sx + 6, sz + 6) &&
            listOf(0 to 0, 12 to 0, 0 to 12, 12 to 12).all { (dx, dz) ->
                abs(naturalHeight((sx + dx).toDouble(), (sz + dz).toDouble()) - y) < 3.0
            }
        Site(sx, sz, y, if (rng.nextFloat() < .62f && suitable) rng.nextInt(3) else -1)
    }

    fun height(x: Double, z: Double): Double {
        val h = naturalHeight(x, z)
        val s = site(floor(x / 128).toInt(), floor(z / 128).toInt())
        if (s.kind < 0) return h
        val distance = max(max(s.x - x, x - (s.x + 12)), max(s.z - z, z - (s.z + 12)))
        return mix(s.y.toDouble(), h, smooth(distance / 7.0))
    }

    fun topBlock(b: SurfaceBiomeDef, x: Double, z: Double, h: Int): Short {
        if (h <= 74) return SAND
        if (b.surfaceBlocks.any { it.block == GRASS }) return GRASS
        val n = SimplexNoise.noise(x * b.surfaceVarietyScale + b.surfaceVarietyOffset, z * b.surfaceVarietyScale)
        return b.surfaceBlocks.firstOrNull { n >= it.noiseMin }?.block ?: b.surfaceBlocks.last().block
    }

    private fun put(c: Chunk, x: Int, y: Int, z: Int, block: Short, onlyAir: Boolean = false) {
        val lx = x - c.worldX; val ly = y - c.worldY; val lz = z - c.worldZ
        if (lx in 0..15 && ly in 0..15 && lz in 0..15 && (!onlyAir || c.blockAt(lx, ly, lz) == AIR))
            c.setBlock(lx, ly, lz, block)
    }

    fun decorate(c: Chunk, heights: IntArray, tops: ShortArray, biomes: IntArray) {
        if (c.worldY > heights.max() + 14 || c.worldY + 16 <= heights.min() - 3) return
        trees(c)
        for (z in 0..15) for (x in 0..15) {
            val i = z * 16 + x; val h = heights[i]
            val y = h + 1 - c.worldY
            if (y !in 0..15 || c.blockAt(x, y, z) != AIR || h < 75) continue
            // Check the actual carved ground; never suspend plants over cave mouths.
            if (y > 0 && c.blockAt(x, y - 1, z) != tops[i]) continue
            val wx = c.worldX + x; val wz = c.worldZ + z
            if (y == 0 && nearCave(wx, wz)) continue
            val rng = random(wx, wz, 71893L)
            val biome = BiomeRegistry.surfaceBiomes[biomes[i]]
            val patch = SimplexNoise.noise(wx * .042 + offset + 49, wz * .042)
            val block: Short = when {
                tops[i] == GRASS && rng.nextFloat() < .18 + max(0.0, patch) * .08 -> {
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
                tops[i] == GRASS && rng.nextFloat() < .005f -> ROCK_MOSS
                else -> continue
            }
            put(c, wx, h + 1, wz, block, true)
        }
        // Each plot fits within its own 128-block cell, including the seven-block skirt.
        val s = site(Math.floorDiv(c.worldX, 128), Math.floorDiv(c.worldZ, 128))
        if (s.kind >= 0 && c.worldX + 16 > s.x && c.worldX <= s.x + 12 &&
            c.worldZ + 16 > s.z && c.worldZ <= s.z + 12) buildSite(c, s)
    }

    private fun trees(c: Chunk) {
        for (gz in Math.floorDiv(c.worldZ - 4, 7)..Math.floorDiv(c.worldZ + 19, 7))
            for (gx in Math.floorDiv(c.worldX - 4, 7)..Math.floorDiv(c.worldX + 19, 7)) {
                val rng = random(gx, gz, 44281L)
                val x = gx * 7 + 1 + rng.nextInt(5); val z = gz * 7 + 1 + rng.nextInt(5)
                val biome = BiomeMap.surfaceBiomeAt(x.toDouble(), z.toDouble(), seed)
                if (biome.treeType == "none") continue
                val grove = SimplexNoise.noise(x * .018 + offset + 83, z * .018)
                if (rng.nextFloat() > (biome.treeDensityBase * 12 + grove * .25).coerceIn(.025, .78)) continue
                if (nearCave(x, z)) continue
                val s = site(Math.floorDiv(x, 128), Math.floorDiv(z, 128))
                if (s.kind >= 0 && x in s.x - 5..s.x + 17 && z in s.z - 5..s.z + 17) continue
                val y = height(x.toDouble(), z.toDouble()).toInt()
                if (y <= 75 || topBlock(biome, x.toDouble(), z.toDouble(), y) !in shortArrayOf(GRASS, DIRT_SNOW, SNOW)) continue
                val tall = biome.treeMinHeight.coerceIn(4, 8) + rng.nextInt(3)
                if (c.worldY > y + tall + 4 || c.worldY + 16 <= y) continue
                val (wood, leaf) = when (biome.treeType) {
                    "birch" -> WOOD_WHITE to LEAVES
                    "sapin" -> WOOD_SAPIN to LEAVES_SAPIN
                    "darkwood" -> WOOD_DARK to LEAVES_DARK
                    "jungle", "jungle_small" -> WOOD_JUNGLE to LEAVES_JUNGLE
                    "redwood" -> WOOD_RED to LEAVES_ORANGE
                    "pink" -> WOOD_PINK to LEAVES_PINK
                    "purple" -> WOOD_PURPLE to LEAVES_PURPLE
                    "blue" -> WOOD_BLUE to LEAVES_BLUE
                    "yellow" -> WOOD_YELLOW to LEAVES_YELLOW
                    else -> WOOD to LEAVES
                }
                for (dy in 1..tall) put(c, x, y + dy, z, wood)
                val pine = biome.treeType == "sapin"
                for (dy in (if (pine) 2 else tall - 2)..tall + 2) {
                    val r = if (pine) ((tall + 3 - dy) / 2.4).coerceIn(.6, 3.0)
                        else sqrt((1.0 - ((dy - tall) / 3.0).pow(2)).coerceAtLeast(0.0)) * 3.2
                    for (dz in -3..3) for (dx in -3..3)
                        if (dx * dx + dz * dz <= r * r && (dx != 0 || dz != 0 || dy > tall))
                            put(c, x + dx, y + dy, z + dz, leaf, true)
                }
            }
    }

    private fun buildSite(c: Chunk, s: Site) {
        fun p(x: Int, y: Int, z: Int, b: Short) = put(c, s.x + x, s.y + y, s.z + z, b)
        // Clear the volume explicitly, including decorations and foliage from nearby chunks.
        for (z in 0..12) for (x in 0..12) {
            for (y in 1..10) p(x, y, z, AIR)
            for (y in -3..-1) p(x, y, z, DIRT)
            p(x, 0, z, if (x in 5..7 || z == 6) 106 else GRASS)
        }
        when (s.kind) {
            0 -> { // Cream cottage, teal pitched roof, porch, flower boxes and chimney.
                for (z in 2..9) for (x in 2..10) {
                    p(x, 0, z, 2201)
                    for (y in 1..4) if (x == 2 || x == 10 || z == 2 || z == 9)
                        p(x, y, z, if (x in listOf(2, 10) && z in listOf(2, 9)) WOOD else 2201)
                }
                for (y in 1..3) p(6, y, 2, AIR)
                for (x in listOf(4, 8)) { p(x, 2, 2, GLASS); p(x, 3, 2, GLASS); p(x, 1, 1, PLANK); p(x, 2, 1, 7040) }
                for (z in 4..7) { p(2, 2, z, GLASS); p(10, 2, z, GLASS) }
                for (x in 1..11) {
                    val roof = 5 + (5 - abs(x - 6)) / 2
                    for (z in 1..10) p(x, roof, z, 2200)
                    for (y in 5 until roof) { p(x, y, 2, PLANK); p(x, y, 9, PLANK) }
                }
                for (y in 4..9) p(9, y, 7, BRICK_RED)
                p(4, 1, 7, TABLE); p(8, 1, 7, FURNACE); p(5, 3, 8, TORCH)
            }
            1 -> { // Open pavilion beside a small stone well.
                for (x in 2..9) for (z in 3..10) p(x, 0, z, PLANK)
                for (x in listOf(2, 9)) for (z in listOf(3, 10)) for (y in 1..4) p(x, y, z, WOOD)
                for (x in 1..10) for (z in 2..11) p(x, 5, z, 2200)
                for (x in 2..9) p(x, 1, 9, PLANK)
                p(4, 1, 6, TABLE); p(7, 1, 6, TABLE); p(2, 3, 3, TORCH)
                for (x in 9..11) for (z in 0..2) {
                    p(x, 0, z, 2201)
                    p(x, 1, z, if (x == 10 && z == 1) WATER else 2201)
                }
            }
            else -> { // Walled flower garden among mossy remains, with planted rows.
                for (x in 1..11) for (z in listOf(1, 11)) if (x !in 5..7) {
                    p(x, 1, z, BRICK_MOSS)
                    if (x % 4 == 1) p(x, 2, z, 2201)
                }
                for (x in listOf(2, 4, 8, 10)) for (z in 3..9) {
                    p(x, 0, z, DIRT)
                    p(x, 1, z, (if (x < 6) 7040 + z % 6 else 7022 + z % 2).toShort())
                }
                for (y in 1..4) p(1, y, 6, 2201)
                for (x in 1..3) p(x, 4, 6, BRICK_MOSS)
                p(3, 4, 6, LEAVES); p(10, 1, 10, PLANK); p(11, 1, 10, PLANK)
            }
        }
        for (z in listOf(0, 12)) for (x in listOf(0, 3, 9, 12)) p(x, 1, z, (7040 + (x + z) % 6).toShort())
    }
}
