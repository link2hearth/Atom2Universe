package com.Atom2Universe.app.games.caves.world

data class BiomeBlend(
    val primary: CaveBiomeDef,
    val secondary: CaveBiomeDef,
    val blend: Float
)

internal object BiomeMap {

    private const val SCALE = 0.0015
    private const val TRANSITION_WIDTH = 0.18

    fun biomeAt(chunkX: Int, chunkY: Int, chunkZ: Int, seed: Long): CaveBiomeDef =
        biomeBlendAt(chunkX, chunkY, chunkZ, seed).primary

    fun biomeBlendAt(chunkX: Int, chunkY: Int, chunkZ: Int, seed: Long): BiomeBlend {
        val cx = chunkX * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val cy = chunkY * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val cz = chunkZ * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val sx = (seed and 0x0FFFFFL).toDouble() * 0.001

        val biomes = BiomeRegistry.caveBiomes
        var bestIdx = 0; var bestVal = Double.NEGATIVE_INFINITY
        var secIdx  = 0; var secVal  = Double.NEGATIVE_INFINITY

        for (i in biomes.indices) {
            val off = biomes[i].noiseOffsets
            val n = SimplexNoise.noise(
                (cx + off[0] + sx) * SCALE,
                (cy + off[1])      * SCALE,
                (cz + off[2])      * SCALE,
            )
            if (n > bestVal) { secIdx = bestIdx; secVal = bestVal; bestIdx = i; bestVal = n }
            else if (n > secVal) { secIdx = i; secVal = n }
        }

        val delta = (bestVal - secVal).coerceAtLeast(0.0)
        val blend = (1.0 - delta / TRANSITION_WIDTH).coerceIn(0.0, 1.0).toFloat()
        return BiomeBlend(biomes[bestIdx], biomes[secIdx], blend)
    }

    fun surfaceBiomeAt(wx: Double, wz: Double, seed: Long): SurfaceBiomeDef {
        val sx = (seed and 0x0FFFFFL).toDouble() * 0.001
        val biomes = BiomeRegistry.surfaceBiomes
        var bestIdx = 0; var bestVal = Double.NEGATIVE_INFINITY

        for (i in biomes.indices) {
            val off = biomes[i].noiseOffsets
            val n = SimplexNoise.noise(
                (wx + off[0] + sx) * SCALE,
                0.0,
                (wz + off[2])      * SCALE,
            )
            if (n > bestVal) { bestIdx = i; bestVal = n }
        }
        return biomes[bestIdx]
    }
}
