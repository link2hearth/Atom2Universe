package com.Atom2Universe.app.games.caves.world

enum class Biome { DEFAULT, LIMESTONE, GRANITE_ROUGE, MUSHROOM_CAVE, ICE_CAVE }

internal object BiomeMap {

    // Scale choisie pour des bulles de ~300-600 blocs de rayon (≈ 20-40 chunks).
    private const val SCALE = 0.0015

    // Décalages en unités monde entre les champs de bruit de chaque biome.
    // Suffisamment larges (>1/SCALE) pour une décorrélation totale entre champs.
    private val OFFSETS = arrayOf(
        doubleArrayOf(    0.0,     0.0,     0.0),   // DEFAULT
        doubleArrayOf( 1000.0,  -700.0,   500.0),   // LIMESTONE
        doubleArrayOf( -800.0,  1200.0,  -600.0),   // GRANITE_ROUGE
        doubleArrayOf(  600.0, -1100.0,   900.0),   // MUSHROOM_CAVE
        doubleArrayOf(-1300.0,   400.0, -1000.0)    // ICE_CAVE
    )

    /**
     * Retourne le biome du chunk (chunkX, chunkY, chunkZ).
     * Évalué au centre du chunk pour éviter les artefacts de bord.
     * Le seed décale l'ensemble du paysage de biomes pour chaque monde.
     */
    fun biomeAt(chunkX: Int, chunkY: Int, chunkZ: Int, seed: Long): Biome {
        val cx = chunkX * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val cy = chunkY * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val cz = chunkZ * CHUNK_SIZE + CHUNK_SIZE * 0.5
        val sx = (seed and 0x0FFFFFL).toDouble() * 0.001  // décalage unique par monde

        var best = 0
        var bestVal = Double.NEGATIVE_INFINITY
        for (i in OFFSETS.indices) {
            val n = SimplexNoise.noise(
                (cx + OFFSETS[i][0] + sx) * SCALE,
                (cy + OFFSETS[i][1]) * SCALE,
                (cz + OFFSETS[i][2]) * SCALE
            )
            if (n > bestVal) { bestVal = n; best = i }
        }
        return Biome.values()[best]
    }
}
