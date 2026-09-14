package com.Atom2Universe.app.games.caves.world

import kotlin.math.*

/** Decorations are reconstructed from immutable terrain, never from loaded neighbours. */
internal class UndergroundDecor(private val seed: Long, private val terrain: NaturalTerrain) {
    internal enum class Biome(val rock: Short, val floor: Short, val glow: Short) {
        CALCITE(2201, 2613, 2600), ROOTS(STONE, MOSS, 2606),
        FUNGAL(STONE, 2607, 2602), CRYSTAL(QUARTZ, QUARTZ, 2603),
        FROZEN(2612, BLUE_ICE, 2604), BASALT(2307, 2307, 2605)
    }
    private val offset = ((seed xor (seed ushr 32)) and 0xFFFFFF) * .013
    private fun noise(x: Int, y: Int, z: Int, salt: Double, scale: Double) =
        SimplexNoise.noise(x * scale + offset + salt, y * scale, z * scale)

    internal fun biomeAt(x: Int, y: Int, z: Int, height: Int,
        humidity: Double = terrain.humidity(x.toDouble(), z.toDouble()),
        thermal: Double = terrain.temperature(x.toDouble(), z.toDouble())): Biome {
        val depth = height - y
        val wet = humidity + noise(x, y, z, 131.0, .006) * .35
        val region = noise(x, y, z, 711.0, .005)
        // Slightly irregular boundaries within slowly varying, three-dimensional regions.
        val edge = noise(x, y, z, 917.0, .06) * .035
        return when {
            thermal < .30 + edge && depth < 240 -> Biome.FROZEN
            depth > 170 && region > .18 + edge && thermal > .48 -> Biome.BASALT
            depth < 100 && wet > .55 + edge -> Biome.ROOTS
            depth > 45 && wet > .55 + edge && region < .24 -> Biome.FUNGAL
            depth > 70 && region < -.30 + edge -> Biome.CRYSTAL
            else -> Biome.CALCITE
        }
    }

    private fun hash(x: Int, y: Int, z: Int, salt: Long = 0): Long {
        var v = seed xor (x.toLong() * 341873128712L) xor (y.toLong() * 42317861L) xor
            (z.toLong() * 132897987541L) xor salt
        v = (v xor (v ushr 30)) * -4658895280553007687L
        return (v xor (v ushr 27)) and Long.MAX_VALUE
    }

    fun decorate(c: Chunk, heights: IntArray, field: DoubleArray) {
        // No shared mutable world state; buffers belong exclusively to this generation thread.
        val s = Samples(c, terrain, scratch.get()!!, heights, field)
        for (z in 0..15) for (x in 0..15) {
            val h = s.height(x, z)
            for (y in 0..15) {
                val wy = c.worldY + y
                if (h - wy < 12) continue
                val b = c.blockAt(x, y, z)
                // Keep ore deposits intact; palette only coats the walls, floor and ceiling.
                if (b != STONE && b != GRANITE && b != QUARTZ && b != BASALT && b != 2201.toShort()) continue
                val floor = s.air(x, y + 1, z)
                if (!floor && !s.air(x, y - 1, z) && !s.air(x + 1, y, z) &&
                    !s.air(x - 1, y, z) && !s.air(x, y, z + 1) && !s.air(x, y, z - 1)) continue
                val wx = c.worldX + x; val wz = c.worldZ + z
                val biome = biomeAt(wx, wy, wz, h, s.humidity(x, z), s.temperature(x, z))
                val patch = noise(wx, wy, wz, 450.0, .045)
                // Leave bare patches through the coating, especially along transitions.
                c.setBlock(x, y, z, if (patch < -.45) b else if (floor) biome.floor else biome.rock)
                // Roughly one candidate per 9³ cell, only when it reaches an exposed face.
                val lightHash = hash(Math.floorDiv(wx, 9), Math.floorDiv(wy, 9), Math.floorDiv(wz, 9), 83)
                if (Math.floorMod(wx, 9) == (lightHash % 9).toInt() &&
                    Math.floorMod(wy, 9) == (lightHash / 97 % 9).toInt() &&
                    Math.floorMod(wz, 9) == (lightHash / 11 % 9).toInt()) {
                    c.setBlock(x, y, z, biome.glow)
                }
            }
        }
        // Query the analytic support even at y=0: plants do not stop at chunk seams.
        for (z in 0..15) for (x in 0..15) for (y in 0..15) {
            if (c.blockAt(x, y, z) != AIR || s.air(x, y - 1, z) || !s.air(x, y + 1, z)) continue
            val wx = c.worldX + x; val wy = c.worldY + y - 1; val wz = c.worldZ + z
            val h = s.height(x, z)
            if (h - wy < 16 || noise(wx, wy, wz, 450.0, .045) <= .1) continue
            val roll = hash(wx, wy, wz, 221) % 100
            val plant: Short = when (biomeAt(wx, wy, wz, h, s.humidity(x, z), s.temperature(x, z))) {
                Biome.FUNGAL -> if (roll < 9) { if (roll == 0L) 2611 else 7011 } else AIR
                Biome.ROOTS -> if (roll < 7) 7011 else AIR
                else -> AIR
            }
            if (plant != AIR) c.setBlock(x, y, z, plant)
        }
        formations(c, s)
    }

