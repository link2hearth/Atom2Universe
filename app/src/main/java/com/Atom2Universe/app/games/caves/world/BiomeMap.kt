package com.Atom2Universe.app.games.caves.world

data class BiomeBlend(
    val primary: CaveBiomeDef,
    val secondary: CaveBiomeDef,
    val blend: Float
)

internal object BiomeMap {

    private const val SCALE = 0.0015
    private const val TRANSITION_WIDTH = 0.18

    // Amplitude (en blocs) du domain warp appliqué à l'échantillonnage des biomes de surface.
    // Décale les coordonnées avant l'argmax pour onduler les frontières entre biomes.
    private const val WARP_AMP = 28.0
    private const val WARP_SCALE = 0.012

    // Largeur (en unités de bruit) de la bande de transition où deux biomes mélangent leur
    // relief. Plus large = pentes plus douces entre un biome plat et un biome montagneux.
    private const val HEIGHT_BLEND = 0.22

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

    fun surfaceBiomeAt(wx: Double, wz: Double, seed: Long): SurfaceBiomeDef =
        BiomeRegistry.surfaceBiomes[surfaceBiomeIndexAt(wx, wz, seed)]

    /**
     * Indice du biome de surface en (wx,wz), échantillonné AU BLOC.
     * Un domain warp décale les coordonnées d'échantillonnage avec un bruit moyenne fréquence :
     * les frontières entre biomes deviennent des courbes organiques au lieu de s'aligner sur
     * la grille 16×16 des chunks. Appelé une fois par colonne lors de la génération de surface.
     */
    fun surfaceBiomeIndexAt(wx: Double, wz: Double, seed: Long): Int {
        val sx = (seed and 0x0FFFFFL).toDouble() * 0.001
        val px = wx + SimplexNoise.noise(wx * WARP_SCALE + 1700.0, wz * WARP_SCALE) * WARP_AMP
        val pz = wz + SimplexNoise.noise(wx * WARP_SCALE + 9300.0, wz * WARP_SCALE) * WARP_AMP

        val biomes = BiomeRegistry.surfaceBiomes
        var bestIdx = 0; var bestVal = Double.NEGATIVE_INFINITY
        for (i in biomes.indices) {
            val off = biomes[i].noiseOffsets
            val n = SimplexNoise.noise(
                (px + off[0] + sx) * SCALE,
                0.0,
                (pz + off[2])      * SCALE,
            )
            if (n > bestVal) { bestIdx = i; bestVal = n }
        }
        return bestIdx
    }

    /**
     * Remplit [wOut] (taille = nombre de biomes) avec le poids normalisé de chaque biome en
     * (wx,wz) et retourne l'indice du biome dominant. Les biomes dont le bruit est à moins de
     * [HEIGHT_BLEND] du maximum reçoivent un poids partiel : leur relief se mélange en douceur.
     * Sert au calcul de la hauteur de surface mélangée (les blocs, eux, utilisent le dominant).
     */
    fun biomeWeights(wx: Double, wz: Double, seed: Long, wOut: DoubleArray): Int {
        val sx = (seed and 0x0FFFFFL).toDouble() * 0.001
        val px = wx + SimplexNoise.noise(wx * WARP_SCALE + 1700.0, wz * WARP_SCALE) * WARP_AMP
        val pz = wz + SimplexNoise.noise(wx * WARP_SCALE + 9300.0, wz * WARP_SCALE) * WARP_AMP

        val biomes = BiomeRegistry.surfaceBiomes
        var nMax = Double.NEGATIVE_INFINITY; var dom = 0
        for (i in biomes.indices) {
            val off = biomes[i].noiseOffsets
            val n = SimplexNoise.noise(
                (px + off[0] + sx) * SCALE,
                0.0,
                (pz + off[2])      * SCALE,
            )
            wOut[i] = n
            if (n > nMax) { nMax = n; dom = i }
        }

        var sum = 0.0
        val floor = nMax - HEIGHT_BLEND
        for (i in biomes.indices) {
            val t = wOut[i] - floor
            val v = if (t > 0.0) t else 0.0
            wOut[i] = v; sum += v
        }
        if (sum > 0.0) for (i in biomes.indices) wOut[i] /= sum
        else { for (i in biomes.indices) wOut[i] = 0.0; wOut[dom] = 1.0 }
        return dom
    }
}
