package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry

internal object LodBuilder {

    fun buildColumn(cx: Int, cz: Int, world: World, cache: LodCache? = null): FloatArray {
        val H = CHUNK_SIZE
        val buf = Buf()

        // ── Heightmap ────────────────────────────────────────────────────────
        // Hauteur (absY du bloc le plus haut) et type pour chaque cellule (lx, lz).
        // Int.MIN_VALUE = colonne vide ou non générée.
        //
        // On part du cache (hauteurs déjà vues) puis on fusionne avec les chunks actuellement
        // chargés en gardant le MAX par cellule : une montagne grandit dans le LOD au fur et à
        // mesure qu'on l'explore en hauteur, et ne rétrécit pas quand on s'en éloigne (les chunks
        // hauts se déchargent mais la hauteur mémorisée persiste). Les éditions joueur invalident
        // le cache (CaveRenderer), forçant une reconstruction propre à partir des chunks chargés.
        val cached = cache?.get(cx, cz)
        val heights   = IntArray(H * H) { i ->
            val c = cached?.heights?.get(i)
            if (c == null || c == Short.MIN_VALUE) Int.MIN_VALUE else c.toInt()
        }
        val topBlocks = ShortArray(H * H) { i -> cached?.blocks?.get(i) ?: AIR }

        // Chunks chargés de la colonne, du plus haut au plus bas (un seul balayage cy).
        val loaded = ArrayList<Chunk>()
        for (cy in SURFACE_CY_MAX downTo -2) {
            val chunk = world.getChunk(cx, cy, cz) ?: continue
            if (chunk.generated) loaded.add(chunk)
        }

        // Le scan 256 cellules + la réécriture du cache ne servent que si les chunks chargés
        // peuvent DÉPASSER la hauteur déjà mémorisée. Cas courant (terrain plat déjà visité,
        // ou tout le ring au démarrage) : on saute, et on rebâtit le mesh à partir du cache.
        var cachedMax = Int.MIN_VALUE
        if (cached != null) for (h in heights) if (h > cachedMax) cachedMax = h
        val topLoadedY = if (loaded.isNotEmpty()) loaded[0].cy * H + (H - 1) else Int.MIN_VALUE
        val canGrow = loaded.isNotEmpty() && (cached == null || topLoadedY > cachedMax)

        if (canGrow) {
            for (lz in 0 until H) for (lx in 0 until H) {
                val idx = lz * H + lx
                for (chunk in loaded) {                 // haut → bas : 1er bloc solide = sommet chargé
                    var hit = false
                    for (ly in H - 1 downTo 0) {
                        val b = chunk.blockAt(lx, ly, lz)
                        if (b == AIR || isDecoration(b) || isWater(b)) continue
                        val wy = chunk.cy * H + ly
                        if (wy > heights[idx]) { heights[idx] = wy; topBlocks[idx] = b }
                        hit = true; break
                    }
                    if (hit) break
                }
            }
            if (cache != null) {
                val shortH = ShortArray(H * H) { i ->
                    heights[i].let { if (it == Int.MIN_VALUE) Short.MIN_VALUE else it.toShort() }
                }
                cache.put(cx, cz, shortH, topBlocks.copyOf())
            }
        }

        // ── Faces supérieures (couleur du bloc, pleine lumière) ───────────────
        for (lz in 0 until H) for (lx in 0 until H) {
            val h = heights[lz * H + lx]
            if (h == Int.MIN_VALUE) continue
            val c = BlockRegistry.getColor(topBlocks[lz * H + lx])
            val r = ((c ushr 16) and 0xFF) / 255f
            val g = ((c ushr 8)  and 0xFF) / 255f
            val b =  (c          and 0xFF) / 255f
            val x = lx.toFloat(); val z = lz.toFloat(); val y1 = (h + 1).toFloat()
            buf.add6(x,    y1, z,    r, g, b); buf.add6(x+1f, y1, z,    r, g, b)
            buf.add6(x+1f, y1, z+1f, r, g, b); buf.add6(x,    y1, z,    r, g, b)
            buf.add6(x+1f, y1, z+1f, r, g, b); buf.add6(x,    y1, z+1f, r, g, b)
        }

        // ── Faces latérales : comble l'écart de hauteur avec les voisins ─────
        // Couleur du bloc × lumière de face (côtés X 0.72, côtés Z 0.62) bakée dans le vertex.
        for (lz in 0 until H) for (lx in 0 until H) {
            val h = heights[lz * H + lx]
            if (h == Int.MIN_VALUE) continue
            val c = BlockRegistry.getColor(topBlocks[lz * H + lx])
            val br = ((c ushr 16) and 0xFF) / 255f
            val bg = ((c ushr 8)  and 0xFF) / 255f
            val bb =  (c          and 0xFF) / 255f
            val xr = br * 0.72f; val xg = bg * 0.72f; val xb = bb * 0.72f   // côtés X
            val zr = br * 0.62f; val zg = bg * 0.62f; val zb = bb * 0.62f   // côtés Z
            val y1 = (h + 1).toFloat()

            // +X
            val hPX = if (lx < H - 1) heights[lz * H + lx + 1]
                      else adjHeight(cx + 1, cz, 0, lz, world, cache)
            if (hPX != Int.MIN_VALUE && hPX < h) {
                val yb = (hPX + 1).toFloat()
                val x = (lx + 1).toFloat(); val z = lz.toFloat()
                buf.add6(x, y1, z,    xr, xg, xb); buf.add6(x, y1, z+1f, xr, xg, xb)
                buf.add6(x, yb, z+1f, xr, xg, xb); buf.add6(x, y1, z,    xr, xg, xb)
                buf.add6(x, yb, z+1f, xr, xg, xb); buf.add6(x, yb, z,    xr, xg, xb)
            }

            // -X
            val hMX = if (lx > 0) heights[lz * H + lx - 1]
                      else adjHeight(cx - 1, cz, H - 1, lz, world, cache)
            if (hMX != Int.MIN_VALUE && hMX < h) {
                val yb = (hMX + 1).toFloat()
                val x = lx.toFloat(); val z = lz.toFloat()
                buf.add6(x, y1, z+1f, xr, xg, xb); buf.add6(x, y1, z,    xr, xg, xb)
                buf.add6(x, yb, z,    xr, xg, xb); buf.add6(x, y1, z+1f, xr, xg, xb)
                buf.add6(x, yb, z,    xr, xg, xb); buf.add6(x, yb, z+1f, xr, xg, xb)
            }

            // +Z
            val hPZ = if (lz < H - 1) heights[(lz + 1) * H + lx]
                      else adjHeight(cx, cz + 1, lx, 0, world, cache)
            if (hPZ != Int.MIN_VALUE && hPZ < h) {
                val yb = (hPZ + 1).toFloat()
                val x = lx.toFloat(); val z = (lz + 1).toFloat()
                buf.add6(x+1f, y1, z, zr, zg, zb); buf.add6(x,    y1, z, zr, zg, zb)
                buf.add6(x,    yb, z, zr, zg, zb); buf.add6(x+1f, y1, z, zr, zg, zb)
                buf.add6(x,    yb, z, zr, zg, zb); buf.add6(x+1f, yb, z, zr, zg, zb)
            }

            // -Z
            val hMZ = if (lz > 0) heights[(lz - 1) * H + lx]
                      else adjHeight(cx, cz - 1, lx, H - 1, world, cache)
            if (hMZ != Int.MIN_VALUE && hMZ < h) {
                val yb = (hMZ + 1).toFloat()
                val x = lx.toFloat(); val z = lz.toFloat()
                buf.add6(x,    y1, z, zr, zg, zb); buf.add6(x+1f, y1, z, zr, zg, zb)
                buf.add6(x+1f, yb, z, zr, zg, zb); buf.add6(x,    y1, z, zr, zg, zb)
                buf.add6(x+1f, yb, z, zr, zg, zb); buf.add6(x,    yb, z, zr, zg, zb)
            }
        }

        return buf.toArray()
    }

