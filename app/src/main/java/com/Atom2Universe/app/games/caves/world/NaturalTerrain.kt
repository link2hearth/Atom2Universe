package com.Atom2Universe.app.games.caves.world

import android.content.res.AssetManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

internal data class NaturalBiomeProfile(val id: String, val base: Double, val amplitude: Double,
    val temperature: Double, val humidity: Double, val rarity: Double)

internal object NaturalTerrainSettings {
    var profiles: List<NaturalBiomeProfile> = emptyList()
        private set
    fun load(assets: AssetManager) {
        if (profiles.isNotEmpty()) return
        val root = JSONObject(assets.open("caves/natural_generation.json").bufferedReader().use { it.readText() })
        val a = root.getJSONArray("biomes")
        profiles = (0 until a.length()).map { i -> a.getJSONObject(i).let {
            NaturalBiomeProfile(it.getString("id"), it.getDouble("base"), it.getDouble("amplitude"),
                it.getDouble("temperature"), it.getDouble("humidity"), it.optDouble("rarity", 0.0))
        } }
        require(profiles.isNotEmpty() && profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.all { it.amplitude > 0 && it.temperature in 0.0..1.0 && it.humidity in 0.0..1.0 })
    }
}

/** Version 3: a single surface, unstratified caves and world-coordinate deposits. */
internal class NaturalTerrain(private val seed: Long, private val profiles: List<NaturalBiomeProfile> = NaturalTerrainSettings.profiles) {
    companion object { const val SEA_LEVEL = 74; const val SURFACE_MAX_CY = 255 }
    private val offset = ((seed xor (seed ushr 32)) and 0xFFFFFF).toDouble() * .013
    private data class Relief(val base: Double, val amplitude: Double)
    private val relief = ConcurrentHashMap<Long, Relief>()
    private fun n(x: Double, z: Double, scale: Double, salt: Double) =
        SimplexNoise.noise(x * scale + offset + salt, z * scale - offset)
    private fun smooth(v: Double) = v.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun temperature(x: Double, z: Double) = (.5 + n(x, z, .00065, 410.0) * .75).coerceIn(0.0, 1.0)
    fun humidity(x: Double, z: Double) = (.5 + n(x, z, .0008, 870.0) * .75).coerceIn(0.0, 1.0)

