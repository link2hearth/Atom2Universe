package com.Atom2Universe.app.games.caves.world

/**
 * Remplit le monde avec une carte préparée ([A2Map]) au lieu de la génération procédurale.
 *
 * La carte est posée avec son coin (0, 0, 0) au point monde ([originX], [originY], [originZ]).
 * Tout ce qui est hors de la carte reste de l'air.
 */
internal class MapSource(
    val map: A2Map,
    val originX: Int = 0,
    val originY: Int = DEFAULT_ORIGIN_Y,
    val originZ: Int = 0,
) : WorldSource {

    // Pour chaque colonne de la carte : Y monde juste au-dessus de son bloc le plus haut.
    // Calculé une fois ici, parce que la lumière du ciel le demande très souvent.
    private val skyTop = IntArray(map.sizeX * map.sizeZ).also { top ->
        for (z in 0 until map.sizeZ) for (x in 0 until map.sizeX) {
            var y = map.sizeY - 1
            while (y >= 0 && map.blockAt(x, y, z) == AIR) y--
            top[x + z * map.sizeX] = originY + y + 1
        }
    }

    override fun fill(chunk: Chunk) {
        // Partie commune entre le chunk et la carte (bornes hautes exclues).
        val x0 = maxOf(chunk.worldX, originX); val x1 = minOf(chunk.worldX + CHUNK_SIZE, originX + map.sizeX)
        val y0 = maxOf(chunk.worldY, originY); val y1 = minOf(chunk.worldY + CHUNK_SIZE, originY + map.sizeY)
        val z0 = maxOf(chunk.worldZ, originZ); val z1 = minOf(chunk.worldZ + CHUNK_SIZE, originZ + map.sizeZ)
        if (x0 >= x1 || y0 >= y1 || z0 >= z1) return

        for (wy in y0 until y1) for (wz in z0 until z1) for (wx in x0 until x1) {
            val i = map.index(wx - originX, wy - originY, wz - originZ)
            val block = map.blocks[i]
            if (block == AIR) continue
            val lx = wx - chunk.worldX; val ly = wy - chunk.worldY; val lz = wz - chunk.worldZ
            chunk.setBlock(lx, ly, lz, block)
            val m = map.meta[i]
            if (m.toInt() != 0) chunk.setMeta(lx, ly, lz, m)
        }
    }

    override fun skyTopY(wx: Int, wz: Int): Int {
        val mx = wx - originX; val mz = wz - originZ
        if (mx !in 0 until map.sizeX || mz !in 0 until map.sizeZ) return Int.MIN_VALUE
        return skyTop[mx + mz * map.sizeX]
    }

    /**
     * Position des yeux au point d'apparition n° [index] : camp A d'abord, puis camp B.
     * Sans aucune balise, le centre de la carte, posé sur son bloc le plus haut.
     */
    fun spawnPoint(index: Int = 0): FloatArray {
        val all = map.spawnsA + map.spawnsB
        if (all.isEmpty()) {
            val cx = originX + map.sizeX / 2
            val cz = originZ + map.sizeZ / 2
            return floatArrayOf(cx + 0.5f, skyTopY(cx, cz) + EYE_HEIGHT, cz + 0.5f)
        }
        // La balise occupait la case des pieds : les yeux sont 1,62 bloc au-dessus.
        val p = all[index.mod(all.size)]
        return floatArrayOf(originX + p.x + 0.5f, originY + p.y + EYE_HEIGHT, originZ + p.z + 0.5f)
    }

    companion object {
        /** Assez haut pour rester dans la bande « surface » du monde (lumière du ciel). */
        const val DEFAULT_ORIGIN_Y = 64

        /** Hauteur des yeux au-dessus des pieds, comme dans World.findSpawnPoint. */
        private const val EYE_HEIGHT = 1.62f
    }
}
