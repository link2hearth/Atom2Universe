package com.Atom2Universe.app.games.caves.world

enum class Biome { DEFAULT, LIMESTONE, GRANITE_ROUGE, MUSHROOM_CAVE, ICE_CAVE }

enum class SurfaceBiome { PLAINS, FOREST, DESERT, ROCKY, RED_DESERT, BIRCH_FOREST }

/**
 * Résultat étendu du biome : biome dominant + biome voisin + facteur de mélange [0..1].
 * blend=0 → 100 % primary ; blend=1 → 100 % secondary (frontière parfaite).
 */
data class BiomeBlend(
    val primary: Biome,
    val secondary: Biome,
    val blend: Float          // 0 = tout primary, 1 = tout secondary
)

internal object BiomeMap {

    // Scale choisie pour des bulles de ~300-600 blocs de rayon (≈ 20-40 chunks).
    private const val SCALE = 0.0015

    // Zone de transition : si l'écart entre les deux meilleurs scores est < TRANSITION_WIDTH,
    // on est dans une zone de frontière et on interpole.
    private const val TRANSITION_WIDTH = 0.18

    // Décalages en unités monde entre les champs de bruit de chaque biome.
    private val OFFSETS = arrayOf(
        doubleArrayOf(    0.0,     0.0,     0.0),   // DEFAULT
        doubleArrayOf( 1000.0,  -700.0,   500.0),   // LIMESTONE
        doubleArrayOf( -800.0,  1200.0,  -600.0),   // GRANITE_ROUGE
        doubleArrayOf(  600.0, -1100.0,   900.0),   // MUSHROOM_CAVE
        doubleArrayOf(-1300.0,   400.0, -1000.0)    // ICE_CAVE
    )

    private val ALL_BIOMES = Biome.values()

    /**
     * Biome simple (rétro-compat) — utilisé pour les décisions binaires (worms, salles…).
     */
    fun biomeAt(chunkX: Int, chunkY: Int, chunkZ: Int, seed: Long): Biome =
        biomeBlendAt(chunkX, chunkY, chunkZ, seed).primary

    /**
     * Biome avec facteur de mélange — utilisé pour les couches de roche et les décos.
     */
    fun biomeBlendAt(chunkX: Int, chunkY: Int, chunkZ: Int, seed: Long): BiomeBlend {
        val cx = chunkX * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val cy = chunkY * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val cz = chunkZ * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val sx = (seed and 0x0FFFFFL).toDouble() * 0.001

        var best = 0;  var bestVal  = Double.NEGATIVE_INFINITY
        var sec  = 0;  var secVal   = Double.NEGATIVE_INFINITY
        for (i in OFFSETS.indices) {
            val n = SimplexNoise.noise(
                (cx + OFFSETS[i][0] + sx) * SCALE,
                (cy + OFFSETS[i][1]) * SCALE,
                (cz + OFFSETS[i][2]) * SCALE
            )
            if (n > bestVal) { sec = best; secVal = bestVal; best = i; bestVal = n }
            else if (n > secVal) { sec = i; secVal = n }
        }

        // blend = 0 au cœur du biome, monte vers 1 à l'approche de la frontière
        val delta = (bestVal - secVal).coerceAtLeast(0.0)
        val blend = (1.0 - delta / TRANSITION_WIDTH).coerceIn(0.0, 1.0).toFloat()

        return BiomeBlend(ALL_BIOMES[best], ALL_BIOMES[sec], blend)
    }

    fun surfaceBiomeAt(wx: Double, wz: Double, seed: Long): SurfaceBiome {
        val s = (seed and 0xFFFFF).toDouble() * 0.0001
        val temp = SimplexNoise.noise(wx * 0.0012 + s + 777.0, wz * 0.0012)
        val hum  = SimplexNoise.noise(wx * 0.0012 + s + 444.0, wz * 0.0012)
        return when {
            temp >  0.55                   -> SurfaceBiome.RED_DESERT   // très chaud → badlands
            temp >  0.30                   -> SurfaceBiome.DESERT
            temp < -0.25                   -> SurfaceBiome.ROCKY
            hum  >  0.40 && temp < 0.10   -> SurfaceBiome.BIRCH_FOREST  // froid+humide → bouleaux
            hum  >  0.20 && temp < 0.15   -> SurfaceBiome.FOREST
            else                           -> SurfaceBiome.PLAINS
        }
    }
}