    private fun weights(x: Double, z: Double, out: DoubleArray): Int {
        val t = temperature(x, z); val h = humidity(x, z)
        val continental = n(x, z, .00045, 181.0)
        val rugged = n(x, z, .00085, 920.0)
        var best = -Double.MAX_VALUE; var dominant = 0
        for (i in profiles.indices) {
            val p = profiles[i]
            val ocean = p.id == "ocean" || p.id == "iceberg"
            val special = when (p.id) {
                "ocean", "iceberg" -> (-continental - .08) * 2.5
                "mountains", "rocky", "volcanic" -> (rugged - .40) * 1.2
                "wetlands" -> -.12
                else -> 0.0
            }
            val score = -((t - p.temperature).pow(2) + (h - p.humidity).pow(2)) * 2.6 +
                n(x, z, .0014, 2100.0 + i * 137.0) * .20 + special - p.rarity -
                (if (!ocean) max(0.0, -continental - .10) * 2.5 else 0.0)
            out[i] = score
            if (score > best) { best = score; dominant = i }
        }
        var sum = 0.0
        for (i in out.indices) { out[i] = max(0.0, out[i] - best + .16).pow(2); sum += out[i] }
        for (i in out.indices) out[i] /= sum
        return dominant
    }
    fun biomeIdAt(x: Double, z: Double): String {
        val w = DoubleArray(profiles.size)
        return profiles[weights(x, z, w)].id
    }
    /** Analytic ground lookup for cross-chunk plant support, before neighbours are loaded. */
    fun groundAt(x: Int, y: Int, z: Int): Short {
        val h = height(x.toDouble(), z.toDouble()).toInt()
        if (y > h) return if (y == SEA_LEVEL && temperature(x.toDouble(), z.toDouble()) < .18) ICE else if (y <= SEA_LEVEL) WATER else AIR
        if (caveAt(x, y, z)) return AIR
        if (y < h) return STONE
        val id = biomeIdAt(x.toDouble(), z.toDouble())
        val biome = BiomeRegistry.surfaceBiomes.first { it.id == id }
        return topBlock(biome, x.toDouble(), z.toDouble(), h)
    }
    private fun reliefAt(x: Int, z: Int): Relief = relief.getOrPut((x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)) {
        if (relief.size > 8192) relief.clear()
        val w = DoubleArray(profiles.size); weights(x * 192.0, z * 192.0, w)
        Relief(profiles.indices.sumOf { w[it] * profiles[it].base }, profiles.indices.sumOf { w[it] * profiles[it].amplitude })
    }
    fun height(x: Double, z: Double): Double {
        val gx = floor(x / 192).toInt(); val gz = floor(z / 192).toInt()
        val tx = smooth(x / 192 - gx); val tz = smooth(z / 192 - gz)
        val a = reliefAt(gx, gz); val b = reliefAt(gx + 1, gz); val c = reliefAt(gx, gz + 1); val d = reliefAt(gx + 1, gz + 1)
        val base = mix(mix(a.base, b.base, tx), mix(c.base, d.base, tx), tz)
        val amplitude = mix(mix(a.amplitude, b.amplitude, tx), mix(c.amplitude, d.amplitude, tx), tz)
        val broad = .5 + n(x, z, .00065, 31.0) * .5
        val peak = smooth((n(x, z, .0009, 121.0) - .45) / .35)
        val ridge = (1.0 - abs(n(x, z, .0010, 51.0))).pow(2) * peak
        val hills = n(x, z, .0025, 62.0) * .035 + n(x, z, .009, 92.0) * .006
        val land = SEA_LEVEL + base + amplitude * (broad * .65 + ridge * .28 + hills)
        val river = abs(n(x, z, .0012, 712.0))
        val valley = (1 - smooth((river - .012) / .08)) * (1 - smooth((land - 110) / 180))
        return mix(land, min(land, SEA_LEVEL - 3.0), valley).coerceAtLeast(8.0)
    }
    fun topBlock(b: SurfaceBiomeDef, x: Double, z: Double, h: Int): Short {
        val patch = n(x, z, .035, 334.0)
        val wet = humidity(x, z)
        if (h <= SEA_LEVEL + 2) {
            if (patch > .22 && h >= SEA_LEVEL - 16) return CLAY
            if (wet > .65 && h >= SEA_LEVEL - 2) return if (patch > -.15) MUD else CLAY
            return if (b.id == "red_desert") REDSAND else if (patch < -.45) GRAVEL else SAND
        }
        val snowLine = 420 + temperature(x, z) * 700
        if (h > snowLine || b.id == "tundra" || b.id == "iceberg") return if (patch > .2) SNOW else DIRT_SNOW
        return when (b.id) {
            "volcanic" -> BASALT
            "mountains", "rocky" -> if (patch > .36) GRAVEL else if (patch < -.40) GRANITE else STONE
            "desert" -> if (patch > .40) SANDSTONE else SAND
            "red_desert" -> if (patch > .40) 2308 else REDSAND
            "wetlands" -> if (patch > .20) MOSS else MUD
            else -> if (b.treeType != "none" && patch > -.12) { if (patch > .48) MOSS else FOREST_FLOOR } else GRASS
        }
    }

    /** Signed field: positive means air. No periodic Y bands, cylinders or flat cavern floors. */
    fun caveField(x: Double, y: Double, z: Double): Double {
        val depth = height(x, z) - y
        if (depth < -8) return -10.0
        val warp = SimplexNoise.noise(x * .008 + offset, y * .012, z * .008) * 9
        val chamber = SimplexNoise.noise((x + warp) * .012 + offset + 180, y * .019, (z - warp) * .012)
        val a = SimplexNoise.noise((x + warp) * .022 + offset + 510, y * .029, z * .022)
        val b = SimplexNoise.noise(x * .024 + offset + 950, y * .031, (z - warp) * .024)
        val entrance = smooth((n(x, z, .012, 177.0) + .08) / .32)
        val width = .12 + .035 * smooth(depth / 70) - (1 - entrance) * .16 * (1 - smooth(depth / 24))
        val tunnel = (width - max(abs(a), abs(b))) * 22
        val room = (chamber - .44) * 30 - (1 - smooth(depth / 36)) * 14
        return max(tunnel, room)
    }
    fun caveAt(x: Int, y: Int, z: Int): Boolean {
        val h = height(x.toDouble(), z.toDouble()).toInt()
        if (y > h || (h <= SEA_LEVEL && h - y < 8)) return false
        val gx = Math.floorDiv(x, 4) * 4; val gy = Math.floorDiv(y, 4) * 4; val gz = Math.floorDiv(z, 4) * 4
        val tx = Math.floorMod(x, 4) / 4.0; val ty = Math.floorMod(y, 4) / 4.0; val tz = Math.floorMod(z, 4) / 4.0
        fun plane(dz: Int): Double {
            fun f(dx: Int, dy: Int) = caveField((gx + dx).toDouble(), (gy + dy).toDouble(), (gz + dz).toDouble())
            return mix(mix(f(0, 0), f(4, 0), tx), mix(f(0, 4), f(4, 4), tx), ty)
        }
        return mix(plane(0), plane(4), tz) > 0
    }