    private fun formations(c: Chunk, s: Samples) {
        // Nine-block cells keep large obstacles separated; maximum horizontal reach is three.
        for (gz in Math.floorDiv(c.worldZ - 3, 9)..Math.floorDiv(c.worldZ + 18, 9))
            for (gx in Math.floorDiv(c.worldX - 3, 9)..Math.floorDiv(c.worldX + 18, 9)) {
                val key = hash(gx, 0, gz, 510)
                val x = gx * 9 + (key % 9).toInt() - c.worldX
                val z = gz * 9 + (key / 11 % 9).toInt() - c.worldZ
                if (x !in -3..18 || z !in -3..18) continue
                val h = s.height(x, z)
                for (y in -8..23) {
                    if (h - (c.worldY + y) < 16 || !s.air(x, y, z)) continue
                    val floor = !s.air(x, y - 1, z)
                    val ceiling = !s.air(x, y + 1, z)
                    if (!floor && !ceiling) continue
                    if ((floor && y > 15) || (!floor && y < 0)) continue
                    val rng = hash(c.worldX + x, c.worldY + y, c.worldZ + z, 817)
                    if (rng % 3 == 0L) continue
                    val biome = biomeAt(c.worldX + x, c.worldY + y, c.worldZ + z, h, s.humidity(x, z), s.temperature(x, z))
                    if (biome == Biome.FUNGAL && !floor) continue
                    val direction = if (floor) 1 else -1
                    var space = 0
                    while (space < 10 && s.air(x, y + direction * space, z)) space++
                    if (space < 5) continue
                    val length = min(8, min(space - 3, 3 + (rng / 7 % 6).toInt()))
                    fun put(dx: Int, dy: Int, dz: Int, block: Short) {
                        val px = x + dx; val py = y + dy; val pz = z + dz
                        if (px in 0..15 && py in 0..15 && pz in 0..15 && s.air(px, py, pz))
                            c.setBlock(px, py, pz, block)
                    }
                    if (floor && (biome == Biome.ROOTS || biome == Biome.BASALT) && rng % 11 == 0L) {
                        // Closed, supported bowls. Never flood-fill an unbounded cave network.
                        val radius = if (biome == Biome.ROOTS) 2 else 1
                        val fits = (-radius..radius).all { dx -> (-radius..radius).all { dz ->
                            !s.air(x + dx, y - 1, z + dz) &&
                                (0..2).all { dy -> s.air(x + dx, y + dy, z + dz) }
                        } }
                        if (fits) {
                            val rim: Short = if (biome == Biome.ROOTS) CLAY else BASALT
                            for (dx in -radius..radius) for (dz in -radius..radius) {
                                put(dx, 0, dz, rim)
                                put(dx, 1, dz, if (abs(dx) == radius || abs(dz) == radius) rim
                                    else if (biome == Biome.ROOTS) WATER else LAVA)
                            }
                            continue
                        }
                    }
                    if (biome == Biome.FUNGAL && floor && space >= 8) {
                        val tall = min(length, space - 3)
                        // Whole crown must fit, including across chunk borders.
                        if ((-2..2).any { dx -> (-2..2).any { dz ->
                                !s.air(x + dx, y + tall, z + dz) || !s.air(x + dx, y + tall - 1, z + dz)
                            } }) continue
                        for (dy in 0 until tall) put(0, dy, 0, 2609)
                        for (dx in -2..2) for (dz in -2..2) if (dx * dx + dz * dz <= 5) {
                            put(dx, tall - 1, dz, 2610)
                            if (abs(dx) + abs(dz) <= 2) put(dx, tall, dz, 2610)
                        }
                        put(1, tall - 1, 0, 2602)
                    } else {
                        val material: Short = when (biome) {
                            Biome.CALCITE -> 2613
                            Biome.ROOTS -> 2608
                            Biome.FUNGAL -> 2607
                            Biome.CRYSTAL -> 2614
                            Biome.FROZEN -> BLUE_ICE
                            Biome.BASALT -> BASALT
                        }
                        // Narrow pendants and tapered floor formations, with supported bases.
                        for (dy in 0 until length) {
                            val radius = if (biome == Biome.ROOTS || dy >= length / 2) 0 else 1
                            for (dx in -radius..radius) for (dz in -radius..radius) {
                                if (abs(dx) + abs(dz) > radius) continue
                                if ((dx != 0 || dz != 0) && s.air(x + dx, y - direction, z + dz)) continue
                                put(dx, dy * direction, dz, material)
                            }
                        }
                        if (biome == Biome.ROOTS && !floor && length >= 4) {
                            val side = if (rng % 2 == 0L) 1 else -1
                            // Small crooked offshoot, attached to the main hanging root.
                            if ((1..2).all { dx -> s.air(x + dx * side, y - 2, z) } &&
                                s.air(x + 2 * side, y - 3, z)) {
                                put(side, -2, 0, 2608)
                                put(2 * side, -2, 0, 2608)
                                put(2 * side, -3, 0, 2608)
                            }
                        }
                        // One luminous accent per some formations, never a fully glowing pillar.
                        if (rng % 4 == 0L) put(0, (length - 1) * direction, 0,
                            if (biome == Biome.ROOTS) 2601 else biome.glow)
                    }
                }
            }
    }