    // Hauteur du voisin : chunk monde en priorité, sinon cache LOD.
    private fun adjHeight(cx: Int, cz: Int, lx: Int, lz: Int, world: World, cache: LodCache?): Int {
        val worldH = columnHeight(cx, cz, lx, lz, world)
        if (worldH != Int.MIN_VALUE) return worldH
        val entry = cache?.get(cx, cz) ?: return Int.MIN_VALUE
        val idx = lz * CHUNK_SIZE + lx
        val h = entry.heights[idx]
        return if (h == Short.MIN_VALUE) Int.MIN_VALUE else h.toInt()
    }

    // Hauteur du bloc le plus haut en (lx, lz) dans la colonne d'un chunk adjacent.
    private fun columnHeight(cx: Int, cz: Int, lx: Int, lz: Int, world: World): Int {
        for (cy in SURFACE_CY_MAX downTo -2) {
            val chunk = world.getChunk(cx, cy, cz) ?: continue
            if (!chunk.generated) continue
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b == AIR || isDecoration(b) || isWater(b)) continue
                return cy * CHUNK_SIZE + ly
            }
        }
        return Int.MIN_VALUE
    }

    private class Buf(cap: Int = 8192) {
        private var data = FloatArray(cap); private var n = 0
        fun add(v: Float) { if (n == data.size) data = data.copyOf(n * 2); data[n++] = v }
        fun add6(x:Float,y:Float,z:Float,r:Float,g:Float,b:Float) { add(x);add(y);add(z);add(r);add(g);add(b) }
        fun toArray() = data.copyOf(n)
    }
}