    private fun hash(x: Int, y: Int, z: Int): Long {
        var v = seed xor (x.toLong() * 341873128712L) xor (y.toLong() * 42317861L) xor (z.toLong() * 132897987541L)
        v = (v xor (v ushr 30)) * -4658895280553007687L
        return (v xor (v ushr 27)) and Long.MAX_VALUE
    }
    fun rockAt(x: Int, y: Int, z: Int, depth: Int): Short {
        val cell = hash(Math.floorDiv(x, 12), Math.floorDiv(y, 12), Math.floorDiv(z, 12))
        val dx = Math.floorMod(x, 12) - (3 + (cell % 6).toInt())
        val dy = Math.floorMod(y, 12) - (3 + (cell / 7 % 6).toInt())
        val dz = Math.floorMod(z, 12) - (3 + (cell / 43 % 6).toInt())
        if (depth > 5 && cell % 5 < 2 && dx * dx + dy * dy + dz * dz <= 7) {
            val ore = (cell / 251 % 20).toInt()
            return when {
                ore < 6 -> COAL
                ore < 11 -> IRON
                ore < 15 -> COPPER
                depth < 40 -> COAL
                ore == 15 -> SILVER
                ore == 16 -> GOLD
                ore == 17 -> REDSTONE
                ore == 18 -> if (cell % 2 == 0L) RUBY else EMERALD
                else -> CRYSTAL
            }
        }
        val geology = SimplexNoise.noise(x * .009 + offset + 390, y * .013, z * .009)
        return when { geology > .44 -> BASALT; geology < -.45 -> 2201; geology > .23 -> GRANITE; geology < -.28 -> QUARTZ; else -> STONE }
    }

    fun generate(chunk: Chunk, landscape: CozyLandscape) {
        val biomes = BiomeRegistry.surfaceBiomes
        val heights = IntArray(256); val tops = ShortArray(256); val indices = IntArray(256)
        for (z in 0..15) for (x in 0..15) {
            val wx = chunk.worldX + x; val wz = chunk.worldZ + z; val i = z * 16 + x
            val h = height(wx.toDouble(), wz.toDouble()).toInt(); heights[i] = h
            val id = biomeIdAt(wx.toDouble(), wz.toDouble())
            indices[i] = biomes.indexOfFirst { it.id == id }.coerceAtLeast(0)
            tops[i] = topBlock(biomes[indices[i]], wx.toDouble(), wz.toDouble(), h)
        }
        if (chunk.worldY > max(SEA_LEVEL, heights.max()) + TreeShape.HEIGHT) return
        // Shared 4-block lattice: interpolation remains identical across chunk boundaries.
        val field = DoubleArray(125)
        for (z in 0..4) for (y in 0..4) for (x in 0..4)
            field[x + 5 * (y + 5 * z)] = caveField((chunk.worldX + x * 4).toDouble(), (chunk.worldY + y * 4).toDouble(), (chunk.worldZ + z * 4).toDouble())
        fun density(x: Int, y: Int, z: Int): Double {
            val gx = x / 4; val gy = y / 4; val gz = z / 4
            val tx = x % 4 / 4.0; val ty = y % 4 / 4.0; val tz = z % 4 / 4.0
            fun plane(dz: Int): Double {
                val i = gx + 5 * (gy + 5 * (gz + dz))
                return mix(mix(field[i], field[i + 1], tx), mix(field[i + 5], field[i + 6], tx), ty)
            }
            return mix(plane(0), plane(1), tz)
        }
        for (z in 0..15) for (x in 0..15) for (y in 0..15) {
            val wx = chunk.worldX + x; val wy = chunk.worldY + y; val wz = chunk.worldZ + z
            val h = heights[z * 16 + x]; val depth = h - wy; val top = tops[z * 16 + x]
            val block = when {
                wy > h -> if (wy == SEA_LEVEL && temperature(wx.toDouble(), wz.toDouble()) < .18) ICE else if (wy <= SEA_LEVEL) WATER else AIR
                // Keep a seabed so ocean sources cannot flood entire cave networks on load.
                density(x, y, z) > 0 && !(h <= SEA_LEVEL && depth < 8) -> AIR
                depth == 0 -> top
                depth < 4 && top in shortArrayOf(SAND, REDSAND, CLAY, MUD, SANDSTONE) -> if (top == SAND || top == REDSAND) SANDSTONE else top
                depth < 4 && top in shortArrayOf(GRASS, FOREST_FLOOR, MOSS, DIRT_SNOW) -> DIRT
                else -> rockAt(wx, wy, wz, depth)
            }
            chunk.setBlock(x, y, z, block)
        }
        landscape.decorate(chunk, heights, tops, indices)
    }
}
