package com.Atom2Universe.app.games.caves.world

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

/** Whole sites are planned before chunk clipping; neither slope checks nor RNG depend on loading order. */
internal class ColdLandscape(
    private val seed: Long,
    private val height: (Int, Int) -> Int,
    private val biome: (Int, Int) -> String,
    private val snowy: (Int, Int) -> Boolean,
    private val blocked: (Int, Int) -> Boolean
) {
    private data class Site(val x: Int, val z: Int, val kind: Int, val seed: Long)
    private val sites = ConcurrentHashMap<Pair<Int, Int>, Site>()
    private val absent = Site(0, 0, -1, 0)

    private fun site(cx: Int, cz: Int): Site? {
        val result = sites.getOrPut(cx to cz) {
            if (sites.size > 4096) sites.clear()
            val s = seed xor (cx.toLong() * 341873128712L) xor (cz.toLong() * 132897987541L) xor 618923L
            val rng = Random(s)
            val x = cx * CELL + 24 + rng.nextInt(16)
            val z = cz * CELL + 24 + rng.nextInt(16)
            val region = biome(x, z)
            if (region !in setOf("tundra", "taiga") || rng.nextFloat() > .35f) return@getOrPut absent
            // Taiga favors dead wood; tundra favors exposed stone and ice. Circles stay rare.
            val roll = rng.nextInt(100)
            var kind = when {
                roll < 4 -> 6
                roll < 10 -> 7
                roll < 23 -> 3
                region == "taiga" && roll < 50 -> 1
                region == "taiga" && roll < 70 -> 4
                roll < 48 -> 0
                roll < 66 -> 2
                roll < 82 -> 5
                else -> 4
            }
            // An unsnowy taiga gets wood and rock, not isolated ice formations.
            if (kind in setOf(0, 3) && !snowy(x, z)) kind = if (kind == 0) 5 else 1
            val h = height(x, z)
            if (h <= 76) return@getOrPut absent
            for (dz in -7..7) for (dx in -7..7) {
                if (biome(x + dx, z + dz) != region || blocked(x + dx, z + dz)) return@getOrPut absent
                val delta = height(x + dx, z + dz) - h
                if (abs(delta) > if (kind == 3) 1 else 2) return@getOrPut absent
            }
            Site(x, z, kind, s)
        }
        return result.takeIf { it.kind >= 0 }
    }

    fun reserves(x: Int, z: Int, margin: Int = 0): Boolean {
        for (cz in Math.floorDiv(z - 7 - margin, CELL)..Math.floorDiv(z + 7 + margin, CELL))
            for (cx in Math.floorDiv(x - 7 - margin, CELL)..Math.floorDiv(x + 7 + margin, CELL)) {
                val s = site(cx, cz) ?: continue
                if (abs(s.x - x) <= 7 + margin && abs(s.z - z) <= 7 + margin) return true
            }
        return false
    }

    fun decorate(chunk: Chunk) {
        for (cz in Math.floorDiv(chunk.worldZ - 7, CELL)..Math.floorDiv(chunk.worldZ + 22, CELL))
            for (cx in Math.floorDiv(chunk.worldX - 7, CELL)..Math.floorDiv(chunk.worldX + 22, CELL)) {
                val s = site(cx, cz) ?: continue
                generate(s.kind, Random(s.seed), { x, z -> height(s.x + x, s.z + z) },
                    { x, z -> snowy(s.x + x, s.z + z) }) { x, y, z, id, meta ->
                    val lx = s.x + x - chunk.worldX; val ly = y - chunk.worldY; val lz = s.z + z - chunk.worldZ
                    if (lx in 0..15 && ly in 0..15 && lz in 0..15) {
                        chunk.setBlock(lx, ly, lz, id)
                        chunk.setMeta(lx, ly, lz, meta)
                    }
                }
            }
    }

    companion object {
        // Neighboring candidates are at least 49 blocks apart, leaving 35 blocks between footprints.
        const val CELL = 64
        const val MARBLE: Short = 2321
        const val MONOLITH: Short = 2322
        const val CRACKED_ICE: Short = 5003
        const val LICHEN_STONE: Short = 2320

        /** Recipes can also be displayed on flat exhibition ground. No water source or air carving. */
        fun generate(kind: Int, rng: Random, ground: (Int, Int) -> Int,
                     snowAt: (Int, Int) -> Boolean = { _, _ -> true },
                     put: (Int, Int, Int, Short, Byte) -> Unit) {
            fun column(x: Int, z: Int, h: Int, id: Short, snow: Boolean = true) {
                val floor = ground(x, z)
                for (y in 1..h) put(x, floor + y, z, id, 0)
                if (snow && snowAt(x, z)) put(x, floor + h + 1, z, SNOW, 0)
            }
            fun stone(x: Int, z: Int, h: Int) {
                column(x, z, h, LICHEN_STONE, false)
                column(x + 1, z, (h - 1).coerceAtLeast(1), STONE)
            }
            val monumentMaterial = if (rng.nextBoolean()) MARBLE else MONOLITH
            fun menhir(x: Int, z: Int, h: Int) {
                val base = (-1..1).maxOf { dx -> (0..1).maxOf { dz -> ground(x + dx, z + dz) } }
                val chippedSide = if (rng.nextBoolean()) -1 else 1
                // A single broad upright mass: buried foot, tapered shoulders and an uneven summit.
                for (dz in 0..1) for (dx in -1..1) {
                    val top = h - if (dx == chippedSide) 2 else if (dx != 0 || dz == 1) 1 else 0
                    for (y in ground(x + dx, z + dz)..base + top)
                        put(x + dx, y, z + dz, monumentMaterial, 0)
                    if (snowAt(x + dx, z + dz)) put(x + dx, base + top + 1, z + dz, SNOW, 0)
                }
            }
            when (kind) {
                0 -> { // Broken ice outcrop, with a low skirt and a blue core.
                    for (z in -3..3) for (x in -4..4) if (x*x + z*z*2 < 19)
                        column(x, z, if (abs(x) + abs(z) < 3) 2 + rng.nextInt(3) else 1,
                            if (rng.nextInt(4) == 0) BLUE_ICE else CRACKED_ICE, rng.nextBoolean())
                }
                1 -> { // Split fallen trunk: broken roof, jagged tapered end and an open rotten heart.
                    val alongX = rng.nextBoolean()
                    val length = 4 + rng.nextInt(3)
                    val base = (-3..length).maxOf { t -> (-1..1).maxOf { side ->
                        if (alongX) ground(t, side) else ground(side, t)
                    } }
                    for (t in -3..length) for (side in -1..1) for (dy in 0..2) {
                        if (side == 0 && dy == 1 && t != -3) continue
                        if (t == length && (side != 0 || dy == 2)) continue
                        if (dy == 2 && (t >= 1 || (t == 0 && side >= 0) || (t == -3 && side == -1))) continue
                        if (side == 1 && dy == 1 && t >= length - 2) continue
                        val x = if (alongX) t else side; val z = if (alongX) side else t
                        // Support the underside continuously on uneven ground.
                        if (dy == 0) for (y in ground(x, z) + 1..base) put(x, y, z, WOOD_SAPIN, if (alongX) 1 else 2)
                        put(x, base + 1 + dy, z, WOOD_SAPIN, if (alongX) 1 else 2)
                        if (dy == 2 && rng.nextInt(3) != 0 && snowAt(x, z)) put(x, base + 4, z, SNOW, 0)
                    }
                    // Short root remnants and a ragged stump, kept within the site footprint.
                    for (side in listOf(-2, 2)) {
                        val x = if (alongX) -2 else side
                        val z = if (alongX) side else -2
                        column(x, z, 1, WOOD_SAPIN, false)
                    }
                    column(-5, -3, 2, WOOD_SAPIN, false)
                    column(-4, -3, 1, WOOD_SAPIN)
                    column(-5, -2, 1, WOOD_SAPIN, false)
                }
                2 -> menhir(0, 0, 8 + rng.nextInt(4))
                3 -> { // Flat frozen pond with sealed bed and a continuous solid bank.
                    val y = (-4..4).maxOf { z -> (-5..5).maxOf { x -> ground(x, z) } }
                    for (z in -4..4) for (x in -5..5) {
                        val d = x*x / 25.0 + z*z / 16.0
                        if (d > 1.2) continue
                        put(x, y - 1, z, CLAY, 0)
                        put(x, y, z, if (d < .8) { if (rng.nextInt(4) == 0) CRACKED_ICE else ICE } else LICHEN_STONE, 0)
                        if (d >= .8 && rng.nextInt(3) == 0 && snowAt(x, z)) put(x, y + 1, z, SNOW, 0)
                    }
                }
                4 -> { // Wind-bent snag, sometimes with a sparse living crown.
                    val h = 3 + rng.nextInt(3); val y = ground(0, 0)
                    column(0, 0, h, WOOD_SAPIN, false)
                    for (x in 1..3) put(x, y + h, 0, WOOD_SAPIN, 1)
                    put(2, y + h + 1, 0, WOOD_SAPIN, 0)
                    put(-1, y + 2, 0, WOOD_SAPIN, 1)
                    if (rng.nextBoolean()) for (z in -1..1) for (x in 1..3)
                        put(x, y + h + 2, z, LEAVES_SAPIN, 0)
                }
                5 -> { stone(-2, -1, 1); stone(2, 1, 2); column(0, 3, 1, LICHEN_STONE) }
                6 -> { // Deliberately incomplete ring with an entrance facing south.
                    for ((x, z) in listOf(-4 to 0, -3 to -3, 0 to -4, 3 to -3, 4 to 0, -3 to 3))
                        menhir(x, z, 5 + rng.nextInt(4))
                }
                7 -> for (x in -4..4 step 4) menhir(x, 0, 6 + rng.nextInt(4))
            }
            // Sparse dry vegetation and lichen stones tie each group to its surroundings.
            for (i in 0..5) {
                val x = if (rng.nextBoolean()) -7 else 7; val z = rng.nextInt(-6, 7)
                put(x, ground(x, z) + 1, z, if (rng.nextInt(3) == 0) ROCK_MOSS else GRASS_TAN, 0)
            }
        }
    }
}