    /** Shared four-block lattice, cached in a bounded halo for cross-chunk formations. */
    private class Buffers {
        val field = DoubleArray(13 * 13 * 13)
        val heights = IntArray(48 * 48)
        val air = ByteArray(48 * 48 * 48)
        val humidity = DoubleArray(48 * 48)
        val temperature = DoubleArray(48 * 48)
    }
    private class Samples(val c: Chunk, val t: NaturalTerrain, val b: Buffers,
        heights: IntArray, field: DoubleArray) {
        init {
            b.field.fill(Double.NaN); b.heights.fill(Int.MIN_VALUE); b.air.fill(0)
            b.humidity.fill(Double.NaN); b.temperature.fill(Double.NaN)
            // The terrain was just generated: seed the interior instead of computing it twice.
            for (z in 0..15) for (x in 0..15) {
                b.heights[x + 12 + (z + 12) * 48] = heights[x + z * 16]
                for (y in 0..15) b.air[x + 12 + 48 * (y + 12 + 48 * (z + 12))] =
                    if (c.blockAt(x, y, z) == AIR) 1 else 2
            }
            for (z in 0..4) for (y in 0..4) for (x in 0..4)
                b.field[x + 3 + 13 * (y + 3 + 13 * (z + 3))] = field[x + 5 * (y + 5 * z)]
        }
        fun humidity(x: Int, z: Int): Double {
            val i = x + 12 + (z + 12) * 48
            if (b.humidity[i].isNaN()) b.humidity[i] = t.humidity((c.worldX+x).toDouble(), (c.worldZ+z).toDouble())
            return b.humidity[i]
        }
        fun temperature(x: Int, z: Int): Double {
            val i = x + 12 + (z + 12) * 48
            if (b.temperature[i].isNaN()) b.temperature[i] = t.temperature((c.worldX+x).toDouble(), (c.worldZ+z).toDouble())
            return b.temperature[i]
        }
        fun height(x: Int, z: Int): Int {
            val i = x + 12 + (z + 12) * 48
            if (b.heights[i] == Int.MIN_VALUE) b.heights[i] = t.height((c.worldX + x).toDouble(), (c.worldZ + z).toDouble()).toInt()
            return b.heights[i]
        }
        private fun field(x: Int, y: Int, z: Int): Double {
            val i = x + 13 * (y + 13 * z)
            if (b.field[i].isNaN()) b.field[i] = t.caveField((c.worldX - 12 + x * 4).toDouble(),
                (c.worldY - 12 + y * 4).toDouble(), (c.worldZ - 12 + z * 4).toDouble())
            return b.field[i]
        }
        fun air(x: Int, y: Int, z: Int): Boolean {
            val ax = x + 12; val ay = y + 12; val az = z + 12
            val i = ax + 48 * (ay + 48 * az)
            if (b.air[i].toInt() != 0) return b.air[i].toInt() == 1
            val h = height(x, z); val wy = c.worldY + y
            val result = if (wy > h) wy > NaturalTerrain.SEA_LEVEL
                else if (h <= NaturalTerrain.SEA_LEVEL && h - wy < 8) false
                else {
                    val gx = ax / 4; val gy = ay / 4; val gz = az / 4
                    val tx = ax % 4 / 4.0; val ty = ay % 4 / 4.0; val tz = az % 4 / 4.0
                    fun mix(a: Double, v: Double, f: Double) = a + (v - a) * f
                    fun plane(dz: Int) = mix(mix(field(gx, gy, gz + dz), field(gx + 1, gy, gz + dz), tx),
                        mix(field(gx, gy + 1, gz + dz), field(gx + 1, gy + 1, gz + dz), tx), ty)
                    mix(plane(0), plane(1), tz) > 0
                }
            b.air[i] = if (result) 1 else 2
            return result
        }
    }
    companion object { private val scratch = ThreadLocal.withInitial { Buffers() } }
}
